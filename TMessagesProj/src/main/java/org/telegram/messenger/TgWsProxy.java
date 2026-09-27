/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger;

import android.content.SharedPreferences;

import org.telegram.tgnet.ConnectionsManager;

public class TgWsProxy {

    public static boolean isRunning() {
        return SharedConfig.tgwsProxyEnabled;
    }

    public static void start() {
        int port = SharedConfig.tgwsProxyPort;
        if (port <= 0 || port > 65535) {
            port = 1443;
            SharedConfig.setTgwsProxyPort(port);
        }

        ProxyService.start();

        SharedConfig.ProxyInfo info = SharedConfig.addProxy(new SharedConfig.ProxyInfo("127.0.0.1", port, "", "", ""));
        SharedConfig.currentProxy = info;

        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        editor.putString("proxy_ip", info.address);
        editor.putString("proxy_pass", info.password);
        editor.putString("proxy_user", info.username);
        editor.putInt("proxy_port", info.port);
        editor.putString("proxy_secret", "");
        editor.putBoolean("proxy_enabled", true);
        editor.commit();

        ConnectionsManager.setProxySettings(true, info.address, info.port, info.username, info.password, "");
        SharedConfig.setTgwsProxyEnabled(true);

        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        FileLog.d("TgWsProxy started on 127.0.0.1:" + port);
    }

    public static void stop() {
        ProxyService.stop();

        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        editor.putBoolean("proxy_enabled", false);
        editor.commit();

        SharedConfig.currentProxy = null;
        ConnectionsManager.setProxySettings(false, "", 1080, "", "", "");
        SharedConfig.setTgwsProxyEnabled(false);

        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        FileLog.d("TgWsProxy stopped");
    }
}