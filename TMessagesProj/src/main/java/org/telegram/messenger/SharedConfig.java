/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.BitmapFactory;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;
import android.webkit.WebView;

import androidx.annotation.IntDef;
import androidx.annotation.RequiresApi;
import androidx.core.content.pm.ShortcutManagerCompat;

import org.json.JSONObject;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.SwipeGestureSettingsView;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class SharedConfig {
    /**
     * V2: Ping and check time serialized
     */
    private final static int PROXY_SCHEMA_V2 = 2;
    private final static int PROXY_CURRENT_SCHEMA_VERSION = PROXY_SCHEMA_V2;

    public final static int PASSCODE_TYPE_PIN = 0,
            PASSCODE_TYPE_PASSWORD = 1;
    private static int legacyDevicePerformanceClass = -1;

    public static boolean loopStickers() {
        return LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_STICKERS_CHAT);
    }

    public static boolean readOnlyStorageDirAlertShowed;

    public static void checkSdCard(File file) {
        if (file == null || SharedConfig.storageCacheDir == null || readOnlyStorageDirAlertShowed) {
            return;
        }
        if (file.getPath().startsWith(SharedConfig.storageCacheDir)) {
            AndroidUtilities.runOnUIThread(() -> {
                if (readOnlyStorageDirAlertShowed) {
                    return;
                }
                BaseFragment fragment = LaunchActivity.getLastFragment();
                if (fragment != null && fragment.getParentActivity() != null) {
                    SharedConfig.storageCacheDir = null;
                    SharedConfig.saveConfig();
                    ImageLoader.getInstance().checkMediaPaths(() -> {

                    });

                    readOnlyStorageDirAlertShowed = true;
                    AlertDialog.Builder dialog = new AlertDialog.Builder(fragment.getParentActivity());
                    dialog.setTitle(LocaleController.getString(R.string.SdCardError));
                    dialog.setSubtitle(LocaleController.getString(R.string.SdCardErrorDescription));
                    dialog.setPositiveButton(LocaleController.getString(R.string.DoNotUseSDCard), (dialog1, which) -> {

                    });
                    Dialog dialogFinal = dialog.create();
                    dialogFinal.setCanceledOnTouchOutside(false);
                    dialogFinal.show();
                }
            });
        }
    }

    static Boolean allowPreparingHevcPlayers;

    public static boolean allowPreparingHevcPlayers() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
            return false;
        }
        if (allowPreparingHevcPlayers == null) {
            int codecCount = MediaCodecList.getCodecCount();
            int maxInstances = 0;
            int capabilities = 0;

            for (int i = 0; i < codecCount; i++) {
                MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
                if (codecInfo.isEncoder()) {
                    continue;
                }

                boolean found = false;
                for (int k = 0; k < codecInfo.getSupportedTypes().length; k++) {
                    if (codecInfo.getSupportedTypes()[k].contains("video/hevc")) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    continue;
                }
                capabilities = codecInfo.getCapabilitiesForType("video/hevc").getMaxSupportedInstances();
                if (capabilities > maxInstances) {
                    maxInstances = capabilities;
                }
            }
            allowPreparingHevcPlayers = maxInstances >= 8;
        }
        return allowPreparingHevcPlayers;
    }

    public static void togglePaymentByInvoice() {
        payByInvoice = !payByInvoice;
        ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE)
                .edit()
                .putBoolean("payByInvoice", payByInvoice)
                .apply();
    }

    public static void toggleSurfaceInStories() {
        useSurfaceInStories = !useSurfaceInStories;
        ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE)
                .edit()
                .putBoolean("useSurfaceInStories", useSurfaceInStories)
                .apply();
    }

    public static void togglePhotoViewerBlur() {
        photoViewerBlur = !photoViewerBlur;
        ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE)
                .edit()
                .putBoolean("photoViewerBlur", photoViewerBlur)
                .apply();
    }

    private static String goodHevcEncoder;
    private static HashSet<String> hevcEncoderWhitelist = new HashSet<>();
    static {
        hevcEncoderWhitelist.add("c2.exynos.hevc.encoder");
        hevcEncoderWhitelist.add("OMX.Exynos.HEVC.Encoder".toLowerCase());
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static String findGoodHevcEncoder() {
        if (goodHevcEncoder == null) {
            int codecCount = MediaCodecList.getCodecCount();
            for (int i = 0; i < codecCount; i++) {
                MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
                if (!codecInfo.isEncoder()) {
                    continue;
                }

                for (int k = 0; k < codecInfo.getSupportedTypes().length; k++) {
                    if (codecInfo.getSupportedTypes()[k].contains("video/hevc") && codecInfo.isHardwareAccelerated() && isWhitelisted(codecInfo)) {
                        return goodHevcEncoder = codecInfo.getName();
                    }
                }
            }
            goodHevcEncoder = "";
        }
        return TextUtils.isEmpty(goodHevcEncoder) ? null : goodHevcEncoder;
    }

    private static boolean isWhitelisted(MediaCodecInfo codecInfo) {
        if (BuildVars.DEBUG_PRIVATE_VERSION) {
            return true;
        }
        return hevcEncoderWhitelist.contains(codecInfo.getName().toLowerCase());
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            PASSCODE_TYPE_PIN,
            PASSCODE_TYPE_PASSWORD
    })
    public @interface PasscodeType {}

    public final static int SAVE_TO_GALLERY_FLAG_PEER = 1;
    public final static int SAVE_TO_GALLERY_FLAG_GROUP = 2;
    public final static int SAVE_TO_GALLERY_FLAG_CHANNELS = 4;

    @PushListenerController.PushType
    public static int pushType = PushListenerController.PUSH_TYPE_FIREBASE;
    public static String pushString = "";
    public static String pushStringStatus = "";
    public static long pushStringGetTimeStart;
    public static long pushStringGetTimeEnd;
    public static boolean pushStatSent;
    public static byte[] pushAuthKey;
    public static byte[] pushAuthKeyId;
    public static boolean forceForumTabs;
    public static boolean fastWallpaperDisabled;
    public static boolean frameMetricsEnabled;

    public static String directShareHash;

    @PasscodeType
    public static int passcodeType;
    public static String passcodeHash = "";
    public static long passcodeRetryInMs;
    public static long lastUptimeMillis;
    public static int badPasscodeTries;
    public static byte[] passcodeSalt = new byte[0];
    public static boolean appLocked;
    public static int autoLockIn = 60 * 60;

    public static boolean saveIncomingPhotos;
    public static boolean allowScreenCapture;
    public static int lastPauseTime;
    public static boolean isWaitingForPasscodeEnter;
    public static boolean useFingerprintLock = true;
    public static boolean useFaceLock = true;
    public static int suggestStickers;
    public static boolean suggestAnimatedEmoji;
    public static int keepMedia = CacheByChatsController.KEEP_MEDIA_ONE_MONTH; //deprecated
    public static int lastKeepMediaCheckTime;
    public static int lastLogsCheckTime;
    public static int textSelectionHintShows;
    public static int scheduledOrNoSoundHintShows;
    public static long scheduledOrNoSoundHintSeenAt;
    public static int scheduledHintShows;
    public static long scheduledHintSeenAt;
    public static int lockRecordAudioVideoHint;
    public static boolean forwardingOptionsHintShown, replyingOptionsHintShown;
    public static boolean searchMessagesAsListUsed;
    public static boolean stickersReorderingHintUsed;
    public static int dayNightWallpaperSwitchHint;
    public static boolean storyReactionsLongPressHint;
    public static boolean storiesIntroShown;
    public static boolean disableVoiceAudioEffects;
    public static boolean forceDisableTabletMode;
    public static boolean updateStickersOrderOnSend = true;
    public static boolean bigCameraForRound;
    public static Boolean useCamera2Force;
    public static boolean useNewBlur;
    public static boolean useSurfaceInStories;
    public static boolean photoViewerBlur = true;
    public static boolean payByInvoice;
    public static int stealthModeSendMessageConfirm = 2;
    public static boolean minegramBubbles = true;
    public static boolean minegramWave = true;
    public static boolean minegramLargeCorners = false;
    public static int minegramAccent = 0;
    public static boolean tgwsProxyEnabled = false;
    public static int tgwsProxyPort = 1443;
    public static boolean saveDeletedMessages = false;
    public static int deletedMessagesOpacity = 100;
    public static boolean numberProtection = false;
    public static boolean ghostHideOnline = false;
    public static boolean ghostHideRead = false;
    public static boolean ghostHideTyping = false;
    public static boolean localPremium = false;
    public static boolean autoReplyEnabled = false;
    public static String autoReplyText = "";
    public static int autoReplyCooldownSec = 600;
    public static int appNameMode = 0;
    public static String appNameCustom = "";
    public static boolean chatPlates = true;
    public static int chatPlateColor = 0;
    public static int chatPlateOpacity = 100;
    public static boolean circleVideoBackCamera = false;
    public static boolean diveGramCentered = false;
    public static int musicCardBlur = 55;
    public static boolean playerLyricsEnabled = true;
    public static boolean playerLyricsAlbumMode = false;
    public static boolean playerLyricsHideControls = false;
    public static boolean playerLyricsTranslate = false;
    public static String playerLyricsTranslateLang = "";
    public static float albumCoverX = 0.25f;
    public static float albumCoverY = 0.5f;
    public static float albumCoverSize = 0.6f;
    public static float albumLyricsX = 0.72f;
    public static float albumLyricsY = 0.5f;
    public static float albumLyricsWidth = 0.42f;
    public static String tgwsProxySecret = "";
    public static boolean weatherEnabled = false;
    public static String weatherCity = "";
    public static String weatherText = "";
    public static long weatherTime = 0;
    public static double weatherLat;
    public static double weatherLon;
    public static boolean badgesEnabled = true;
    public static long badgesFetchTime = 0;
    public static boolean streaksEnabled = true;
    public static long streakFetchTime = 0;
    public static long nftsFetchTime = 0;
    public static boolean dialogsWallpaper = false;
    public static boolean dialogsOwnWallpaper = false;
    public static int dialogsWallpaperScale = 100;
    public static int dialogsWallpaperOffsetX = 0;
    public static int dialogsWallpaperOffsetY = 0;
    public static int dialogsWallpaperBlur = 0;
    public static int mainTabsOpacity = 100;
    public static int dropeRippleStrength = 100;
    public static boolean playerBitrate;
    public static final int AUDIO_CODEC_AUTO = 0;
    public static final int AUDIO_CODEC_AAC = 1;
    public static final int AUDIO_CODEC_LDAC = 2;
    public static int audioCodec = AUDIO_CODEC_AUTO;
    public static int voiceRecordBitrate = 0;
    public static boolean quickRepliesEnabled = false;
    public static boolean mentionAllEnabled = false;
    public static boolean mentionAllSplitEnabled = false;
    public static boolean aiChatEnabled = false;
    public static String geminiApiKey = "";
    public static String aiModelId = "";
    public static boolean hideAllChatsFolder = false;
    public static boolean hideContactsBar = false;
    public static boolean showTypingLocation = false;
    public static boolean hideCameraInPicker = false;
    public static boolean oldDesign = false;
    public static java.util.HashSet<String> hiddenLyricsKeys = new java.util.HashSet<>();
    public static long localEmojiStatusDocumentId = -1;

    public static int localEmojiStatusCollectibleId = 0;
    public static long localEmojiStatusUntil = 0;

    public static long wornGiftCollectibleId = 0;
    public static long wornGiftDocumentId = 0;
    public static long wornGiftPatternDocId = 0;
    public static int wornGiftCenterColor = 0;
    public static int wornGiftEdgeColor = 0;
    public static int wornGiftTextColor = 0;
    public static int wornGiftPatternColor = 0;
    public static String wornGiftPatternDocB64 = "";
    public static boolean wornGiftIsLocal = false;
    public static String wornGiftModelDocB64 = "";
    public static String wornGiftObjectB64 = "";
    public static String localEmojiStatusB64 = "";
    public static String localNameColorB64 = "";
    public static String localProfileColorB64 = "";
    public static final HashSet<String> deletedMessagesIds = new HashSet<>();
    private static int lastLocalId = -210000;

    public static String storageCacheDir;

    private static String passportConfigJson = "";
    private static HashMap<String, String> passportConfigMap;
    public static int passportConfigHash;

    private static boolean configLoaded;
    private static final Object sync = new Object();
    private static final Object localIdSync = new Object();

