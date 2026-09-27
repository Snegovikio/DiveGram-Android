/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import org.telegram.messenger.tgwsproxy.proxy.ProxyServer;

import java.util.HashMap;
import java.util.Map;

public class ProxyService extends Service {

    private static final Map<Integer, String> DEFAULT_DC_OPT;

    static {
        DEFAULT_DC_OPT = new HashMap<>();
        DEFAULT_DC_OPT.put(1, "149.154.175.50");
        DEFAULT_DC_OPT.put(2, "149.154.167.220");
        DEFAULT_DC_OPT.put(3, "149.154.175.100");
        DEFAULT_DC_OPT.put(4, "149.154.167.220");
        DEFAULT_DC_OPT.put(5, "149.154.171.5");
        DEFAULT_DC_OPT.put(203, "91.105.192.100");
    }

    private ProxyServer server;

    public static void start() {
        Intent intent = new Intent(ApplicationLoader.applicationContext, ProxyService.class);
        try {
            ApplicationLoader.applicationContext.startService(intent);
        } catch (Exception ignored) {
        }
    }

    public static void stop() {
        Intent intent = new Intent(ApplicationLoader.applicationContext, ProxyService.class);
        try {
            ApplicationLoader.applicationContext.stopService(intent);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationsController.checkOtherNotificationsChannel();
        int port = SharedConfig.tgwsProxyPort;
        if (port <= 0 || port > 65535) {
            port = 1443;
            SharedConfig.setTgwsProxyPort(port);
        }
        Notification notification = new Notification.Builder(this, NotificationsController.OTHER_NOTIFICATIONS_CHANNEL)
                .setContentTitle(getText(R.string.TgWsProxyActive))
                .setContentText(String.format(getString(R.string.TgWsProxyNotificationText), port))
                .setAutoCancel(false)
                .setOngoing(true)
                .setSmallIcon(R.drawable.notification)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(302, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(302, notification);
        }

        if (server == null) {
            server = new ProxyServer("127.0.0.1", port, DEFAULT_DC_OPT, 8, 128, null);
            server.start();
            FileLog.d("ProxyService started, proxy on 127.0.0.1:" + port);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (server != null) {
            server.stop();
            server = null;
        }
        FileLog.d("ProxyService destroyed");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}