/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class DiveGramStreak implements NotificationCenter.NotificationCenterDelegate {

    public static final String CONFIG_URL = "";
    public static final long CACHE_TTL = 6 * 60 * 60 * 1000L;
    public static final long REFRESH_INTERVAL = 60 * 1000L;
    private static final ZoneId STREAK_ZONE = ZoneId.of("UTC");
    public static final int LOOKBACK_DAYS = 220;

    private static final AtomicBoolean fetching = new AtomicBoolean(false);
    private static final DiveGramStreak DELEGATE = new DiveGramStreak();
    private static boolean inited;

    public static class StreakInfo {
        public final int days;
        public final boolean pending;

        StreakInfo(int days, boolean pending) {
            this.days = days;
            this.pending = pending;
        }
    }

    private static class CachedEntry {
        final int account;
        final long dialogId;
        int days;
        boolean pending;
        long day;
        boolean unreliable;

        CachedEntry(int account, long dialogId, int days, boolean pending, long day, boolean unreliable) {
            this.account = account;
            this.dialogId = dialogId;
            this.days = days;
            this.pending = pending;
            this.day = day;
            this.unreliable = unreliable;
        }
    }

    private static final HashMap<Long, CachedEntry> CACHE = new HashMap<>();
    private static final HashSet<Long> PENDING_CALC = new HashSet<>();
    private static final HashMap<Long, Long> DIRTY = new HashMap<>();
    private static final HashSet<Long> BACKFILLING = new HashSet<>();
    private static final HashMap<Long, Long> BACKFILLED_AT = new HashMap<>();
    private static final long BACKFILL_COOLDOWN_MS = 2 * 60 * 1000L;
    private static final int MAX_BACKFILLS = 2;
    private static final int BACKFILL_PAGE = 100;
    private static final int MAX_BACKFILL_PAGES = 10;

    private static long mapKey(int account, long dialogId) {
        return ((long) account << 32) ^ (dialogId & 0xFFFFFFFFL);
    }

    private static volatile int configThreshold = 3;
    private static volatile int configGray = 0xFF909090;
    private static volatile int[] configTiers = {3, 10, 30, 100};
    private static volatile int[] configColors = {0xFFFBC02D, 0xFFFF9800, 0xFFF44336, 0xFFAB47BC};
    private static volatile boolean configEnabled = true;
    private static volatile boolean hasRemoteConfig;

    public static synchronized void init() {
        if (inited) {
            return;
        }
        inited = true;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).addObserver(DELEGATE, NotificationCenter.didReceiveNewMessages);
        }
        fetch();
        scheduleMidnightTick();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didReceiveNewMessages) {
            long dialogId = 0;
            if (args.length > 0) {
                if (args[0] instanceof Long) {
                    dialogId = (Long) args[0];
                } else if (args.length > 1 && args[1] instanceof Long) {
                    dialogId = (Long) args[1];
                }
            }
            if (dialogId > 0 && DialogObject.isUserDialog(dialogId)) {
                invalidateDialog(dialogId, account);
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_CHAT);
            }
        }
    }

    public static boolean isEnabled() {
        return SharedConfig.streaksEnabled && (!hasRemoteConfig || configEnabled);
    }

    public static int getThreshold() {
        return configThreshold;
    }

    public static int getGrayColor() {
        return configGray;
    }

    public static int getFireColor(int days) {
        int color = configColors[0];
        for (int a = 0; a < configTiers.length; a++) {
            if (days >= configTiers[a]) {
                color = configColors[a];
            }
        }
        return color;
    }

    public static StreakInfo getStreak(long dialogId, int account) {
        if (!isEnabled() || dialogId <= 0 || !DialogObject.isUserDialog(dialogId)) {
            return null;
        }
        long today = LocalDate.now(STREAK_ZONE).toEpochDay();
        long key = mapKey(account, dialogId);
        synchronized (CACHE) {
            CachedEntry entry = CACHE.get(key);
            boolean fresh = entry != null && entry.day == today;
            if (!fresh && !PENDING_CALC.contains(key)) {
                PENDING_CALC.add(key);
                computeAsync(dialogId, account);
            }
            if (entry == null || !fresh || entry.unreliable) {
                return null;
            }
            return new StreakInfo(entry.days, entry.pending);
        }
    }

    private static void invalidateDialog(long dialogId, int account) {
        long key = mapKey(account, dialogId);
        synchronized (CACHE) {
            CachedEntry entry = CACHE.get(key);
            if (entry != null) {
                entry.day = -1;
            }
            DIRTY.put(key, System.currentTimeMillis());
            if (!PENDING_CALC.contains(key)) {
                PENDING_CALC.add(key);
                computeAsync(dialogId, account);
            }
        }
    }

    private static void computeAsync(final long dialogId, final int account) {
        Utilities.globalQueue.postRunnable(() -> {
            StreakInfo info = null;
            boolean unreliable = false;
            long oldestId = 0;
            long today = LocalDate.now(STREAK_ZONE).toEpochDay();
            HashSet<Long> myDays = new HashSet<>();
            HashSet<Long> theirDays = new HashSet<>();
            try {
                MessagesStorage storage = AccountInstance.getInstance(account).getMessagesStorage();
                long minDate = (today - LOOKBACK_DAYS) * 86400L;
                long oldestDay = Long.MAX_VALUE;
                SQLiteCursor cursor = storage.getDatabase().queryFinalized(String.format(Locale.US,
                        "SELECT date, out, mid FROM messages_v2 WHERE uid = %d AND date >= %d AND out IN (0, 1)", dialogId, minDate));
                while (cursor.next()) {
                    long day = java.time.Instant.ofEpochSecond(cursor.intValue(0)).atZone(STREAK_ZONE).toLocalDate().toEpochDay();
                    if (cursor.intValue(1) == 1) {
                        myDays.add(day);
                    } else {
                        theirDays.add(day);
                    }
                    if (day < oldestDay) {
                        oldestDay = day;
                        oldestId = cursor.intValue(2);
                    }
                }
                cursor.dispose();

                HashSet<Long> both = intersect(myDays, theirDays);
                long[] run = trailingRun(both, today);
                info = new StreakInfo((int) run[0], run[1] == 1);
                unreliable = run[0] > 0 && oldestDay != Long.MAX_VALUE && run[2] - 1 < oldestDay;
            } catch (Exception e) {
                FileLog.e(e);
            }

            if (info == null) {
                info = new StreakInfo(0, false);
            }
            final StreakInfo finalInfo = info;
            final boolean finalUnreliable = unreliable;
            synchronized (CACHE) {
                long key = mapKey(account, dialogId);
                CachedEntry prev = CACHE.get(key);
                CACHE.put(key, new CachedEntry(account, dialogId, finalInfo.days, finalInfo.pending, today, finalUnreliable));
                PENDING_CALC.remove(key);
                boolean rerun = DIRTY.remove(key) != null;
                if (prev != null && (prev.days != finalInfo.days || prev.pending != finalInfo.pending || prev.unreliable != finalUnreliable)) {
                    NotificationCenter.getInstance(prev.account).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_CHAT);
                }
                if (rerun) {
                    PENDING_CALC.add(key);
                    computeAsync(dialogId, account);
                }
            }
            if (finalUnreliable) {
                requestBackfill(dialogId, account, myDays, theirDays, today, oldestId);
            }
        });
    }

    private static HashSet<Long> intersect(HashSet<Long> a, HashSet<Long> b) {
        HashSet<Long> both = new HashSet<>(a);
        both.retainAll(b);
        return both;
    }

    private static long[] trailingRun(HashSet<Long> both, long today) {
        long endDay = today;
        boolean pending = false;
        if (!both.contains(today)) {
            if (!both.contains(today - 1)) {
                return new long[]{0, 0, Long.MAX_VALUE};
            }
            endDay = today - 1;
            pending = true;
        }
        int run = 0;
        while (run < LOOKBACK_DAYS && both.contains(endDay - run)) {
            run++;
        }
        return new long[]{run + (pending ? 1 : 0), pending ? 1 : 0, endDay - run + 1};
    }

    private static void requestBackfill(final long dialogId, final int account, final HashSet<Long> myDays,
                                        final HashSet<Long> theirDays, final long today, long oldestId) {
        if (oldestId <= 0) {
            return;
        }
        long key = mapKey(account, dialogId);
        long now = System.currentTimeMillis();
        synchronized (BACKFILLING) {
            if (BACKFILLING.contains(key) || BACKFILLING.size() >= MAX_BACKFILLS) {
                return;
            }
            Long last = BACKFILLED_AT.get(key);
            if (last != null && now - last < BACKFILL_COOLDOWN_MS) {
                return;
            }
            BACKFILLING.add(key);
        }
        backfillStep(dialogId, account, myDays, theirDays, today, (int) oldestId, 0);
    }

    private static void backfillStep(final long dialogId, final int account, final HashSet<Long> myDays,
                                     final HashSet<Long> theirDays, final long today, final int fromId, final int page) {
        final MessagesController messagesController = AccountInstance.getInstance(account).getMessagesController();
        TLRPC.InputPeer peer;
        try {
            peer = messagesController.getInputPeer(dialogId);
        } catch (Exception e) {
            FileLog.e(e);
            peer = null;
        }
        if (peer == null) {
            finishBackfill(dialogId, account, myDays, theirDays, today, false, false);
            return;
        }
        TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = peer;
        req.offset_id = fromId;
        req.limit = BACKFILL_PAGE;
        messagesController.getConnectionsManager().sendRequest(req, (response, error) -> {
            if (!(response instanceof TLRPC.messages_Messages)) {
                finishBackfill(dialogId, account, myDays, theirDays, today, false, false);
                return;
            }
            TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
            int oldest = Integer.MAX_VALUE;
            boolean received = false;
            for (int a = 0; a < res.messages.size(); a++) {
                TLRPC.Message message = res.messages.get(a);
                if (message instanceof TLRPC.TL_messageEmpty || message.id <= 0) {
                    continue;
                }
                message.dialog_id = dialogId;
                long day = java.time.Instant.ofEpochSecond(message.date).atZone(STREAK_ZONE).toLocalDate().toEpochDay();
                if (day < today - LOOKBACK_DAYS) {
                    continue;
                }
                if (message.out) {
                    myDays.add(day);
                } else {
                    theirDays.add(day);
                }
                oldest = Math.min(oldest, message.id);
                received = true;
            }
            if (received) {
                messagesController.getMessagesStorage().putMessages(res, dialogId, -1, 0, false, 0, 0);
            }
            long[] run = trailingRun(intersect(myDays, theirDays), today);
            boolean decided = run[0] == 0 || (run[2] - 1) >= oldestDayOf(myDays, theirDays);
            boolean exhausted = !received || oldest == Integer.MAX_VALUE || oldest >= fromId;
            if (decided || exhausted || page + 1 >= MAX_BACKFILL_PAGES) {
                finishBackfill(dialogId, account, myDays, theirDays, today, true, exhausted || decided || run[0] >= LOOKBACK_DAYS);
            } else {
                backfillStep(dialogId, account, myDays, theirDays, today, oldest, page + 1);
            }
        });
    }

    private static long oldestDayOf(HashSet<Long> myDays, HashSet<Long> theirDays) {
        long oldest = Long.MAX_VALUE;
        for (Long day : myDays) {
            oldest = Math.min(oldest, day);
        }
        for (Long day : theirDays) {
            oldest = Math.min(oldest, day);
        }
        return oldest;
    }

    private static void finishBackfill(long dialogId, int account, HashSet<Long> myDays, HashSet<Long> theirDays,
                                      long today, boolean completed, boolean verified) {
        long key = mapKey(account, dialogId);
        boolean done;
        synchronized (BACKFILLING) {
            done = BACKFILLING.remove(key);
            if (completed) {
                BACKFILLED_AT.put(key, System.currentTimeMillis());
            }
        }
        if (!done) {
            return;
        }
        long[] run = trailingRun(intersect(myDays, theirDays), today);
        boolean pending = run[1] == 1;
        long oldestDay = oldestDayOf(myDays, theirDays);
        boolean unreliable = !verified && run[0] > 0 && oldestDay != Long.MAX_VALUE && run[2] - 1 < oldestDay;
        CachedEntry previous;
        synchronized (CACHE) {
            previous = CACHE.get(key);
            CACHE.put(key, new CachedEntry(account, dialogId, (int) run[0], pending, today, unreliable));
        }
        if (previous == null || previous.days != run[0] || previous.pending != pending || previous.unreliable != unreliable) {
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_CHAT);
        }
    }

    private static void scheduleMidnightTick() {
        ZoneId zone = STREAK_ZONE;
        long now = System.currentTimeMillis();
        long nextMidnight = LocalDate.now(STREAK_ZONE).plusDays(1).atStartOfDay(STREAK_ZONE).toInstant().toEpochMilli() + 120000;
        AndroidUtilities.runOnUIThread(() -> {
            synchronized (CACHE) {
                for (Map.Entry<Long, CachedEntry> e : CACHE.entrySet()) {
                    e.getValue().day = -1;
                }
            }
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_CHAT);
            }
            scheduleMidnightTick();
        }, nextMidnight - now);
    }

    public static void notifyStreakChanged() {
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.diveGramStreakChanged);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_CHAT);
            }
        }
    }

    public static void fetch() {
        if (!SharedConfig.streaksEnabled) {
            return;
        }
        final boolean recent = System.currentTimeMillis() - SharedConfig.streakFetchTime < REFRESH_INTERVAL;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                loadCached();
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (recent && hasRemoteConfig) {
                return;
            }
            if (!fetching.compareAndSet(false, true)) {
                return;
            }
            if (CONFIG_URL.isEmpty()) {
                return;
            }
            try {
                String jsonText = httpGet(CONFIG_URL);
                int oldThreshold = configThreshold;
                int oldGray = configGray;
                boolean oldEnabled = configEnabled;
                int[] oldTiers = configTiers;
                int[] oldColors = configColors;
                applyConfig(jsonText);
                saveCache(jsonText);
                SharedConfig.saveStreakFetchTime();
                boolean changed = oldThreshold != configThreshold || oldGray != configGray || oldEnabled != configEnabled ||
                        !java.util.Arrays.equals(oldTiers, configTiers) || !java.util.Arrays.equals(oldColors, configColors);
                if (changed) {
                    notifyStreakChanged();
                }
            } catch (Exception e) {
                FileLog.e(e);
                FileLog.d("DiveGramStreak: fetch failed — " + e);
            } finally {
                fetching.set(false);
            }
        });
    }

    private static void applyConfig(String jsonText) throws Exception {
        JSONObject config = new JSONObject(jsonText);
        configThreshold = config.optInt("threshold", configThreshold);
        configGray = parseColor(config.optString("gray", "#909090"), configGray);
        if (config.has("enabled")) {
            configEnabled = config.optBoolean("enabled", true);
        }

        JSONArray colors = config.optJSONArray("colors");
        if (colors != null && colors.length() > 0) {
            ArrayList<int[]> list = new ArrayList<>();
            for (int a = 0; a < colors.length(); a++) {
                JSONObject c = colors.optJSONObject(a);
                if (c == null) {
                    continue;
                }
                int min = c.optInt("min", -1);
                int color = parseColor(c.optString("color", "#FFFFFF"), 0xFFFFFFFF);
                if (min >= 0) {
                    list.add(new int[]{min, color});
                }
            }
            if (!list.isEmpty()) {
                Collections.sort(list, (o1, o2) -> Integer.compare(o1[0], o2[0]));
                int[] tiers = new int[list.size()];
                int[] colorsArr = new int[list.size()];
                for (int a = 0; a < list.size(); a++) {
                    tiers[a] = list.get(a)[0];
                    colorsArr[a] = list.get(a)[1];
                }
                configTiers = tiers;
                configColors = colorsArr;
            }
        }
        hasRemoteConfig = true;
    }

    private static int parseColor(String hex, int def) {
        try {
            String s = hex.replace("#", "");
            if (s.length() == 6) {
                s = "FF" + s;
            }
            return (int) Long.parseLong(s, 16);
        } catch (Exception e) {
            return def;
        }
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
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
        if (code >= 400) {
            throw new Exception("http " + code);
        }
        return sb.toString();
    }

    private static File cacheDir() {
        return new File(ApplicationLoader.applicationContext.getFilesDir(), "divegram_streaks");
    }

    private static void saveCache(String json) {
        try {
            File dir = cacheDir();
            if (!dir.exists()) {
                dir.mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(new File(dir, "config.json"))) {
                fos.write(json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void loadCached() throws Exception {
        File file = new File(cacheDir(), "config.json");
        if (!file.exists()) {
            return;
        }
        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) >= 0) {
                n += r;
            }
            applyConfig(new String(buf, StandardCharsets.UTF_8));
        }
    }

    public static class FireDrawable extends Drawable {

        private static final Path FLAME = new Path();
        private static final Path CORE = new Path();

        static {
            FLAME.moveTo(0.50f, 1.00f);
            FLAME.cubicTo(0.66f, 0.94f, 0.84f, 0.84f, 0.86f, 0.68f);
            FLAME.cubicTo(0.89f, 0.55f, 0.84f, 0.44f, 0.72f, 0.40f);
            FLAME.cubicTo(0.70f, 0.40f, 0.66f, 0.30f, 0.66f, 0.26f);
            FLAME.cubicTo(0.60f, 0.06f, 0.52f, 0.02f, 0.46f, 0.02f);
            FLAME.cubicTo(0.40f, 0.24f, 0.30f, 0.30f, 0.28f, 0.34f);
            FLAME.cubicTo(0.24f, 0.40f, 0.16f, 0.46f, 0.16f, 0.60f);
            FLAME.cubicTo(0.16f, 0.74f, 0.28f, 0.92f, 0.50f, 1.00f);
            FLAME.close();

            CORE.moveTo(0.56f, 0.92f);
            CORE.cubicTo(0.68f, 0.82f, 0.72f, 0.70f, 0.62f, 0.56f);
            CORE.cubicTo(0.58f, 0.46f, 0.53f, 0.42f, 0.53f, 0.34f);
            CORE.cubicTo(0.48f, 0.42f, 0.45f, 0.48f, 0.41f, 0.52f);
            CORE.cubicTo(0.34f, 0.62f, 0.34f, 0.72f, 0.46f, 0.88f);
            CORE.close();
        }

        private final Paint flamePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int color = 0xFFFBC02D;
        private int coreColor = 0xFFFFF8E1;

        public FireDrawable() {
            flamePaint.setStyle(Paint.Style.FILL);
            corePaint.setStyle(Paint.Style.FILL);
        }

        public void setColor(int color) {
            this.color = color;
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            coreColor = (255 << 24) | (Math.min(255, (int) (r + (255 - r) * 0.7f)) << 16)
                    | (Math.min(255, (int) (g + (255 - g) * 0.7f)) << 8)
                    | Math.min(255, (int) (b + (255 - b) * 0.7f));
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            int w = getBounds().width();
            int h = getBounds().height();
            if (w <= 0 || h <= 0) {
                return;
            }
            float scale = Math.min(w, h);
            float ox = getBounds().left + (w - scale) / 2f;
            float oy = getBounds().top + (h - scale) / 2f;
            canvas.save();
            canvas.translate(ox, oy);
            canvas.scale(scale, scale);
            flamePaint.setColor(color);
            canvas.drawPath(FLAME, flamePaint);
            corePaint.setColor(coreColor);
            canvas.drawPath(CORE, corePaint);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            flamePaint.setAlpha(alpha);
            corePaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            flamePaint.setColorFilter(colorFilter);
            corePaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}