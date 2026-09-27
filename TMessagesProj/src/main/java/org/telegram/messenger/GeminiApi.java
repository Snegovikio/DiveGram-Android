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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class GeminiApi {

    public interface Callback {
        void onResult(String answer, String error);
    }

    private static final String ENDPOINT = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODELS_ENDPOINT = "https://openrouter.ai/api/v1/models";
    private static final String FALLBACK_MODEL = "google/gemini-2.0-flash-exp:free";

    private static volatile String resolvedModel;
    private static final Object modelLock = new Object();

    public static boolean isConfigured() {
        return SharedConfig.aiChatEnabled && SharedConfig.geminiApiKey.length() > 0;
    }

    private static String resolveModel() {
        synchronized (modelLock) {
            if (resolvedModel != null && !resolvedModel.isEmpty()) {
                return resolvedModel;
            }
            if (!SharedConfig.aiModelId.isEmpty()) {
                resolvedModel = SharedConfig.aiModelId;
                return resolvedModel;
            }
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(MODELS_ENDPOINT).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("Authorization", "Bearer " + SharedConfig.geminiApiKey);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36");
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
                if (code == 200) {
                    JSONArray data = new JSONObject(sb.toString()).optJSONArray("data");
                    String geminiFree = null, anyGemini = null, anyFree = null;
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject m = data.getJSONObject(i);
                            String id = m.optString("id", "");
                            JSONObject pricing = m.optJSONObject("pricing");
                            boolean free = pricing != null
                                    && isZeroPrice(pricing.optString("prompt"))
                                    && isZeroPrice(pricing.optString("completion"));
                            if (free && id.contains("gemini") && geminiFree == null) geminiFree = id;
                            if (id.contains("gemini") && anyGemini == null) anyGemini = id;
                            if (free && anyFree == null) anyFree = id;
                        }
                    }
                    String chosen = geminiFree != null ? geminiFree : (anyFree != null ? anyFree : (anyGemini != null ? anyGemini : FALLBACK_MODEL));
                    resolvedModel = chosen;
                    SharedConfig.setAiModelId(chosen);
                    FileLog.d("TgWsAI: resolved model " + chosen);
                    return resolvedModel;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            return FALLBACK_MODEL;
        }
    }

    private static boolean isZeroPrice(String v) {
        if (v == null || v.isEmpty()) return false;
        try {
            return Double.parseDouble(v) <= 0d;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static void translate(String text, String targetLanguageName, Callback callback) {
        if (!isConfigured()) {
            AndroidUtilities.runOnUIThread(() -> callback.onResult(null, "Укажите API-ключ OpenRouter в настройках DiveGram → ИИ анализ чата."));
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("model", resolveModel());
                JSONArray messages = new JSONArray();
                messages.put(new JSONObject()
                        .put("role", "system")
                        .put("content", "Ты — переводчик текстов песен. Переведи текст на язык «" + targetLanguageName
                                + "». На вход поданы строки вида «N: исходная строка». Верни ТОЛЬКО переведённые строки в том же формате"
                                + " «N: перевод», по одной на каждую входную строку, номер не меняй, порядок не меняй, без вступлений и пояснений."
                                + " Если строка уже на целевом языке — верни её без изменений."));
                messages.put(new JSONObject()
                        .put("role", "user")
                        .put("content", text));
                body.put("messages", messages);

                HttpURLConnection conn = (HttpURLConnection) new URL(ENDPOINT).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(90000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + SharedConfig.geminiApiKey);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36");
                conn.setRequestProperty("HTTP-Referer", "https://web.telegram.org");
                conn.setRequestProperty("X-Title", "DiveGram");
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }
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
                if (code >= 400) {
                    String msg = "HTTP " + code;
                    try {
                        JSONObject err = new JSONObject(sb.toString());
                        msg = err.getJSONObject("error").optString("message", msg);
                    } catch (Exception ignored) {
                    }
                    final String e2 = msg;
                    AndroidUtilities.runOnUIThread(() -> callback.onResult(null, e2));
                    return;
                }
                JSONObject resp = new JSONObject(sb.toString());
                String answer = null;
                JSONArray choices = resp.optJSONArray("choices");
                if (choices != null && choices.length() > 0) {
                    answer = choices.getJSONObject(0).getJSONObject("message").optString("content", null);
                }
                final String result = answer != null && !answer.isEmpty() ? answer.trim() : null;
                AndroidUtilities.runOnUIThread(() -> callback.onResult(result, result == null ? "Пустой ответ от модели." : null));
            } catch (Exception e) {
                FileLog.e(e);
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                AndroidUtilities.runOnUIThread(() -> callback.onResult(null, msg));
            }
        });
    }

    public static void ask(String question, String chatTranscript, Callback callback) {
        if (!isConfigured()) {
            AndroidUtilities.runOnUIThread(() -> callback.onResult(null, "Укажите API-ключ OpenRouter в настройках DiveGram → ИИ анализ чата."));
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("model", resolveModel());
                JSONArray messages = new JSONArray();
                messages.put(new JSONObject()
                        .put("role", "system")
                        .put("content", "Ты — ассистент внутри мессенджера. Тебе дана история переписки чата "
                                + "(формат: [время] Имя: сообщение). Отвечай кратко на русском языке, опираясь только на историю. "
                                + "Если ответа нет в истории — так и скажи."));
                messages.put(new JSONObject()
                        .put("role", "user")
                        .put("content", "ИСТОРИЯ ЧАТА:\n" + chatTranscript + "\n\nВОПРОС: " + question));
                body.put("messages", messages);

                HttpURLConnection conn = (HttpURLConnection) new URL(ENDPOINT).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + SharedConfig.geminiApiKey);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36");
                conn.setRequestProperty("HTTP-Referer", "https://web.telegram.org");
                conn.setRequestProperty("X-Title", "DiveGram");
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }
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
                if (code >= 400) {
                    String msg = "HTTP " + code;
                    String bodyText = sb.length() > 600 ? sb.substring(0, 600) : sb.toString();
                    try {
                        JSONObject err = new JSONObject(sb.toString());
                        msg = err.getJSONObject("error").optString("message", msg);
                    } catch (Exception ignored) {
                        if (!bodyText.trim().isEmpty()) {
                            msg = msg + ": " + bodyText;
                        }
                    }
                    FileLog.d("TgWsAI: request failed, code=" + code + ", body=" + bodyText);
                    final String e2 = msg;
                    AndroidUtilities.runOnUIThread(() -> callback.onResult(null, e2));
                    return;
                }

                JSONObject resp = new JSONObject(sb.toString());
                String answer = null;
                JSONArray choices = resp.optJSONArray("choices");
                if (choices != null && choices.length() > 0) {
                    answer = choices.getJSONObject(0).getJSONObject("message").optString("content", null);
                }
                final String result = answer != null && !answer.isEmpty() ? answer.trim() : "Пустой ответ от модели.";
                AndroidUtilities.runOnUIThread(() -> callback.onResult(result, null));
            } catch (Exception e) {
                FileLog.e(e);
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                AndroidUtilities.runOnUIThread(() -> callback.onResult(null, msg));
            }
        });
    }
}
