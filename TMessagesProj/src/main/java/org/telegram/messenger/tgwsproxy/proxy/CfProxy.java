/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger.tgwsproxy.proxy;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class CfProxy {

    private static final String TAG = "CfProxy";

    private static final String DOMAINS_URL =
            "https://raw.githubusercontent.com/Flowseal/tg-ws-proxy/main/.github/cfproxy-domains.txt";
    private static final long REFRESH_INTERVAL_MS = 3600_000L;
    private static final int MIN_VALID_DOMAINS = 3;

    private static final String[] ENCODED_DEFAULTS = {
            "virkgj.com",
            "vmmzovy.com",
            "mkuosckvso.com",
            "zaewayzmplad.com",
            "twdmbzcm.com",
            "awzwsldi.com",
            "clngqrflngqin.com",
            "tjacxbqtj.com",
            "bxaxtxmrw.com",
            "dmohrsgmohcrwb.com",
            "vwbmtmoi.com",
            "khgrre.com",
            "ulihssf.com",
            "tmhqsdqmfpmk.com",
            "xwuwoqbm.com",
            "orgcnunpj.com",
            "zhkuldz.com",
            "zypoljnslxa.com",
            "efabnxaowuzs.com",
            "zaftuzsftqdq.com",
    };

    private static final Object lock = new Object();
    private static List<String> domains = decodeDefaults();
    private static final Map<Integer, String> dcToDomain = new HashMap<>();
    private static final Random rng = new Random();

    private CfProxy() {
    }

    public static String decode(String s) {
        if (!s.endsWith(".com")) {
            return s;
        }
        String p = s.substring(0, s.length() - 4);
        int n = 0;
        for (int i = 0; i < p.length(); i++) {
            if (Character.isLetter(p.charAt(i))) n++;
        }
        StringBuilder sb = new StringBuilder(p.length() + 6);
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c >= 'a' && c <= 'z') {
                sb.append((char) (((c - 'a') - n % 26 + 26) % 26 + 'a'));
            } else if (c >= 'A' && c <= 'Z') {
                sb.append((char) (((c - 'A') - n % 26 + 26) % 26 + 'A'));
            } else {
                sb.append(c);
            }
        }
        return sb.append(".co.uk").toString();
    }

    private static List<String> decodeDefaults() {
        List<String> result = new ArrayList<>(ENCODED_DEFAULTS.length);
        for (String d : ENCODED_DEFAULTS) {
            result.add(decode(d));
        }
        return result;
    }

    private static boolean isValidDomain(String domain) {
        if (domain == null || domain.isEmpty() || domain.length() > 253) return false;
        if (domain.startsWith(".") || domain.endsWith(".")) return false;
        String[] labels = domain.split("\\.");
        if (labels.length < 2) return false;
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63) return false;
            if (label.startsWith("-") || label.endsWith("-")) return false;
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                if (!(Character.isLetterOrDigit(c) || c == '-')) return false;
            }
        }
        String tld = labels[labels.length - 1];
        if (tld.length() < 2) return false;
        boolean hasLetter = false;
        for (int i = 0; i < tld.length(); i++) {
            if (Character.isLetter(tld.charAt(i))) hasLetter = true;
        }
        return hasLetter;
    }

    public static List<String> getDomainsForDc(int dc) {
        synchronized (lock) {
            List<String> shuffled = new ArrayList<>(domains);
            Collections.shuffle(shuffled, rng);
            String current = dcToDomain.get(dc);
            if (current != null) {
                shuffled.remove(current);
                shuffled.add(0, current);
            }
            return shuffled;
        }
    }

    public static void updateDomainForDc(int dc, String domain) {
        synchronized (lock) {
            if (!domain.equals(dcToDomain.get(dc))) {
                dcToDomain.put(dc, domain);
                Log.i(TAG, "Switched active CF domain for DC" + dc + " -> " + domain);
            }
        }
    }

    public static void startRefresh() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    refreshDomains();
                } catch (Exception e) {
                    Log.w(TAG, "CF proxy domain refresh failed: " + e);
                }
                try {
                    Thread.sleep(REFRESH_INTERVAL_MS);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "cfproxy-domain-refresh");
        t.setDaemon(true);
        t.start();
    }

    private static void refreshDomains() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(DOMAINS_URL).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        conn.setRequestProperty("User-Agent", "tg-ws-proxy");
        try {
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new Exception("HTTP " + code);
            }
            List<String> fetched = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    fetched.add(decode(line.toLowerCase()));
                }
            }
            List<String> valid = new ArrayList<>();
            for (String d : fetched) {
                if (isValidDomain(d) && !valid.contains(d)) valid.add(d);
            }
            if (valid.size() >= MIN_VALID_DOMAINS) {
                synchronized (lock) {
                    domains = valid;
                }
                Log.i(TAG, "CF proxy domain pool updated from GitHub (" + valid.size() + " domains)");
            } else {
                Log.w(TAG, "Ignoring fetched CF proxy domains (total=" + fetched.size()
                        + ", valid=" + valid.size() + "); keeping current pool");
            }
        } finally {
            conn.disconnect();
        }
    }
}
