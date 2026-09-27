/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger.tgwsproxy.proxy;

import android.util.Log;

import java.io.EOFException;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ProxyServer {

    private static final String TAG = "ProxyServer";

    private final int bufSize;
    private final Map<Integer, String> dcOpt;
    private ExecutorService executor;
    private final String host;
    private final int poolSize;
    private final int port;
    private ServerSocket serverSocket;
    public final Stats stats;
    private final WsPool wsPool;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Set<DcKey> wsBlacklist = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private final Map<DcKey, Long> dcFailUntil = new java.util.concurrent.ConcurrentHashMap<>();

    public interface LogCallback {
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

    public ProxyServer(String host, int port, Map<Integer, String> dcOpt, int poolSize, int bufKb, LogCallback cb) {
        this.host = host;
        this.port = port;
        this.dcOpt = dcOpt;
        this.poolSize = poolSize;
        this.bufSize = bufKb * 1024;
        this.wsPool = new WsPool(poolSize);
        this.stats = wsPool.stats;
    }

    public void start() {
        if (running.getAndSet(true)) return;
        executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("proxy-worker");
            return t;
        });
        Thread server = new Thread(this::acceptLoop, "proxy-server");
        server.setDaemon(true);
        CfProxy.startRefresh();
        server.start();
    }

    private void acceptLoop() {
        try {
            ServerSocket ss = new ServerSocket();
            this.serverSocket = ss;
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(host, port));
            log("Telegram WS Bridge Proxy listening on " + host + ":" + port);
            StringBuilder sb = new StringBuilder("Target DCs: ");
            for (Map.Entry<Integer, String> e : dcOpt.entrySet()) {
                sb.append("DC").append(e.getKey()).append(":").append(e.getValue()).append(" ");
            }
            log(sb.toString().trim());
            wsPool.warmup(dcOpt);
            while (running.get()) {
                try {
                    final Socket client = serverSocket.accept();
                    client.setTcpNoDelay(true);
                    client.setSendBufferSize(bufSize);
                    client.setReceiveBufferSize(bufSize);
                    executor.execute(() -> handleClient(client));
                } catch (Exception e) {
                    if (running.get()) Log.e(TAG, "Accept error: " + e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Server start error: " + e);
            log("Failed to start: " + e);
        }
    }

    public void stop() {
        if (!running.getAndSet(false)) return;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {
        }
        wsPool.shutdown();
        if (executor != null) executor.shutdownNow();
        serverSocket = null;
        executor = null;
        log("Proxy stopped");
    }

    private void log(String msg) {
        Log.i(TAG, msg);
    }

    private byte[] socks5Reply(int code) {
        return new byte[]{5, (byte) code, 0, 1, 0, 0, 0, 0, 0, 0};
    }

    private void handleClient(Socket socket) {
        String label = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        stats.connectionsTotal.incrementAndGet();
        try {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            byte[] greet = readExactly(in, 2);
            if ((greet[0] & 0xFF) != 5) {
                Log.d(TAG, "[" + label + "] not SOCKS5 (ver=" + (greet[0] & 0xFF) + ")");
                socket.close();
                return;
            }
            readExactly(in, greet[1] & 0xFF);
            out.write(new byte[]{5, 0});
            out.flush();

            byte[] req = readExactly(in, 4);
            int cmd = req[1] & 0xFF;
            int atyp = req[3] & 0xFF;
            if (cmd != 1) {
                out.write(socks5Reply(7));
                out.flush();
                socket.close();
                return;
            }

            String host;
            int port;
            if (atyp == 1) {
                host = InetAddress.getByAddress(readExactly(in, 4)).getHostAddress();
                byte[] p = readExactly(in, 2);
                port = ((p[0] & 0xFF) << 8) | (p[1] & 0xFF);
            } else if (atyp == 3) {
                int len = readExactly(in, 1)[0] & 0xFF;
                host = new String(readExactly(in, len), StandardCharsets.US_ASCII);
                byte[] p = readExactly(in, 2);
                port = ((p[0] & 0xFF) << 8) | (p[1] & 0xFF);
            } else if (atyp == 4) {
                host = InetAddress.getByAddress(readExactly(in, 16)).getHostAddress();
                byte[] p = readExactly(in, 2);
                port = ((p[0] & 0xFF) << 8) | (p[1] & 0xFF);
            } else {
                out.write(socks5Reply(8));
                out.flush();
                socket.close();
                return;
            }

            if (host.contains(":")) {
                Log.e(TAG, "[" + label + "] IPv6 address: " + host + ":" + port + " — not supported");
                out.write(socks5Reply(5));
                out.flush();
                socket.close();
                return;
            }

            if (!TelegramDC.isTelegramIp(host)) {
                stats.connectionsPassthrough.incrementAndGet();
                Log.d(TAG, "[" + label + "] passthrough -> " + host + ":" + port);
                try {
                    Socket remote = new Socket();
                    remote.connect(new InetSocketAddress(host, port), 10000);
                    remote.setTcpNoDelay(true);
                    out.write(socks5Reply(0));
                    out.flush();
                    bridgeTcp(in, out, remote.getInputStream(), remote.getOutputStream(), label, socket, remote);
                } catch (Exception e) {
                    Log.w(TAG, "[" + label + "] passthrough failed: " + e);
                    out.write(socks5Reply(5));
                    out.flush();
                }
                socket.close();
                return;
            }

            out.write(socks5Reply(0));
            out.flush();

            byte[] init;
            try {
                init = readExactly(in, 64);
            } catch (Exception e) {
                Log.d(TAG, "[" + label + "] client disconnected before init");
                socket.close();
                return;
            }

            if (TelegramDC.isHttpTransport(init)) {
                stats.connectionsPassthrough.incrementAndGet();
                Log.d(TAG, "[" + label + "] HTTP transport passthrough -> " + host + ":" + port);
                try {
                    Socket remote = new Socket();
                    remote.setTcpNoDelay(true);
                    remote.connect(new InetSocketAddress(host, port), 10000);
                    remote.setSoTimeout(0);
                    out.write(init);
                    out.flush();
                    bridgeTcp(in, out, remote.getInputStream(), remote.getOutputStream(), label, socket, remote);
                } catch (Exception e) {
                    Log.w(TAG, "[" + label + "] HTTP passthrough failed: " + e);
                    try { out.write(socks5Reply(5)); out.flush(); } catch (Exception ignored2) {}
                }
                socket.close();
                return;
            }

            TelegramDC.DcResult parsed = TelegramDC.dcFromInit(init);
            Integer dc = parsed.dc;
            boolean isMedia = parsed.isMedia;
            Integer proto = parsed.proto;
            TelegramDC.DcInfo byIp = (TelegramDC.DcInfo) TelegramDC.IP_TO_DC.get(host);

            boolean patched = false;
            if (dc == null && byIp != null) {
                dc = byIp.dc;
                isMedia = byIp.isMedia;
            }

            if (dc == null || !dcOpt.containsKey(dc)) {
                Log.w(TAG, "[" + label + "] unknown DC" + dc + " for " + host + ":" + port + " -> TCP passthrough");
                tcpFallback(in, out, host, port, init, label, socket, dc, isMedia);
                socket.close();
                return;
            }

            if (isMedia) {
                init = TelegramDC.patchInitDc(init, -dc);
                patched = true;
            }

            DcKey dcKey = new DcKey(dc, isMedia);
            long now = System.currentTimeMillis();
            String mediaLabel = isMedia ? " media" : "";

            if (wsBlacklist.contains(dcKey)) {
                Log.d(TAG, "[" + label + "] DC" + dc + mediaLabel + " WS blacklisted -> TCP " + host + ":" + port);
                tcpFallback(in, out, host, port, init, label, socket, dc, isMedia);
                socket.close();
                return;
            }

            int timeout = 10000;
            Long failUntil = dcFailUntil.get(dcKey);
            if (failUntil != null && now < failUntil) timeout = 2000;

            List<String> domains = TelegramDC.wsDomains(dc, Boolean.valueOf(isMedia));
            String targetIp = dcOpt.get(dc);

            RawWebSocket ws = wsPool.get(dc, isMedia, targetIp, domains);
            boolean sawRedirect = false;
            boolean allRedirect = true;

            if (ws == null) {
                for (String domain : domains) {
                    Log.i(TAG, "[" + label + "] DC" + dc + mediaLabel + " (" + host + ":" + port + ") -> wss://" + domain + "/apiws via " + targetIp);
                    try {
                        ws = RawWebSocket.connect(targetIp, domain, timeout);
                        allRedirect = false;
                        break;
                    } catch (RawWebSocket.WsHandshakeError e) {
                        stats.wsErrors.incrementAndGet();
                        if (e.isRedirect()) {
                            Log.w(TAG, "[" + label + "] DC" + dc + mediaLabel + " got " + e.statusCode + " from " + domain + " -> " + e.location);
                            sawRedirect = true;
                        } else {
                            Log.w(TAG, "[" + label + "] DC" + dc + mediaLabel + " WS handshake: " + e.statusLine);
                            allRedirect = false;
                        }
                    } catch (Exception e) {
                        stats.wsErrors.incrementAndGet();
                        Log.w(TAG, "[" + label + "] DC" + dc + mediaLabel + " WS connect failed: " + e);
                        allRedirect = false;
                    }
                }
            } else {
                log("[" + label + "] DC" + dc + mediaLabel + " (" + host + ":" + port + ") -> pool hit via " + targetIp);
            }

            if (ws != null) {
                dcFailUntil.remove(dcKey);
                stats.connectionsWs.incrementAndGet();

                MsgSplitter splitter = null;
                if (proto != null && (patched || isMedia || proto.intValue() != 0xEEEEEEEE)) {
                    try {
                        splitter = new MsgSplitter(init, proto.intValue());
                        Log.d(TAG, "[" + label + "] MsgSplitter activated for proto 0x" + Integer.toHexString(proto.intValue()));
                    } catch (Exception ignored) {
                    }
                }

                ws.send(init);
                bridgeWs(in, out, ws, label, socket, dc, host, port, isMedia, splitter);
                socket.close();
                return;
            }

            RawWebSocket cfWs = tryCfProxy(dc, label, mediaLabel);
            if (cfWs != null) {
                dcFailUntil.remove(dcKey);
                stats.connectionsCfProxy.incrementAndGet();

                MsgSplitter cfSplitter = null;
                if (proto != null && (patched || isMedia || proto.intValue() != 0xEEEEEEEE)) {
                    try {
                        cfSplitter = new MsgSplitter(init, proto.intValue());
                        Log.d(TAG, "[" + label + "] MsgSplitter activated for proto 0x" + Integer.toHexString(proto.intValue()));
                    } catch (Exception ignored) {
                    }
                }

                Log.i(TAG, "[" + label + "] DC" + dc + mediaLabel + " -> CF proxy connected");
                cfWs.send(init);
                bridgeWs(in, out, cfWs, label, socket, dc, host, port, isMedia, cfSplitter);
                socket.close();
                return;
            }

            if (sawRedirect && allRedirect) {
                wsBlacklist.add(dcKey);
                Log.w(TAG, "[" + label + "] DC" + dc + mediaLabel + " blacklisted for WS (all 302)");
            } else {
                dcFailUntil.put(dcKey, now + 30000);
                log("[" + label + "] DC" + dc + mediaLabel + " WS cooldown for 30s");
            }
            log("[" + label + "] DC" + dc + mediaLabel + " -> TCP fallback to " + host + ":" + port);
            tcpFallback(in, out, host, port, init, label, socket, dc, isMedia);
            socket.close();
        } catch (Exception e) {
            Log.d(TAG, "[" + label + "] error: " + e);
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    private RawWebSocket tryCfProxy(int dc, String label, String mediaLabel) {
        for (String base : CfProxy.getDomainsForDc(dc)) {
            String domain = "kws" + dc + "." + base;
            Log.i(TAG, "[" + label + "] DC" + dc + mediaLabel + " -> trying CF proxy wss://" + domain + "/apiws");
            try {
                return RawWebSocket.connect(domain, domain, 10000);
            } catch (Exception e) {
                Log.w(TAG, "[" + label + "] DC" + dc + mediaLabel + " CF proxy " + domain + " failed: " + e);
            }
        }
        return null;
    }

    private void tcpFallback(InputStream in, OutputStream out, String host, int port, byte[] init,
                             String label, Socket socket, Integer dc, boolean isMedia) {
        try {
            Socket remote = new Socket();
            remote.connect(new InetSocketAddress(host, port), 10000);
            remote.setTcpNoDelay(true);
            stats.connectionsTcpFallback.incrementAndGet();
            remote.getOutputStream().write(init);
            remote.getOutputStream().flush();
            bridgeTcp(in, out, remote.getInputStream(), remote.getOutputStream(), label, socket, remote);
            log("[" + label + "] DC" + dc + (isMedia ? "m" : "") + " TCP fallback closed");
        } catch (Exception e) {
            Log.w(TAG, "[" + label + "] TCP fallback to " + host + ":" + port + " failed: " + e);
        }
    }

    private void bridgeTcp(final InputStream clientIn, final OutputStream clientOut,
                           final InputStream remoteIn, final OutputStream remoteOut,
                           final String label, final Socket client, final Socket remote) {
        final AtomicBoolean closed = new AtomicBoolean(false);
        Thread c2r = new Thread(() -> {
            try {
                pipe(clientIn, remoteOut, true);
            } catch (Exception ignored) {
            } finally {
                if (closed.compareAndSet(false, true)) {
                    try { client.close(); } catch (Exception ignored) {}
                    try { remote.close(); } catch (Exception ignored) {}
                }
            }
        }, "tcp-c2r-" + label);
        Thread r2c = new Thread(() -> {
            try {
                pipe(remoteIn, clientOut, false);
            } catch (Exception ignored) {
            } finally {
                if (closed.compareAndSet(false, true)) {
                    try { client.close(); } catch (Exception ignored) {}
                    try { remote.close(); } catch (Exception ignored) {}
                }
            }
        }, "tcp-r2c-" + label);
        c2r.setDaemon(true);
        r2c.setDaemon(true);
        c2r.start();
        r2c.start();
        try {
            c2r.join();
            r2c.join();
        } catch (InterruptedException ignored) {
        }
    }

    private void pipe(InputStream in, OutputStream out, boolean up) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) != -1) {
            if (up) stats.bytesUp.addAndGet(n);
            else stats.bytesDown.addAndGet(n);
            out.write(buf, 0, n);
            out.flush();
        }
    }

    private void bridgeWs(final InputStream clientIn, final OutputStream clientOut,
                          final RawWebSocket ws, final String label, final Socket socket,
                          final int dc, final String host, final int port, final boolean isMedia,
                          final MsgSplitter splitter) {
        StringBuilder sb = new StringBuilder();
        sb.append("DC").append(dc).append(isMedia ? "m" : "");
        String dcLabel = sb.toString();
        long started = System.currentTimeMillis();
        final long[] up = {0};
        final long[] down = {0};

        Thread c2s = new Thread(() -> {
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = clientIn.read(buf)) != -1) {
                    byte[] chunk = java.util.Arrays.copyOfRange(buf, 0, n);
                    stats.bytesUp.addAndGet(n);
                    up[0] += n;
                    if (splitter != null) {
                        List<byte[]> parts = splitter.split(chunk);
                        if (!parts.isEmpty()) {
                            if (parts.size() > 1) ws.sendBatch(parts);
                            else ws.send(parts.get(0));
                        }
                    } else {
                        ws.send(chunk);
                    }
                }
                if (splitter != null) {
                    List<byte[]> flush = splitter.flush();
                    if (!flush.isEmpty()) ws.send(flush.get(0));
                }
            } catch (Exception ignored) {
            } finally {
                try { ws.close(); } catch (Exception ignored) {}
                try { socket.close(); } catch (Exception ignored) {}
            }
        }, "ws-c2s-" + label);

        Thread s2c = new Thread(() -> {
            try {
                while (true) {
                    byte[] recv = ws.recv();
                    if (recv == null) break;
                    stats.bytesDown.addAndGet(recv.length);
                    down[0] += recv.length;
                    clientOut.write(recv);
                    clientOut.flush();
                }
            } catch (Exception ignored) {
            } finally {
                try { socket.close(); } catch (Exception ignored) {}
                try { ws.close(); } catch (Exception ignored) {}
            }
        }, "ws-s2c-" + label);

        c2s.setDaemon(true);
        s2c.setDaemon(true);
        c2s.start();
        s2c.start();
        try {
            c2s.join();
            s2c.join();
        } catch (InterruptedException ignored) {
        }
        log("[" + label + "] " + dcLabel + " (" + host + ":" + port + ") WS closed: ^"
                + Stats.humanBytes(up[0]) + " v" + Stats.humanBytes(down[0]) + " in "
                + String.format(java.util.Locale.US, "%.1f", (System.currentTimeMillis() - started) / 1000.0) + "s");
    }

    private byte[] readExactly(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(buf, off, n - off);
            if (r == -1) throw new EOFException("Connection closed reading " + n + " bytes at offset " + off);
            off += r;
        }
        return buf;
    }
}
