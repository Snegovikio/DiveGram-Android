/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

public class BatteryOptimizationHelper {

    public static boolean isBatteryOptimizationEnabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return !pm.isIgnoringBatteryOptimizations(context.getPackageName());
        }
        return false;
    }

    public static void requestIgnoreBatteryOptimizations(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
            } catch (Exception e) {
                FileLog.e(e);
                openBatterySettings(activity);
            }
        }
    }

    public static void openBatterySettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            context.startActivity(intent);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void openAutoStartSettings(Context context) {
        if ("huawei".equalsIgnoreCase(Build.MANUFACTURER)) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity");
                context.startActivity(intent);
            } catch (Exception e) {
                FileLog.e(e);
                try {
                    Intent intent = new Intent();
                    intent.setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity");
                    context.startActivity(intent);
                } catch (Exception e2) {
                    FileLog.e(e2);
                    openBatterySettings(context);
                }
            }
        } else if ("xiaomi".equalsIgnoreCase(Build.MANUFACTURER) || XiaomiUtilities.isMIUI()) {
            try {
                Intent intent = XiaomiUtilities.getPermissionManagerIntent();
                context.startActivity(intent);
            } catch (Exception e) {
                FileLog.e(e);
                openBatterySettings(context);
            }
        } else if ("samsung".equalsIgnoreCase(Build.MANUFACTURER)) {
            try {
                Intent intent = new Intent();
                intent.setAction("com.samsung.android.intent.action.APP_BATTERY_OPTIMIZATION");
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                context.startActivity(intent);
            } catch (Exception e) {
                FileLog.e(e);
                openBatterySettings(context);
            }
        } else if ("oppo".equalsIgnoreCase(Build.MANUFACTURER) || "realme".equalsIgnoreCase(Build.MANUFACTURER)) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity");
                context.startActivity(intent);
            } catch (Exception e) {
                FileLog.e(e);
                try {
                    Intent intent = new Intent();
                    intent.setClassName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity");
                    context.startActivity(intent);
                } catch (Exception e2) {
                    FileLog.e(e2);
                    openBatterySettings(context);
                }
            }
        } else if ("vivo".equalsIgnoreCase(Build.MANUFACTURER)) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity");
                context.startActivity(intent);
            } catch (Exception e) {
                FileLog.e(e);
                openBatterySettings(context);
            }
        } else if ("oneplus".equalsIgnoreCase(Build.MANUFACTURER)) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity");
                context.startActivity(intent);
            } catch (Exception e) {
                FileLog.e(e);
                openBatterySettings(context);
            }
        } else {
            openBatterySettings(context);
        }
    }

    public static String getManufacturerSpecificTip() {
        String manufacturer = Build.MANUFACTURER;
        if ("samsung".equalsIgnoreCase(manufacturer)) {
            if (OneUIUtilities.isOneUI() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return "AllowBackgroundActivityInfoOneUIAboveS";
            } else if (OneUIUtilities.isOneUI()) {
                return "AllowBackgroundActivityInfoOneUIBelowS";
            }
        } else if ("huawei".equalsIgnoreCase(manufacturer)) {
            return "AllowBackgroundActivityInfoHuawei";
        } else if ("xiaomi".equalsIgnoreCase(manufacturer) || XiaomiUtilities.isMIUI()) {
            return "AllowBackgroundActivityInfoXiaomi";
        } else if ("oppo".equalsIgnoreCase(manufacturer) || "realme".equalsIgnoreCase(manufacturer)) {
            return "AllowBackgroundActivityInfoOppo";
        } else if ("vivo".equalsIgnoreCase(manufacturer)) {
            return "AllowBackgroundActivityInfoVivo";
        } else if ("oneplus".equalsIgnoreCase(manufacturer)) {
            return "AllowBackgroundActivityInfoOnePlus";
        }
        return "AllowBackgroundActivityInfo";
    }
}
