/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger.tgwsproxy.proxy;

import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class RawWebSocket {

    private static final String TAG = "RawWebSocket";
    private static final SecureRandom random = new SecureRandom();
    private static final SSLContext sslContext = createSslContext();

    private static SSLContext createSslContext() {
        try {
            TrustManager[] trustAll = new TrustManager[]{new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }
            }};
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, random);
            return ctx;
        } catch (Exception e) {
            throw new RuntimeException("Failed to init SSL context", e);
        }
    }

    private volatile boolean closed = false;
    private final InputStream input;
    private final OutputStream output;
    private final Socket socket;

    private RawWebSocket(InputStream input, OutputStream output, Socket socket) {
        this.input = input;
        this.output = output;
        this.socket = socket;
    }

    public static RawWebSocket connect(String ip, String domain, String path, int timeoutMs) throws IOException {
        Socket raw = new Socket();
        try {
            raw.connect(new InetSocketAddress(ip, 443), timeoutMs);
            raw.setSoTimeout(timeoutMs);
            raw.setTcpNoDelay(true);
            raw.setSendBufferSize(256 * 1024);
            raw.setReceiveBufferSize(256 * 1024);

            SSLSocket ssl = (SSLSocket) sslContext.getSocketFactory().createSocket(raw, domain, 443, true);
            ssl.startHandshake();
            OutputStream out = ssl.getOutputStream();
            InputStream in = ssl.getInputStream();

            byte[] keyBytes = new byte[16];
            random.nextBytes(keyBytes);
            String req = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + domain + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + Base64.encodeToString(keyBytes, Base64.NO_WRAP) + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "Sec-WebSocket-Protocol: binary\r\n"
                    + "Origin: https://web.telegram.org\r\n"
                    + "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/131.0.0.0 Safari/537.36\r\n"
                    + "\r\n";
            out.write(req.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            java.util.List<String> lines = new java.util.ArrayList<>();
            StringBuilder lb = new StringBuilder();
            while (true) {
                int b = in.read();
                if (b == -1) break;
                if (b == '\n') {
                    String line = lb.toString();
                    if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
                    if (line.isEmpty()) break;
                    lines.add(line);
                    lb.setLength(0);
                } else {
                    lb.append((char) b);
                }
            }

            if (lines.isEmpty()) {
                ssl.close();
                throw new IOException("empty response");
            }

            String firstLine = lines.get(0);
            int status;
            try {
                status = Integer.parseInt(firstLine.split(" ", 3)[1]);
            } catch (Exception ignored) {
                status = 0;
            }

            if (status == 101) {
                ssl.setSoTimeout(0);
                return new RawWebSocket(in, out, ssl);
            }

            Map<String, String> headers = new java.util.HashMap<>();
            for (int i = 1; i < lines.size(); i++) {
                String h = lines.get(i);
                int c = h.indexOf(':');
                if (c > 0) {
                    headers.put(h.substring(0, c).trim().toLowerCase(), h.substring(c + 1).trim());
                }
            }
            ssl.close();
            throw new WsHandshakeError(status, firstLine, headers, headers.get("location"));
        } catch (WsHandshakeError e) {
            throw e;
        } catch (Exception e) {
            try {
                raw.close();
            } catch (Exception ignored) {
            }
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException(e);
        }
    }

    public static RawWebSocket connect(String ip, String domain, int timeoutMs) throws IOException {
        return connect(ip, domain, "/apiws", timeoutMs);
    }

    public void send(byte[] data) throws IOException {
        if (closed) throw new IOException("WebSocket closed");
        byte[] frame = buildFrame(0x2, data, true);
        synchronized (output) {
            output.write(frame);
            output.flush();
        }
    }

    public void sendBatch(java.util.List<byte[]> parts) throws IOException {
        if (closed) throw new IOException("WebSocket closed");
        synchronized (output) {
            for (byte[] part : parts) {
                output.write(buildFrame(0x2, part, true));
            }
            output.flush();
        }
    }

    public byte[] recv() throws IOException {
        while (!closed) {
            byte[] hdr = readExactly(2);
            int opcode = hdr[0] & 0x0F;
            long len = hdr[1] & 0x7F & 0xFFL;
            if (len == 126L) {
                len = ByteBuffer.wrap(readExactly(2)).getShort() & 0xFFFFL;
            } else if (len == 127L) {
                len = ByteBuffer.wrap(readExactly(8)).getLong();
            }
            byte[] mask = (hdr[1] & 0x80) != 0 ? readExactly(4) : null;
            byte[] payload = readExactly((int) len);
            if (mask != null) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] = (byte) (payload[i] ^ mask[i % 4]);
                }
            }

            if (opcode == 0x1 || opcode == 0x2) {
                return payload;
            }
            switch (opcode) {
                case 0x8:
                    this.closed = true;
                    try {
                        byte[] closePayload = payload.length >= 2
                                ? java.util.Arrays.copyOfRange(payload, 0, 2) : new byte[0];
                        synchronized (output) {
                            output.write(buildFrame(0x8, closePayload, true));
                            output.flush();
                        }
                    } catch (Exception ignored) {
                    }
                    return null;
                case 0x9:
                    try {
                        synchronized (output) {
                            output.write(buildFrame(0xA, payload, true));
                            output.flush();
                        }
                    } catch (Exception ignored) {
                    }
                    break;
                case 0xA:
                    break;
                default:
                    Log.d(TAG, "unknown opcode " + opcode);
                    break;
            }
        }
        return null;
    }

    public void close() {
        if (closed) return;
        closed = true;
        try {
            synchronized (output) {
                output.write(buildFrame(0x8, new byte[0], true));
                output.flush();
            }
        } catch (Exception ignored) {
        }
        try {
            socket.close();
        } catch (Exception ignored) {
        }
    }

    public boolean isClosed() {
        return closed || socket.isClosed();
    }

    private byte[] readExactly(int n) throws IOException {
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            int r = input.read(buf, off, n - off);
            if (r == -1) throw new IOException("Connection closed");
            off += r;
        }
        return buf;
    }

    private byte[] buildFrame(int opcode, byte[] data, boolean mask) {
        int len = data.length;
        byte fb = (byte) (0x80 | opcode);

        if (!mask) {
            byte[] header;
            int hlen;
            if (len < 126) {
                header = new byte[]{fb, (byte) len};
                hlen = 2;
            } else if (len < 65536) {
                header = new byte[]{fb, 126, 0, 0};
                ByteBuffer.wrap(header, 2, 2).putShort((short) len);
                hlen = 4;
            } else {
                header = new byte[10];
                header[0] = fb;
                header[1] = 127;
                ByteBuffer.wrap(header, 2, 8).putLong(len);
                hlen = 10;
            }
            byte[] frame = new byte[hlen + len];
            System.arraycopy(header, 0, frame, 0, hlen);
            System.arraycopy(data, 0, frame, hlen, len);
            return frame;
        }

        byte[] maskKey = new byte[4];
        random.nextBytes(maskKey);
        byte[] masked = new byte[len];
        for (int i = 0; i < len; i++) {
            masked[i] = (byte) (data[i] ^ maskKey[i % 4]);
        }
        byte[] header;
        int hlen;
        if (len < 126) {
            header = new byte[6];
            header[0] = fb;
            header[1] = (byte) (0x80 | len);
            System.arraycopy(maskKey, 0, header, 2, 4);
            hlen = 6;
        } else if (len < 65536) {
            header = new byte[8];
            header[0] = fb;
            header[1] = (byte) (0x80 | 126);
            ByteBuffer.wrap(header, 2, 2).putShort((short) len);
            System.arraycopy(maskKey, 0, header, 4, 4);
            hlen = 8;
        } else {
            header = new byte[14];
            header[0] = fb;
            header[1] = (byte) (0x80 | 127);
            ByteBuffer.wrap(header, 2, 8).putLong(len);
            System.arraycopy(maskKey, 0, header, 10, 4);
            hlen = 14;
        }
        byte[] frame = new byte[hlen + len];
        System.arraycopy(header, 0, frame, 0, hlen);
        System.arraycopy(masked, 0, frame, hlen, len);
        return frame;
    }

    public static class WsHandshakeError extends IOException {
        public final int statusCode;
        public final String statusLine;
        public final Map<String, String> headers;
        public final String location;

        public WsHandshakeError(int statusCode, String statusLine, Map<String, String> headers, String location) {
            super("HTTP " + statusCode + ": " + statusLine);
            this.statusCode = statusCode;
            this.statusLine = statusLine;
            this.headers = headers;
            this.location = location;
        }

        public boolean isRedirect() {
            return statusCode == 301 || statusCode == 302 || statusCode == 303
                    || statusCode == 307 || statusCode == 308;
        }
    }
}
