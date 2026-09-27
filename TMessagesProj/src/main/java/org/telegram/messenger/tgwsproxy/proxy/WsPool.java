/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger.tgwsproxy.proxy;

import android.util.Log;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

public class WsPool {

    private static final long MAX_AGE_MS = 120_000L;

    private final int poolSize;
    private final Map<DcKey, LinkedBlockingDeque<PoolEntry>> idle = new ConcurrentHashMap<>();
    private final Set<DcKey> refilling = ConcurrentHashMap.newKeySet();
    public final Stats stats = new Stats();

    private static class PoolEntry {
        final RawWebSocket ws;
        final long createdAt;

        PoolEntry(RawWebSocket ws, long createdAt) {
            this.ws = ws;
            this.createdAt = createdAt;
        }
    }

    private static class DcKey {
        final int dc;
        final boolean isMedia;

        DcKey(int dc, boolean isMedia) {
            this.dc = dc;
            this.isMedia = isMedia;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DcKey)) return false;
            DcKey k = (DcKey) o;
            return dc == k.dc && isMedia == k.isMedia;
        }

        @Override
        public int hashCode() {
            return dc * 31 + (isMedia ? 1 : 0);
        }
    }

    public WsPool(int poolSize) {
        this.poolSize = poolSize;
    }

    public RawWebSocket get(int dc, boolean isMedia, String targetIp, List<String> domains) {
        if (poolSize <= 0) return null;
        DcKey key = new DcKey(dc, isMedia);
        LinkedBlockingDeque<PoolEntry> deque = idle.get(key);
        if (deque == null) {
            stats.poolMisses.incrementAndGet();
            scheduleRefill(key, targetIp, domains);
            return null;
        }
        long now = System.currentTimeMillis();
        while (true) {
            PoolEntry e = deque.pollFirst();
            if (e == null) {
                stats.poolMisses.incrementAndGet();
                scheduleRefill(key, targetIp, domains);
                return null;
            }
            long age = now - e.createdAt;
            if (age > MAX_AGE_MS || e.ws.isClosed()) {
                closeQuietly(e.ws);
            } else {
                stats.poolHits.incrementAndGet();
                Log.d("WsPool", "Pool hit DC" + dc + (isMedia ? "m" : "") + " age=" + age + "ms left=" + deque.size());
                scheduleRefill(key, targetIp, domains);
                return e.ws;
            }
        }
    }

    private void scheduleRefill(final DcKey key, final String targetIp, final List<String> domains) {
        if (poolSize > 0 && refilling.add(key)) {
            Thread t = new Thread(() -> {
                try {
                    refill(key, targetIp, domains);
                } finally {
                    refilling.remove(key);
                }
            }, "ws-pool-refill-DC" + key.dc + (key.isMedia ? "m" : ""));
            t.setDaemon(true);
            t.start();
        }
    }

    private void refill(DcKey key, String targetIp, List<String> domains) {
        LinkedBlockingDeque<PoolEntry> deque = idle.computeIfAbsent(key, k -> new LinkedBlockingDeque<>());
        int need = poolSize - deque.size();
        if (need <= 0) return;
        for (int i = 0; i < need; i++) {
            RawWebSocket ws = connectOne(targetIp, domains);
            if (ws != null) {
                deque.addLast(new PoolEntry(ws, System.currentTimeMillis()));
            }
        }
        Log.d("WsPool", "Pool refilled DC" + key.dc + (key.isMedia ? "m" : "") + ": " + deque.size() + " ready");
    }

    private RawWebSocket connectOne(String targetIp, List<String> domains) {
        Iterator<String> it = domains.iterator();
        while (it.hasNext()) {
            try {
                return RawWebSocket.connect(targetIp, it.next(), 8000);
            } catch (RawWebSocket.WsHandshakeError e) {
                if (!e.isRedirect()) break;
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    public void warmup(Map<Integer, String> dcOpt) {
        for (Map.Entry<Integer, String> e : dcOpt.entrySet()) {
            int dc = e.getKey();
            String ip = e.getValue();
            for (boolean media : new boolean[]{false, true}) {
                scheduleRefill(new DcKey(dc, media), ip, TelegramDC.wsDomains(dc, Boolean.valueOf(media)));
            }
        }
        Log.i("WsPool", "Pool warmup started for " + dcOpt.size() + " DC(s)");
    }

    public void shutdown() {
        for (LinkedBlockingDeque<PoolEntry> deque : idle.values()) {
            PoolEntry e;
            while ((e = deque.pollFirst()) != null) closeQuietly(e.ws);
        }
        idle.clear();
    }

    private void closeQuietly(RawWebSocket ws) {
        try {
            ws.close();
        } catch (Exception ignored) {
        }
    }
}
