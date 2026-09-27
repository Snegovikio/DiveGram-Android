/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger.tgwsproxy.proxy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MsgSplitter {

    private final Cipher cipher;
    private final ByteArrayOutputStream cipherBuf = new ByteArrayOutputStream();
    private final ByteArrayOutputStream plainBuf = new ByteArrayOutputStream();
    private final int proto;
    private boolean disabled = false;

    public MsgSplitter(byte[] initData, int proto) throws Exception {
        this.proto = proto;
        byte[] key = java.util.Arrays.copyOfRange(initData, 8, 40);
        byte[] iv = java.util.Arrays.copyOfRange(initData, 40, 56);
        cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        cipher.update(new byte[64]);
    }

    public java.util.List<byte[]> split(byte[] chunk) {
        if (chunk.length == 0) return java.util.Collections.emptyList();
        if (disabled) return java.util.Collections.singletonList(chunk);

        cipherBuf.write(chunk);
        plainBuf.write(cipher.update(chunk));

        java.util.List<byte[]> parts = new java.util.ArrayList<>();
        byte[] cipherArr = cipherBuf.toByteArray();
        byte[] plainArr = plainBuf.toByteArray();
        int consumed = 0;

        while (consumed < cipherArr.length) {
            int remaining = plainArr.length - consumed;
            Integer packetLen = nextPacketLen(plainArr, consumed, remaining);

            if (packetLen == null) break;

            if (packetLen <= 0) {
                parts.add(java.util.Arrays.copyOfRange(cipherArr, consumed, cipherArr.length));
                consumed = cipherArr.length;
                disabled = true;
                break;
            }

            parts.add(java.util.Arrays.copyOfRange(cipherArr, consumed, consumed + packetLen));
            consumed += packetLen;
        }

        cipherBuf.reset();
        plainBuf.reset();
        if (consumed < cipherArr.length) {
            cipherBuf.write(cipherArr, consumed, cipherArr.length - consumed);
            plainBuf.write(plainArr, consumed, plainArr.length - consumed);
        }

        return parts;
    }

    public java.util.List<byte[]> flush() {
        byte[] data = cipherBuf.toByteArray();
        cipherBuf.reset();
        plainBuf.reset();
        if (data.length > 0) {
            return java.util.Collections.singletonList(data);
        }
        return java.util.Collections.emptyList();
    }

    private Integer nextPacketLen(byte[] plain, int offset, int remaining) {
        if (remaining == 0) return null;
        switch (proto) {
            case TelegramDC.PROTO_ABRIDGED:
                return nextAbridgedLen(plain, offset, remaining);
            case TelegramDC.PROTO_INTERMEDIATE:
            case TelegramDC.PROTO_PADDED_INTERMEDIATE:
                return nextIntermediateLen(plain, offset, remaining);
            default:
                return 0;
        }
    }

    private Integer nextAbridgedLen(byte[] plain, int offset, int remaining) {
        int first = plain[offset] & 0xFF;
        int headerLen;
        int payloadLen;

        if (first == 0x7F || first == 0xFF) {
            if (remaining < 4) return null;
            payloadLen = ((plain[offset + 1] & 0xFF) |
                    ((plain[offset + 2] & 0xFF) << 8) |
                    ((plain[offset + 3] & 0xFF) << 16)) * 4;
            headerLen = 4;
        } else {
            payloadLen = (first & 0x7F) * 4;
            headerLen = 1;
        }

        if (payloadLen <= 0) return 0;
        int packetLen = headerLen + payloadLen;
        return remaining < packetLen ? null : packetLen;
    }

    private Integer nextIntermediateLen(byte[] plain, int offset, int remaining) {
        if (remaining < 4) return null;
        int payloadLen = ByteBuffer.wrap(plain, offset, 4)
                .order(ByteOrder.LITTLE_ENDIAN).getInt() & 0x7FFFFFFF;
        if (payloadLen <= 0) return 0;
        int packetLen = 4 + payloadLen;
        return remaining < packetLen ? null : packetLen;
    }

    private static class ByteArrayOutputStream {
        private byte[] buf = new byte[4096];
        private int count = 0;

        void write(byte[] data) {
            write(data, 0, data.length);
        }

        void write(byte[] data, int off, int len) {
            ensureCapacity(count + len);
            System.arraycopy(data, off, buf, count, len);
            count += len;
        }

        byte[] toByteArray() {
            return java.util.Arrays.copyOfRange(buf, 0, count);
        }

        void reset() {
            count = 0;
        }

        int size() {
            return count;
        }

        private void ensureCapacity(int minCapacity) {
            if (minCapacity > buf.length) {
                buf = java.util.Arrays.copyOf(buf, Math.max(buf.length * 2, minCapacity));
            }
        }
    }
}