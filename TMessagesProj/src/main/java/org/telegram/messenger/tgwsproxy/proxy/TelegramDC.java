/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger.tgwsproxy.proxy;

import android.util.Log;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public abstract class TelegramDC {

    private static final String TAG = "TelegramDC";

    public static final int PROTO_ABRIDGED = 0xEFEFEFEF;
    public static final int PROTO_INTERMEDIATE = 0xEEEEEEEE;
    public static final int PROTO_PADDED_INTERMEDIATE = 0xDDDDDDDD;

    public static final Set<Integer> VALID_PROTOS;
    public static final Map<String, DcInfo> IP_TO_DC = buildIpToDc();
    public static final Map<Integer, Integer> DC_OVERRIDES = Collections.singletonMap(203, 2);

    private static final byte[] ZERO_64 = new byte[64];

    private static final long[][] TG_RANGES = new long[][]{
            {ipToLong("185.76.151.0"), ipToLong("185.76.151.255")},
            {ipToLong("149.154.160.0"), ipToLong("149.154.175.255")},
            {ipToLong("91.105.192.0"), ipToLong("91.105.193.255")},
            {ipToLong("91.108.0.0"), ipToLong("91.108.255.255")},
    };

    static {
        Set<Integer> protos = new HashSet<>();
        protos.add(PROTO_ABRIDGED);
        protos.add(PROTO_INTERMEDIATE);
        protos.add(PROTO_PADDED_INTERMEDIATE);
        VALID_PROTOS = Collections.unmodifiableSet(protos);
    }

    private static Map<String, DcInfo> buildIpToDc() {
        Map<String, DcInfo> map = new java.util.HashMap<>();
        map.put("149.154.175.50", new DcInfo(1, false));
        map.put("149.154.175.51", new DcInfo(1, false));
        map.put("149.154.175.53", new DcInfo(1, false));
        map.put("149.154.175.54", new DcInfo(1, false));
        map.put("149.154.175.52", new DcInfo(1, true));
        map.put("149.154.167.41", new DcInfo(2, false));
        map.put("149.154.167.50", new DcInfo(2, false));
        map.put("149.154.167.51", new DcInfo(2, false));
        map.put("149.154.167.220", new DcInfo(2, false));
        map.put("95.161.76.100", new DcInfo(2, false));
        map.put("149.154.167.151", new DcInfo(2, true));
        map.put("149.154.167.222", new DcInfo(2, true));
        map.put("149.154.167.223", new DcInfo(2, true));
        map.put("149.154.162.123", new DcInfo(2, true));
        map.put("149.154.175.100", new DcInfo(3, false));
        map.put("149.154.175.101", new DcInfo(3, false));
        map.put("149.154.175.102", new DcInfo(3, true));
        map.put("149.154.167.91", new DcInfo(4, false));
        map.put("149.154.167.92", new DcInfo(4, false));
        map.put("149.154.164.250", new DcInfo(4, true));
        map.put("149.154.166.120", new DcInfo(4, true));
        map.put("149.154.166.121", new DcInfo(4, true));
        map.put("149.154.167.118", new DcInfo(4, true));
        map.put("149.154.165.111", new DcInfo(4, true));
        map.put("91.108.56.100", new DcInfo(5, false));
        map.put("91.108.56.101", new DcInfo(5, false));
        map.put("91.108.56.116", new DcInfo(5, false));
        map.put("91.108.56.126", new DcInfo(5, false));
        map.put("149.154.171.5", new DcInfo(5, false));
        map.put("91.108.56.102", new DcInfo(5, true));
        map.put("91.108.56.128", new DcInfo(5, true));
        map.put("91.108.56.151", new DcInfo(5, true));
        map.put("91.105.192.100", new DcInfo(203, false));
        return Collections.unmodifiableMap(map);
    }

    public static class DcInfo {
        public final int dc;
        public final boolean isMedia;

        public DcInfo(int dc, boolean isMedia) {
            this.dc = dc;
            this.isMedia = isMedia;
        }
    }

    public static class DcResult {
        public final Integer dc;
        public final boolean isMedia;
        public final Integer proto;

        public DcResult(Integer dc, boolean isMedia, Integer proto) {
            this.dc = dc;
            this.isMedia = isMedia;
            this.proto = proto;
        }
    }

    private static long ipToLong(String ip) {
        try {
            return ByteBuffer.wrap(InetAddress.getByName(ip).getAddress()).getInt() & 0xFFFFFFFFL;
        } catch (Exception e) {
            return 0L;
        }
    }

    public static boolean isTelegramIp(String ip) {
        try {
            long n = ipToLong(ip);
            for (long[] range : TG_RANGES) {
                if (n >= range[0] && n <= range[1]) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static boolean isHttpTransport(byte[] data) {
        if (data.length < 4) return false;
        int len = Math.min(8, data.length);
        String prefix = new String(data, 0, len, java.nio.charset.StandardCharsets.US_ASCII);
        return prefix.startsWith("POST ") || prefix.startsWith("GET ")
                || prefix.startsWith("HEAD ") || prefix.startsWith("OPTIONS ");
    }

    public static List<String> wsDomains(int dc, Boolean isMedia) {
        int resolved = DC_OVERRIDES.getOrDefault(dc, dc);
        if (isMedia == null || isMedia) {
            return Arrays.asList("kws" + resolved + "-1.web.telegram.org", "kws" + resolved + ".web.telegram.org");
        }
        return Arrays.asList("kws" + resolved + ".web.telegram.org", "kws" + resolved + "-1.web.telegram.org");
    }

    public static DcResult dcFromInit(byte[] data) {
        if (data.length < 64) return new DcResult(null, false, null);
        try {
            byte[] key = Arrays.copyOfRange(data, 8, 40);
            byte[] iv = Arrays.copyOfRange(data, 40, 56);

            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] keystream = cipher.update(ZERO_64);

            byte[] plain = new byte[8];
            for (int i = 0; i < 8; i++) {
                plain[i] = (byte) (data[56 + i] ^ keystream[56 + i]);
            }

            ByteBuffer buf = ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN);
            int proto = buf.getInt(0);
            short dcRaw = buf.getShort(4);

            Log.d(TAG, "dc_from_init: proto=0x" + Integer.toHexString(proto) + " dc_raw=" + ((int) dcRaw));

            if (VALID_PROTOS.contains(proto)) {
                int dc = Math.abs((int) dcRaw);
                if ((dc >= 1 && dc <= 5) || dc == 203) {
                    return new DcResult(dc, dcRaw < 0, proto);
                }
                return new DcResult(null, false, proto);
            }
        } catch (Exception e) {
            Log.d(TAG, "DC extraction failed: " + e);
        }
        return new DcResult(null, false, null);
    }

    public static byte[] patchInitDc(byte[] data, int dc) {
        if (data.length < 64) return data;
        try {
            byte[] key = Arrays.copyOfRange(data, 8, 40);
            byte[] iv = Arrays.copyOfRange(data, 40, 56);

            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] ks = cipher.update(ZERO_64);

            byte[] patched = data.clone();
            byte[] dcBytes = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) dc).array();
            patched[60] = (byte) (ks[60] ^ dcBytes[0]);
            patched[61] = (byte) (ks[61] ^ dcBytes[1]);
            Log.d(TAG, "init patched: dc_id -> " + dc);
            return patched;
        } catch (Exception ignored) {
            return data;
        }
    }
}
