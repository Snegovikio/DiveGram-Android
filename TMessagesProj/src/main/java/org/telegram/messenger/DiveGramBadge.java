/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Stars.StarsReactionsSheet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class DiveGramBadge {

    public static final String CONFIG_URL = "";
    public static final long CACHE_TTL = 6 * 60 * 60 * 1000L;
    public static final long REFRESH_INTERVAL = 60 * 1000L;

    private static final AtomicBoolean fetching = new AtomicBoolean(false);

    public static class Badge {
        public final String id;
        public final String title;
        public final String imageUrl;
        public final int fallbackRes;
        public final boolean filled;

        Badge(String id, String title, String imageUrl, int fallbackRes, boolean filled) {
            this.id = id;
            this.title = title;
            this.imageUrl = imageUrl;
            this.fallbackRes = fallbackRes;
            this.filled = filled;
        }
    }

    private static final HashMap<Long, Badge> DEFAULT_BADGES = new HashMap<>();
    private static volatile Map<Long, Badge> REMOTE_BADGES = Collections.emptyMap();

    private static void addDefault(long userId, String id, String title, int res, boolean filled) {
        DEFAULT_BADGES.put(userId, new Badge(id, title, null, res, filled));
    }

    static {
        final String beta = "Этот человек участвовал в бета-тестах DiveGram";
        final String dev = "Этот человек является одним из разработчиков DiveGram";
        addDefault(1449798682L, "badge_1", beta, R.drawable.dg_badge_1, false);
        addDefault(5454995128L, "badge_1", beta, R.drawable.dg_badge_1, false);
        addDefault(1474610225L, "badge_1", beta, R.drawable.dg_badge_1, false);
        addDefault(1952520327L, "badge_1", beta, R.drawable.dg_badge_1, false);
        addDefault(1326973638L, "badge_1", beta, R.drawable.dg_badge_1, false);
        addDefault(1706500998L, "badge_1", beta, R.drawable.dg_badge_1, false);
        addDefault(8466107875L, "badge_1", beta, R.drawable.dg_badge_1, false);
        addDefault(1092550803L, "badge_1", beta, R.drawable.dg_badge_1, false);
        addDefault(7732948154L, "badge_2", dev, R.drawable.dg_badge_2, true);
        addDefault(6303494431L, "badge_2", dev, R.drawable.dg_badge_2, true);
    }

    public static Badge getBadge(long userId) {
        Badge remote = REMOTE_BADGES.get(userId);
        if (remote != null) {
            return remote;
        }
        return DEFAULT_BADGES.get(userId);
    }

    public static boolean hasBadge(long userId) {
        return getBadge(userId) != null;
    }

    public static String getBadgeTitle(long userId) {
        Badge badge = getBadge(userId);
        return badge != null ? badge.title : null;
    }

    public static int getBadgeRes(long userId) {
        Badge badge = getBadge(userId);
        return badge != null ? badge.fallbackRes : 0;
    }

    public static void showBadgeInfo(Context context, long userId) {
        Badge badge = getBadge(userId);
        if (badge == null || badge.title == null || badge.title.isEmpty() || context == null) {
            return;
        }
        BottomSheet sheet = new BottomSheet(context, false);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, AndroidUtilities.dp(24), 0, AndroidUtilities.dp(20));

        ImageView badgeView = new ImageView(context);
        Drawable animated = createAnimatedDiveGramBadge(badge, Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), AndroidUtilities.dp(72), AndroidUtilities.dp(120));
        badgeView.setImageDrawable(animated);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(AndroidUtilities.dp(120), AndroidUtilities.dp(120));
        badgeLp.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(badgeView, badgeLp);

        TextView titleView = new TextView(context);
        titleView.setText(badge.title);
        titleView.setTextSize(18);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setGravity(Gravity.CENTER);
        titleView.setLineSpacing(0, 1.1f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = AndroidUtilities.dp(18);
        content.addView(titleView, titleLp);

        TextView closeView = new TextView(context);
        closeView.setText("Ок");
        closeView.setTextSize(15);
        closeView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        closeView.setGravity(Gravity.CENTER);
        closeView.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(4));
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        closeLp.topMargin = AndroidUtilities.dp(18);
        content.addView(closeView, closeLp);
        closeView.setOnClickListener(v -> sheet.dismiss());

        sheet.setCustomView(content);
        sheet.setCancelable(true);
        sheet.show();
        AndroidUtilities.runOnUIThread(sheet::dismiss, 4000);
    }

    public static Drawable createBadgeDrawable(Badge badge, int sizePx) {
        return new ScaledBadgeDrawable(createEmblemDrawable(badge, sizePx), sizePx);
    }

    public static Drawable createAnimatedDiveGramBadge(Badge badge, int color, int sizePx) {
        return new AnimatedBadgeDrawable(badge, color, sizePx, sizePx);
    }

    public static Drawable createAnimatedDiveGramBadge(Badge badge, int color, int badgeSizePx, int sizePx) {
        return new AnimatedBadgeDrawable(badge, color, badgeSizePx, sizePx);
    }

    public static Drawable createDiveGramBadge(Badge badge, int color, int sizePx) {
        return new DiveGramBadgeDrawable(badge, color, sizePx);
    }

    private static final HashMap<String, Drawable> THEME_BADGE_CACHE = new HashMap<>();
    private static final HashMap<String, Bitmap> EMBLEM_CACHE = new HashMap<>();

    private static Bitmap sourceEmblem(Badge badge) {
        File file = cachedImageFile(badge);
        if (file != null && file.exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null) {
                return bitmap;
            }
        }
        if (badge.fallbackRes != 0) {
            return BitmapFactory.decodeResource(ApplicationLoader.applicationContext.getResources(), badge.fallbackRes);
        }
        return null;
    }

    private static Bitmap buildEmblem(Badge badge, int color, boolean keepLight) {
        Bitmap src = sourceEmblem(badge);
        if (src == null) {
            return null;
        }
        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        int w = out.getWidth();
        int h = out.getHeight();
        int[] pix = new int[w * h];
        out.getPixels(pix, 0, w, 0, 0, w, h);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        if (keepLight) {
            final int themeLum = Math.max(1, (r * 299 + g * 587 + b * 114) / 1000);
            for (int i = 0; i < pix.length; i++) {
                int p = pix[i];
                int a = (p >>> 24) & 0xFF;
                if (a < 200) {
                    continue;
                }
                int pr = (p >> 16) & 0xFF;
                int pg = (p >> 8) & 0xFF;
                int pb = p & 0xFF;
                if (pr >= 235 && pg >= 235 && pb >= 235) {
                    continue;
                }
                float k = (pr * 299 + pg * 587 + pb * 114) / 1000f / themeLum;
                if (k < 0.6f) k = 0.6f;
                if (k > 1.35f) k = 1.35f;
                pix[i] = (a << 24)
                        | (Math.min(255, (int) (r * k)) << 16)
                        | (Math.min(255, (int) (g * k)) << 8)
                        | Math.min(255, (int) (b * k));
            }
        } else {
            for (int i = 0; i < pix.length; i++) {
                int p = pix[i];
                int a = (p >>> 24) & 0xFF;
                if (a == 0) {
                    continue;
                }
                pix[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        out.setPixels(pix, 0, w, 0, 0, w, h);
        return out;
    }

    private static Bitmap cachedEmblem(Badge badge, int color, boolean keepLight) {
        String key = badge.id + "_" + (keepLight ? "f" : "w") + "_" + color;
        Bitmap emblem = EMBLEM_CACHE.get(key);
        if (emblem == null) {
            emblem = buildEmblem(badge, color, keepLight);
            if (emblem != null) {
                EMBLEM_CACHE.put(key, emblem);
            }
        }
        return emblem;
    }

    private static Drawable createEmblemDrawable(Badge badge, int px) {
        Bitmap src = sourceEmblem(badge);
        if (src == null) {
            return null;
        }
        return new BitmapDrawable(ApplicationLoader.applicationContext.getResources(), src);
    }

    private static class DiveGramBadgeDrawable extends Drawable {

        private final Badge badge;
        private final int color;
        private final int size;
        private final boolean filled;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap emblem;
        private boolean emblemReady;
        private boolean selfContainedDisc;

        DiveGramBadgeDrawable(Badge badge, int color, int size) {
            this.badge = badge;
            this.color = color;
            this.size = size;
            this.filled = badge.filled;
        }

        private void ensureEmblem() {
            emblemReady = true;
            emblem = cachedEmblem(badge, color, filled);
            selfContainedDisc = emblem != null && hasOuterOpaque(emblem);
        }

        private boolean hasOuterOpaque(Bitmap bitmap) {
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            if (w <= 0 || h <= 0) {
                return false;
            }
            float cx = (w - 1) / 2f;
            float cy = (h - 1) / 2f;
            float maxR = Math.min(w, h) / 2f;
            int opaque = 0;
            int total = 0;
            for (int ring = 0; ring < 3; ring++) {
                float r = maxR * (0.38f + 0.08f * ring);
                for (int i = 0; i < 72; i++) {
                    double a = 2 * Math.PI * i / 72;
                    int x = Math.round(cx + (float) Math.cos(a) * r);
                    int y = Math.round(cy + (float) Math.sin(a) * r);
                    if (x < 0 || y < 0 || x >= w || y >= h) {
                        continue;
                    }
                    total++;
                    if ((bitmap.getPixel(x, y) >>> 24) > 40) {
                        opaque++;
                    }
                }
            }
            return total > 0 && opaque * 100 >= total * 10;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            int s = Math.min(getBounds().width(), getBounds().height());
            if (s <= 0) {
                s = size;
            }
            if (!emblemReady) {
                ensureEmblem();
            }
            float cx = getBounds().left + s / 2f;
            float cy = getBounds().top + s / 2f;
            if (filled && emblem != null && !selfContainedDisc) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(color);
                canvas.drawCircle(cx, cy, s / 2f, paint);
            }
            if (emblem != null) {
                paint.setStyle(Paint.Style.FILL);
                float logoSize = s * (filled ? 1f : 1.15f);
                canvas.drawBitmap(emblem, null, new android.graphics.RectF(cx - logoSize / 2f, cy - logoSize / 2f, cx + logoSize / 2f, cy + logoSize / 2f), paint);
            }
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    public static Drawable createThemeBadgeDrawable(Badge badge, int color, int sizePx) {
        String key = badge.id + "_" + color;
        Drawable recolored = THEME_BADGE_CACHE.get(key);
        if (recolored == null) {
            recolored = recolorBadge(badge, color);
            if (recolored == null) {
                recolored = createBadgeDrawable(badge, sizePx);
            }
            THEME_BADGE_CACHE.put(key, recolored);
        }
        return new ScaledBadgeDrawable(recolored, sizePx);
    }

    private static Drawable recolorBadge(Badge badge, int color) {
        Bitmap src = sourceEmblem(badge);
        if (src == null) {
            return null;
        }
        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        int w = out.getWidth();
        int h = out.getHeight();
        int[] pix = new int[w * h];
        out.getPixels(pix, 0, w, 0, 0, w, h);
        int tintR = (color >> 16) & 0xFF;
        int tintG = (color >> 8) & 0xFF;
        int tintB = color & 0xFF;
        final int themeLum = Math.max(1, (tintR * 299 + tintG * 587 + tintB * 114) / 1000);
        for (int i = 0; i < pix.length; i++) {
            int p = pix[i];
            int a = (p >>> 24) & 0xFF;
            if (a == 0) {
                continue;
            }
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            if (r >= 235 && g >= 235 && b >= 235) {
                continue;
            }
            float k = (r * 299 + g * 587 + b * 114) / 1000f / themeLum;
            if (k < 0.6f) k = 0.6f;
            if (k > 1.35f) k = 1.35f;
            pix[i] = (a << 24)
                    | (Math.min(255, (int) (tintR * k)) << 16)
                    | (Math.min(255, (int) (tintG * k)) << 8)
                    | Math.min(255, (int) (tintB * k));
        }
        out.setPixels(pix, 0, w, 0, 0, w, h);
        return new BitmapDrawable(ApplicationLoader.applicationContext.getResources(), out);
    }

    private static class ScaledBadgeDrawable extends Drawable {

        private final Drawable drawable;
        private final int size;

        ScaledBadgeDrawable(Drawable drawable, int size) {
            this.drawable = drawable;
            this.size = size;
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            int s = Math.min(getBounds().width(), getBounds().height());
            if (s <= 0) {
                s = size;
            }
            drawable.setBounds(getBounds().left, getBounds().top, getBounds().left + s, getBounds().top + s);
            drawable.draw(canvas);
        }

        @Override
        public void setAlpha(int alpha) {
            drawable.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            drawable.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return drawable.getOpacity();
        }
    }

    private static class AnimatedBadgeDrawable extends Drawable {

        private final DiveGramBadgeDrawable badgeDrawable;
        private final int badgeSize;
        private final int size;
        private final int color;
        private final StarsReactionsSheet.Particles particles;
        private int alpha = 255;

        AnimatedBadgeDrawable(Badge badge, int color, int badgeSizePx, int sizePx) {
            badgeDrawable = new DiveGramBadgeDrawable(badge, color, badgeSizePx);
            badgeSize = badgeSizePx;
            size = sizePx;
            this.color = color;
            particles = new StarsReactionsSheet.Particles(StarsReactionsSheet.Particles.TYPE_RADIAL, 6);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            int boundsW = getBounds().width();
            int boundsH = getBounds().height();
            if (boundsW <= 0 || boundsH <= 0) {
                return;
            }
            float centerX = getBounds().left + boundsW / 2f;
            float centerY = getBounds().top + boundsH / 2f;

            int s = Math.min(badgeSize, Math.min(boundsW, boundsH));
            badgeDrawable.setBounds((int) centerX - s / 2, (int) centerY - s / 2, (int) centerX + s / 2, (int) centerY + s / 2);
            badgeDrawable.draw(canvas);

            float spread = size * 0.55f;
            particles.setBounds((int) (centerX - spread / 2f), (int) (centerY - spread / 2f), (int) (centerX + spread / 2f), (int) (centerY + spread / 2f));
            particles.process();
            particles.draw(canvas, color, alpha / 255f);

            invalidateSelf();
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
            badgeDrawable.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            badgeDrawable.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static File cacheDir() {
        return new File(ApplicationLoader.applicationContext.getFilesDir(), "divegram_badges");
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static File cachedImageFile(Badge badge) {
        if (badge == null || badge.imageUrl == null) {
            return null;
        }
        return new File(cacheDir(), sanitize(badge.id) + ".png");
    }

    public static void notifyBadgesChanged() {
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.diveGramBadgesChanged);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.diveGramBadgesChanged);
            }
        }
    }

    public static void fetch() {
        fetch(null);
    }

    public static void fetch(Runnable callback) {
        if (!SharedConfig.badgesEnabled) {
            if (callback != null) {
                AndroidUtilities.runOnUIThread(callback);
            }
            return;
        }
        final boolean recent = System.currentTimeMillis() - SharedConfig.badgesFetchTime < REFRESH_INTERVAL;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                loadCachedConfig();
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (recent && !REMOTE_BADGES.isEmpty()) {
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(callback);
                }
                return;
            }
            if (!fetching.compareAndSet(false, true)) {
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(callback);
                }
                return;
            }
            if (CONFIG_URL.isEmpty()) {
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(callback);
                }
                return;
            }
            try {
                String jsonText = httpGet(CONFIG_URL);
                JSONObject config = new JSONObject(jsonText);
                JSONObject badges = config.getJSONObject("badges");
                JSONObject users = config.getJSONObject("users");

                HashMap<String, String[]> defs = new HashMap<>();
                Iterator<String> it = badges.keys();
                while (it.hasNext()) {
                    String id = it.next();
                    JSONObject b = badges.getJSONObject(id);
                    defs.put(id, new String[]{b.optString("title", ""), b.optString("image", ""), b.optBoolean("filled", false) ? "1" : "0"});
                }

                HashMap<Long, Badge> remote = new HashMap<>();
                Iterator<String> uit = users.keys();
                while (uit.hasNext()) {
                    String uid = uit.next();
                    String badgeId = users.optString(uid, null);
                    String[] def = badgeId != null ? defs.get(badgeId) : null;
                    if (def == null) {
                        continue;
                    }
                    try {
                        long userId = Long.parseLong(uid);
                        boolean filled = "1".equals(def[2]);
                        remote.put(userId, new Badge(badgeId, def[0], def[1].isEmpty() ? null : def[1], fallbackResFor(badgeId), filled));
                    } catch (Exception ignore) {
                    }
                }

                File dir = cacheDir();
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                for (Map.Entry<String, String[]> entry : defs.entrySet()) {
                    if (entry.getValue()[1].isEmpty()) {
                        continue;
                    }
                    try {
                        downloadToFile(entry.getValue()[1], new File(dir, sanitize(entry.getKey()) + ".png"));
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }

                boolean changed = badgesChanged(remote);
                REMOTE_BADGES = Collections.synchronizedMap(remote);
                saveConfigCache(jsonText);
                SharedConfig.saveBadgesFetchTime();
                if (changed) {
                    emblemCacheClear();
                    notifyBadgesChanged();
                }
            } catch (Exception e) {
                FileLog.e(e);
                FileLog.d("DiveGramBadge: fetch failed — " + e);
            } finally {
                fetching.set(false);
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(callback);
                }
            }
        });
    }

    private static boolean badgesChanged(HashMap<Long, Badge> remote) {
        Map<Long, Badge> old = REMOTE_BADGES;
        if (old.size() != remote.size()) {
            return true;
        }
        for (Map.Entry<Long, Badge> e : remote.entrySet()) {
            Badge prev = old.get(e.getKey());
            if (prev == null || !prev.id.equals(e.getValue().id) ||
                    !prev.title.equals(e.getValue().title) || prev.filled != e.getValue().filled ||
                    prev.imageUrl == null ? e.getValue().imageUrl != null : !prev.imageUrl.equals(e.getValue().imageUrl)) {
                return true;
            }
        }
        return false;
    }

    private static int fallbackResFor(String badgeId) {
        if ("badge_1".equals(badgeId)) {
            return R.drawable.dg_badge_1;
        }
        if ("badge_2".equals(badgeId)) {
            return R.drawable.dg_badge_2;
        }
        return 0;
    }

    private static void emblemCacheClear() {
        EMBLEM_CACHE.clear();
    }

    private static void loadCachedConfig() throws Exception {
        File file = new File(cacheDir(), "config.json");
        if (!file.exists()) {
            return;
        }
        String jsonText = readFile(file);
        if (jsonText == null || jsonText.isEmpty()) {
            return;
        }
        JSONObject config = new JSONObject(jsonText);
        JSONObject badges = config.getJSONObject("badges");
        JSONObject users = config.getJSONObject("users");

        HashMap<String, String[]> defs = new HashMap<>();
        Iterator<String> it = badges.keys();
        while (it.hasNext()) {
            String id = it.next();
            JSONObject b = badges.getJSONObject(id);
            defs.put(id, new String[]{b.optString("title", ""), b.optString("image", ""), b.optBoolean("filled", false) ? "1" : "0"});
        }

        HashMap<Long, Badge> remote = new HashMap<>();
        Iterator<String> uit = users.keys();
        while (uit.hasNext()) {
            String uid = uit.next();
            String badgeId = users.optString(uid, null);
            String[] def = badgeId != null ? defs.get(badgeId) : null;
            if (def == null) {
                continue;
            }
            try {
                long userId = Long.parseLong(uid);
                boolean filled = "1".equals(def[2]);
                remote.put(userId, new Badge(badgeId, def[0], def[1].isEmpty() ? null : def[1], fallbackResFor(badgeId), filled));
            } catch (Exception ignore) {
            }
        }
        if (!remote.isEmpty()) {
            REMOTE_BADGES = Collections.synchronizedMap(remote);
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

    private static void downloadToFile(String url, File target) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", "DiveGram/1.0");
        int code = conn.getResponseCode();
        if (code >= 400) {
            return;
        }
        InputStream is = conn.getInputStream();
        byte[] buf = new byte[8192];
        int n;
        try (FileOutputStream fos = new FileOutputStream(target)) {
            while ((n = is.read(buf)) > 0) {
                fos.write(buf, 0, n);
            }
        }
    }

    private static void saveConfigCache(String json) {
        File dir = cacheDir();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(new File(dir, "config.json"))) {
            fos.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static String readFile(File file) {
        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) >= 0) {
                n += r;
            }
            return new String(buf, StandardCharsets.UTF_8);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }
}