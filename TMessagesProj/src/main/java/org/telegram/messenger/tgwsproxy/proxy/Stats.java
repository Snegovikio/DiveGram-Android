/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger.tgwsproxy.proxy;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public class Stats {
    public final AtomicLong connectionsTotal = new AtomicLong(0);
    public final AtomicLong connectionsWs = new AtomicLong(0);
    public final AtomicLong connectionsTcpFallback = new AtomicLong(0);
    public final AtomicLong connectionsBad = new AtomicLong(0);
    public final AtomicLong connectionsHttpRejected = new AtomicLong(0);
    public final AtomicLong connectionsPassthrough = new AtomicLong(0);
    public final AtomicLong wsErrors = new AtomicLong(0);
    public final AtomicLong bytesUp = new AtomicLong(0);
    public final AtomicLong bytesDown = new AtomicLong(0);
    public final AtomicLong poolHits = new AtomicLong(0);
    public final AtomicLong poolMisses = new AtomicLong(0);
    public final AtomicLong connectionsFronting = new AtomicLong(0);
    public final AtomicLong connectionsCfProxy = new AtomicLong(0);

    public String summary() {
        return "total=" + connectionsTotal.get() + " ws=" + connectionsWs.get() +
                " tcp_fb=" + connectionsTcpFallback.get() +
                " bad=" + connectionsBad.get() +
                " err=" + wsErrors.get() +
                " up=" + humanBytes(bytesUp.get()) +
                " down=" + humanBytes(bytesDown.get());
    }

    public void reset() {
        connectionsTotal.set(0);
        connectionsWs.set(0);
        connectionsTcpFallback.set(0);
        connectionsHttpRejected.set(0);
        connectionsPassthrough.set(0);
        wsErrors.set(0);
        bytesUp.set(0);
        bytesDown.set(0);
        poolHits.set(0);
        poolMisses.set(0);
    }

    public static String humanBytes(long n) {
        double value = (double) n;
        for (String unit : new String[]{"B", "KB", "MB", "GB"}) {
            if (Math.abs(value) < 1024) {
                return String.format(Locale.US, "%.1f%s", value, unit);
            }
            value /= 1024;
        }
        return String.format(Locale.US, "%.1f%s", value, "TB");
    }
}