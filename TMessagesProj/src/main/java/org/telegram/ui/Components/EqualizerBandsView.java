package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * Пять вертикальных ползунков эквалайзера (60 / 230 / 910 / 3.6k / 14k Гц).
 */
public class EqualizerBandsView extends View {

    public interface Delegate {
        void onBandValueChanged(int index, float db);
        void onBandsReleased();
    }

    public static final float MIN_DB = -12f;
    public static final float MAX_DB = 12f;

    public static final int[] BAND_HZ = {60, 230, 910, 3600, 14000};

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private final float[] values = new float[BAND_HZ.length];
    private final int[] thumbColors = new int[BAND_HZ.length];

    private int activeIndex = -1;
    private boolean enabled = true;
    private Delegate delegate;
    private Theme.ResourcesProvider resourcesProvider;

    private int accentColor;
    private int trackColor;
    private int textColor;
    private int labelColor;

    public EqualizerBandsView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setTextAlign(Paint.Align.CENTER);
        updateColors();
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void setEnabledState(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        invalidate();
    }

    public void setValues(float[] values) {
        if (values == null) {
            return;
        }
        for (int i = 0; i < BAND_HZ.length; i++) {
            this.values[i] = i < values.length ? Math.max(MIN_DB, Math.min(MAX_DB, values[i])) : 0f;
        }
        invalidate();
    }

    public float[] getValues() {
        return values.clone();
    }

    public void updateColors() {
        accentColor = resourcesProvider != null
                ? resourcesProvider.getColor(Theme.key_telegram_color)
                : Theme.getColor(Theme.key_telegram_color);
        trackColor = resourcesProvider != null
                ? resourcesProvider.getColor(Theme.key_windowBackgroundWhite)
                : Theme.getColor(Theme.key_windowBackgroundWhite);
        textColor = resourcesProvider != null
                ? resourcesProvider.getColor(Theme.key_dialogTextBlack)
                : Theme.getColor(Theme.key_dialogTextBlack);
        labelColor = Theme.multAlpha(textColor, 0.55f);
        trackPaint.setColor(trackColor);
        textPaint.setColor(labelColor);
        for (int i = 0; i < thumbColors.length; i++) {
            thumbColors[i] = Theme.multAlpha(accentColor, i == 0 ? 1f : 1f - i * 0.11f);
        }
        invalidate();
    }

    private float trackTop() {
        return AndroidUtilities.dp(34);
    }

    private float trackBottom() {
        return getHeight() - AndroidUtilities.dp(30);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        final int count = BAND_HZ.length;
        if (getWidth() <= 0 || trackBottom() - trackTop() < AndroidUtilities.dp(60)) {
            return;
        }
        final float slot = (float) getWidth() / count;
        final float trackWidth = AndroidUtilities.dp(5);
        final float top = trackTop();
        final float bottom = trackBottom();
        final float center = (top + bottom) / 2f;
        final float radius = trackWidth / 2f;

        for (int i = 0; i < count; i++) {
            final float cx = slot * i + slot / 2f;

            trackPaint.setAlpha(enabled ? 255 : 120);
            rect.set(cx - radius, top, cx + radius, bottom);
            canvas.drawRoundRect(rect, radius, radius, trackPaint);

            final float value = values[i];
            final float thumbY = valueToY(value, top, bottom);

            fillPaint.setColor(thumbColors[i]);
            fillPaint.setAlpha(enabled ? 255 : 110);
            if (value >= 0) {
                rect.set(cx - radius, center - Math.max(trackWidth, center - thumbY), cx + radius, center);
                canvas.drawRoundRect(rect, radius, radius, fillPaint);
            } else {
                rect.set(cx - radius, center, cx + radius, center + Math.max(trackWidth, thumbY - center));
                canvas.drawRoundRect(rect, radius, radius, fillPaint);
            }

            final float thumbRadius = AndroidUtilities.dp(activeIndex == i ? 11 : 9);
            thumbPaint.setColor(enabled ? thumbColors[i] : Theme.multAlpha(textColor, 0.35f));
            canvas.drawCircle(cx, thumbY, thumbRadius, thumbPaint);
            thumbPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
            canvas.drawCircle(cx - thumbRadius * 0.3f, thumbY - thumbRadius * 0.35f, thumbRadius * 0.28f, thumbPaint);

            textPaint.setColor(labelColor);
            textPaint.setAlpha(enabled ? 255 : 140);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(label(i), cx, getHeight() - AndroidUtilities.dp(11), textPaint);
            canvas.drawText(format(value), cx, AndroidUtilities.dp(20), textPaint);
        }

        fillPaint.setAlpha(enabled ? 90 : 50);
        fillPaint.setColor(textColor);
        canvas.drawRect(0, center - AndroidUtilities.dp(0.5f), getWidth(), center + AndroidUtilities.dp(0.5f), fillPaint);
    }

    private float valueToY(float value, float top, float bottom) {
        float t = (MAX_DB - value) / (MAX_DB - MIN_DB);
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        return top + t * (bottom - top);
    }

    private float yToValue(float y, float top, float bottom) {
        float t = (y - top) / Math.max(1f, bottom - top);
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        float value = MAX_DB - t * (MAX_DB - MIN_DB);
        if (Math.abs(value) < 0.7f) {
            value = 0f;
        }
        return Math.round(value * 10f) / 10f;
    }

    public static String label(int index) {
        switch (index) {
            case 0: return "60";
            case 1: return "230";
            case 2: return "910";
            case 3: return "3.6k";
            default: return "14k";
        }
    }

    public static String format(float value) {
        if (value > 0f) {
            return "+" + formatNumber(value);
        } else if (value < 0f) {
            return "-" + formatNumber(-value);
        }
        return "0";
    }

    private static String formatNumber(float value) {
        String s = String.valueOf(Math.abs(Math.round(value * 10f) / 10f));
        return s + " dB";
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!enabled || !isEnabled()) {
            return super.onTouchEvent(event);
        }
        final float top = trackTop();
        final float bottom = trackBottom();
        final float slot = (float) getWidth() / BAND_HZ.length;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                // иначе шторка BottomSheet перехватывает жест и закрывается
                getParent().requestDisallowInterceptTouchEvent(true);
                activeIndex = (int) Math.max(0, Math.min(BAND_HZ.length - 1, event.getX() / slot));
                updateValue(activeIndex, event.getY(), top, bottom);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (activeIndex >= 0) {
                    updateValue(activeIndex, event.getY(), top, bottom);
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                getParent().requestDisallowInterceptTouchEvent(false);
                if (activeIndex >= 0 && delegate != null) {
                    delegate.onBandsReleased();
                }
                activeIndex = -1;
                invalidate();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private void updateValue(int index, float y, float top, float bottom) {
        float value = yToValue(y, top, bottom);
        if (values[index] == value) {
            return;
        }
        values[index] = value;
        if (delegate != null) {
            delegate.onBandValueChanged(index, value);
        }
    }
}