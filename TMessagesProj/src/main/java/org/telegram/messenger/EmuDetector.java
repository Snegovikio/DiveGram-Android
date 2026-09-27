package org.telegram.messenger;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

public class EmuDetector {

    private static final String[] ANDY_FILES = {
            "fstab.andy",
            "ueventd.andy.rc"
    };

    private static final String[] NOX_FILES = {
            "fstab.nox",
            "init.nox.rc",
            "ueventd.nox.rc",
            "/BigNoxGameHD",
            "/YSLauncher"
    };

    private static final String[] BLUE_FILES = {
            "/Android/data/com.bluestacks.home",
            "/Android/data/com.bluestacks.settings"
    };

    private static final String[] GENY_FILES = {
            "/dev/socket/genyd",
            "/dev/socket/baseband_genyd"
    };

    private static final String[] PIPES = {
            "/dev/socket/qemud",
            "/dev/qemu_pipe"
    };

    private static final String[] X86_FILES = {
            "ueventd.android_x86.rc",
            "x86.prop",
            "ueventd.ttVM_x86.rc",
            "init.ttVM_x86.rc",
            "fstab.ttVM_x86",
            "fstab.vbox86",
            "init.vbox86.rc",
            "ueventd.vbox86.rc"
    };

    @SuppressWarnings("SpellCheckingInspection")
    private static final String[] QEMU_DRIVERS = {"goldfish"};

    private static EmuDetector mEmulatorDetector;
    private boolean isCheckPackage = true;
    private final List<String> mListPackageName = new ArrayList<>();

    private boolean detected;
    private boolean detectResult;

    public static EmuDetector with(android.content.Context pContext) {
        if (pContext == null) {
            throw new IllegalArgumentException("Context must not be null.");
        }
        if (mEmulatorDetector == null) {
            mEmulatorDetector = new EmuDetector(pContext.getApplicationContext());
        }
        return mEmulatorDetector;
    }

    private EmuDetector(android.content.Context pContext) {
        mListPackageName.add("com.google.android.launcher.layouts.genymotion");
        mListPackageName.add("com.bluestacks");
        mListPackageName.add("com.bignox.app");
        mListPackageName.add("com.vphone.launcher");
    }

    public boolean isCheckPackage() {
        return isCheckPackage;
    }

    public EmuDetector setCheckPackage(boolean chkPackage) {
        this.isCheckPackage = chkPackage;
        return this;
    }

    public EmuDetector addPackageName(String pPackageName) {
        this.mListPackageName.add(pPackageName);
        return this;
    }

    public EmuDetector addPackageName(List<String> pListPackageName) {
        this.mListPackageName.addAll(pListPackageName);
        return this;
    }

    public boolean detect() {
        if (detected) {
            return detectResult;
        }
        try {
            detected = true;
            detectResult = checkBuildProperties()
                    || checkPackageName()
                    || EmuInputDevicesDetector.detect();
            return detectResult;
        } catch (Exception ignore) {
        }
        return false;
    }

    private boolean checkBuildProperties() {
        boolean result =
                Build.BOARD.toLowerCase().contains("nox")
                        || Build.BOOTLOADER.toLowerCase().contains("nox")
                        || Build.FINGERPRINT.startsWith("generic")
                        || Build.MODEL.toLowerCase().contains("google_sdk")
                        || Build.MODEL.toLowerCase().contains("droid4x")
                        || Build.MODEL.toLowerCase().contains("emulator")
                        || Build.MODEL.contains("Android SDK built for x86")
                        || Build.MANUFACTURER.toLowerCase().contains("genymotion")
                        || Build.HARDWARE.toLowerCase().contains("goldfish")
                        || Build.HARDWARE.toLowerCase().contains("vbox86")
                        || Build.HARDWARE.toLowerCase().contains("android_x86")
                        || Build.HARDWARE.toLowerCase().contains("nox")
                        || Build.HARDWARE.toLowerCase().contains("ranchu")
                        || Build.PRODUCT.equals("sdk")
                        || Build.PRODUCT.equals("google_sdk")
                        || Build.PRODUCT.equals("sdk_x86")
                        || Build.PRODUCT.equals("vbox86p")
                        || Build.PRODUCT.toLowerCase().contains("nox")
                        || "generic".equals(Build.FINGERPRINT);

        if (result) {
            return true;
        }
        result |= Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic");
        if (result) {
            return true;
        }
        result |= "google_sdk".equals(Build.PRODUCT);
        return result;
    }

    private boolean checkPackageName() {
        if (!isCheckPackage || mListPackageName.isEmpty()) {
            return false;
        }
        try {
            final PackageManager pm = ApplicationLoader.applicationContext.getPackageManager();
            for (final String pkgName : mListPackageName) {
                final Intent tryIntent = pm.getLaunchIntentForPackage(pkgName);
                if (tryIntent != null) {
                    final List<ResolveInfo> resolveInfos = pm.queryIntentActivities(tryIntent, PackageManager.MATCH_DEFAULT_ONLY);
                    if (!resolveInfos.isEmpty()) {
                        return true;
                    }
                }
            }
        } catch (Exception ignore) {
        }
        return false;
    }
}