//    public static int saveToGalleryFlags;
    public static int mapPreviewType = 2;
    public static int searchEngineType = 0;
    public static String searchEngineCustomURLQuery, searchEngineCustomURLAutocomplete;
    public static boolean chatBubbles = Build.VERSION.SDK_INT >= 30;
    public static boolean raiseToSpeak = false;
    public static boolean raiseToListen = true;
    public static boolean nextMediaTap = true;
    public static boolean recordViaSco = false;
    public static boolean adaptableColorInBrowser = true;
    public static boolean onlyLocalInstantView = false;
    public static boolean directShare = true;
    public static boolean inappCamera = true;
    public static boolean roundCamera16to9 = true;
    public static boolean noSoundHintShowed = false;
    public static boolean streamMedia = true;
    public static boolean streamAllVideo = false;
    public static boolean streamMkv = false;
    public static boolean saveStreamMedia = true;
    public static boolean pauseMusicOnRecord = false;
    public static boolean pauseMusicOnMedia = false;
    public static boolean noiseSupression;
    public static boolean debugWebView;
    public static boolean sortContactsByName;
    public static boolean sortFilesByName;
    public static boolean shuffleMusic;
    public static boolean playOrderReversed;
    public static boolean hasCameraCache;
    public static boolean showNotificationsForAllAccounts = true;
    public static boolean debugVideoQualities = false;
    public static int repeatMode;
    public static boolean allowBigEmoji;
    public static boolean useSystemEmoji;
    public static boolean useSystemBoldFont;
    public static int fontSize = 16;
    public static boolean fontSizeIsDefault;
    public static int bubbleRadius = 17;
    public static int ivFontSize = 16;
    public static boolean proxyRotationEnabled;
    public static int proxyRotationTimeout;
    public static int messageSeenHintCount;
    public static int emojiInteractionsHintCount;
    public static int dayNightThemeSwitchHintCount;
    public static int callEncryptionHintDisplayedCount;
    public static boolean shadowsInSections;
    public static boolean debugViewMetrics;
    public static boolean photoHighQualityDefault;
    public static boolean photoLiveDefault;

    public static TLRPC.TL_help_appUpdate pendingAppUpdate;
    public static int pendingAppUpdateBuildVersion;
    public static long lastUpdateCheckTime;

    public static boolean hasEmailLogin;

    @PerformanceClass
    private static int devicePerformanceClass;
    @PerformanceClass
    private static int overrideDevicePerformanceClass;

    public static boolean drawDialogIcons;
    public static boolean useThreeLinesLayout;
    public static boolean archiveHidden;

    private static int chatSwipeAction;

    public static int distanceSystemType;
    public static int mediaColumnsCount = 3;
    public static int storiesColumnsCount = 3;
    public static int fastScrollHintCount = 3;
    public static boolean dontAskManageStorage;
    public static boolean multipleReactionsPromoShowed;

    public static boolean isFloatingDebugActive;
    public static LiteMode liteMode;

    private static final int[] LOW_SOC = {
            -1775228513, // EXYNOS 850
            802464304,  // EXYNOS 7872
            802464333,  // EXYNOS 7880
            802464302,  // EXYNOS 7870
            2067362118, // MSM8953
            2067362060, // MSM8937
            2067362084, // MSM8940
            2067362241, // MSM8992
            2067362117, // MSM8952
            2067361998, // MSM8917
            -1853602818 // SDM439
    };

    static {
        loadConfig();
    }

    public static class ProxyInfo {

        public String address;
        public int port;
        public String username;
        public String password;
        public String secret;

        public long proxyCheckPingId;
        public long ping;
        public boolean checking;
        public boolean available;
        public long availableCheckTime;

        public ProxyInfo(String address, int port, String username, String password, String secret) {
            this.address = address;
            this.port = port;
            this.username = username;
            this.password = password;
            this.secret = secret;
            if (this.address == null) {
                this.address = "";
            }
            if (this.password == null) {
                this.password = "";
            }
            if (this.username == null) {
                this.username = "";
            }
            if (this.secret == null) {
                this.secret = "";
            }
        }

        public String getLink() {
            StringBuilder url = new StringBuilder(!TextUtils.isEmpty(secret) ? "https://t.me/proxy?" : "https://t.me/socks?");
            try {
                url.append("server=").append(URLEncoder.encode(address, "UTF-8")).append("&").append("port=").append(port);
                if (!TextUtils.isEmpty(username)) {
                    url.append("&user=").append(URLEncoder.encode(username, "UTF-8"));
                }
                if (!TextUtils.isEmpty(password)) {
                    url.append("&pass=").append(URLEncoder.encode(password, "UTF-8"));
                }
                if (!TextUtils.isEmpty(secret)) {
                    url.append("&secret=").append(URLEncoder.encode(secret, "UTF-8"));
                }
            } catch (UnsupportedEncodingException ignored) {}
            return url.toString();
        }
    }

    public static ArrayList<ProxyInfo> proxyList = new ArrayList<>();
    private static boolean proxyListLoaded;
    public static ProxyInfo currentProxy;

    public static void saveConfig() {
        synchronized (sync) {
            try {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("saveIncomingPhotos", saveIncomingPhotos);
                editor.putString("passcodeHash1", passcodeHash);
                editor.putString("passcodeSalt", passcodeSalt.length > 0 ? Base64.encodeToString(passcodeSalt, Base64.DEFAULT) : "");
                editor.putBoolean("appLocked", appLocked);
                editor.putInt("passcodeType", passcodeType);
                editor.putLong("passcodeRetryInMs", passcodeRetryInMs);
                editor.putLong("lastUptimeMillis", lastUptimeMillis);
                editor.putInt("badPasscodeTries", badPasscodeTries);
                editor.putInt("autoLockIn", autoLockIn);
                editor.putInt("lastPauseTime", lastPauseTime);
                editor.putBoolean("useFingerprint", useFingerprintLock);
                editor.putBoolean("allowScreenCapture", allowScreenCapture);
                editor.putString("pushString2", pushString);
                editor.putInt("pushType", pushType);
                editor.putBoolean("pushStatSent", pushStatSent);
                editor.putString("pushAuthKey", pushAuthKey != null ? Base64.encodeToString(pushAuthKey, Base64.DEFAULT) : "");
                editor.putInt("lastLocalId", lastLocalId);
                editor.putString("passportConfigJson", passportConfigJson);
                editor.putInt("passportConfigHash", passportConfigHash);
                editor.putBoolean("sortContactsByName", sortContactsByName);
                editor.putBoolean("sortFilesByName", sortFilesByName);
                editor.putInt("textSelectionHintShows", textSelectionHintShows);
                editor.putInt("scheduledOrNoSoundHintShows", scheduledOrNoSoundHintShows);
                editor.putLong("scheduledOrNoSoundHintSeenAt", scheduledOrNoSoundHintSeenAt);
                editor.putInt("scheduledHintShows", scheduledHintShows);
                editor.putLong("scheduledHintSeenAt", scheduledHintSeenAt);
                editor.putBoolean("forwardingOptionsHintShown", forwardingOptionsHintShown);
                editor.putBoolean("replyingOptionsHintShown", replyingOptionsHintShown);
                editor.putInt("lockRecordAudioVideoHint", lockRecordAudioVideoHint);
                editor.putString("storageCacheDir", !TextUtils.isEmpty(storageCacheDir) ? storageCacheDir : "");
                editor.putBoolean("proxyRotationEnabled", proxyRotationEnabled);
                editor.putInt("proxyRotationTimeout", proxyRotationTimeout);

                if (pendingAppUpdate != null) {
                    try {
                        SerializedData data = new SerializedData(pendingAppUpdate.getObjectSize());
                        pendingAppUpdate.serializeToStream(data);
                        String str = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT);
                        editor.putString("appUpdate", str);
                        editor.putInt("appUpdateBuild", pendingAppUpdateBuildVersion);
                        data.cleanup();
                    } catch (Exception ignore) {

                    }
                } else {
                    editor.remove("appUpdate");
                }
                editor.putLong("appUpdateCheckTime", lastUpdateCheckTime);

                editor.apply();

                editor = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE).edit();
                editor.putBoolean("hasEmailLogin", hasEmailLogin);
                editor.putBoolean("floatingDebugActive", isFloatingDebugActive);
                editor.putBoolean("record_via_sco", recordViaSco);
                editor.apply();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public static int getLastLocalId() {
        int value;
        synchronized (localIdSync) {
            value = lastLocalId--;
        }
        return value;
    }

    public static void loadConfig() {
        synchronized (sync) {
            if (configLoaded || ApplicationLoader.applicationContext == null) {
                return;
            }

            BackgroundActivityPrefs.prefs = ApplicationLoader.applicationContext.getSharedPreferences("background_activity", Context.MODE_PRIVATE);

            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE);
            saveIncomingPhotos = preferences.getBoolean("saveIncomingPhotos", false);
            passcodeHash = preferences.getString("passcodeHash1", "");
            appLocked = preferences.getBoolean("appLocked", false);
            passcodeType = preferences.getInt("passcodeType", 0);
            passcodeRetryInMs = preferences.getLong("passcodeRetryInMs", 0);
            lastUptimeMillis = preferences.getLong("lastUptimeMillis", 0);
            badPasscodeTries = preferences.getInt("badPasscodeTries", 0);
            autoLockIn = preferences.getInt("autoLockIn", 60 * 60);
            lastPauseTime = preferences.getInt("lastPauseTime", 0);
            useFingerprintLock = preferences.getBoolean("useFingerprint", true);
            allowScreenCapture = preferences.getBoolean("allowScreenCapture", false);
            lastLocalId = preferences.getInt("lastLocalId", -210000);
            pushString = preferences.getString("pushString2", "");
            pushType = preferences.getInt("pushType", PushListenerController.PUSH_TYPE_FIREBASE);
            pushStatSent = preferences.getBoolean("pushStatSent", false);
            passportConfigJson = preferences.getString("passportConfigJson", "");
            passportConfigHash = preferences.getInt("passportConfigHash", 0);
            storageCacheDir = preferences.getString("storageCacheDir", null);
            proxyRotationEnabled = preferences.getBoolean("proxyRotationEnabled", false);
            proxyRotationTimeout = preferences.getInt("proxyRotationTimeout", ProxyRotationController.DEFAULT_TIMEOUT_INDEX);
            String authKeyString = preferences.getString("pushAuthKey", null);
            if (!TextUtils.isEmpty(authKeyString)) {
                pushAuthKey = Base64.decode(authKeyString, Base64.DEFAULT);
            }

            if (passcodeHash.length() > 0 && lastPauseTime == 0) {
                lastPauseTime = (int) (SystemClock.elapsedRealtime() / 1000 - 60 * 10);
            }

            String passcodeSaltString = preferences.getString("passcodeSalt", "");
            if (passcodeSaltString.length() > 0) {
                passcodeSalt = Base64.decode(passcodeSaltString, Base64.DEFAULT);
            } else {
                passcodeSalt = new byte[0];
            }
            lastUpdateCheckTime = preferences.getLong("appUpdateCheckTime", System.currentTimeMillis());
            try {
                String update = preferences.getString("appUpdate", null);
                if (update != null) {
                    pendingAppUpdateBuildVersion = preferences.getInt("appUpdateBuild", buildVersion());
                    byte[] arr = Base64.decode(update, Base64.DEFAULT);
                    if (arr != null) {
                        SerializedData data = new SerializedData(arr);
                        pendingAppUpdate = (TLRPC.TL_help_appUpdate) TLRPC.help_AppUpdate.TLdeserialize(data, data.readInt32(false), false);
                        data.cleanup();
                    }
                }
                if (pendingAppUpdate != null) {
                    long updateTime = 0;
                    int updateVersion = 0;
                    String updateVersionString = null;
                    try {
                        PackageInfo packageInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                        updateVersion = packageInfo.versionCode;
                        updateVersionString = packageInfo.versionName;
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (updateVersion == 0) {
                        updateVersion = buildVersion();
                    }
                    if (updateVersionString == null) {
                        updateVersionString = BuildVars.BUILD_VERSION_STRING;
                    }
                    if (pendingAppUpdateBuildVersion != updateVersion || pendingAppUpdate.version == null || updateVersionString.compareTo(pendingAppUpdate.version) >= 0 || BuildVars.DEBUG_PRIVATE_VERSION) {
                        pendingAppUpdate = null;
                        AndroidUtilities.runOnUIThread(SharedConfig::saveConfig);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            SaveToGallerySettingsHelper.load(preferences);
            minegramBubbles = preferences.getBoolean("minegramBubbles", true);
            minegramWave = preferences.getBoolean("minegramWave", true);
            minegramLargeCorners = preferences.getBoolean("minegramLargeCorners", false);
            minegramAccent = preferences.getInt("minegramAccent", 0);
            tgwsProxyEnabled = preferences.getBoolean("tgwsProxyEnabled", false);
            tgwsProxyPort = preferences.getInt("tgwsProxyPort", 1443);
            if (tgwsProxyPort == 1080) {
                tgwsProxyPort = 1443;
            }
            saveDeletedMessages = preferences.getBoolean("saveDeletedMessages", false);
            deletedMessagesOpacity = preferences.getInt("dgDeletedMessagesOpacity", 100);
            numberProtection = preferences.getBoolean("numberProtection", false);
            ghostHideOnline = preferences.getBoolean("ghostHideOnline", false);
            ghostHideRead = preferences.getBoolean("ghostHideRead", false);
            ghostHideTyping = preferences.getBoolean("ghostHideTyping", false);
            localPremium = preferences.getBoolean("localPremium", false);
             autoReplyEnabled = preferences.getBoolean("autoReplyEnabled", false);
             autoReplyText = preferences.getString("autoReplyText", "");
             autoReplyCooldownSec = preferences.getInt("autoReplyCooldownSec", 600);
            appNameMode = preferences.getInt("appNameMode", 0);
            appNameCustom = preferences.getString("appNameCustom", "");
            chatPlates = preferences.getBoolean("chatPlates", true);
            chatPlateColor = preferences.getInt("dgChatPlateColor", 0);
            chatPlateOpacity = preferences.getInt("dgChatPlateOpacity", 100);
            circleVideoBackCamera = preferences.getBoolean("circleVideoBackCamera", false);
            diveGramCentered = preferences.getBoolean("diveGramCentered", false);
            oldDesign = preferences.getBoolean("dgOldDesign", false);
            musicCardBlur = preferences.getInt("musicCardBlur", 55);
            playerLyricsEnabled = preferences.getBoolean("dgPlayerLyrics", true);
            playerLyricsAlbumMode = preferences.getBoolean("dgPlayerLyricsAlbumMode", false);
            playerLyricsHideControls = preferences.getBoolean("dgPlayerLyricsHideControls", false);
            playerLyricsTranslate = preferences.getBoolean("dgPlayerLyricsTranslate", false);
            playerLyricsTranslateLang = preferences.getString("dgPlayerLyricsTranslateLang", "");
            albumCoverX = preferences.getFloat("dgAlbumCoverX", 0.04f);
            albumCoverY = preferences.getFloat("dgAlbumCoverY", 0.10f);
            albumCoverSize = preferences.getFloat("dgAlbumCoverSize", 0.6f);
            albumLyricsX = preferences.getFloat("dgAlbumLyricsX", 0.52f);
            albumLyricsY = preferences.getFloat("dgAlbumLyricsY", 0.06f);
            albumLyricsWidth = preferences.getFloat("dgAlbumLyricsWidth", 0.42f);
            tgwsProxySecret = preferences.getString("tgwsProxySecret", "");
            weatherEnabled = preferences.getBoolean("dgWeatherEnabled", false);
            weatherCity = preferences.getString("dgWeatherCity", "");
            weatherText = preferences.getString("dgWeatherText", "");
            weatherTime = preferences.getLong("dgWeatherTime", 0);
            weatherLat = Double.longBitsToDouble(preferences.getLong("dgWeatherLat", 0));
            weatherLon = Double.longBitsToDouble(preferences.getLong("dgWeatherLon", 0));
            badgesEnabled = preferences.getBoolean("dgBadgesEnabled", true);
            badgesFetchTime = preferences.getLong("dgBadgesFetchTime", 0);
            streaksEnabled = preferences.getBoolean("dgStreaksEnabled", true);
            streakFetchTime = preferences.getLong("dgStreaksFetchTime", 0);
            nftsFetchTime = preferences.getLong("dgNftsFetchTime", 0);
            dialogsWallpaper = preferences.getBoolean("dgDialogsWallpaper", false);
            dialogsOwnWallpaper = preferences.getBoolean("dgDialogsOwnWallpaper", false);
            dialogsWallpaperScale = preferences.getInt("dgDialogsWallpaperScale", 100);
            dialogsWallpaperOffsetX = preferences.getInt("dgDialogsWallpaperOffsetX", 0);
            dialogsWallpaperOffsetY = preferences.getInt("dgDialogsWallpaperOffsetY", 0);
            dialogsWallpaperBlur = preferences.getInt("dgDialogsWallpaperBlur", 0);
            mainTabsOpacity = preferences.getInt("dgMainTabsOpacity", 100);
            dropeRippleStrength = preferences.getInt("dgDropeRippleStrength", 100);
            playerBitrate = preferences.getBoolean("dgPlayerBitrate", false);
            audioCodec = preferences.getInt("dgAudioCodec", AUDIO_CODEC_AUTO);
            voiceRecordBitrate = preferences.getInt("dgVoiceRecordBitrate", 0);
            quickRepliesEnabled = preferences.getBoolean("dgQuickReplies", false);
            mentionAllEnabled = preferences.getBoolean("dgMentionAll", false);
            mentionAllSplitEnabled = preferences.getBoolean("dgMentionAllSplit", false);
            aiChatEnabled = preferences.getBoolean("dgAiChat", false);
            geminiApiKey = preferences.getString("dgGeminiKey", "");
            hideAllChatsFolder = preferences.getBoolean("dgHideAllChats", false);
            hideContactsBar = preferences.getBoolean("dgHideContactsBar", false);
            showTypingLocation = preferences.getBoolean("dgShowTypingLocation", false);
            hideCameraInPicker = preferences.getBoolean("dgHideCameraInPicker", false);
            String hiddenRaw = preferences.getString("dgHiddenLyrics", "");
            hiddenLyricsKeys.clear();
            if (!hiddenRaw.isEmpty()) {
                for (String k : hiddenRaw.split(",")) {
                    if (!k.isEmpty()) {
                        hiddenLyricsKeys.add(k);
                    }
                }
            }
            aiModelId = preferences.getString("dgAiModel", "");
            reloadDiveGramPremiumDecor();
            try {
                deletedMessagesIds.clear();
                deletedMessagesIds.addAll(preferences.getStringSet("deletedMessagesIds", new HashSet<>()));
            } catch (Exception e) {
                FileLog.e(e);
            }
            mapPreviewType = preferences.getInt("mapPreviewType", 2);
            searchEngineType = preferences.getInt("searchEngineType", 0);
            raiseToListen = preferences.getBoolean("raise_to_listen", true);
            raiseToSpeak = preferences.getBoolean("raise_to_speak", false);
            nextMediaTap = preferences.getBoolean("next_media_on_tap", true);
            recordViaSco = preferences.getBoolean("record_via_sco", false);
            adaptableColorInBrowser = preferences.getBoolean("adaptableBrowser", false);
            onlyLocalInstantView = preferences.getBoolean("onlyLocalInstantView", BuildVars.DEBUG_PRIVATE_VERSION);
            directShare = preferences.getBoolean("direct_share", true);
            shuffleMusic = preferences.getBoolean("shuffleMusic", false);
            playOrderReversed = !shuffleMusic && preferences.getBoolean("playOrderReversed", false);
            inappCamera = preferences.getBoolean("inappCamera", true);
            hasCameraCache = preferences.contains("cameraCache");
            roundCamera16to9 = true;
            repeatMode = preferences.getInt("repeatMode", 0);
            fontSize = preferences.getInt("fons_size", AndroidUtilities.isTablet() && !AndroidUtilities.isFold() ? 18 : 16);
            fontSizeIsDefault = !preferences.contains("fons_size");
            bubbleRadius = preferences.getInt("bubbleRadius", 17);
            ivFontSize = preferences.getInt("iv_font_size", fontSize);
            allowBigEmoji = preferences.getBoolean("allowBigEmoji", true);
            useSystemEmoji = preferences.getBoolean("useSystemEmoji", false);
            useSystemBoldFont = preferences.getBoolean("useSystemBoldFont", false);
            forceForumTabs = preferences.getBoolean("forceForumTabs", false);
            fastWallpaperDisabled = preferences.getBoolean("fastWallpaperDisabled", false);
            frameMetricsEnabled = preferences.getBoolean("frameMetricsEnabled", false);
            if (useSystemBoldFont) {
                AndroidUtilities.mediumTypeface = null;
            }
            streamMedia = preferences.getBoolean("streamMedia", true);
            saveStreamMedia = preferences.getBoolean("saveStreamMedia", true);
            pauseMusicOnRecord = preferences.getBoolean("pauseMusicOnRecord", true);
            pauseMusicOnMedia = preferences.getBoolean("pauseMusicOnMedia", false);
            forceDisableTabletMode = preferences.getBoolean("forceDisableTabletMode", false);
            streamAllVideo = preferences.getBoolean("streamAllVideo", BuildVars.DEBUG_VERSION);
            streamMkv = preferences.getBoolean("streamMkv", false);
            suggestStickers = preferences.getInt("suggestStickers", 0);
            suggestAnimatedEmoji = preferences.getBoolean("suggestAnimatedEmoji", true);
            overrideDevicePerformanceClass = preferences.getInt("overrideDevicePerformanceClass", -1);
            devicePerformanceClass = preferences.getInt("devicePerformanceClass", -1);
            sortContactsByName = preferences.getBoolean("sortContactsByName", false);
            sortFilesByName = preferences.getBoolean("sortFilesByName", false);
            noSoundHintShowed = preferences.getBoolean("noSoundHintShowed", false);
            directShareHash = preferences.getString("directShareHash2", null);
            useThreeLinesLayout = preferences.getBoolean("useThreeLinesLayout", false);
            archiveHidden = preferences.getBoolean("archiveHidden", false);
            distanceSystemType = preferences.getInt("distanceSystemType", 0);
            keepMedia = preferences.getInt("keep_media", CacheByChatsController.KEEP_MEDIA_ONE_MONTH);
            debugWebView = preferences.getBoolean("debugWebView", false);
            lastKeepMediaCheckTime = preferences.getInt("lastKeepMediaCheckTime", 0);
            lastLogsCheckTime = preferences.getInt("lastLogsCheckTime", 0);
            searchMessagesAsListUsed = preferences.getBoolean("searchMessagesAsListUsed", false);
            stickersReorderingHintUsed = preferences.getBoolean("stickersReorderingHintUsed", false);
            storyReactionsLongPressHint = preferences.getBoolean("storyReactionsLongPressHint", false);
            storiesIntroShown = preferences.getBoolean("storiesIntroShown", false);
            textSelectionHintShows = preferences.getInt("textSelectionHintShows", 0);
            scheduledOrNoSoundHintShows = preferences.getInt("scheduledOrNoSoundHintShows", 0);
            scheduledOrNoSoundHintSeenAt = preferences.getLong("scheduledOrNoSoundHintSeenAt", 0);
            scheduledHintShows = preferences.getInt("scheduledHintShows", 0);
            scheduledHintSeenAt = preferences.getLong("scheduledHintSeenAt", 0);
            forwardingOptionsHintShown = preferences.getBoolean("forwardingOptionsHintShown", false);
            replyingOptionsHintShown = preferences.getBoolean("replyingOptionsHintShown", false);
            lockRecordAudioVideoHint = preferences.getInt("lockRecordAudioVideoHint", 0);
            disableVoiceAudioEffects = preferences.getBoolean("disableVoiceAudioEffects", false);
            noiseSupression = preferences.getBoolean("noiseSupression", false);
            chatSwipeAction = preferences.getInt("ChatSwipeAction", -1);
            messageSeenHintCount = preferences.getInt("messageSeenCount", 3);
            emojiInteractionsHintCount = preferences.getInt("emojiInteractionsHintCount", 3);
            dayNightThemeSwitchHintCount = preferences.getInt("dayNightThemeSwitchHintCount", 3);
            stealthModeSendMessageConfirm = preferences.getInt("stealthModeSendMessageConfirm", 2);
            mediaColumnsCount = preferences.getInt("mediaColumnsCount", 3);
            storiesColumnsCount = preferences.getInt("storiesColumnsCount", 3);
            fastScrollHintCount = preferences.getInt("fastScrollHintCount", 3);
            dontAskManageStorage = preferences.getBoolean("dontAskManageStorage", false);
            hasEmailLogin = preferences.getBoolean("hasEmailLogin", false);
            isFloatingDebugActive = preferences.getBoolean("floatingDebugActive", false);
            updateStickersOrderOnSend = preferences.getBoolean("updateStickersOrderOnSend", true);
            dayNightWallpaperSwitchHint = preferences.getInt("dayNightWallpaperSwitchHint", 0);
            bigCameraForRound = preferences.getBoolean("bigCameraForRound", false);
            useNewBlur = preferences.getBoolean("useNewBlur", true);
            useCamera2Force = !preferences.contains("useCamera2Force_2") ? null : preferences.getBoolean("useCamera2Force_2", false);
            useSurfaceInStories = preferences.getBoolean("useSurfaceInStories", Build.VERSION.SDK_INT >= 30);
            payByInvoice = preferences.getBoolean("payByInvoice", false);
            photoViewerBlur = preferences.getBoolean("photoViewerBlur", true);
            multipleReactionsPromoShowed = preferences.getBoolean("multipleReactionsPromoShowed", false);
            callEncryptionHintDisplayedCount = preferences.getInt("callEncryptionHintDisplayedCount", 0);
            debugVideoQualities = preferences.getBoolean("debugVideoQualities", false);
            shadowsInSections = preferences.getBoolean("shadowsInSections", false);
            debugViewMetrics = preferences.getBoolean("debugViewMetrics", false);
            photoHighQualityDefault = preferences.getBoolean("photoHighQualityDefault", false);
            photoLiveDefault = preferences.getBoolean("photoLiveDefault", false);

            loadDebugConfig(preferences);

            preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
            showNotificationsForAllAccounts = preferences.getBoolean("AllAccounts", true);

            configLoaded = true;
        }
    }

    public static int buildVersion() {
        try {
            return ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            FileLog.e(e);
            return 0;
        }
    }

    public static void updateTabletConfig() {
        if (fontSizeIsDefault) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            fontSize = preferences.getInt("fons_size", AndroidUtilities.isTablet() && !AndroidUtilities.isFold() ? 18 : 16);
            ivFontSize = preferences.getInt("iv_font_size", fontSize);
        }
    }

    public static void increaseBadPasscodeTries() {
        badPasscodeTries++;
        if (badPasscodeTries >= 3) {
            switch (badPasscodeTries) {
                case 3:
                    passcodeRetryInMs = 5000;
                    break;
                case 4:
                    passcodeRetryInMs = 10000;
                    break;
                case 5:
                    passcodeRetryInMs = 15000;
                    break;
                case 6:
                    passcodeRetryInMs = 20000;
                    break;
                case 7:
                    passcodeRetryInMs = 25000;
                    break;
                default:
                    passcodeRetryInMs = 30000;
                    break;
            }
            lastUptimeMillis = SystemClock.elapsedRealtime();
        }
        saveConfig();
    }

    public static boolean isAutoplayVideo() {
        return LiteMode.isEnabled(LiteMode.FLAG_AUTOPLAY_VIDEOS);
    }

    public static boolean isAutoplayGifs() {
        return LiteMode.isEnabled(LiteMode.FLAG_AUTOPLAY_GIFS);
    }

    public static boolean isPassportConfigLoaded() {
        return passportConfigMap != null;
    }

    public static void setPassportConfig(String json, int hash) {
        passportConfigMap = null;
        passportConfigJson = json;
        passportConfigHash = hash;
        saveConfig();
        getCountryLangs();
    }

    public static HashMap<String, String> getCountryLangs() {
        if (passportConfigMap == null) {
            passportConfigMap = new HashMap<>();
            try {
                JSONObject object = new JSONObject(passportConfigJson);
                Iterator<String> iter = object.keys();
                while (iter.hasNext()) {
                    String key = iter.next();
                    passportConfigMap.put(key.toUpperCase(), object.getString(key).toUpperCase());
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        return passportConfigMap;
    }

    public static boolean isAppUpdateAvailable() {
        if (pendingAppUpdate == null || pendingAppUpdate.document == null || !ApplicationLoader.isStandaloneBuild()) {
            return false;
        }
        int currentVersion;
        try {
            PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            currentVersion = pInfo.versionCode;
        } catch (Exception e) {
            FileLog.e(e);
            currentVersion = buildVersion();
        }
        return pendingAppUpdateBuildVersion == currentVersion;
    }

    public static boolean setNewAppVersionAvailable(TLRPC.TL_help_appUpdate update) {
        String updateVersionString = null;
        int versionCode = 0;
        try {
            PackageInfo packageInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            versionCode = packageInfo.versionCode;
            updateVersionString = packageInfo.versionName;
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (versionCode == 0) {
            versionCode = buildVersion();
        }
        if (updateVersionString == null) {
            updateVersionString = BuildVars.BUILD_VERSION_STRING;
        }
        if (update.version == null || versionBiggerOrEqual(updateVersionString, update.version)) {
            return false;
        }
        pendingAppUpdate = update;
        pendingAppUpdateBuildVersion = versionCode;
        saveConfig();
        return true;
    }

    // returns a >= b
    public static boolean versionBiggerOrEqual(String a, String b) {
        String[] partsA = a.split("\\.");
        String[] partsB = b.split("\\.");
        for (int i = 0; i < Math.min(partsA.length, partsB.length); ++i) {
            int numA = Integer.parseInt(partsA[i]);
            int numB = Integer.parseInt(partsB[i]);
            if (numA < numB) {
                return false;
            } else if (numA > numB) {
                return true;
            }
        }
        return true;
    }

    public static boolean checkPasscode(String passcode) {
        if (passcodeSalt.length == 0) {
            boolean result = Utilities.MD5(passcode).equals(passcodeHash);
            if (result) {
                try {
                    passcodeSalt = new byte[16];
                    Utilities.random.nextBytes(passcodeSalt);
                    byte[] passcodeBytes = passcode.getBytes("UTF-8");
                    byte[] bytes = new byte[32 + passcodeBytes.length];
                    System.arraycopy(passcodeSalt, 0, bytes, 0, 16);
                    System.arraycopy(passcodeBytes, 0, bytes, 16, passcodeBytes.length);
                    System.arraycopy(passcodeSalt, 0, bytes, passcodeBytes.length + 16, 16);
                    passcodeHash = Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
                    saveConfig();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            return result;
        } else {
            try {
                byte[] passcodeBytes = passcode.getBytes("UTF-8");
                byte[] bytes = new byte[32 + passcodeBytes.length];
                System.arraycopy(passcodeSalt, 0, bytes, 0, 16);
                System.arraycopy(passcodeBytes, 0, bytes, 16, passcodeBytes.length);
                System.arraycopy(passcodeSalt, 0, bytes, passcodeBytes.length + 16, 16);
                String hash = Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.length));
                return passcodeHash.equals(hash);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return false;
    }

    public static void clearConfig() {
        saveIncomingPhotos = false;
        appLocked = false;
        passcodeType = PASSCODE_TYPE_PIN;
        passcodeRetryInMs = 0;
        lastUptimeMillis = 0;
        badPasscodeTries = 0;
        passcodeHash = "";
        passcodeSalt = new byte[0];
        autoLockIn = 60 * 60;
        lastPauseTime = 0;
        useFingerprintLock = true;
        isWaitingForPasscodeEnter = false;
        allowScreenCapture = false;
        textSelectionHintShows = 0;
        scheduledOrNoSoundHintShows = 0;
        scheduledOrNoSoundHintSeenAt = 0;
        scheduledHintShows = 0;
        scheduledHintSeenAt = 0;
        lockRecordAudioVideoHint = 0;
        forwardingOptionsHintShown = false;
        replyingOptionsHintShown = false;
        messageSeenHintCount = 3;
        emojiInteractionsHintCount = 3;
        dayNightThemeSwitchHintCount = 3;
        stealthModeSendMessageConfirm = 2;
        dayNightWallpaperSwitchHint = 0;
        saveConfig();
    }

    public static void setMultipleReactionsPromoShowed(boolean val) {
        multipleReactionsPromoShowed = val;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("multipleReactionsPromoShowed", multipleReactionsPromoShowed);
        editor.apply();
    }

    public static void setMinegramBubbles(boolean value) {
        minegramBubbles = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("minegramBubbles", minegramBubbles);
        editor.apply();
    }

    public static void setMinegramWave(boolean value) {
        minegramWave = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("minegramWave", minegramWave);
        editor.apply();
    }

    public static void setMinegramLargeCorners(boolean value) {
        minegramLargeCorners = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("minegramLargeCorners", minegramLargeCorners);
        editor.apply();
    }

    public static void setMinegramAccent(int value) {
        minegramAccent = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("minegramAccent", minegramAccent);
        editor.apply();
    }

    public static void setTgwsProxyEnabled(boolean value) {
        tgwsProxyEnabled = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("tgwsProxyEnabled", tgwsProxyEnabled);
        editor.apply();
    }

    public static void setTgwsProxyPort(int value) {
        tgwsProxyPort = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("tgwsProxyPort", tgwsProxyPort);
        editor.apply();
    }

    public static void setSaveDeletedMessages(boolean value) {
        saveDeletedMessages = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("saveDeletedMessages", saveDeletedMessages);
        editor.apply();
    }

    public static void setDeletedMessagesOpacity(int value) {
        deletedMessagesOpacity = Math.max(10, Math.min(100, value));
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        preferences.edit().putInt("dgDeletedMessagesOpacity", deletedMessagesOpacity).apply();
    }

    public static void setChatPlates(boolean value) {
        chatPlates = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("chatPlates", chatPlates);
        editor.apply();
    }

    public static void setChatPlateColor(int value) {
        chatPlateColor = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        preferences.edit().putInt("dgChatPlateColor", chatPlateColor).apply();
    }

    public static void setChatPlateOpacity(int value) {
        chatPlateOpacity = Math.max(10, Math.min(100, value));
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        preferences.edit().putInt("dgChatPlateOpacity", chatPlateOpacity).apply();
    }

    public static void setDialogsOwnWallpaper(boolean value) {
        dialogsOwnWallpaper = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        preferences.edit().putBoolean("dgDialogsOwnWallpaper", dialogsOwnWallpaper).apply();
        dialogsOwnWallpaperBitmap = null;
    }

    public static void setDialogsWallpaperScale(int value) {
        dialogsWallpaperScale = Math.max(100, Math.min(400, value));
        MessagesController.getGlobalMainSettings().edit().putInt("dgDialogsWallpaperScale", dialogsWallpaperScale).apply();
    }

    public static void setDialogsWallpaperOffsetX(int value) {
        dialogsWallpaperOffsetX = Math.max(-100, Math.min(100, value));
        MessagesController.getGlobalMainSettings().edit().putInt("dgDialogsWallpaperOffsetX", dialogsWallpaperOffsetX).apply();
    }

    public static void setDialogsWallpaperOffsetY(int value) {
        dialogsWallpaperOffsetY = Math.max(-100, Math.min(100, value));
        MessagesController.getGlobalMainSettings().edit().putInt("dgDialogsWallpaperOffsetY", dialogsWallpaperOffsetY).apply();
    }

    public static void setDialogsWallpaperBlur(int value) {
        dialogsWallpaperBlur = Math.max(0, Math.min(100, value));
        MessagesController.getGlobalMainSettings().edit().putInt("dgDialogsWallpaperBlur", dialogsWallpaperBlur).apply();
    }

    public static void setMainTabsOpacity(int value) {
        mainTabsOpacity = Math.max(10, Math.min(100, value));
        MessagesController.getGlobalMainSettings().edit().putInt("dgMainTabsOpacity", mainTabsOpacity).apply();
    }

    public static void setDropeRippleStrength(int value) {
        dropeRippleStrength = Math.max(0, Math.min(200, value));
        MessagesController.getGlobalMainSettings().edit().putInt("dgDropeRippleStrength", dropeRippleStrength).apply();
    }


    public static void setPlayerBitrate(boolean value) {
        playerBitrate = value;
        MessagesController.getGlobalMainSettings().edit().putBoolean("dgPlayerBitrate", playerBitrate).apply();
    }

    public static void setAudioCodec(int value) {
        audioCodec = value < AUDIO_CODEC_AUTO || value > AUDIO_CODEC_LDAC ? AUDIO_CODEC_AUTO : value;
        MessagesController.getGlobalMainSettings().edit().putInt("dgAudioCodec", audioCodec).apply();
    }

    public static void setVoiceRecordBitrate(int value) {
        voiceRecordBitrate = value < 0 ? 0 : value;
        MessagesController.getGlobalMainSettings().edit().putInt("dgVoiceRecordBitrate", voiceRecordBitrate).apply();
    }

    private static android.graphics.Bitmap dialogsOwnWallpaperBitmap;

    public static File getDialogsOwnWallpaperFile() {
        return new File(ApplicationLoader.getFilesDirFixed(), "dialogs_wallpaper.dat");
    }

    public static android.graphics.Bitmap getDialogsOwnWallpaper() {
        if (!dialogsOwnWallpaper) {
            return null;
        }
        File file = getDialogsOwnWallpaperFile();
        if (!file.exists()) {
            return null;
        }
        if (dialogsOwnWallpaperBitmap != null && !dialogsOwnWallpaperBitmap.isRecycled()) {
            return dialogsOwnWallpaperBitmap;
        }
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            BitmapFactory.Options real = new BitmapFactory.Options();
            real.inSampleSize = Math.max(1, Math.round(opts.outWidth / 1080f));
            dialogsOwnWallpaperBitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), real);
            return dialogsOwnWallpaperBitmap;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static void setCircleVideoBackCamera(boolean value) {
        circleVideoBackCamera = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("circleVideoBackCamera", circleVideoBackCamera);
        editor.apply();
    }

    public static void setDiveGramCentered(boolean value) {
        diveGramCentered = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("diveGramCentered", diveGramCentered);
        editor.apply();
    }

    public static void setBubbleRadius(int value) {
        bubbleRadius = Math.max(0, Math.min(25, value));
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("bubbleRadius", bubbleRadius);
        editor.apply();
    }

    public static void setOldDesign(boolean value) {
        oldDesign = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("dgOldDesign", oldDesign);
        editor.apply();
    }

    public static void setMusicCardBlur(int value) {
        musicCardBlur = Math.max(0, Math.min(100, value));
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("musicCardBlur", musicCardBlur);
        editor.apply();
    }

    public static void setTgwsProxySecret(String value) {
        tgwsProxySecret = value == null ? "" : value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("tgwsProxySecret", tgwsProxySecret);
        editor.apply();
    }

    public static void setAutoReplyEnabled(boolean value) {
        autoReplyEnabled = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("autoReplyEnabled", autoReplyEnabled);
        editor.apply();
    }

    public static void setAutoReplyText(String value) {
        autoReplyText = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("autoReplyText", autoReplyText);
        editor.apply();
    }

    public static void setAutoReplyCooldownSec(int value) {
        autoReplyCooldownSec = Math.max(60, value);
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("autoReplyCooldownSec", autoReplyCooldownSec);
        editor.apply();
    }

    public static void setWeatherEnabled(boolean value) {
        weatherEnabled = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("dgWeatherEnabled", weatherEnabled);
        editor.apply();
    }

    public static void setWeatherCity(String value) {
        weatherCity = value == null ? "" : value;
        weatherLat = 0;
        weatherLon = 0;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("dgWeatherCity", weatherCity);
        editor.putLong("dgWeatherLat", 0);
        editor.putLong("dgWeatherLon", 0);
        editor.apply();
    }

    public static void saveWeatherCache(String text, double lat, double lon) {
        weatherText = text == null ? "" : text;
        weatherTime = System.currentTimeMillis();
        if (lat != 0 || lon != 0) {
            weatherLat = lat;
            weatherLon = lon;
        }
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("dgWeatherText", weatherText);
        editor.putLong("dgWeatherTime", weatherTime);
        editor.putLong("dgWeatherLat", Double.doubleToRawLongBits(weatherLat));
        editor.putLong("dgWeatherLon", Double.doubleToRawLongBits(weatherLon));
        editor.apply();
    }

    public static void saveBadgesFetchTime() {
        badgesFetchTime = System.currentTimeMillis();
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong("dgBadgesFetchTime", badgesFetchTime);
        editor.putBoolean("dgBadgesEnabled", badgesEnabled);
        editor.apply();
    }

    public static void toggleBadgesEnabled(boolean enabled) {
        badgesEnabled = enabled;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("dgBadgesEnabled", badgesEnabled);
        editor.apply();
    }

    public static void saveStreakFetchTime() {
        streakFetchTime = System.currentTimeMillis();
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong("dgStreaksFetchTime", streakFetchTime);
        editor.putBoolean("dgStreaksEnabled", streaksEnabled);
        editor.apply();
    }

    public static void toggleStreaksEnabled(boolean enabled) {
        streaksEnabled = enabled;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("dgStreaksEnabled", streaksEnabled);
        editor.apply();
    }

    public static void saveNftsFetchTime() {
        nftsFetchTime = System.currentTimeMillis();
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong("dgNftsFetchTime", nftsFetchTime);
        editor.apply();
    }

    public static void setPlayerLyricsEnabled(boolean value) {
        playerLyricsEnabled = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("dgPlayerLyrics", playerLyricsEnabled);
        editor.apply();
    }

    public static void setPlayerLyricsAlbumMode(boolean value) {
        playerLyricsAlbumMode = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("dgPlayerLyricsAlbumMode", playerLyricsAlbumMode);
        editor.apply();
    }

    public static void setPlayerLyricsHideControls(boolean value) {
        playerLyricsHideControls = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("dgPlayerLyricsHideControls", playerLyricsHideControls);
        editor.apply();
    }

    public static void setPlayerLyricsTranslate(boolean value) {
        playerLyricsTranslate = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("dgPlayerLyricsTranslate", playerLyricsTranslate);
        editor.apply();
    }

    public static void setPlayerLyricsTranslateLang(String value) {
        playerLyricsTranslateLang = value == null ? "" : value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        preferences.edit().putString("dgPlayerLyricsTranslateLang", playerLyricsTranslateLang).apply();
    }

    public static void setAlbumLayout(float coverX, float coverY, float coverSize, float lyricsX, float lyricsY, float lyricsWidth) {
        albumCoverX = coverX;
        albumCoverY = coverY;
        albumCoverSize = coverSize;
        albumLyricsX = lyricsX;
        albumLyricsY = lyricsY;
        albumLyricsWidth = lyricsWidth;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putFloat("dgAlbumCoverX", albumCoverX);
        editor.putFloat("dgAlbumCoverY", albumCoverY);
        editor.putFloat("dgAlbumCoverSize", albumCoverSize);
        editor.putFloat("dgAlbumLyricsX", albumLyricsX);
        editor.putFloat("dgAlbumLyricsY", albumLyricsY);
        editor.putFloat("dgAlbumLyricsWidth", albumLyricsWidth);
        editor.apply();
    }

    public static void resetAlbumLayout() {
        setAlbumLayout(0.25f, 0.5f, 0.6f, 0.72f, 0.5f, 0.52f);
    }

    public static void setDialogsWallpaper(boolean value) {
        dialogsWallpaper = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("dgDialogsWallpaper", dialogsWallpaper);
        editor.apply();
    }

    public static void setQuickRepliesEnabled(boolean value) {
        quickRepliesEnabled = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("dgQuickReplies", quickRepliesEnabled);
        editor.apply();
    }

    public static final String[] DEFAULT_QUICK_REPLIES = new String[]{
            "\uD83D\uDC4B Привет!",
            "Спасибо!",
            "Хорошо, договорились",
            "Отвечу позже",
            "Я занят, напишу как освобожусь",
            "\uD83D\uDC4D"
    };

    public static String[] getQuickReplies() {
        String raw = MessagesController.getGlobalMainSettings().getString("dgQuickRepliesText", null);
        if (raw == null || raw.isEmpty()) {
            return DEFAULT_QUICK_REPLIES;
        }
        ArrayList<String> list = new ArrayList<>();
        for (String part : raw.split("\n")) {
            if (!part.trim().isEmpty()) {
                list.add(part);
            }
        }
        return list.toArray(new String[0]);
    }

    public static void setQuickReplies(String[] replies) {
        StringBuilder sb = new StringBuilder();
        for (String r : replies) {
            if (r != null && !r.trim().isEmpty()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(r.trim());
            }
        }
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        preferences.edit().putString("dgQuickRepliesText", sb.toString()).apply();
    }

    public static void setMentionAllEnabled(boolean value) {
        mentionAllEnabled = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("dgMentionAll", mentionAllEnabled);
        editor.apply();
    }

    public static void setMentionAllSplitEnabled(boolean value) {
        mentionAllSplitEnabled = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("dgMentionAllSplit", mentionAllSplitEnabled);
        editor.apply();
    }

    public static void setAiChatEnabled(boolean value) {
        aiChatEnabled = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("dgAiChat", aiChatEnabled);
        editor.apply();
    }

    public static void setGeminiApiKey(String value) {
        geminiApiKey = value == null ? "" : value.trim();
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("dgGeminiKey", geminiApiKey);
        editor.apply();
    }

    public static void setHideAllChatsFolder(boolean value) {
        hideAllChatsFolder = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("dgHideAllChats", hideAllChatsFolder);
        editor.apply();
    }

    public static void setHideContactsBar(boolean value) {
        hideContactsBar = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("dgHideContactsBar", hideContactsBar);
        editor.apply();
    }


    private static String dgAccKey(String key) {
        return key + "_a" + UserConfig.selectedAccount;
    }

    private static long getAccLong(SharedPreferences p, String key, long def) {
        final String k = dgAccKey(key);
        return p.contains(k) ? p.getLong(k, def) : p.getLong(key, def);
    }

    private static int getAccInt(SharedPreferences p, String key, int def) {
        final String k = dgAccKey(key);
        return p.contains(k) ? p.getInt(k, def) : p.getInt(key, def);
    }

    private static String getAccString(SharedPreferences p, String key, String def) {
        final String k = dgAccKey(key);
        return p.contains(k) ? p.getString(k, def) : p.getString(key, def);
    }

    private static boolean getAccBoolean(SharedPreferences p, String key, boolean def) {
        final String k = dgAccKey(key);
        return p.contains(k) ? p.getBoolean(k, def) : p.getBoolean(key, def);
    }

    public static void reloadDiveGramPremiumDecor() {        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        localEmojiStatusDocumentId = getAccLong(preferences, "dgLocalEmojiDoc", -1);
        localEmojiStatusCollectibleId = getAccInt(preferences, "dgLocalEmojiColl", 0);
        localEmojiStatusUntil = getAccLong(preferences, "dgLocalEmojiUntil", 0);
        localEmojiStatusB64 = getAccString(preferences, "dgLocalEmojiB64", "");
        localNameColorB64 = getAccString(preferences, "dgLocalNameColor", "");
        localProfileColorB64 = getAccString(preferences, "dgLocalProfileColor", "");
        wornGiftCollectibleId = getAccLong(preferences, "dgWornCollId", 0);
        wornGiftDocumentId = getAccLong(preferences, "dgWornDocId", 0);
        wornGiftPatternDocId = getAccLong(preferences, "dgWornPatternDocId", 0);
        wornGiftCenterColor = getAccInt(preferences, "dgWornCenterColor", 0);
        wornGiftEdgeColor = getAccInt(preferences, "dgWornEdgeColor", 0);
        wornGiftTextColor = getAccInt(preferences, "dgWornTextColor", 0);
        wornGiftPatternColor = getAccInt(preferences, "dgWornPatternColor", 0);
        wornGiftPatternDocB64 = getAccString(preferences, "dgWornPatternDocB64", "");
        wornGiftIsLocal = getAccBoolean(preferences, "dgWornIsLocal", false);
        wornGiftModelDocB64 = getAccString(preferences, "dgWornModelDocB64", "");
        wornGiftObjectB64 = getAccString(preferences, "dgWornObjectB64", "");
    }

    /**
     */
    public static void saveWornGiftObject(TL_stars.TL_starGiftUnique gift) {
        String objectB64 = "";
        if (gift != null) {
            try {
                org.telegram.tgnet.SerializedData out = new org.telegram.tgnet.SerializedData();
                gift.serializeToStream(out);
                objectB64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP);
                out.cleanup();
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
        wornGiftObjectB64 = objectB64;
        MessagesController.getGlobalMainSettings().edit().putString(dgAccKey("dgWornObjectB64"), wornGiftObjectB64).apply();
    }

    public static TL_stars.TL_starGiftUnique getWornGiftObject() {
        if (wornGiftObjectB64 == null || wornGiftObjectB64.isEmpty()) return null;
        try {
            byte[] data = android.util.Base64.decode(wornGiftObjectB64, android.util.Base64.NO_WRAP);
            org.telegram.tgnet.InputSerializedData in = new org.telegram.tgnet.SerializedData(data);
            TL_stars.StarGift gift = TL_stars.StarGift.TLdeserialize(in, in.readInt32(false), false);
            return gift instanceof TL_stars.TL_starGiftUnique ? (TL_stars.TL_starGiftUnique) gift : null;
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    private static void saveGlobalFlagPref(String key, Object value) {
        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        editor.apply();
    }

    public static void setShowTypingLocation(boolean v) {
        showTypingLocation = v;
        saveGlobalFlagPref("dgShowTypingLocation", v);
    }

    public static void setHideCameraInPicker(boolean value) {
        hideCameraInPicker = value;
        saveGlobalFlagPref("dgHideCameraInPicker", value);
    }

    public static boolean isLyricsHidden(String key) {
        return hiddenLyricsKeys.contains(key);
    }

    public static void setLyricsHidden(String key, boolean hidden) {
        if (hidden) {
            hiddenLyricsKeys.add(key);
        } else {
            hiddenLyricsKeys.remove(key);
        }
        StringBuilder sb = new StringBuilder();
        for (String k : hiddenLyricsKeys) {
            if (sb.length() > 0) sb.append(",");
            sb.append(k);
        }
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        preferences.edit().putString("dgHiddenLyrics", sb.toString()).apply();
    }

    public static void setAiModelId(String value) {
        aiModelId = value == null ? "" : value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        preferences.edit().putString("dgAiModel", aiModelId).apply();
    }

    public static void setLocalEmojiStatus(long documentId, int collectibleId, long until) {
        localEmojiStatusDocumentId = documentId;
        localEmojiStatusCollectibleId = collectibleId;
        localEmojiStatusUntil = until;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        preferences.edit()
                .putLong(dgAccKey("dgLocalEmojiDoc"), documentId)
                .putInt(dgAccKey("dgLocalEmojiColl"), collectibleId)
                .putLong(dgAccKey("dgLocalEmojiUntil"), until)
                .apply();
    }

    public static void setLocalEmojiStatusBlob(String b64) {
        localEmojiStatusB64 = b64 == null ? "" : b64;
        MessagesController.getGlobalMainSettings().edit().putString(dgAccKey("dgLocalEmojiB64"), localEmojiStatusB64).apply();
    }

    public static void saveWornGift(TLRPC.TL_emojiStatusCollectible status) {
        saveWornGift(status, null);
    }

    public static void saveWornGift(TLRPC.TL_emojiStatusCollectible status, TLRPC.Document patternDocument) {
        saveWornGift(status, patternDocument, false);
    }

    public static void saveWornGift(TLRPC.TL_emojiStatusCollectible status, TLRPC.Document patternDocument, boolean isLocal) {
        if (status == null) {
            clearWornGift();
            return;
        }
        wornGiftCollectibleId = status.collectible_id;
        wornGiftDocumentId = status.document_id;
        wornGiftPatternDocId = status.pattern_document_id;
        wornGiftCenterColor = status.center_color;
        wornGiftEdgeColor = status.edge_color;
        wornGiftTextColor = status.text_color;
        wornGiftPatternColor = status.pattern_color;
        String newPatternB64 = serializeDocument(patternDocument);
        boolean effectiveLocal = isLocal;
        if (!isLocal && wornGiftIsLocal && status.collectible_id == wornGiftCollectibleId && newPatternB64.isEmpty()) {
            newPatternB64 = wornGiftPatternDocB64;
            effectiveLocal = true;
        }
        wornGiftPatternDocB64 = newPatternB64;
        wornGiftIsLocal = effectiveLocal;
        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        editor.putLong(dgAccKey("dgWornCollId"), wornGiftCollectibleId);
        editor.putLong(dgAccKey("dgWornDocId"), wornGiftDocumentId);
        editor.putLong(dgAccKey("dgWornPatternDocId"), wornGiftPatternDocId);
        editor.putInt(dgAccKey("dgWornCenterColor"), wornGiftCenterColor);
        editor.putInt(dgAccKey("dgWornEdgeColor"), wornGiftEdgeColor);
        editor.putInt(dgAccKey("dgWornTextColor"), wornGiftTextColor);
        editor.putInt(dgAccKey("dgWornPatternColor"), wornGiftPatternColor);
        editor.putString(dgAccKey("dgWornPatternDocB64"), wornGiftPatternDocB64);
        editor.putBoolean(dgAccKey("dgWornIsLocal"), wornGiftIsLocal);
        editor.apply();
    }

    private static String serializeDocument(TLRPC.Document document) {
        if (document == null) return "";
        try {
            org.telegram.tgnet.SerializedData out = new org.telegram.tgnet.SerializedData();
            document.serializeToStream(out);
            return android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP);
        } catch (Throwable t) {
            FileLog.e(t);
            return "";
        }
    }

    public static void saveWornModelDoc(TLRPC.Document document) {
        wornGiftModelDocB64 = serializeDocument(document);
        MessagesController.getGlobalMainSettings().edit().putString(dgAccKey("dgWornModelDocB64"), wornGiftModelDocB64).apply();
    }

    public static TLRPC.Document deserializeWornModelDoc() {
        if (wornGiftModelDocB64 == null || wornGiftModelDocB64.isEmpty()) return null;
        try {
            byte[] data = android.util.Base64.decode(wornGiftModelDocB64, android.util.Base64.NO_WRAP);
            org.telegram.tgnet.InputSerializedData in = new org.telegram.tgnet.SerializedData(data);
            return TLRPC.Document.TLdeserialize(in, in.readInt32(false), false);
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    public static TLRPC.Document deserializeWornPatternDoc() {
        if (wornGiftPatternDocB64 == null || wornGiftPatternDocB64.isEmpty()) return null;
        try {
            byte[] data = android.util.Base64.decode(wornGiftPatternDocB64, android.util.Base64.NO_WRAP);
            org.telegram.tgnet.InputSerializedData in = new org.telegram.tgnet.SerializedData(data);
            return TLRPC.Document.TLdeserialize(in, in.readInt32(false), false);
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    public static void clearWornGift() {
        wornGiftCollectibleId = 0;
        wornGiftDocumentId = 0;
        wornGiftPatternDocId = 0;
        wornGiftCenterColor = 0;
        wornGiftEdgeColor = 0;
        wornGiftTextColor = 0;
        wornGiftPatternColor = 0;
        wornGiftPatternDocB64 = "";
        wornGiftIsLocal = false;
        wornGiftModelDocB64 = "";
        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        editor.putLong(dgAccKey("dgWornCollId"), 0);
        editor.putLong(dgAccKey("dgWornDocId"), 0);
        editor.putLong(dgAccKey("dgWornPatternDocId"), 0);
        editor.putInt(dgAccKey("dgWornCenterColor"), 0);
        editor.putInt(dgAccKey("dgWornEdgeColor"), 0);
        editor.putInt(dgAccKey("dgWornTextColor"), 0);
        editor.putInt(dgAccKey("dgWornPatternColor"), 0);
        editor.putString(dgAccKey("dgWornPatternDocB64"), "");
        editor.putBoolean(dgAccKey("dgWornIsLocal"), false);
        editor.putString(dgAccKey("dgWornModelDocB64"), "");
        editor.putString(dgAccKey("dgWornObjectB64"), "");
        wornGiftObjectB64 = "";
        editor.apply();
    }

    public static TLRPC.EmojiStatus deserializeLocalEmojiStatus(String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        try {
            byte[] data = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP);
            org.telegram.tgnet.InputSerializedData in = new org.telegram.tgnet.SerializedData(data);
            int ctor = in.readInt32(false);
            TLRPC.EmojiStatus status;
            if (ctor == TLRPC.TL_emojiStatus.constructor) status = new TLRPC.TL_emojiStatus();
            else if (ctor == TLRPC.TL_emojiStatusCollectible.constructor) status = new TLRPC.TL_emojiStatusCollectible();
            else if (ctor == TLRPC.TL_emojiStatusEmpty.constructor) return new TLRPC.TL_emojiStatusEmpty();
            else return null;
            status.readParams(in, false);
            return status;
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    public static String serializeLocalEmojiStatus(TLRPC.EmojiStatus status) {
        if (status == null) return "";
        try {
            org.telegram.tgnet.SerializedData out = new org.telegram.tgnet.SerializedData();
            status.serializeToStream(out);
            return android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP);
        } catch (Throwable t) {
            FileLog.e(t);
            return "";
        }
    }

    public static void setLocalPeerColor(boolean forProfile, String b64) {
        if (forProfile) {
            localProfileColorB64 = b64 == null ? "" : b64;
            MessagesController.getGlobalMainSettings().edit().putString(dgAccKey("dgLocalProfileColor"), localProfileColorB64).apply();
        } else {
            localNameColorB64 = b64 == null ? "" : b64;
            MessagesController.getGlobalMainSettings().edit().putString(dgAccKey("dgLocalNameColor"), localNameColorB64).apply();
        }
    }

    public static TLRPC.PeerColor deserializeLocalPeerColor(String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        try {
            byte[] data = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP);
            org.telegram.tgnet.InputSerializedData in = new org.telegram.tgnet.SerializedData(data);
            int ctor = in.readInt32(false);
            return TLRPC.PeerColor.TLdeserialize(in, ctor, false);
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    public static String serializeLocalPeerColor(TLRPC.PeerColor color) {
        if (color == null) return "";
        try {
            org.telegram.tgnet.SerializedData out = new org.telegram.tgnet.SerializedData();
            color.serializeToStream(out);
            return android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP);
        } catch (Throwable t) {
            FileLog.e(t);
            return "";
        }
    }

    public static void setAppNameMode(int value) {
        appNameMode = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("appNameMode", appNameMode);
        editor.apply();
    }

    public static void setAppNameCustom(String value) {
        appNameCustom = value == null ? "" : value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("appNameCustom", appNameCustom);
        editor.apply();
    }

    public static String getAppName(TLRPC.User user) {
        if (appNameMode == 1) {
            if (user != null) {
                return UserObject.getUserName(user);
            }
        } else if (appNameMode == 2) {
            if (user != null && !TextUtils.isEmpty(user.username)) {
                return "@" + user.username;
            }
        } else if (appNameMode == 3) {
            if (!TextUtils.isEmpty(appNameCustom)) {
                return appNameCustom;
            }
        }
        return "DiveGram";
    }

    public static void setNumberProtection(boolean value) {
        numberProtection = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("numberProtection", numberProtection);
        editor.apply();
    }

    public static String maskPhoneNumber(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = PhoneFormat.stripExceptNumbers(phone);
        if (digits.length() <= 4) {
            return digits;
        }
        StringBuilder masked = new StringBuilder(digits.length());
        for (int i = 0; i < digits.length(); i++) {
            if (i < 2 || i >= digits.length() - 2) {
                masked.append(digits.charAt(i));
            } else {
                masked.append('*');
            }
        }
        return masked.toString();
    }

    public static void setGhostHideOnline(boolean value) {
        ghostHideOnline = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("ghostHideOnline", ghostHideOnline);
        editor.apply();
    }

    public static void setGhostHideRead(boolean value) {
        ghostHideRead = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("ghostHideRead", ghostHideRead);
        editor.apply();
    }

    public static void setGhostHideTyping(boolean value) {
        ghostHideTyping = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("ghostHideTyping", ghostHideTyping);
        editor.apply();
    }

    public static void setLocalPremium(boolean value) {
        localPremium = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("localPremium", localPremium);
        editor.apply();
    }

    public static boolean isDeletedMessage(long dialogId, int mid) {
        return deletedMessagesIds.contains(dialogId + "_" + mid);
    }

    public static void addDeletedMessage(long dialogId, int mid) {
        deletedMessagesIds.add(dialogId + "_" + mid);
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        preferences.edit().putStringSet("deletedMessagesIds", new HashSet<>(deletedMessagesIds)).apply();
    }

    public static void removeDeletedMessage(long dialogId, int mid) {
        deletedMessagesIds.remove(dialogId + "_" + mid);
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        preferences.edit().putStringSet("deletedMessagesIds", new HashSet<>(deletedMessagesIds)).apply();
    }

    public static HashSet<String> getDeletedMessageKeysCopy() {
        synchronized (deletedMessagesIds) {
            return new HashSet<>(deletedMessagesIds);
        }
    }

    public static void clearDeletedMessages() {
        deletedMessagesIds.clear();
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        preferences.edit().putStringSet("deletedMessagesIds", new HashSet<>()).apply();
    }

    public static void setSuggestStickers(int type) {
        suggestStickers = type;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("suggestStickers", suggestStickers);
        editor.apply();
    }

    public static void setSearchMessagesAsListUsed(boolean value) {
        searchMessagesAsListUsed = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("searchMessagesAsListUsed", searchMessagesAsListUsed);
        editor.apply();
    }

    public static void setStickersReorderingHintUsed(boolean value) {
        stickersReorderingHintUsed = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("stickersReorderingHintUsed", stickersReorderingHintUsed);
        editor.apply();
    }

    public static void setStoriesReactionsLongPressHintUsed(boolean value) {
        storyReactionsLongPressHint = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("storyReactionsLongPressHint", storyReactionsLongPressHint);
        editor.apply();
    }

    public static void setStoriesIntroShown(boolean isShown) {
        storiesIntroShown = isShown;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("storiesIntroShown", storiesIntroShown);
        editor.apply();
    }

    public static void increaseTextSelectionHintShowed() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("textSelectionHintShows", ++textSelectionHintShows);
        editor.apply();
    }

    public static void increaseDayNightWallpaperSiwtchHint() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("dayNightWallpaperSwitchHint", ++dayNightWallpaperSwitchHint);
        editor.apply();
    }

    public static void removeTextSelectionHint() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("textSelectionHintShows", 3);
        editor.apply();
    }

    public static void increaseScheduledOrNoSoundHintShowed() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        scheduledOrNoSoundHintSeenAt = System.currentTimeMillis();
        editor.putInt("scheduledOrNoSoundHintShows", ++scheduledOrNoSoundHintShows);
        editor.putLong("scheduledOrNoSoundHintSeenAt", scheduledOrNoSoundHintSeenAt);
        editor.apply();
    }

    public static void increaseScheduledHintShowed() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        scheduledHintSeenAt = System.currentTimeMillis();
        editor.putInt("scheduledHintShows", ++scheduledHintShows);
        editor.putLong("scheduledHintSeenAt", scheduledHintSeenAt);
        editor.apply();
    }

    public static void forwardingOptionsHintHintShowed() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        forwardingOptionsHintShown = true;
        editor.putBoolean("forwardingOptionsHintShown", forwardingOptionsHintShown);
        editor.apply();
    }

    public static void replyingOptionsHintHintShowed() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        replyingOptionsHintShown = true;
        editor.putBoolean("replyingOptionsHintShown", replyingOptionsHintShown);
        editor.apply();
    }

    public static void removeScheduledOrNoSoundHint() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("scheduledOrNoSoundHintShows", 3);
        editor.apply();
    }

    public static void removeScheduledHint() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("scheduledHintShows", 3);
        editor.apply();
    }

    public static void increaseLockRecordAudioVideoHintShowed() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("lockRecordAudioVideoHint", ++lockRecordAudioVideoHint);
        editor.apply();
    }

    public static void removeLockRecordAudioVideoHint() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("lockRecordAudioVideoHint", 3);
        editor.apply();
    }

    public static void setKeepMedia(int value) {
        keepMedia = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("keep_media", keepMedia);
        editor.apply();
    }

    public static void toggleUpdateStickersOrderOnSend() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("updateStickersOrderOnSend", updateStickersOrderOnSend = !updateStickersOrderOnSend);
        editor.apply();
    }

    public static void checkLogsToDelete() {
        if (!BuildVars.LOGS_ENABLED) {
            return;
        }
        int time = (int) (System.currentTimeMillis() / 1000);
        if (Math.abs(time - lastLogsCheckTime) < 60 * 60) {
            return;
        }
        lastLogsCheckTime = time;
        Utilities.cacheClearQueue.postRunnable(() -> {
            long currentTime = time - 60 * 60 * 24 * 10;
            try {
                File dir = AndroidUtilities.getLogsDir();
                if (dir == null) {
                    return;
                }
                Utilities.clearDir(dir.getAbsolutePath(), 0, currentTime, false);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt("lastLogsCheckTime", lastLogsCheckTime);
            editor.apply();
        });
    }

    public static void toggleDisableVoiceAudioEffects() {
        disableVoiceAudioEffects = !disableVoiceAudioEffects;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("disableVoiceAudioEffects", disableVoiceAudioEffects);
        editor.apply();
    }

    public static void toggleNoiseSupression() {
        noiseSupression = !noiseSupression;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("noiseSupression", noiseSupression);
        editor.apply();
    }

    public static void toggleDebugWebView() {
        debugWebView = !debugWebView;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(debugWebView);
        }
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("debugWebView", debugWebView);
        editor.apply();
    }

    public static void incrementCallEncryptionHintDisplayed(int count) {
        callEncryptionHintDisplayedCount += count;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("callEncryptionHintDisplayedCount", callEncryptionHintDisplayedCount);
        editor.apply();
    }

    public static void toggleLoopStickers() {
        LiteMode.toggleFlag(LiteMode.FLAG_ANIMATED_STICKERS_CHAT);
    }

    public static void toggleBigEmoji() {
        allowBigEmoji = !allowBigEmoji;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("allowBigEmoji", allowBigEmoji);
        editor.apply();
    }

    public static void toggleUseSystemBoldFont() {
        useSystemBoldFont = !useSystemBoldFont;
        AndroidUtilities.mediumTypeface = null;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("useSystemBoldFont", useSystemBoldFont);
        editor.apply();
    }

    public static void toggleForceForumTabs() {
        forceForumTabs = !forceForumTabs;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("forceForumTabs", forceForumTabs);
        editor.apply();
    }

    public static void toggleFastWallpaperDisabled() {
        fastWallpaperDisabled = !fastWallpaperDisabled;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("fastWallpaperDisabled", fastWallpaperDisabled);
        editor.apply();
    }

    public static void toggleFrameMetricsEnabled() {
        frameMetricsEnabled = !frameMetricsEnabled;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("frameMetricsEnabled", frameMetricsEnabled);
        editor.apply();
    }

    public static void toggleSuggestAnimatedEmoji() {
        suggestAnimatedEmoji = !suggestAnimatedEmoji;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("suggestAnimatedEmoji", suggestAnimatedEmoji);
        editor.apply();
    }

    public static void setPlaybackOrderType(int type) {
        if (type == 2) {
            shuffleMusic = true;
            playOrderReversed = false;
        } else if (type == 1) {
            playOrderReversed = true;
            shuffleMusic = false;
        } else {
            playOrderReversed = false;
            shuffleMusic = false;
        }
        MediaController.getInstance().checkIsNextMediaFileDownloaded();
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("shuffleMusic", shuffleMusic);
        editor.putBoolean("playOrderReversed", playOrderReversed);
        editor.apply();
    }

    public static void setRepeatMode(int mode) {
        repeatMode = mode;
        if (repeatMode < 0 || repeatMode > 2) {
            repeatMode = 0;
        }
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("repeatMode", repeatMode);
        editor.apply();
    }

    public static void overrideDevicePerformanceClass(int performanceClass) {
        MessagesController.getGlobalMainSettings().edit().putInt("overrideDevicePerformanceClass", overrideDevicePerformanceClass = performanceClass).remove("lite_mode").apply();
        if (liteMode != null) {
            liteMode.loadPreference();
        }
    }

    public static void toggleAutoplayGifs() {
        LiteMode.toggleFlag(LiteMode.FLAG_AUTOPLAY_GIFS);
    }

    public static void setUseThreeLinesLayout(boolean value) {
        useThreeLinesLayout = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("useThreeLinesLayout", useThreeLinesLayout);
        editor.apply();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.dialogsNeedReload, true);
    }

    public static void toggleArchiveHidden() {
        archiveHidden = !archiveHidden;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("archiveHidden", archiveHidden);
        editor.apply();
    }

    public static void toggleAutoplayVideo() {
        LiteMode.toggleFlag(LiteMode.FLAG_AUTOPLAY_VIDEOS);
    }

    public static boolean isSecretMapPreviewSet() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        return preferences.contains("mapPreviewType");
    }

    public static void setSecretMapPreviewType(int value) {
        mapPreviewType = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("mapPreviewType", mapPreviewType);
        editor.apply();
    }

    public static void setSearchEngineType(int value) {
        searchEngineType = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("searchEngineType", searchEngineType);
        editor.apply();
    }

    public static void setNoSoundHintShowed(boolean value) {
        if (noSoundHintShowed == value) {
            return;
        }
        noSoundHintShowed = value;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("noSoundHintShowed", noSoundHintShowed);
        editor.apply();
    }

    public static void toggleRaiseToSpeak() {
        raiseToSpeak = !raiseToSpeak;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("raise_to_speak", raiseToSpeak);
        editor.apply();
    }

    public static void toggleRaiseToListen() {
        raiseToListen = !raiseToListen;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("raise_to_listen", raiseToListen);
        editor.apply();
    }

    public static void toggleNextMediaTap() {
        nextMediaTap = !nextMediaTap;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("next_media_on_tap", nextMediaTap);
        editor.apply();
    }

    public static boolean enabledRaiseTo(boolean speak) {
        return raiseToListen && (!speak || raiseToSpeak);
    }

    public static void toggleBrowserAdaptableColors() {
        adaptableColorInBrowser = !adaptableColorInBrowser;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("adaptableBrowser", adaptableColorInBrowser);
        editor.apply();
    }

    public static void toggleDebugVideoQualities() {
        debugVideoQualities = !debugVideoQualities;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("debugVideoQualities", debugVideoQualities);
        editor.apply();
    }

    public static void toggleLocalInstantView() {
        onlyLocalInstantView = !onlyLocalInstantView;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("onlyLocalInstantView", onlyLocalInstantView);
        editor.apply();
    }

    public static void toggleDirectShare() {
        directShare = !directShare;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("direct_share", directShare);
        editor.apply();
        ShortcutManagerCompat.removeAllDynamicShortcuts(ApplicationLoader.applicationContext);
        MediaDataController.getInstance(UserConfig.selectedAccount).buildShortcuts();
    }

    public static void toggleStreamMedia() {
        streamMedia = !streamMedia;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("streamMedia", streamMedia);
        editor.apply();
    }

    public static void toggleSortContactsByName() {
        sortContactsByName = !sortContactsByName;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("sortContactsByName", sortContactsByName);
        editor.apply();
    }

    public static void toggleSortFilesByName() {
        sortFilesByName = !sortFilesByName;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("sortFilesByName", sortFilesByName);
        editor.apply();
    }

    public static void toggleStreamAllVideo() {
        streamAllVideo = !streamAllVideo;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("streamAllVideo", streamAllVideo);
        editor.apply();
    }

    public static void toggleStreamMkv() {
        streamMkv = !streamMkv;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("streamMkv", streamMkv);
        editor.apply();
    }

    public static void toggleSaveStreamMedia() {
        saveStreamMedia = !saveStreamMedia;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("saveStreamMedia", saveStreamMedia);
        editor.apply();
    }

    public static void togglePauseMusicOnRecord() {
        pauseMusicOnRecord = !pauseMusicOnRecord;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("pauseMusicOnRecord", pauseMusicOnRecord);
        editor.apply();
    }

    public static void togglePauseMusicOnMedia() {
        pauseMusicOnMedia = !pauseMusicOnMedia;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("pauseMusicOnMedia", pauseMusicOnMedia);
        editor.apply();
    }

    public static void toggleChatBlur() {
        LiteMode.toggleFlag(LiteMode.FLAG_CHAT_BLUR);
    }

    public static void toggleForceDisableTabletMode() {
        forceDisableTabletMode = !forceDisableTabletMode;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("forceDisableTabletMode", forceDisableTabletMode);
        editor.apply();
    }

    public static void toggleInappCamera() {
        inappCamera = !inappCamera;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("inappCamera", inappCamera);
        editor.apply();
    }

    public static void toggleRoundCamera16to9() {
        roundCamera16to9 = !roundCamera16to9;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("roundCamera16to9", roundCamera16to9);
        editor.apply();
    }

    public static void setDistanceSystemType(int type) {
        distanceSystemType = type;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("distanceSystemType", distanceSystemType);
        editor.apply();
        LocaleController.resetImperialSystemType();
    }

    public static void loadProxyList() {
        if (proxyListLoaded) {
            return;
        }
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        String proxyAddress = preferences.getString("proxy_ip", "");
        String proxyUsername = preferences.getString("proxy_user", "");
        String proxyPassword = preferences.getString("proxy_pass", "");
        String proxySecret = preferences.getString("proxy_secret", "");
        int proxyPort = preferences.getInt("proxy_port", 1080);

        proxyListLoaded = true;
        proxyList.clear();
        currentProxy = null;
        String list = preferences.getString("proxy_list", null);
        if (!TextUtils.isEmpty(list)) {
            byte[] bytes = Base64.decode(list, Base64.DEFAULT);
            SerializedData data = new SerializedData(bytes);
            int count = data.readInt32(false);
            if (count == -1) { // V2 or newer
                int version = data.readByte(false);

                if (version == PROXY_SCHEMA_V2) {
                    count = data.readInt32(false);

                    for (int i = 0; i < count; i++) {
                        ProxyInfo info = new ProxyInfo(
                                data.readString(false),
                                data.readInt32(false),
                                data.readString(false),
                                data.readString(false),
                                data.readString(false));

                        info.ping = data.readInt64(false);
                        info.availableCheckTime = data.readInt64(false);

                        proxyList.add(0, info);
                        if (currentProxy == null && !TextUtils.isEmpty(proxyAddress)) {
                            if (proxyAddress.equals(info.address) && proxyPort == info.port && proxyUsername.equals(info.username) && proxyPassword.equals(info.password)) {
                                currentProxy = info;
                            }
                        }
                    }
                } else {
                    FileLog.e("Unknown proxy schema version: " + version);
                }
            } else {
                for (int a = 0; a < count; a++) {
                    ProxyInfo info = new ProxyInfo(
                            data.readString(false),
                            data.readInt32(false),
                            data.readString(false),
                            data.readString(false),
                            data.readString(false));
                    proxyList.add(0, info);
                    if (currentProxy == null && !TextUtils.isEmpty(proxyAddress)) {
                        if (proxyAddress.equals(info.address) && proxyPort == info.port && proxyUsername.equals(info.username) && proxyPassword.equals(info.password)) {
                            currentProxy = info;
                        }
                    }
                }
            }
            data.cleanup();
        }
        if (currentProxy == null && !TextUtils.isEmpty(proxyAddress)) {
            ProxyInfo info = currentProxy = new ProxyInfo(proxyAddress, proxyPort, proxyUsername, proxyPassword, proxySecret);
            proxyList.add(0, info);
        }
    }

    public static void saveProxyList() {
        List<ProxyInfo> infoToSerialize = new ArrayList<>(proxyList);
        Collections.sort(infoToSerialize, (o1, o2) -> {
            long bias1 = SharedConfig.currentProxy == o1 ? -200000 : 0;
            if (!o1.available) {
                bias1 += 100000;
            }
            long bias2 = SharedConfig.currentProxy == o2 ? -200000 : 0;
            if (!o2.available) {
                bias2 += 100000;
            }
            return Long.compare(o1.ping + bias1, o2.ping + bias2);
        });
        SerializedData serializedData = new SerializedData();
        serializedData.writeInt32(-1);
        serializedData.writeByte(PROXY_CURRENT_SCHEMA_VERSION);
        int count = infoToSerialize.size();
        serializedData.writeInt32(count);
        for (int a = count - 1; a >= 0; a--) {
            ProxyInfo info = infoToSerialize.get(a);
            serializedData.writeString(info.address != null ? info.address : "");
            serializedData.writeInt32(info.port);
            serializedData.writeString(info.username != null ? info.username : "");
            serializedData.writeString(info.password != null ? info.password : "");
            serializedData.writeString(info.secret != null ? info.secret : "");

            serializedData.writeInt64(info.ping);
            serializedData.writeInt64(info.availableCheckTime);
        }
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        preferences.edit().putString("proxy_list", Base64.encodeToString(serializedData.toByteArray(), Base64.NO_WRAP)).apply();
        serializedData.cleanup();
    }

    public static ProxyInfo addProxy(ProxyInfo proxyInfo) {
        loadProxyList();
        int count = proxyList.size();
        for (int a = 0; a < count; a++) {
            ProxyInfo info = proxyList.get(a);
            if (proxyInfo.address.equals(info.address) && proxyInfo.port == info.port && proxyInfo.username.equals(info.username) && proxyInfo.password.equals(info.password) && proxyInfo.secret.equals(info.secret)) {
                return info;
            }
        }
        proxyList.add(0, proxyInfo);
        saveProxyList();
        return proxyInfo;
    }

    public static boolean isProxyEnabled() {
        return MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled", false) && currentProxy != null;
    }

    public static void deleteProxy(ProxyInfo proxyInfo) {
        if (currentProxy == proxyInfo) {
            currentProxy = null;
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean enabled = preferences.getBoolean("proxy_enabled", false);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("proxy_ip", "");
            editor.putString("proxy_pass", "");
            editor.putString("proxy_user", "");
            editor.putString("proxy_secret", "");
            editor.putInt("proxy_port", 1080);
            editor.putBoolean("proxy_enabled", false);
            editor.putBoolean("proxy_enabled_calls", false);
            editor.apply();
            if (enabled) {
                ConnectionsManager.setProxySettings(false, "", 0, "", "", "");
            }
        }
        proxyList.remove(proxyInfo);
        saveProxyList();
    }

    public static void checkSaveToGalleryFiles() {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                File telegramPath = new File(Environment.getExternalStorageDirectory(), "Telegram");
                File imagePath = new File(telegramPath, "Telegram Images");
                imagePath.mkdir();
                File videoPath = new File(telegramPath, "Telegram Video");
                videoPath.mkdir();

                if (!BuildVars.NO_SCOPED_STORAGE) {
                    if (imagePath.isDirectory()) {
                        new File(imagePath, ".nomedia").delete();
                    }
                    if (videoPath.isDirectory()) {
                        new File(videoPath, ".nomedia").delete();
                    }
                } else {
                    if (imagePath.isDirectory()) {
                        AndroidUtilities.createEmptyFile(new File(imagePath, ".nomedia"));
                    }
                    if (videoPath.isDirectory()) {
                        AndroidUtilities.createEmptyFile(new File(videoPath, ".nomedia"));
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    public static int getChatSwipeAction(int currentAccount) {
        if (chatSwipeAction >= 0) {
            if (chatSwipeAction == SwipeGestureSettingsView.SWIPE_GESTURE_FOLDERS && MessagesController.getInstance(currentAccount).dialogFilters.isEmpty()) {
                return SwipeGestureSettingsView.SWIPE_GESTURE_ARCHIVE;
            }
            return chatSwipeAction;
        } else if (!MessagesController.getInstance(currentAccount).dialogFilters.isEmpty()) {
            return SwipeGestureSettingsView.SWIPE_GESTURE_FOLDERS;

        }
        return SwipeGestureSettingsView.SWIPE_GESTURE_ARCHIVE;
    }

    public static void updateChatListSwipeSetting(int newAction) {
        chatSwipeAction = newAction;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        preferences.edit().putInt("ChatSwipeAction", chatSwipeAction).apply();
    }

    public static void updateMessageSeenHintCount(int count) {
        messageSeenHintCount = count;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        preferences.edit().putInt("messageSeenCount", messageSeenHintCount).apply();
    }

    public static void updateEmojiInteractionsHintCount(int count) {
        emojiInteractionsHintCount = count;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        preferences.edit().putInt("emojiInteractionsHintCount", emojiInteractionsHintCount).apply();
    }

    public static void updateDayNightThemeSwitchHintCount(int count) {
        dayNightThemeSwitchHintCount = count;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        preferences.edit().putInt("dayNightThemeSwitchHintCount", dayNightThemeSwitchHintCount).apply();
    }

    public static void updateStealthModeSendMessageConfirm(int count) {
        stealthModeSendMessageConfirm = count;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        preferences.edit().putInt("stealthModeSendMessageConfirm", stealthModeSendMessageConfirm).apply();
    }

    public final static int PERFORMANCE_CLASS_LOW = 0;
    public final static int PERFORMANCE_CLASS_AVERAGE = 1;
    public final static int PERFORMANCE_CLASS_HIGH = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            PERFORMANCE_CLASS_LOW,
            PERFORMANCE_CLASS_AVERAGE,
            PERFORMANCE_CLASS_HIGH
    })
    public @interface PerformanceClass {}

    @PerformanceClass
    public static int getDevicePerformanceClass() {
        if (overrideDevicePerformanceClass != -1) {
            return overrideDevicePerformanceClass;
        }
        if (devicePerformanceClass == -1) {
            devicePerformanceClass = measureDevicePerformanceClass();
        }
        return devicePerformanceClass;
    }

    public static int measureDevicePerformanceClass() {
        int androidVersion = Build.VERSION.SDK_INT;
        int cpuCount = ConnectionsManager.CPU_COUNT;
        int memoryClass = ((ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.SOC_MODEL != null) {
            int hash = Build.SOC_MODEL.toUpperCase().hashCode();
            for (int i = 0; i < LOW_SOC.length; ++i) {
                if (LOW_SOC[i] == hash) {
                    return PERFORMANCE_CLASS_LOW;
                }
            }
        }

        int totalCpuFreq = 0;
        int freqResolved = 0;
        for (int i = 0; i < cpuCount; i++) {
            try {
                RandomAccessFile reader = new RandomAccessFile(String.format(Locale.ENGLISH, "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", i), "r");
                String line = reader.readLine();
                if (line != null) {
                    totalCpuFreq += Utilities.parseInt(line) / 1000;
                    freqResolved++;
                }
                reader.close();
            } catch (Throwable ignore) {}
        }
        int maxCpuFreq = freqResolved == 0 ? -1 : (int) Math.ceil(totalCpuFreq / (float) freqResolved);

        long ram = -1;
        try {
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            ((ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memoryInfo);
            ram = memoryInfo.totalMem;
        } catch (Exception ignore) {}

        int performanceClass;
        if (
            androidVersion < 21 ||
            cpuCount <= 2 ||
            memoryClass <= 100 ||
            cpuCount <= 4 && maxCpuFreq != -1 && maxCpuFreq <= 1250 ||
            cpuCount <= 4 && maxCpuFreq <= 1600 && memoryClass <= 128 && androidVersion <= 21 ||
            cpuCount <= 4 && maxCpuFreq <= 1300 && memoryClass <= 128 && androidVersion <= 24 ||
            ram != -1 && ram < 2L * 1024L * 1024L * 1024L
        ) {
            performanceClass = PERFORMANCE_CLASS_LOW;
        } else if (
            cpuCount < 8 ||
            memoryClass <= 160 ||
            maxCpuFreq != -1 && maxCpuFreq <= 2055 ||
            maxCpuFreq == -1 && cpuCount == 8 && androidVersion <= 23
        ) {
            performanceClass = PERFORMANCE_CLASS_AVERAGE;
        } else {
            performanceClass = PERFORMANCE_CLASS_HIGH;
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("device performance info selected_class = " + performanceClass + " (cpu_count = " + cpuCount + ", freq = " + maxCpuFreq + ", memoryClass = " + memoryClass + ", android version " + androidVersion + ", manufacture " + Build.MANUFACTURER + ", screenRefreshRate=" + AndroidUtilities.screenRefreshRate + ", screenMaxRefreshRate=" + AndroidUtilities.screenMaxRefreshRate + ")");
        }

        return performanceClass;
    }

    public static String performanceClassName(int perfClass) {
        switch (perfClass) {
            case PERFORMANCE_CLASS_HIGH: return "HIGH";
            case PERFORMANCE_CLASS_AVERAGE: return "AVERAGE";
            case PERFORMANCE_CLASS_LOW: return "LOW";
            default: return "UNKNOWN";
        }
    }

    public static void setMediaColumnsCount(int count) {
        if (mediaColumnsCount != count) {
            mediaColumnsCount = count;
            ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit().putInt("mediaColumnsCount", mediaColumnsCount).apply();
        }
    }

    public static void setStoriesColumnsCount(int count) {
        if (storiesColumnsCount != count) {
            storiesColumnsCount = count;
            ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit().putInt("storiesColumnsCount", storiesColumnsCount).apply();
        }
    }

    public static void setFastScrollHintCount(int count) {
        if (fastScrollHintCount != count) {
            fastScrollHintCount = count;
            ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit().putInt("fastScrollHintCount", fastScrollHintCount).apply();
        }
    }

    public static void setDontAskManageStorage(boolean b) {
        dontAskManageStorage = b;
        ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit().putBoolean("dontAskManageStorage", dontAskManageStorage).apply();
    }

    public static boolean canBlurChat() {
        return getDevicePerformanceClass() >= (Build.VERSION.SDK_INT >= 31 ? PERFORMANCE_CLASS_AVERAGE : PERFORMANCE_CLASS_HIGH) || BuildVars.DEBUG_PRIVATE_VERSION;
    }

    public static boolean chatBlurEnabled() {
        return canBlurChat() && LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR);
    }

    public static class BackgroundActivityPrefs {
        private static SharedPreferences prefs;

        public static long getLastCheckedBackgroundActivity() {
            return prefs.getLong("last_checked", 0);
        }

        public static void setLastCheckedBackgroundActivity(long l) {
            prefs.edit().putLong("last_checked", l).apply();
        }

        public static int getDismissedCount() {
            return prefs.getInt("dismissed_count", 0);
        }

        public static void increaseDismissedCount() {
            prefs.edit().putInt("dismissed_count", getDismissedCount() + 1).apply();
        }
    }

    private static Boolean animationsEnabled;

    public static void setAnimationsEnabled(boolean b) {
        animationsEnabled = b;
    }

    public static boolean animationsEnabled() {
        if (animationsEnabled == null) {
            animationsEnabled = MessagesController.getGlobalMainSettings().getBoolean("view_animations", true);
        }
        return animationsEnabled;
    }

    public static SharedPreferences getPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("userconfing", Context.MODE_PRIVATE);
    }

    public static boolean deviceIsLow() {
        return getDevicePerformanceClass() == PERFORMANCE_CLASS_LOW;
    }

    public static boolean deviceIsAboveAverage() {
        return getDevicePerformanceClass() >= PERFORMANCE_CLASS_AVERAGE;
    }

    public static boolean deviceIsHigh() {
        return getDevicePerformanceClass() >= PERFORMANCE_CLASS_HIGH;
    }

    public static boolean deviceIsAverage() {
        return getDevicePerformanceClass() <= PERFORMANCE_CLASS_AVERAGE;
    }

    public static void toggleRoundCamera() {
        bigCameraForRound = !bigCameraForRound;
        ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE)
                .edit()
                .putBoolean("bigCameraForRound", bigCameraForRound)
                .apply();
    }

    public static void toggleUseNewBlur() {
        useNewBlur = !useNewBlur;
        ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE)
                .edit()
                .putBoolean("useNewBlur", useNewBlur)
                .apply();
    }

    public static boolean isUsingCamera2(int currentAccount) {
        return useCamera2Force == null ? !MessagesController.getInstance(currentAccount).androidDisableRoundCamera2 : useCamera2Force;
    }

    public static void toggleUseCamera2(int currentAccount) {
        ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE)
                .edit()
                .putBoolean("useCamera2Force_2", useCamera2Force = !isUsingCamera2(currentAccount))
                .apply();
    }


    @Deprecated
    public static int getLegacyDevicePerformanceClass() {
        if (legacyDevicePerformanceClass == -1) {
            int androidVersion = Build.VERSION.SDK_INT;
            int cpuCount = ConnectionsManager.CPU_COUNT;
            int memoryClass = ((ActivityManager) ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
            int totalCpuFreq = 0;
            int freqResolved = 0;
            for (int i = 0; i < cpuCount; i++) {
                try {
                    RandomAccessFile reader = new RandomAccessFile(String.format(Locale.ENGLISH, "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq", i), "r");
                    String line = reader.readLine();
                    if (line != null) {
                        totalCpuFreq += Utilities.parseInt(line) / 1000;
                        freqResolved++;
                    }
                    reader.close();
                } catch (Throwable ignore) {}
            }
            int maxCpuFreq = freqResolved == 0 ? -1 : (int) Math.ceil(totalCpuFreq / (float) freqResolved);

            if (androidVersion < 21 || cpuCount <= 2 || memoryClass <= 100 || cpuCount <= 4 && maxCpuFreq != -1 && maxCpuFreq <= 1250 || cpuCount <= 4 && maxCpuFreq <= 1600 && memoryClass <= 128 && androidVersion <= 21 || cpuCount <= 4 && maxCpuFreq <= 1300 && memoryClass <= 128 && androidVersion <= 24) {
                legacyDevicePerformanceClass = PERFORMANCE_CLASS_LOW;
            } else if (cpuCount < 8 || memoryClass <= 160 || maxCpuFreq != -1 && maxCpuFreq <= 2050 || maxCpuFreq == -1 && cpuCount == 8 && androidVersion <= 23) {
                legacyDevicePerformanceClass = PERFORMANCE_CLASS_AVERAGE;
            } else {
                legacyDevicePerformanceClass = PERFORMANCE_CLASS_HIGH;
            }
        }
        return legacyDevicePerformanceClass;
    }


    //DEBUG
    public static boolean drawActionBarShadow = true;

    private static void loadDebugConfig(SharedPreferences preferences) {
        drawActionBarShadow = preferences.getBoolean("drawActionBarShadow", true);
    }

    public static void saveDebugConfig() {
        SharedPreferences pref = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        pref.edit().putBoolean("drawActionBarShadow", drawActionBarShadow);
    }



}
