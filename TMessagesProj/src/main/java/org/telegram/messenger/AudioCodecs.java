/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

import androidx.media3.common.C;

import org.telegram.tgnet.TLRPC;

public class AudioCodecs {

    private AudioCodecs() {
    }

    public static final String[] CODEC_TITLES = {"По умолчанию", "AAC", "LDAC"};
    public static final String[] CODEC_DESCRIPTIONS = {
            "Системный выбор кодеков.",
            "Высокое качество, поддерживается почти везде.",
            "Максимальное качество по Bluetooth, если наушники его поддерживают."
    };

    public static int getCodecCount() {
        return CODEC_TITLES.length;
    }

    public static boolean isHighQualitySelected() {
        return SharedConfig.audioCodec != SharedConfig.AUDIO_CODEC_AUTO;
    }

    public static int getExoContentType() {
        return isHighQualitySelected() ? C.AUDIO_CONTENT_TYPE_MUSIC : C.AUDIO_CONTENT_TYPE_UNKNOWN;
    }

    public static int getPlatformContentType() {
        return isHighQualitySelected()
                ? android.media.AudioAttributes.CONTENT_TYPE_MUSIC
                : android.media.AudioAttributes.CONTENT_TYPE_UNKNOWN;
    }

    public static boolean isBluetoothOutputActive() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
            return false;
        }
        try {
            AudioManager audioManager = (AudioManager) ApplicationLoader.applicationContext.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager == null) {
                return false;
            }
            for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                if (device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                    return true;
                }
            }
        } catch (Throwable ignore) {
        }
        return false;
    }

    public static String getCurrentOutputDescription() {
        if (isBluetoothOutputActive()) {
            if (SharedConfig.audioCodec == SharedConfig.AUDIO_CODEC_LDAC) {
                return "Наушники по Bluetooth: запрошен LDAC.";
            }
            if (SharedConfig.audioCodec == SharedConfig.AUDIO_CODEC_AAC) {
                return "Наушники по Bluetooth: запрошен AAC.";
            }
            return "Наушники по Bluetooth: кодек выбирает система.";
        }
        return "Динамик или проводные наушники: кодек выбирает система.";
    }

    public static final int VOICE_BITRATE_ECONOMIC = 16000;
    public static final int VOICE_BITRATE_STANDARD = 32000;
    public static final int VOICE_BITRATE_HIGH = 64000;
    public static final int VOICE_BITRATE_MAX = 0;

    public static final String[] VOICE_BITRATE_TITLES = {"Максимум", "Высокое", "Стандартное", "Экономное"};
    public static final int[] VOICE_BITRATE_VALUES = {VOICE_BITRATE_MAX, VOICE_BITRATE_HIGH, VOICE_BITRATE_STANDARD, VOICE_BITRATE_ECONOMIC};

    public static int getVoiceBitrateIndex() {
        for (int i = 0; i < VOICE_BITRATE_VALUES.length; i++) {
            if (VOICE_BITRATE_VALUES[i] == SharedConfig.voiceRecordBitrate) {
                return i;
            }
        }
        return 0;
    }

    public static int getVoiceRecordBitrate() {
        if (SharedConfig.voiceRecordBitrate > 0) {
            return SharedConfig.voiceRecordBitrate;
        }
        return isHighQualitySelected() ? VOICE_BITRATE_HIGH : VOICE_BITRATE_MAX;
    }

    public static String getBitrateLabel(TLRPC.Document document, int durationSeconds) {
        if (document == null || durationSeconds <= 0 || document.size <= 0) {
            return null;
        }
        long kbps = document.size * 8L / durationSeconds / 1000L;
        if (kbps <= 0) {
            return null;
        }
        return kbps + " kbps";
    }
}
