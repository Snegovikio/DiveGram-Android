package org.telegram.ui.Components;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FourierTransform;
import org.telegram.messenger.MediaController;
import org.telegram.ui.ActionBar.Theme;

/**
 * Живой анализатор спектра музыки: небольшой блок столбиков в левом нижнем
 * углу, логарифмическая шкала, плавный подъём и затухание с пиковыми метками.
 * Данные берутся напрямую из аудиопотока плеера (TeeAudioProcessor), поэтому
 * разрешение на микрофон не нужно.
 */
public class SpectrumView extends View implements VideoPlayer.AudioSpectrumListener {

    public interface Delegate {
        void onSpectrumStateChanged(boolean available);
    }

    private final android.graphics.Paint barPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Paint peakPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

    private static final float MIN_HZ = 30f;
    private static final float MAX_HZ = 16000f;
    private static final float MIN_DB = -68f;

    private float[] target = new float[0];
    private float[] values = new float[0];
    private float[] peaks = new float[0];
    private int barCount;

    private final FourierTransform.FFT fft = new FourierTransform.FFT(1024, 48000);
    private final float[] samples = new float[1024];
    private final float[] levels = new float[72];
    private final float[] mags = new float[72];
    private float sampleRate = 96000f;
    private float gain = 0.0001f;
    private float frameMax;
    private long lastBindCheck;

    private boolean listening;
    private boolean active = true;
    private long lastData;
    private boolean available;
    private final Delegate delegate;

    private final Runnable updateRunnable = this::postInvalidateOnAnimation;

    private final Theme.ResourcesProvider resourcesProvider;

    public SpectrumView(Context context, Theme.ResourcesProvider resourcesProvider, Delegate delegate) {
        super(context);
        this.delegate = delegate;
        this.resourcesProvider = resourcesProvider;
        updateColors();
    }

    private int baseColor;
    private int gradientHeight;

    /** Пересчёт цветов под текущую тему (светлая/тёмная, свой акцент). */
    public void updateColors() {
        final boolean dark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        final int accent = resourcesProvider != null
                ? resourcesProvider.getColor(Theme.key_telegram_color)
                : Theme.getColor(Theme.key_telegram_color);
        // на светлом фоне столбики затемняем, иначе они сливаются с подложкой
        baseColor = dark ? accent : androidx.core.graphics.ColorUtils.blendARGB(accent, 0xff101010, 0.28f);
        gradientHeight = 0;
        barPaint.setColor(baseColor);
        peakPaint.setColor(Theme.multAlpha(baseColor, dark ? 0.55f : 0.45f));
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }

    public void start() {
        if (listening) {
            return;
        }
        MediaController.getInstance().setAudioSpectrumListener(this);
        listening = true;
        lastBindCheck = System.currentTimeMillis();
    }

    public void stop() {
        if (!listening) {
            return;
        }
        MediaController.getInstance().setAudioSpectrumListener(null);
        listening = false;
        removeCallbacks(updateRunnable);
    }

    public void setActive(boolean active) {
        if (this.active == active) {
            return;
        }
        this.active = active;
        if (active) {
            postInvalidateOnAnimation();
        }
    }

    public boolean isAvailable() {
        return available;
    }

    @Override
    public void onAudioSpectrum(float[] data, int length, int rate) {
        if (data == null || length <= 0) {
            return;
        }
        final int size = Math.min(length, samples.length);
        System.arraycopy(data, 0, samples, 0, size);
        if (size < samples.length) {
            java.util.Arrays.fill(samples, size, samples.length, 0f);
        }
        if (rate > 0 && rate != (int) sampleRate) {
            sampleRate = rate;
        }
        fft.forward(samples);

        final float[] specReal = fft.getSpectrumReal();
        final float[] specImag = fft.getSpectrumImaginary();
        final int bins = Math.min(specReal.length, specImag.length);
        final float hzPerBin = (sampleRate / 2f) / bins;
        frameMax = 0f;
        final int count = barCount > 0 ? barCount : Math.min(levels.length, Math.max(12, getWidth() / Math.max(1, dp(7))));
        for (int i = 0; i < count; i++) {
            float f0 = (float) (MIN_HZ * Math.pow(MAX_HZ / MIN_HZ, (float) i / count));
            float f1 = (float) (MIN_HZ * Math.pow(MAX_HZ / MIN_HZ, (float) (i + 1) / count));
            int from = Math.max(1, Math.min(bins - 1, (int) (f0 / hzPerBin)));
            int to = Math.max(from + 1, Math.min(bins, (int) Math.ceil(f1 / hzPerBin)));
            float max = 0;
            for (int b = from; b < to; b++) {
                float re = specReal[b];
                float im = specImag[b];
                float mag = (float) Math.sqrt(re * re + im * im);
                if (mag > max) {
                    max = mag;
                }
            }
            mags[i] = max;
            if (max > frameMax) {
                frameMax = max;
            }
        }
        // авто-усиление: приглушённая громкость и тихие треки тоже читаются,
        // картинка живая, а не «зацикленная»
        frameMax *= 0.86f;
        if (frameMax > gain) {
            gain = frameMax;
        } else {
            gain += (frameMax - gain) * 0.08f;
        }
        final float ceiling = Math.max(gain, fft.timeSize() * 0.004f);
        final float floorLevel = ceiling * 0.07f;
        for (int i = 0; i < count; i++) {
            float level = (mags[i] - floorLevel) / (ceiling - floorLevel);
            level = level < 0f ? 0f : (level > 1f ? 1f : level);
            levels[i] = (float) Math.pow(level, 0.62f);
        }
        post(() -> {
            lastData = System.currentTimeMillis();
            if (!available) {
                available = true;
                if (delegate != null) {
                    delegate.onSpectrumStateChanged(true);
                }
            }
            if (barCount == 0) {
                final int width = getWidth();
                if (width <= 0) {
                    return;
                }
                buildBars(width);
            }
            if (barCount > levels.length) {
                buildBars(Math.max(dp(4) * levels.length, getWidth()));
            }
            System.arraycopy(levels, 0, target, 0, Math.min(barCount, levels.length));
            postInvalidateOnAnimation();
        });
    }

