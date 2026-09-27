/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger;

import org.json.JSONArray;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GoogleTranslate {

    public interface Callback {
        void onResult(List<String> translated, String error);
    }

    private static final String ENDPOINT = "https://translate.googleapis.com/translate_a/t?client=gtx&dt=t&sl=auto&tl=";

    public static void translateLines(List<String> lines, String targetLang, Callback callback) {
        if (lines == null || lines.isEmpty()) {
            AndroidUtilities.runOnUIThread(() -> callback.onResult(null, "нечего переводить"));
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                List<String> unique = new ArrayList<>();
                HashMap<String, Integer> index = new HashMap<>();
                int[] map = new int[lines.size()];
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    Integer idx = index.get(line);
                    if (idx == null) {
                        idx = unique.size();
                        index.put(line, idx);
                        unique.add(line);
                    }
                    map[i] = idx;
                }

                List<List<String>> chunks = new ArrayList<>();
                List<String> chunk = new ArrayList<>();
                int chars = 0;
                for (String line : unique) {
                    if (!chunk.isEmpty() && (chars + line.length() > 3500 || chunk.size() >= 60)) {
                        chunks.add(chunk);
                        chunk = new ArrayList<>();
                        chars = 0;
                    }
                    chunk.add(line);
                    chars += line.length();
                }
                if (!chunk.isEmpty()) {
                    chunks.add(chunk);
                }

                List<String> uniqueOut = new ArrayList<>(unique.size());
                for (int i = 0; i < unique.size(); i++) {
                    uniqueOut.add(null);
                }
                int offset = 0;
                int failures = 0;
                for (List<String> c : chunks) {
                    List<String> translated = requestBatch(c, targetLang);
                    if (translated != null && translated.size() == c.size()) {
                        for (int i = 0; i < c.size(); i++) {
                            String value = translated.get(i);
                            if (value != null && !value.trim().isEmpty() && !value.trim().equalsIgnoreCase(c.get(i).trim())) {
                                uniqueOut.set(offset + i, value.trim());
                            } else {
                                failures++;
                            }
                        }
                    } else {
                        failures += c.size();
                    }
                    offset += c.size();
                }
                if (failures >= unique.size()) {
                    AndroidUtilities.runOnUIThread(() -> callback.onResult(null, "нет ответа"));
                    return;
                }
                List<String> result = new ArrayList<>(lines.size());
                for (int i = 0; i < lines.size(); i++) {
                    String t = uniqueOut.get(map[i]);
                    result.add(t == null ? null : t.trim());
                }
                AndroidUtilities.runOnUIThread(() -> callback.onResult(result, null));
            } catch (Exception e) {
                FileLog.e(e);
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                AndroidUtilities.runOnUIThread(() -> callback.onResult(null, msg));
            }
        });
    }

    private static List<String> requestBatch(List<String> lines, String targetLang) {
        for (int attempt = 0; attempt < 3; attempt++) {
            List<String> result = tryRequest(lines, targetLang);
            if (result != null) {
                return result;
            }
            if (attempt < 2) {
                try {
                    Thread.sleep(1500L * (attempt + 1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    private static List<String> tryRequest(List<String> lines, String targetLang) {
        HttpURLConnection connection = null;
        try {
            StringBuilder body = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) {
                    body.append('&');
                }
                body.append("q=").append(URLEncoder.encode(lines.get(i), "UTF-8"));
            }
            connection = (HttpURLConnection) new URL(ENDPOINT + URLEncoder.encode(targetLang, "UTF-8")).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(20000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36");
            try (OutputStream os = connection.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = connection.getResponseCode();
            if (code != 200) {
                FileLog.d("GoogleTranslate: HTTP " + code);
                return null;
            }
            StringBuilder builder = new StringBuilder();
            InputStream is = connection.getInputStream();
            byte[] buf = new byte[8192];
            int n;
            try (InputStream in = is) {
                while (in != null && (n = in.read(buf)) > 0) {
                    builder.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                }
            }
            JSONArray root = new JSONArray(builder.toString());
            if (root.length() != lines.size()) {
                FileLog.d("GoogleTranslate: alignment mismatch " + root.length() + "/" + lines.size());
                return null;
            }
            List<String> result = new ArrayList<>(lines.size());
            for (int i = 0; i < root.length(); i++) {
                JSONArray item = root.optJSONArray(i);
                result.add(item != null ? item.optString(0) : root.optString(i));
            }
            return result;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
