/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class DiveGramWeather {

    public static final long CACHE_TTL = 30 * 60 * 1000L;

    private static final String OPENWEATHERMAP_API_KEY = "";

    private static final AtomicBoolean fetching = new AtomicBoolean(false);

    public interface Callback {
        void onResult(String text);
    }

    public static String getCached() {
        if (!SharedConfig.weatherEnabled || SharedConfig.weatherText.length() == 0) {
            return null;
        }
        return SharedConfig.weatherText;
    }

    public static boolean isStale() {
        return System.currentTimeMillis() - SharedConfig.weatherTime > CACHE_TTL;
    }

    public static void fetch() {
        fetch(null);
    }

    public static void fetch(Callback callback) {
        if (!SharedConfig.weatherEnabled) {
            if (callback != null) callback.onResult(null);
            return;
        }
        final String key = OPENWEATHERMAP_API_KEY;
        if (key == null || key.trim().isEmpty()) {
            FileLog.d("DiveWeather: no API key configured");
            if (callback != null) callback.onResult(getCached());
            return;
        }
        String city = SharedConfig.weatherCity;
        if (city == null || city.trim().isEmpty()) {
            if (callback != null) callback.onResult(null);
            return;
        }
        if (!fetching.compareAndSet(false, true)) {
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.onResult(getCached()));
            }
            return;
        }
        final String query = cleanCityName(city);
        if (query.isEmpty()) {
            fetching.set(false);
            if (callback != null) callback.onResult(null);
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            String result = null;
            try {
                FileLog.d("DiveWeather: fetching for " + query + " (lat=" + SharedConfig.weatherLat + " lon=" + SharedConfig.weatherLon + ")");
                double lat = SharedConfig.weatherLat;
                double lon = SharedConfig.weatherLon;
                boolean haveCoords = lat != 0 || lon != 0;
                if (!haveCoords) {
                    JSONArray geo = jsonGetArray("https://api.openweathermap.org/geo/1.0/direct?limit=1&appid="
                            + URLEncoder.encode(key, "UTF-8")
                            + "&q=" + URLEncoder.encode(query, "UTF-8"));
                    if (geo == null || geo.length() == 0) throw new Exception("city not found");
                    JSONObject res = geo.getJSONObject(0);
                    lat = res.getDouble("lat");
                    lon = res.getDouble("lon");
                    SharedConfig.saveWeatherCache(SharedConfig.weatherText, lat, lon);
                }
                JSONObject weather = jsonGet("https://api.openweathermap.org/data/2.5/weather?lat="
                        + lat + "&lon=" + lon + "&appid=" + URLEncoder.encode(key, "UTF-8")
                        + "&units=metric&lang=ru");
                JSONObject main = weather.getJSONObject("main");
                double temp = main.getDouble("temp");
                JSONArray weatherArr = weather.getJSONArray("weather");
                int code = weatherArr.optJSONObject(0) != null ? weatherArr.getJSONObject(0).optInt("id", 800) : 800;
                result = iconForCode(code) + " " + Math.round(temp) + "°";
                SharedConfig.saveWeatherCache(result, lat, lon);
            } catch (Exception e) {
                FileLog.e(e);
                FileLog.d("DiveWeather: fetch failed — " + e);
            } finally {
                fetching.set(false);
                final String text = result;
                if (text != null) {
                    notifyWeatherChanged();
                }
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(() -> callback.onResult(text != null ? text : getCached()));
                }
            }
        });
    }

    public static String cleanCityName(String raw) {
        if (raw == null) return "";
        String name = raw.trim();
        int comma = name.indexOf(',');
        if (comma > 0) {
            name = name.substring(0, comma);
        }
        int paren = name.indexOf('(');
        if (paren > 0) {
            name = name.substring(0, paren);
        }
        return name.trim();
    }

    public static void notifyWeatherChanged() {
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.diveGramWeatherChanged);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.diveGramWeatherChanged);
            }
        }
    }

    public interface CitiesCallback {
        void onResult(java.util.List<String> cities);
    }

    public static void searchCities(String query, CitiesCallback callback) {
        if (query == null || query.trim().length() < 2) {
            return;
        }
        final String key = OPENWEATHERMAP_API_KEY;
        if (key == null || key.trim().isEmpty()) {
            AndroidUtilities.runOnUIThread(() -> callback.onResult(new java.util.ArrayList<>()));
            return;
        }
        final String q = query.trim();
        Utilities.globalQueue.postRunnable(() -> {
            java.util.List<String> result = new java.util.ArrayList<>();
            try {
                JSONArray arr = jsonGetArray("https://api.openweathermap.org/geo/1.0/direct?limit=5&appid="
                        + URLEncoder.encode(key, "UTF-8")
                        + "&q=" + URLEncoder.encode(q, "UTF-8"));
                if (arr != null) {
                    for (int i = 0; i < arr.length() && i < 5; i++) {
                        JSONObject item = arr.getJSONObject(i);
                        String name = item.optString("name", "");
                        String country = item.optString("country", "");
                        String state = item.optString("state", "");
                        StringBuilder sb = new StringBuilder(name);
                        if (!state.isEmpty() && !state.equals(name)) sb.append(", ").append(state);
                        if (!country.isEmpty()) sb.append(" (").append(country).append(')');
                        String label = sb.toString();
                        if (!label.isEmpty() && !result.contains(label)) {
                            result.add(label);
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            AndroidUtilities.runOnUIThread(() -> callback.onResult(result));
        });
    }

    public static String iconForCode(int code) {
        if (code >= 200 && code < 300) return "\u26C8";
        if (code >= 300 && code < 400) return "\uD83C\uDF26";
        if (code >= 500 && code < 600) return "\uD83C\uDF27";
        if (code >= 600 && code < 700) return "\uD83C\uDF28";
        if (code >= 700 && code < 800) return "\uD83C\uDF2B";
        if (code == 800) return "\u2600\uFE0F";
        if (code == 801) return "\uD83C\uDF24";
        if (code == 802) return "\u26C5";
        if (code >= 803) return "\u2601";
        return "\uD83C\uDF24";
    }

    private static JSONObject jsonGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", "DiveGram/1.0");
        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[8192];
        int n;
        try (InputStream in = is) {
            while (in != null && (n = in.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
        }
        if (code >= 400) throw new Exception("http " + code + " " + sb);
        return new JSONObject(sb.toString());
    }

    private static JSONArray jsonGetArray(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", "DiveGram/1.0");
        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[8192];
        int n;
        try (InputStream in = is) {
            while (in != null && (n = in.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
        }
        if (code >= 400) throw new Exception("http " + code + " " + sb);
        return new JSONArray(sb.toString());
    }
}