    private void buildBars(int width) {
        barCount = Math.max(16, Math.min(levels.length, width / Math.max(1, dp(8))));
        target = new float[barCount];
        values = new float[barCount];
        peaks = new float[barCount];
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && (barCount == 0 || w / Math.max(1, dp(8)) != barCount)) {
            buildBars(w);
        }
    }

    @Override
    protected void onDraw(@NonNull android.graphics.Canvas canvas) {
        super.onDraw(canvas);

        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (barCount == 0) {
            buildBars(w);
        }
        if (barCount == 0) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now - lastData > 1500) {
            if (available) {
                available = false;
                if (delegate != null) {
                    delegate.onSpectrumStateChanged(false);
                }
            }
            if (listening && now - lastBindCheck > 1000) {
                // плеер мог пересоздаться (смена трека) — переподписываемся
                lastBindCheck = now;
                MediaController.getInstance().setAudioSpectrumListener(this);
            }
        }

        final boolean stale = System.currentTimeMillis() - lastData > 400;

        // блок столбиков на всю свободную ширину, прижат к низу
        final float blockWidth = w - dp(16);
        final float left = dp(8);
        final float blockHeight = Math.min(h - dp(8), dp(110));
        final float bottom = h - dp(4);
        final float slot = blockWidth / barCount;
        final float barWidth = Math.max(dp(2), slot * 0.62f);
        final float maxHeight = blockHeight;

        ensureGradient((int) blockHeight);

        for (int i = 0; i < barCount; i++) {
            final float goal = active && !stale ? target[i] : 0f;
            float value = values[i];
            value += (goal - value) * (goal > value ? 0.7f : 0.2f);
            if (value < 0.002f) {
                value = 0f;
            }
            values[i] = value;

            float peak = peaks[i];
            if (value >= peak) {
                peak = value;
            } else {
                peak -= 0.005f;
                if (peak < 0) {
                    peak = 0;
                }
            }
            peaks[i] = peak;

            final float x = left + i * slot;
            final float barHeight = value * maxHeight;
            if (barHeight > 0) {
                barPaint.setAlpha((int) (60 + 195 * Math.min(1f, value * 1.6f)));
                canvas.drawRect(x, bottom - barHeight, x + barWidth, bottom, barPaint);
            }

            if (peak > 0.02f) {
                final float peakHeight = peak * maxHeight;
                peakPaint.setAlpha((int) (60 + 140 * peak));
                canvas.drawRect(x, bottom - peakHeight - dp(2), x + barWidth, bottom - peakHeight, peakPaint);
            }
        }

        if (active) {
            removeCallbacks(updateRunnable);
            post(updateRunnable);
        }
    }

    public void releaseResources() {
        stop();
    }

    private void ensureGradient(int height) {
        if (gradientHeight == height) {
            return;
        }
        gradientHeight = height;
        int top = Theme.blendOver(baseColor, 0xFFFFFFFF);
        int bottomColor = Theme.multAlpha(baseColor, 0.85f);
        barPaint.setShader(new android.graphics.LinearGradient(
                0, 0, 0, height,
                new int[]{top, baseColor, bottomColor},
                new float[]{0f, 0.45f, 1f},
                android.graphics.Shader.TileMode.CLAMP));
    }

    private int dp(float value) {
        return AndroidUtilities.dp(value);
    }
}