package org.telegram.ui.Components;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;

import org.telegram.messenger.SharedConfig;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Пятиполосный эквалайзер, работающий с PCM-потоком ExoPlayer.
 * Полосы совпадают с UI: 60 / 230 / 910 / 3600 / 14000 Гц.
 */
@UnstableApi
public class DiveGramEqualizerProcessor extends BaseAudioProcessor {

    private static final float Q = 1.0f;
    private static final float MIN_FREQUENCY_RATIO = 0.45f;

    private final float[][] b0 = new float[EqualizerBandsView.BAND_HZ.length][];
    private final float[][] b1 = new float[EqualizerBandsView.BAND_HZ.length][];
    private final float[][] b2 = new float[EqualizerBandsView.BAND_HZ.length][];
    private final float[][] a1 = new float[EqualizerBandsView.BAND_HZ.length][];
    private final float[][] a2 = new float[EqualizerBandsView.BAND_HZ.length][];

    private float[][] x1;
    private float[][] x2;
    private float[][] y1;
    private float[][] y2;

    private int sampleRate;
    private int channelCount;
    private float[] appliedGains;
    private float preamp = 1f;

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat) {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT
                && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioFormat.NOT_SET;
        }
        sampleRate = Math.max(8000, inputAudioFormat.sampleRate);
        channelCount = Math.max(1, inputAudioFormat.channelCount);
        x1 = new float[EqualizerBandsView.BAND_HZ.length][channelCount];
        x2 = new float[EqualizerBandsView.BAND_HZ.length][channelCount];
        y1 = new float[EqualizerBandsView.BAND_HZ.length][channelCount];
        y2 = new float[EqualizerBandsView.BAND_HZ.length][channelCount];
        appliedGains = null;
        updateCoefficients();
        return inputAudioFormat;
    }

    @Override
    protected void onFlush() {
        resetState();
    }

    @Override
    protected void onReset() {
        resetState();
    }

    private void resetState() {
        if (x1 != null) {
            for (int i = 0; i < x1.length; i++) {
                Arrays.fill(x1[i], 0f);
                Arrays.fill(x2[i], 0f);
                Arrays.fill(y1[i], 0f);
                Arrays.fill(y2[i], 0f);
            }
        }
    }

    private void updateCoefficients() {
        final float[] gains = SharedConfig.eqBands;
        if (gains == null || appliedGains != null && Arrays.equals(appliedGains, gains)) {
            return;
        }
        appliedGains = Arrays.copyOf(gains, EqualizerBandsView.BAND_HZ.length);
        float maxGain = 0f;
        for (int band = 0; band < EqualizerBandsView.BAND_HZ.length; band++) {
            final float gain = appliedGains[band];
            if (gain > maxGain) {
                maxGain = gain;
            }
            final float frequency = Math.min(
                    EqualizerBandsView.BAND_HZ[band],
                    sampleRate * MIN_FREQUENCY_RATIO);

            final double a = Math.pow(10.0, gain / 40.0);
            final double w0 = 2.0 * Math.PI * frequency / sampleRate;
            final double cosW0 = Math.cos(w0);
            final double alpha = Math.sin(w0) / (2.0 * Q);
            final double a0 = 1.0 + alpha / a;

            b0[band] = new float[]{(float) ((1.0 + alpha * a) / a0)};
            b1[band] = new float[]{(float) (-2.0 * cosW0 / a0)};
            b2[band] = new float[]{(float) ((1.0 - alpha * a) / a0)};
            a1[band] = new float[]{(float) (-2.0 * cosW0 / a0)};
            a2[band] = new float[]{(float) ((1.0 - alpha / a) / a0)};
        }
        preamp = maxGain > 0f ? (float) Math.pow(10.0, -maxGain / 20.0) : 1f;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        if (!inputBuffer.hasRemaining()) {
            return;
        }
        updateCoefficients();
        final boolean enabled = SharedConfig.eqEnabled;
        final int encoding = inputAudioFormat.encoding;
        final ByteBuffer output = replaceOutputBuffer(inputBuffer.remaining());
        inputBuffer.order(ByteOrder.nativeOrder());
        output.order(ByteOrder.nativeOrder());

        if (!enabled) {
            output.put(inputBuffer);
        } else if (encoding == C.ENCODING_PCM_FLOAT) {
            processFloat(inputBuffer, output);
        } else {
            processShort(inputBuffer, output);
        }
        inputBuffer.position(inputBuffer.limit());
        output.flip();
    }

    private void processFloat(ByteBuffer input, ByteBuffer output) {
        final int frames = input.remaining() / (4 * channelCount);
        for (int frame = 0; frame < frames; frame++) {
            for (int channel = 0; channel < channelCount; channel++) {
                float sample = input.getFloat() * preamp;
                for (int band = 0; band < EqualizerBandsView.BAND_HZ.length; band++) {
                    sample = processSample(band, channel, sample);
                }
                output.putFloat(Math.max(-1f, Math.min(1f, sample)));
            }
        }
    }

    private void processShort(ByteBuffer input, ByteBuffer output) {
        final int frames = input.remaining() / (2 * channelCount);
        for (int frame = 0; frame < frames; frame++) {
            for (int channel = 0; channel < channelCount; channel++) {
                float sample = (input.getShort() / 32768f) * preamp;
                for (int band = 0; band < EqualizerBandsView.BAND_HZ.length; band++) {
                    sample = processSample(band, channel, sample);
                }
                sample = Math.max(-1f, Math.min(1f, sample));
                output.putShort((short) Math.round(sample * 32767f));
            }
        }
    }

    private float processSample(int band, int channel, float input) {
        final float result = b0[band][0] * input
                + b1[band][0] * x1[band][channel]
                + b2[band][0] * x2[band][channel]
                - a1[band][0] * y1[band][channel]
                - a2[band][0] * y2[band][channel];
        x2[band][channel] = x1[band][channel];
        x1[band][channel] = input;
        y2[band][channel] = y1[band][channel];
        y1[band][channel] = result;
        return result;
    }
}
