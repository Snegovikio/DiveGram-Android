/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.GoogleTranslate;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;

import android.app.Activity;
import android.content.pm.ActivityInfo;

import static org.telegram.messenger.AndroidUtilities.dp;

@SuppressLint("ViewConstructor")
public class LyricsSheet extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    private static final Object cacheLock = new Object();
    private static final LinkedHashMap<String, LyricsData> cache = new LinkedHashMap<>();
    private static final HashMap<String, Failure> failures = new HashMap<>();
    private static final HashMap<String, ArrayList<Utilities.Callback<LyricsData>>> inFlight = new HashMap<>();
    private static final int MAX_CACHE_ENTRIES = 200;
    private static final long MISS_RETRY_MS = 30 * 60 * 1000L;
    private static final long ERROR_RETRY_MS = 2 * 60 * 1000L;
    private static final int SOURCE_ATTEMPTS = 2;
    private static final long SOURCE_TIMEOUT_MS = 15000;

    private static class Failure {
        final long time;
        final boolean definitive;

        Failure(long time, boolean definitive) {
            this.time = time;
            this.definitive = definitive;
        }
    }

    private static class SourceUnavailable extends Exception {
        SourceUnavailable(String message) {
            super(message);
        }
    }

    public static class SyncLine {
        public long timeMs;
        public String text;
    }

    public static class LyricsData {
        public boolean synced;
        public List<SyncLine> lines = new ArrayList<>();
        public String plainText;

        public boolean isEmpty() {
            return !synced && TextUtils.isEmpty(plainText);
        }
    }

    private static final Pattern LRC_TIME = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:[.:;](\\d{1,3}))?\\]");

    private final MessageObject messageObject;
    private final long durationMs;
    private final Context context;

    private FrameLayout bodyLayout;
    private ScrollView centeredScroll;
    private LinearLayout centeredLines;
    private FrameLayout albumLayout;
    private ImageView albumCoverView;
    private ScrollView albumScroll;
    private LinearLayout albumLines;
    private TextView albumTitleTextView;
    private TextView albumAuthorTextView;
    private AlbumProgressView albumProgressView;
    private TextView titleTextView;
    private TextView authorTextView;
    private Bitmap coverBitmap;
    private final String musicTitle;
    private final String musicAuthor;
    private ContextProgressView progressView;
    private ImageView albumModeButton;
    private ImageView noLyricsCoverView;
    private List<String> translatedLines;
    private boolean translating;
    private ImageView closeButton;
    private FrameLayout topBar;
    private LinearLayout contentLayout;
    private int topInset = AndroidUtilities.statusBarHeight;

    private int headerTop() {
        boolean landscape = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y;
        int inset = Math.min(topInset, dp(24));
        return (landscape ? 0 : inset) + dp(2);
    }

    private int topBarTop() {
        return Math.max(0, headerTop() - dp(20));
    }

    private void applyHeaderPosition() {
        if (topBar == null || contentLayout == null) {
            return;
        }
        ((FrameLayout.LayoutParams) topBar.getLayoutParams()).topMargin = topBarTop();
        ((FrameLayout.LayoutParams) contentLayout.getLayoutParams()).topMargin = headerTop() + dp(50);
        topBar.requestLayout();
        contentLayout.requestLayout();
    }

    private void applyImmersiveMode() {
        try {
            android.view.Window window = getWindow();
            if (window == null) {
                return;
            }
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                android.view.WindowManager.LayoutParams attrs = window.getAttributes();
                attrs.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                window.setAttributes(attrs);
            }
            final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            window.getDecorView().setSystemUiVisibility(flags);
            if (container != null) {
                container.setSystemUiVisibility(flags);
            }
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onOpenAnimationEnd() {
        super.onOpenAnimationEnd();
        applyImmersiveMode();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveMode();
        }
    }

    private LyricsData data;
    private String currentKey;
    private final List<TextView> lineViews = new ArrayList<>();
    private int currentLine = -1;
    private long lastUserScroll;
    private boolean dismissed;
    private boolean rendering;
    public boolean forceNoLyricsMode;
    private int prevSystemUiVisibility = -1;

    public LyricsSheet(Context context, Theme.ResourcesProvider resourcesProvider, MessageObject messageObject, Bitmap cover) {
        this(context, resourcesProvider, messageObject, cover, false);
    }

    public LyricsSheet(Context context, Theme.ResourcesProvider resourcesProvider, MessageObject messageObject, Bitmap cover, boolean forceNoLyrics) {
        super(context, false, true, resourcesProvider);
        forceNoLyricsMode = forceNoLyrics;
        fixNavigationBar();

        fullWidth = true;
        try {
            android.view.Window window = getWindow();
            if (window != null) {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
                prevSystemUiVisibility = window.getDecorView().getSystemUiVisibility();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        applyImmersiveMode();

        this.context = context;
        this.messageObject = messageObject;
        this.durationMs = (long) (messageObject.getDuration() * 1000f);

        final String title = messageObject.getMusicTitle();
        final String author = messageObject.getMusicAuthor();
        final String key = buildKey(author, title);
        currentKey = key;

        if (cover == null || cover.isRecycled()) {
            cover = createFallbackCoverBitmap(dp(256));
        }
        this.coverBitmap = cover;
        this.musicTitle = title;
        this.musicAuthor = author;

        containerView = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                    widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
            }
        };
        FrameLayout container = (FrameLayout) containerView;

        ImageView backgroundImageView = new ImageView(context);
        backgroundImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        container.addView(backgroundImageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        if (cover != null && !cover.isRecycled()) {
            prepareBlurredBackground(backgroundImageView, cover);
        }

        View scrimView = new View(context);
        scrimView.setBackgroundColor(0x8A000000);
        container.addView(scrimView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        topBar = new FrameLayout(context);
        container.addView(topBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP | Gravity.LEFT, 0, topBarTop(), 0, 0));

        closeButton = new ImageView(context);
        closeButton.setImageResource(R.drawable.ic_ab_back);
        closeButton.setRotation(-90f);
        closeButton.setColorFilter(0xFFFFFFFF);
        closeButton.setBackground(Theme.createSelectorDrawable(0x33FFFFFF, 1, dp(20)));
        closeButton.setOnClickListener(v -> dismiss());
        closeButton.setContentDescription("Закрыть");
        closeButton.setPadding(dp(9), dp(9), dp(9), dp(9));
        if (SharedConfig.playerLyricsHideControls) {
            closeButton.setAlpha(0f);
        }
        topBar.addView(closeButton, LayoutHelper.createFrame(40, 40, Gravity.TOP | Gravity.LEFT, 6, 0, 0, 0));

        albumModeButton = new ImageView(context);
        albumModeButton.setImageResource(R.drawable.player_lyrics_album);
        albumModeButton.setColorFilter(0xFFFFFFFF);
        albumModeButton.setBackground(Theme.createSelectorDrawable(0x33FFFFFF, 1, dp(20)));
        albumModeButton.setOnClickListener(v -> toggleAlbumMode());
        albumModeButton.setContentDescription("Альбомный режим");
        albumModeButton.setPadding(dp(9), dp(9), dp(9), dp(9));
        if (SharedConfig.playerLyricsHideControls) {
            albumModeButton.setAlpha(0f);
        }
        topBar.addView(albumModeButton, LayoutHelper.createFrame(40, 40, Gravity.TOP | Gravity.RIGHT, 0, 0, 6, 0));

        contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(dp(24), 0, dp(24), 0);
        container.addView(contentLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, headerTop() + dp(50), 0, 0));

        container.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets != null ? insets.getSystemWindowInsetTop() : 0;
            if (top >= 0 && top != topInset) {
                topInset = top;
                applyHeaderPosition();
            }
            return insets;
        });

        titleTextView = new TextView(context);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        titleTextView.setTypeface(AndroidUtilities.bold());
        titleTextView.setTextColor(0xFFFFFFFF);
        titleTextView.setSingleLine(true);
        titleTextView.setEllipsize(TextUtils.TruncateAt.END);
        titleTextView.setText(title != null ? title : "");
        contentLayout.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        authorTextView = new TextView(context);
        authorTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        authorTextView.setTextColor(0xB3FFFFFF);
        authorTextView.setSingleLine(true);
        authorTextView.setEllipsize(TextUtils.TruncateAt.END);
        authorTextView.setText(author != null ? author : "");
        contentLayout.addView(authorTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 6));

        bodyLayout = new FrameLayout(context);
        contentLayout.addView(bodyLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        centeredScroll = makeScrollView();
        centeredLines = new LinearLayout(context);
        centeredLines.setOrientation(LinearLayout.VERTICAL);
        centeredLines.setGravity(Gravity.CENTER_HORIZONTAL);
        centeredLines.setPadding(0, dp(24), 0, dp(160));
        centeredScroll.addView(centeredLines, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        bodyLayout.addView(centeredScroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        albumLayout = new AlbumFrameLayout(context);
        albumLayout.setVisibility(View.GONE);
        bodyLayout.addView(albumLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        buildAlbumLayout();

        progressView = new ContextProgressView(context, 0);
        progressView.setVisibility(View.VISIBLE);
        container.addView(progressView, LayoutHelper.createFrame(48, 48, Gravity.CENTER));

        applyAlbumMode(SharedConfig.playerLyricsAlbumMode, false);

        if (SharedConfig.playerLyricsAlbumMode) {
            albumLayout.postDelayed(() -> {
                if (!dismissed) {
                    buildAlbumLayout();
                    renderLines();
                }
            }, 350);
        }

        NotificationCenter.getInstance(messageObject.currentAccount).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance(messageObject.currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.onActivityResultReceived);

        LyricsData cached = getCached(key);
        if (forceNoLyricsMode) {
            showNoLyrics();
        } else if (cached != null && !cached.isEmpty()) {
            showLyrics(cached);
        } else {
            fetchAsync(key, author, title, lyrics -> {
                if (dismissed) {
                    return;
                }
                if (lyrics == null || lyrics.isEmpty()) {
                    showNoLyrics();
                } else {
                    showLyrics(lyrics);
                }
            });
        }
    }

    private ScrollView makeScrollView() {
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                lastUserScroll = System.currentTimeMillis();
            }
            return false;
        });
        return scrollView;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (dismissed) {
            return;
        }
        if (id == NotificationCenter.onActivityResultReceived) {
            return;
        }
        if (id == NotificationCenter.messagePlayingDidReset) {
            MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
            int playingId = playing != null ? playing.getId() : -1;
            int selfId = messageObject != null ? messageObject.getId() : -2;
            if (playingId != selfId && args != null && args.length > 1 && (Boolean) args[1]) {
                dismiss();
            }
            return;
        }
        if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
            int playingId = playing != null ? playing.getId() : -1;
            int selfId = messageObject != null ? messageObject.getId() : -2;
            if (playingId != selfId) {
                return;
            }
            if (rendering) {
                return;
            }
            Object arg = args != null && args.length > 1 ? args[1] : null;
            float progress;
            if (arg instanceof Float) {
                progress = (Float) arg;
            } else if (arg instanceof Number) {
                progress = ((Number) arg).floatValue();
            } else {
                return;
            }
            handleProgress(progress);
        }
    }

    private long effectiveDurationMs() {
        if (messageObject != null && messageObject.audioPlayerDuration > 0) {
            return messageObject.audioPlayerDuration * 1000L;
        }
        return durationMs;
    }

    private void handleProgress(float progress) {
        if (albumProgressView != null) {
            albumProgressView.setProgress(progress);
        }
        long duration = effectiveDurationMs();
        if (data == null || duration <= 0) {
            return;
        }
        boolean userScrolling = System.currentTimeMillis() - lastUserScroll < 3000;
        ScrollView scrollView = isAlbumMode() ? albumScroll : centeredScroll;
        if (data.synced && !lineViews.isEmpty()) {
            long timeMs = (long) (progress * duration);
            int index = findCurrentIndex(timeMs);
            if (index != currentLine) {
                currentLine = index;
                applyHighlight(!userScrolling);
                if (!userScrolling) {
                    scrollToActiveLine();
                }
            }
        } else if (!userScrolling && !rendering) {
            View content = scrollView.getChildAt(0);
            if (content == null || scrollView.getHeight() == 0) {
                return;
            }
            int maxScroll = Math.max(0, content.getHeight() - scrollView.getHeight());
            animateScrollTo(scrollView, (int) (progress * maxScroll));
        }
    }

    private void scrollToActiveLine() {
        if (currentLine < 0 || currentLine >= lineViews.size()) {
            return;
        }
        ScrollView scrollView = isAlbumMode() ? albumScroll : centeredScroll;
        if (scrollView == null || scrollView.getHeight() == 0) {
            return;
        }
        View target = lineViews.get(currentLine);
        int top = target.getTop();
        if (target.getParent() instanceof View && target.getParent() != scrollView.getChildAt(0)) {
            top += ((View) target.getParent()).getTop();
        }
        int y = Math.max(0, top - (scrollView.getHeight() - target.getHeight()) / 2);
        animateScrollTo(scrollView, y);
    }

    private android.animation.ValueAnimator scrollAnimator;

    private void animateScrollTo(ScrollView scrollView, int targetY) {
        targetY = Math.max(0, targetY);
        if (Math.abs(scrollView.getScrollY() - targetY) < dp(2)) {
            return;
        }
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
        }
        final int from = scrollView.getScrollY();
        final int dist = Math.abs(targetY - from);
        scrollAnimator = android.animation.ValueAnimator.ofInt(from, targetY);
        scrollAnimator.setDuration(Math.max(220, Math.min(900L, dist)));
        scrollAnimator.addUpdateListener(animation -> {
            if (dismissed) {
                animation.cancel();
                return;
            }
            scrollView.scrollTo(0, (Integer) animation.getAnimatedValue());
        });
        scrollAnimator.start();
    }

    private int pendingIndex;

    private int findCurrentIndex(long timeMs) {
        pendingIndex = -1;
        if (data == null || data.lines.isEmpty()) {
            return -1;
        }
        int lo = 0, hi = data.lines.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (data.lines.get(mid).timeMs <= timeMs) {
                pendingIndex = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return pendingIndex;
    }

    private static final float BASE_LINE_TEXT_SIZE = 27f;
    private static final float ACTIVE_LINE_TEXT_SIZE = 27f;

    private TextView activeLineView;
    private android.animation.ValueAnimator lineSizeAnimator;

    private void stopLineSizeAnimator() {
        if (lineSizeAnimator != null) {
            lineSizeAnimator.cancel();
            lineSizeAnimator = null;
        }
    }

    private void animateActiveLine(TextView newActive, boolean animated) {
        final TextView old = activeLineView;
        activeLineView = newActive;
        stopLineSizeAnimator();
        if (old == newActive) {
            return;
        }
        if (old != null && old.getTextSize() / AndroidUtilities.density != BASE_LINE_TEXT_SIZE) {
            old.setTextSize(TypedValue.COMPLEX_UNIT_DIP, BASE_LINE_TEXT_SIZE);
        }
        if (newActive != null && newActive.getTextSize() / AndroidUtilities.density != ACTIVE_LINE_TEXT_SIZE) {
            newActive.setTextSize(TypedValue.COMPLEX_UNIT_DIP, ACTIVE_LINE_TEXT_SIZE);
        }
    }

    private void applyHighlight(boolean animated) {
        TextView newActive = null;
        for (int i = 0; i < lineViews.size(); i++) {
            TextView view = lineViews.get(i);
            if (i == currentLine) {
                view.setTextColor(0xFFFFFFFF);
                newActive = view;
            } else {
                view.setTextColor(0x99FFFFFF);
            }
            if (view.getParent() instanceof LinearLayout) {
                LinearLayout wrap = (LinearLayout) view.getParent();
                if (wrap.getChildCount() > 1 && wrap.getChildAt(1) instanceof TextView) {
                    TextView subView = (TextView) wrap.getChildAt(1);
                    subView.setTextColor(i == currentLine ? 0xE6FFFFFF : 0xB3FFFFFF);
                }
            }
        }
        animateActiveLine(newActive, animated);
    }

    private boolean isAlbumMode() {
        return albumLayout.getVisibility() == View.VISIBLE;
    }

    private void toggleAlbumMode() {
        boolean target = !isAlbumMode();
        if (!target && openedFromLiquidPlayer) {
            openedFromLiquidPlayer = false;
            dismiss();
            return;
        }
        if (!target && albumLayout != null) {
            albumLayout.animate().cancel();
            albumLayout.setAlpha(0f);
        }
        setOrientation(target);
        applyAlbumMode(target, false);
        SharedConfig.setPlayerLyricsAlbumMode(isAlbumMode());
        if (target) {
            AndroidUtilities.runOnUIThread(() -> {
                if (dismissed) {
                    return;
                }
                buildAlbumLayout();
                albumLayout.requestLayout();
                if (data != null) {
                    renderLines();
                }
            }, 350);
        } else {
            AndroidUtilities.runOnUIThread(() -> {
                if (!dismissed && data != null) {
                    renderLines();
                }
            }, 100);
        }
    }

    private void buildAlbumLayout() {
        if (albumLayout == null) {
            return;
        }
        AlbumFrameLayout frame = (AlbumFrameLayout) albumLayout;
        frame.removeAllViews();

        LinearLayout leftColumn = new LinearLayout(context);
        leftColumn.setOrientation(LinearLayout.VERTICAL);
        leftColumn.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout coverWrap = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int w = MeasureSpec.getSize(widthMeasureSpec);
                int side = Math.max(dp(60), w);
                if (getChildCount() > 0) {
                    getChildAt(0).measure(
                        MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY));
                }
                setMeasuredDimension(side, side);
            }
        };
        albumCoverView = new ImageView(context);
        albumCoverView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        albumCoverView.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, @NonNull android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(14));
            }
        });
        albumCoverView.setClipToOutline(true);
        if (coverBitmap != null && !coverBitmap.isRecycled()) {
            albumCoverView.setImageBitmap(coverBitmap);
        }
        coverWrap.addView(albumCoverView, new FrameLayout.LayoutParams(dp(120), dp(120), Gravity.CENTER));
        leftColumn.addView(coverWrap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        albumTitleTextView = new TextView(context);
        albumTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        albumTitleTextView.setTypeface(AndroidUtilities.bold());
        albumTitleTextView.setTextColor(0xFFFFFFFF);
        albumTitleTextView.setSingleLine(true);
        albumTitleTextView.setEllipsize(TextUtils.TruncateAt.END);
        albumTitleTextView.setText(musicTitle != null ? musicTitle : "");
        leftColumn.addView(albumTitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START, 0, dp(14), 0, 0));

        albumAuthorTextView = new TextView(context);
        albumAuthorTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        albumAuthorTextView.setTextColor(0xB3FFFFFF);
        albumAuthorTextView.setSingleLine(true);
        albumAuthorTextView.setEllipsize(TextUtils.TruncateAt.END);
        albumAuthorTextView.setText(musicAuthor != null ? musicAuthor : "");
        leftColumn.addView(albumAuthorTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START, 0, dp(4), 0, 0));

        albumProgressView = new AlbumProgressView(context);
        albumProgressView.setDurationMs(durationMs);
        leftColumn.addView(albumProgressView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, dp(4), Gravity.START, 0, dp(12), 0, 0));

        frame.addView(leftColumn, new FrameLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        albumScroll = makeScrollView();
        albumLines = new LinearLayout(context);
        albumLines.setOrientation(LinearLayout.VERTICAL);
        albumLines.setGravity(Gravity.START);
        albumLines.setPadding(0, dp(24), 0, dp(160));
        albumScroll.addView(albumLines, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START));
        frame.addView(albumScroll, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        frame.requestLayout();
    }

    private class AlbumFrameLayout extends FrameLayout {
        public AlbumFrameLayout(Context context) {
            super(context);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int w = right - left;
            int h = bottom - top;
            if (w <= 0 || h <= 0 || getChildCount() < 2) {
                super.onLayout(changed, left, top, right, bottom);
                return;
            }
            View coverBlock = getChildAt(0);
            View lyrics = getChildAt(1);

            int minDim = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
            int coverSide = Math.max(dp(60), (int) (SharedConfig.albumCoverSize * minDim));
            coverBlock.measure(
                MeasureSpec.makeMeasureSpec(coverSide, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST));
            int cw = coverBlock.getMeasuredWidth();
            int ch = coverBlock.getMeasuredHeight();
            int ccx, ccy;
            if (noLyricsMode()) {
                ccx = w / 2;
                ccy = h / 2;
            } else {
                ccx = (int) (SharedConfig.albumCoverX * w);
                ccy = (int) (SharedConfig.albumCoverY * h);
            }
            int cl = ccx - cw / 2;
            int ct = ccy - ch / 2;
            cl = Math.max(0, Math.min(cl, w - cw));
            ct = Math.max(0, Math.min(ct, h - ch));
            coverBlock.layout(cl, ct, cl + cw, ct + ch);

            int lw = Math.max(dp(80), (int) (SharedConfig.albumLyricsWidth * AndroidUtilities.displaySize.x));
            lyrics.measure(
                MeasureSpec.makeMeasureSpec(lw, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            int lcx = (int) (SharedConfig.albumLyricsX * w);
            int lcy = (int) (SharedConfig.albumLyricsY * h);
            int ll = lcx - lw / 2;
            int lt = lcy - h / 2;
            ll = Math.max(0, Math.min(ll, w - lw));
            lt = Math.max(0, Math.min(lt, h - h));
            lyrics.layout(ll, lt, ll + lw, lt + h);
        }
    }

    private void applyAlbumMode(boolean album, boolean reRender) {
        albumLayout.setVisibility(album ? View.VISIBLE : View.GONE);
        if (album) {
            albumLayout.setAlpha(1f);
        }
        if (noLyricsCoverView != null) {
            noLyricsCoverView.setVisibility(album ? View.GONE : (noLyricsMode() ? View.VISIBLE : View.GONE));
        }
        centeredScroll.setVisibility(album ? View.GONE : View.VISIBLE);
        if (titleTextView != null) {
            titleTextView.setVisibility(album ? View.GONE : View.VISIBLE);
        }
        if (authorTextView != null) {
            authorTextView.setVisibility(album ? View.GONE : View.VISIBLE);
        }
        if (contentLayout != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) contentLayout.getLayoutParams();
            int targetMargin = album ? 0 : (headerTop() + dp(50));
            if (lp.topMargin != targetMargin) {
                lp.topMargin = targetMargin;
                contentLayout.setLayoutParams(lp);
            }
        }
        albumModeButton.setColorFilter(album ? 0xFF8AB4F8 : 0xFFFFFFFF);
        setOrientation(album);
        if (reRender) {
            bodyLayout.post(() -> {
                if (!dismissed && data != null) {
                    renderLines();
                }
            });
        }
    }

    private boolean orientationLocked;
    private int prevRequestedOrientation = Integer.MIN_VALUE;
    public static boolean openedFromLiquidPlayer = false;

    private void setOrientation(boolean landscape) {
        try {
            Activity activity = AndroidUtilities.getActivity(getContext());
            if (activity == null) {
                return;
            }
            if (landscape && !orientationLocked) {
                orientationLocked = true;
                prevRequestedOrientation = activity.getRequestedOrientation();
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            } else if (!landscape && orientationLocked) {
                orientationLocked = false;
                activity.setRequestedOrientation(prevRequestedOrientation == Integer.MIN_VALUE
                    ? ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED : prevRequestedOrientation);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void dismiss() {
        dismissed = true;
        openedFromLiquidPlayer = false;
        if (scrollAnimator != null) {
            scrollAnimator.cancel();
        }
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.onActivityResultReceived);
        NotificationCenter.getInstance(messageObject.currentAccount).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance(messageObject.currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
        if (orientationLocked) {
            AndroidUtilities.runOnUIThread(() -> setOrientation(false), 280);
        }
        try {
            android.view.Window window = getWindow();
            if (window != null && prevSystemUiVisibility != -1) {
                window.getDecorView().setSystemUiVisibility(prevSystemUiVisibility);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        super.dismiss();
    }

    private void showLyrics(LyricsData lyricsData) {
        this.data = lyricsData;
        progressView.setVisibility(View.GONE);
        if (noLyricsCoverView != null) {
            noLyricsCoverView.setVisibility(View.GONE);
        }
        applyAlbumMode(SharedConfig.playerLyricsAlbumMode, false);
        renderLines();
        maybeAutoTranslate();
    }

    private boolean noLyricsMode() {
        return data == null || data.isEmpty();
    }

    private void showNoLyrics() {
        progressView.setVisibility(View.GONE);
        centeredScroll.setVisibility(View.GONE);
        albumLayout.setVisibility(View.GONE);
        if (titleTextView != null) {
            titleTextView.setVisibility(View.GONE);
        }
        if (authorTextView != null) {
            authorTextView.setVisibility(View.GONE);
        }
        if (contentLayout != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) contentLayout.getLayoutParams();
            int targetMargin = headerTop() + dp(50);
            if (lp.topMargin != targetMargin) {
                lp.topMargin = targetMargin;
                contentLayout.setLayoutParams(lp);
            }
        }
        if (noLyricsCoverView == null && coverBitmap != null && !coverBitmap.isRecycled()) {
            noLyricsCoverView = new ImageView(context);
            noLyricsCoverView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            noLyricsCoverView.setImageBitmap(coverBitmap);
            noLyricsCoverView.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, @NonNull android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(24));
                }
            });
            noLyricsCoverView.setClipToOutline(true);
            bodyLayout.addView(noLyricsCoverView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER, 16, 16, 16, 16));
        }
        if (noLyricsCoverView != null) {
            noLyricsCoverView.setVisibility(View.VISIBLE);
            noLyricsCoverView.requestLayout();
        }
    }

    private List<String> getRenderedLines() {
        List<String> lines = new ArrayList<>();
        if (data.synced) {
            for (SyncLine line : data.lines) {
                lines.add(line.text);
            }
        } else {
            for (String raw : data.plainText.split("\n")) {
                lines.add(raw.trim());
            }
        }
        while (!lines.isEmpty() && lines.get(0).isEmpty()) {
            lines.remove(0);
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private void renderLines() {
        if (data == null) {
            return;
        }
        rendering = true;
        stopLineSizeAnimator();
        activeLineView = null;
        lineViews.clear();
        try {
            LinearLayout target = isAlbumMode() ? albumLines : centeredLines;
            LinearLayout other = isAlbumMode() ? centeredLines : albumLines;
            other.removeAllViews();
            target.removeAllViews();

            List<String> lines = getRenderedLines();

            boolean center = !isAlbumMode();
            int index = 0;
            for (String line : lines) {
                if (line.isEmpty()) {
                    View spacer = new View(context);
                    target.addView(spacer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 22));
                    index++;
                    continue;
                }
                TextView textView = new TextView(context);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, BASE_LINE_TEXT_SIZE);
                textView.setTypeface(AndroidUtilities.bold());
                textView.setTextColor(data.synced ? (index == 0 ? 0xFFFFFFFF : 0x99FFFFFF) : 0xE6FFFFFF);
                textView.setGravity(center ? Gravity.CENTER : Gravity.START);
                textView.setLineSpacing(dp(9), 1f);
                textView.setText(line);
                if (data.synced) {
                    final int lineIndex = index;
                    textView.setOnClickListener(v -> seekTo(lineIndex));
                }
                lineViews.add(textView);
                String translated = translatedLines != null && index < translatedLines.size() ? translatedLines.get(index) : null;
                LinearLayout.LayoutParams lineLp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10);
                if (translated != null && !translated.isEmpty()) {
                    LinearLayout wrap = new LinearLayout(context);
                    wrap.setOrientation(LinearLayout.VERTICAL);
                    TextView subView = new TextView(context);
                    subView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    subView.setTextColor(0xB3FFFFFF);
                    subView.setGravity(center ? Gravity.CENTER : Gravity.START);
                    subView.setLineSpacing(dp(4), 1f);
                    subView.setText(translated);
                    wrap.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    wrap.addView(subView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 10));
                    target.addView(wrap, lineLp);
                } else {
                    target.addView(textView, lineLp);
                }
                index++;
            }

            currentLine = -1;
            target.setAlpha(0f);
            target.animate().alpha(1f).setDuration(220).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    target.animate().setListener(null);
                }
            }).start();
        } catch (Exception e) {
            FileLog.e(e);
        }

        AndroidUtilities.runOnUIThread(() -> {
            rendering = false;
            lastUserScroll = 0;
            MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
            int playingId = playing != null ? playing.getId() : -1;
            int selfId = messageObject != null ? messageObject.getId() : -2;
            if (playingId == selfId) {
                handleProgress(playing.audioProgress);
            }
        }, 260);
    }

    private void seekTo(int lineIndex) {
        if (data == null || !data.synced || lineIndex < 0 || lineIndex >= data.lines.size()) {
            return;
        }
        float progress = data.lines.get(lineIndex).timeMs / (float) Math.max(1L, effectiveDurationMs());
        MediaController.getInstance().seekToProgress(messageObject, Math.max(0f, Math.min(1f, progress)));
        lastUserScroll = 0;
        handleProgress(progress);
    }

    private static final String LYRICS_TRANSLATION_PREFIX = "dgLyricsTranslation2_";

    private static String getSavedTranslation(String key) {
        if (key == null) {
            return null;
        }
        return MessagesController.getGlobalMainSettings().getString(LYRICS_TRANSLATION_PREFIX + Utilities.MD5(key), null);
    }

    private static void putSavedTranslation(String key, String value) {
        if (key == null) {
            return;
        }
        MessagesController.getGlobalMainSettings().edit()
                .putString(LYRICS_TRANSLATION_PREFIX + Utilities.MD5(key), value)
                .apply();
    }

    private static void clearSavedTranslation(String key) {
        if (key == null) {
            return;
        }
        MessagesController.getGlobalMainSettings().edit()
                .remove(LYRICS_TRANSLATION_PREFIX + Utilities.MD5(key))
                .apply();
    }

    private String targetLanguageCode() {
        if (!TextUtils.isEmpty(SharedConfig.playerLyricsTranslateLang)) {
            return SharedConfig.playerLyricsTranslateLang;
        }
        try {
            return LocaleController.getInstance().getCurrentLocale().getLanguage();
        } catch (Exception e) {
            return "ru";
        }
    }

    private static boolean isLetterChar(char c) {
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z'
                || c >= '\u00C0' && c <= '\u024F'
                || c >= '\u0370' && c <= '\u03FF'
                || c >= '\u0400' && c <= '\u04FF'
                || c >= '\u0590' && c <= '\u05FF'
                || c >= '\u0600' && c <= '\u06FF' || c >= '\u0750' && c <= '\u077F'
                || c >= '\u0E00' && c <= '\u0E7F'
                || c >= '\u1100' && c <= '\u11FF'
                || c >= '\u3040' && c <= '\u30FF'
                || c >= '\u4E00' && c <= '\u9FFF'
                || c >= '\uAC00' && c <= '\uD7AF';
    }

    private static boolean isNativeLetter(char c, String lang) {
        if (lang == null) {
            return true;
        }
        if (lang.startsWith("ru") || lang.startsWith("uk") || lang.startsWith("bg") || lang.startsWith("sr") || lang.startsWith("mk")) {
            return c >= '\u0400' && c <= '\u04FF';
        } else if (lang.startsWith("ar")) {
            return c >= '\u0600' && c <= '\u06FF' || c >= '\u0750' && c <= '\u077F';
        } else if (lang.startsWith("zh") || lang.startsWith("ja")) {
            return c >= '\u4E00' && c <= '\u9FFF' || c >= '\u3040' && c <= '\u309F' || c >= '\u30A0' && c <= '\u30FF';
        } else if (lang.startsWith("ko")) {
            return c >= '\uAC00' && c <= '\uD7AF' || c >= '\u1100' && c <= '\u11FF';
        } else if (lang.startsWith("he")) {
            return c >= '\u0590' && c <= '\u05FF';
        } else if (lang.startsWith("el")) {
            return c >= '\u0370' && c <= '\u03FF';
        } else if (lang.startsWith("th")) {
            return c >= '\u0E00' && c <= '\u0E7F';
        } else {
            return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '\u00C0' && c <= '\u024F';
        }
    }

    private static List<int[]> findForeignSegments(String line, String lang) {
        List<int[]> segments = new ArrayList<>();
        int n = line.length();
        int i = 0;
        while (i < n) {
            char c = line.charAt(i);
            if (isLetterChar(c) && !isNativeLetter(c, lang)) {
                int start = i;
                int end = i;
                while (i < n) {
                    char d = line.charAt(i);
                    if (isLetterChar(d) && !isNativeLetter(d, lang)) {
                        end = i + 1;
                        i++;
                    } else if ((d == '\'' || d == '’' || d == '-') && i + 1 < n
                            && isLetterChar(line.charAt(i + 1)) && !isNativeLetter(line.charAt(i + 1), lang)) {
                        i++;
                    } else {
                        break;
                    }
                }
                segments.add(new int[]{start, end});
            } else {
                i++;
            }
        }
        return segments;
    }

    private static List<String> parseTranslation(String answer, int expectedCount, int minMatched) {
        if (answer == null || expectedCount <= 0) {
            return null;
        }
        String[] rows = answer.replace("\r\n", "\n").split("\n");
        String[] result = new String[expectedCount];
        int matched = 0;
        Pattern numbered = Pattern.compile("^\\s*(\\d{1,4})\\s*[:.)|]\\s*(.*)$");
        for (String row : rows) {
            if (row == null) {
                continue;
            }
            row = row.trim();
            if (row.isEmpty()) {
                continue;
            }
            Matcher m = numbered.matcher(row);
            if (m.matches()) {
                try {
                    int idx = Integer.parseInt(m.group(1)) - 1;
                    if (idx >= 0 && idx < expectedCount) {
                        result[idx] = m.group(2).trim();
                        matched++;
                    }
                } catch (Exception ignore) {
                }
            }
        }
        if (matched >= minMatched && matched > 0) {
            List<String> list = new ArrayList<>();
            for (int i = 0; i < expectedCount; i++) {
                list.add(result[i] == null ? "" : result[i]);
            }
            return list;
        }
        if (rows.length == expectedCount) {
            List<String> list = new ArrayList<>();
            for (String row : rows) {
                list.add(row == null ? "" : row.trim());
            }
            return list;
        }
        return null;
    }

    private static List<String> parseSavedTranslation(String saved, List<String> lines) {
        if (saved == null || lines == null || lines.isEmpty()) {
            return null;
        }
        int nonEmpty = 0;
        for (String line : lines) {
            if (!line.isEmpty()) {
                nonEmpty++;
            }
        }
        List<String> numbered = parseTranslation(saved, lines.size(), Math.max(1, nonEmpty / 2));
        if (numbered != null) {
            return numbered;
        }
        String[] parts = saved.replace("\r\n", "\n").split("\n");
        if (parts.length == lines.size()) {
            List<String> list = new ArrayList<>();
            for (String part : parts) {
                list.add(part == null ? "" : part.trim());
            }
            return list;
        }
        if (parts.length == nonEmpty && nonEmpty > 0) {
            List<String> list = new ArrayList<>();
            int p = 0;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).isEmpty()) {
                    list.add("");
                } else {
                    list.add(parts[p++].trim());
                }
            }
            return list;
        }
        return null;
    }

    private void maybeAutoTranslate() {
        if (dismissed || translating || !SharedConfig.playerLyricsTranslate || translatedLines != null || data == null || data.isEmpty()) {
            return;
        }
        List<String> lines = getRenderedLines();
        int nonEmpty = 0;
        for (String line : lines) {
            if (!line.isEmpty()) {
                nonEmpty++;
            }
        }
        if (nonEmpty == 0) {
            return;
        }
        final String lang = targetLanguageCode();
        String saved = getSavedTranslation(currentKey);
        if (saved != null) {
            int sep = saved.indexOf('\u0001');
            String savedLang = sep >= 0 ? saved.substring(0, sep) : null;
            String savedBody = sep >= 0 ? saved.substring(sep + 1) : saved;
            if (lang.equals(savedLang)) {
                List<String> parsed = parseSavedTranslation(savedBody, lines);
                if (parsed != null) {
                    translatedLines = parsed;
                    renderLines();
                    return;
                }
            }
        }
        translating = true;
        final List<String> units = new ArrayList<>();
        final int[] lineUnit = new int[lines.size()];
        final List<List<int[]>> lineSegments = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            lineUnit[i] = -1;
            lineSegments.add(null);
            if (line.isEmpty()) {
                continue;
            }
            boolean hasForeign = false;
            boolean hasNative = false;
            for (int j = 0; j < line.length(); j++) {
                char c = line.charAt(j);
                if (!isLetterChar(c)) {
                    continue;
                }
                if (isNativeLetter(c, lang)) {
                    hasNative = true;
                } else {
                    hasForeign = true;
                }
            }
            if (!hasForeign) {
                continue;
            }
            if (!hasNative) {
                lineUnit[i] = units.size();
                units.add(line);
            } else {
                List<int[]> segments = findForeignSegments(line, lang);
                if (segments.isEmpty()) {
                    continue;
                }
                List<int[]> stored = new ArrayList<>();
                for (int[] segment : segments) {
                    stored.add(new int[]{segment[0], segment[1], units.size()});
                    units.add(line.substring(segment[0], segment[1]));
                }
                lineSegments.set(i, stored);
            }
        }
        if (units.isEmpty()) {
            translating = false;
            return;
        }
        GoogleTranslate.translateLines(units, lang, (translated, error) -> {
            translating = false;
            if (dismissed) {
                return;
            }
            if (error != null || translated == null) {
                Toast.makeText(getContext(), "Перевод лирики: " + (error != null ? error : "нет ответа"), Toast.LENGTH_SHORT).show();
                return;
            }
            List<String> result = new ArrayList<>();
            StringBuilder savedBuilder = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                String value = "";
                if (lineUnit[i] >= 0 && lineUnit[i] < translated.size()) {
                    value = translated.get(lineUnit[i]) == null ? "" : translated.get(lineUnit[i]).trim();
                } else if (lineSegments.get(i) != null) {
                    StringBuilder sb = new StringBuilder(lines.get(i));
                    List<int[]> segments = lineSegments.get(i);
                    for (int s = segments.size() - 1; s >= 0; s--) {
                        int[] segment = segments.get(s);
                        if (segment[2] < translated.size() && translated.get(segment[2]) != null) {
                            String replacement = translated.get(segment[2]).trim();
                            if (!replacement.isEmpty()) {
                                sb.replace(segment[0], segment[1], replacement);
                            }
                        }
                    }
                    value = sb.toString();
                }
                result.add(value);
                if (savedBuilder.length() > 0) {
                    savedBuilder.append('\n');
                }
                savedBuilder.append(value);
            }
            translatedLines = result;
            putSavedTranslation(currentKey, lang + "\u0001" + savedBuilder.toString());
            renderLines();
        });
    }

    private void prepareBlurredBackground(ImageView imageView, Bitmap source) {
        final int targetW = 120;
        final int targetH = Math.max(1, targetW * source.getHeight() / Math.max(1, source.getWidth()));
        final Bitmap scaled = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        new Canvas(scaled).drawBitmap(source, null, new RectF(0, 0, targetW, targetH), paint);
        Utilities.globalQueue.postRunnable(() -> {
            try {
                Utilities.stackBlurBitmap(scaled, 14);
                AndroidUtilities.runOnUIThread(() -> {
                    if (!scaled.isRecycled()) {
                        imageView.setImageBitmap(scaled);
                        imageView.setAlpha(0f);
                        imageView.animate().alpha(1f).setDuration(180).setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                imageView.animate().setListener(null);
                            }
                        }).start();
                    }
                });
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    private static final String CUSTOM_LYRICS_PREFIX = "dgCustomLyrics_";

    public static String getCustomLyrics(String key) {
        if (key == null) {
            return null;
        }
        return MessagesController.getGlobalMainSettings().getString(CUSTOM_LYRICS_PREFIX + Utilities.MD5(key), null);
    }

    public static void putCustomLyrics(String key, String value) {
        if (key == null) {
            return;
        }
        MessagesController.getGlobalMainSettings().edit()
            .putString(CUSTOM_LYRICS_PREFIX + Utilities.MD5(key), value)
            .apply();
    }

    public static String buildKey(String artist, String title) {
        return (artist == null ? "" : cleanForSearch(artist).toLowerCase()) + "|" + (title == null ? "" : cleanForSearch(title).toLowerCase());
    }

    public static LyricsData getCached(String key) {
        if (key == null) {
            return null;
        }
        synchronized (cacheLock) {
            return cache.get(key);
        }
    }

    public static boolean hasLyrics(String key) {
        LyricsData cached = getCached(key);
        return cached != null && !cached.isEmpty();
    }

    private static void putCached(String key, LyricsData value) {
        synchronized (cacheLock) {
            failures.remove(key);
            if (cache.size() >= MAX_CACHE_ENTRIES && !cache.containsKey(key)) {
                java.util.Iterator<String> it = cache.keySet().iterator();
                while (it.hasNext()) {
                    it.next();
                    it.remove();
                    if (cache.size() < MAX_CACHE_ENTRIES) {
                        break;
                    }
                }
            }
            cache.put(key, value);
        }
    }

    private static void putFailure(String key, boolean definitive) {
        synchronized (cacheLock) {
            failures.put(key, new Failure(System.currentTimeMillis(), definitive));
        }
    }

    private static boolean recentlyFailed(String key) {
        synchronized (cacheLock) {
            Failure failure = failures.get(key);
            if (failure == null) {
                return false;
            }
            long ttl = failure.definitive ? MISS_RETRY_MS : ERROR_RETRY_MS;
            if (System.currentTimeMillis() - failure.time < ttl) {
                return true;
            }
            failures.remove(key);
            return false;
        }
    }

    public static void fetchAsync(String key, String artist, String title, Utilities.Callback<LyricsData> onDone) {
        if (key == null) {
            onDone.run(null);
            return;
        }
        LyricsData cached = getCached(key);
        if (cached != null) {
            onDone.run(cached.isEmpty() ? null : cached);
            return;
        }
        if (recentlyFailed(key)) {
            onDone.run(null);
            return;
        }
        ArrayList<Utilities.Callback<LyricsData>> waiting;
        synchronized (cacheLock) {
            waiting = inFlight.get(key);
            if (waiting == null) {
                waiting = new ArrayList<>();
                inFlight.put(key, waiting);
            }
            waiting.add(onDone);
        }
        if (waiting.size() > 1) {
            return;
        }
        new Thread(() -> {
            LyricsData result = null;
            boolean definitive = true;
            String custom = getCustomLyrics(key);
            if (custom != null && !custom.isEmpty()) {
                if (LRC_TIME.matcher(custom).find() && custom.indexOf('[') >= 0 && custom.indexOf(']') > 0) {
                    List<SyncLine> lines = parseLrc(custom);
                    if (!lines.isEmpty()) {
                        Collections.sort(lines, (a, b) -> Long.compare(a.timeMs, b.timeMs));
                        result = new LyricsData();
                        result.lines = lines;
                        result.synced = true;
                    }
                }
                if (result == null) {
                    result = new LyricsData();
                    result.plainText = custom.replace("\r\n", "\n").trim();
                }
            }
            if (result == null) {
                final AtomicReference<LyricsData> winner = new AtomicReference<>();
                final AtomicReference<Throwable> lastError = new AtomicReference<>();
                final CountDownLatch latch = new CountDownLatch(2);
                Runnable lrclib = () -> {
                    try {
                        LyricsData data = runWithRetries("lrclib", () -> requestFromLrclib(artist, title), lastError);
                        if (data != null && !data.isEmpty()) {
                            winner.compareAndSet(null, data);
                        }
                    } catch (Throwable t) {
                        lastError.compareAndSet(null, t);
                        FileLog.e(t);
                    } finally {
                        latch.countDown();
                    }
                };
                Runnable ovh = () -> {
                    try {
                        LyricsData data = runWithRetries("lyrics.ovh", () -> {
                            String plain = requestLyricsOvh(artist, title);
                            if (TextUtils.isEmpty(plain)) {
                                return null;
                            }
                            LyricsData data1 = new LyricsData();
                            data1.plainText = plain;
                            return data1;
                        }, lastError);
                        if (data != null && !data.isEmpty()) {
                            winner.compareAndSet(null, data);
                        }
                    } catch (Throwable t) {
                        lastError.compareAndSet(null, t);
                        FileLog.e(t);
                    } finally {
                        latch.countDown();
                    }
                };
                Thread lrcThread = new Thread(lrclib, "lyrics-lrclib");
                Thread ovhThread = new Thread(ovh, "lyrics-ovh");
                lrcThread.start();
                ovhThread.start();
                try {
                    latch.await(SOURCE_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignore) {
                }
                result = winner.get();
                if (result == null) {
                    Throwable error = lastError.get();
                    definitive = error == null;
                    if (error != null) {
                        FileLog.d("lyrics: all sources failed for '" + key + "': " + error);
                    }
                }
            }
            final LyricsData finalResult = result;
            if (finalResult != null && !finalResult.isEmpty()) {
                putCached(key, finalResult);
            } else {
                putFailure(key, definitive);
            }
            ArrayList<Utilities.Callback<LyricsData>> callbacks;
            synchronized (cacheLock) {
                callbacks = inFlight.remove(key);
            }
            final ArrayList<Utilities.Callback<LyricsData>> toRun = callbacks == null ? new ArrayList<>() : callbacks;
            AndroidUtilities.runOnUIThread(() -> {
                LyricsData value = finalResult != null && !finalResult.isEmpty() ? finalResult : null;
                for (int a = 0; a < toRun.size(); a++) {
                    toRun.get(a).run(value);
                }
            });
        }, "lyrics-fetch").start();
    }

    private interface SourceQuery {
        LyricsData run() throws Exception;
    }

    private static LyricsData runWithRetries(String name, SourceQuery query, AtomicReference<Throwable> lastError) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < SOURCE_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(600);
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            try {
                return query.run();
            } catch (SourceUnavailable e) {
                last = e;
                FileLog.d("lyrics: " + name + " unavailable, retry " + (attempt + 1) + " — " + e.getMessage());
            } catch (Exception e) {
                last = e;
                break;
            }
        }
        if (last != null) {
            lastError.compareAndSet(null, last);
        }
        return null;
    }

    private static LyricsData requestFromLrclib(String artist, String title) throws Exception {
        if (TextUtils.isEmpty(title)) {
            return null;
        }
        String cleanArtist = cleanForSearch(artist);
        String cleanTitle = cleanForSearch(title);

        if (!cleanArtist.isEmpty()) {
            String exactUrl = "https://lrclib.net/api/get?artist_name=" + URLEncoder.encode(cleanArtist, "UTF-8")
                    + "&track_name=" + URLEncoder.encode(cleanTitle, "UTF-8");
            String exactBody = httpGet(exactUrl, "lrclib-get");
            if (exactBody != null) {
                LyricsData data = parseLrclibItem(new JSONObject(exactBody));
                if (data != null) {
                    return data;
                }
            }
        }

        String query = URLEncoder.encode((cleanArtist.isEmpty() ? "" : cleanArtist + " ") + cleanTitle, "UTF-8");
        String body = httpGet("https://lrclib.net/api/search?q=" + query, "lrclib-search");
        if (body == null) {
            return null;
        }
        JSONArray array;
        try {
            array = new JSONArray(body);
        } catch (Exception e) {
            return null;
        }
        JSONObject chosen = null;
        int bestScore = -1;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null || item.optBoolean("instrumental", false)) {
                continue;
            }
            boolean hasAnyLyrics = !TextUtils.isEmpty(item.optString("syncedLyrics", ""))
                    || !TextUtils.isEmpty(item.optString("plainLyrics", ""));
            if (!hasAnyLyrics) {
                continue;
            }
            int score = 0;
            if (matchesTitle(cleanTitle, optAny(item, "trackName", "track_name", "name"))) {
                score += 2;
            }
            if (!cleanArtist.isEmpty() && matchesTitle(cleanArtist, optAny(item, "artistName", "artist_name"))) {
                score++;
            }
            if (!TextUtils.isEmpty(item.optString("syncedLyrics", ""))) {
                score++;
            }
            if (score > bestScore) {
                bestScore = score;
                chosen = item;
            }
            if (bestScore >= 4) {
                break;
            }
        }
        if (chosen == null || bestScore < 2) {
            return null;
        }
        return parseLrclibItem(chosen);
    }

    private static String optAny(JSONObject object, String... names) {
        for (int a = 0; a < names.length; a++) {
            String value = object.optString(names[a], "");
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private static LyricsData parseLrclibItem(JSONObject item) {
        if (item == null) {
            return null;
        }
        LyricsData data = new LyricsData();
        String synced = item.optString("syncedLyrics", "");
        if (!TextUtils.isEmpty(synced)) {
            data.lines = parseLrc(synced);
            Collections.sort(data.lines, (a, b) -> Long.compare(a.timeMs, b.timeMs));
            data.synced = !data.lines.isEmpty();
        }
        if (!data.synced) {
            data.plainText = item.optString("plainLyrics", "").replace("\r\n", "\n").trim();
        }
        return data.isEmpty() ? null : data;
    }

    private static boolean matchesTitle(String expected, String actual) {
        if (TextUtils.isEmpty(expected) || TextUtils.isEmpty(actual)) {
            return false;
        }
        String a = normalizeForCompare(expected);
        String b = normalizeForCompare(actual);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    private static String normalizeForCompare(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9а-яё ]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String httpGet(String url, String name) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);
            connection.setRequestProperty("User-Agent", "Divegram/1.0 (Android)");
            connection.setRequestProperty("Accept", "application/json");
            int code = connection.getResponseCode();
            if (code == 404) {
                return null;
            }
            if (code != 200) {
                throw new SourceUnavailable(name + " http " + code);
            }
            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
            }
            return builder.toString();
        } catch (SourceUnavailable e) {
            throw e;
        } catch (Exception e) {
            throw new SourceUnavailable(name + ": " + e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static List<SyncLine> parseLrc(String lrc) {
        List<SyncLine> lines = new ArrayList<>();
        try {
            for (String raw : lrc.replace("\r\n", "\n").split("\n")) {
                Matcher matcher = LRC_TIME.matcher(raw);
                List<Long> times = new ArrayList<>();
                while (matcher.find()) {
                    long min = Long.parseLong(matcher.group(1));
                    long sec = Long.parseLong(matcher.group(2));
                    String fracStr = matcher.group(3);
                    long frac = 0;
                    if (fracStr != null) {
                        frac = Long.parseLong(fracStr);
                        if (fracStr.length() == 1) {
                            frac *= 100;
                        } else if (fracStr.length() == 2) {
                            frac *= 10;
                        }
                    }
                    times.add(min * 60000L + sec * 1000L + frac);
                }
                if (times.isEmpty()) {
                    continue;
                }
                String text = LRC_TIME.matcher(raw).replaceAll("").trim();
                text = text.replaceAll("\\[[^\\]]*\\]", "").trim();
                if (text.isEmpty()) {
                    continue;
                }
                for (long timeMs : times) {
                    SyncLine line = new SyncLine();
                    line.timeMs = timeMs;
                    line.text = text;
                    lines.add(line);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return lines;
    }

    private static String requestLyricsOvh(String artist, String title) throws Exception {
        if (TextUtils.isEmpty(title)) {
            return null;
        }
        String cleanArtist = cleanForSearch(artist);
        String cleanTitle = cleanForSearch(title);
        if (TextUtils.isEmpty(cleanArtist)) {
            cleanArtist = "Unknown";
        }
        String body = httpGet("https://api.lyrics.ovh/v1/" + URLEncoder.encode(cleanArtist, "UTF-8")
                + "/" + URLEncoder.encode(cleanTitle, "UTF-8"), "lyrics.ovh");
        if (body == null) {
            return null;
        }
        JSONObject object;
        try {
            object = new JSONObject(body);
        } catch (Exception e) {
            return null;
        }
        String lyrics = object.optString("lyrics", "");
        if (!object.has("lyrics")) {
            String message = object.optString("error", object.optString("message", ""));
            if (!TextUtils.isEmpty(message)) {
                throw new SourceUnavailable("lyrics.ovh " + message);
            }
            return null;
        }
        lyrics = lyrics.replace("\r\n", "\n").replace("\r", "\n").trim();
        if (lyrics.isEmpty() || lyrics.toLowerCase(Locale.US).startsWith("error")) {
            return null;
        }
        return lyrics;
    }

    private static String cleanForSearch(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("\\[[^\\]]*\\]", "").replaceAll("\\([^)]*\\)", "").trim();
        cleaned = cleaned.replaceAll("(?i)\\s+(feat|ft|featuring)\\.?\\s+.*$", "").trim();
        if (cleaned.isEmpty()) {
            cleaned = value.trim();
        }
        return cleaned;
    }

    public static Bitmap createFallbackCoverBitmap(int size) {
        if (size <= 0) {
            size = dp(256);
        }
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        float cx = size / 2f;
        float cy = size / 2f;
        float radius = size / 2f;

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0xFF1E1B2E);
        canvas.drawCircle(cx, cy, radius, bgPaint);

        float discR = radius * 0.95f;
        Paint vinylPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        vinylPaint.setColor(0xff15151a);
        canvas.drawCircle(cx, cy, discR, vinylPaint);

        Paint vinylStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        vinylStroke.setStyle(Paint.Style.STROKE);
        vinylStroke.setColor(0x12000000);
        for (int a = 1; a <= 3; a++) {
            vinylStroke.setStrokeWidth(Math.max(1f, dp(1)));
            canvas.drawCircle(cx, cy, discR - dp(2) * a, vinylStroke);
        }
        vinylStroke.setColor(0x0dffffff);
        canvas.drawCircle(cx, cy, discR * 0.62f, vinylStroke);

        vinylPaint.setColor(0xff7C4DFF);
        canvas.drawCircle(cx, cy, discR * 0.36f, vinylPaint);

        vinylPaint.setColor(0xff15151a);
        canvas.drawCircle(cx, cy, discR * 0.12f, vinylPaint);
        return bitmap;
    }

    public static class AlbumProgressView extends View {
        private float progress;
        private long durationMs;
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rectF = new RectF();

        public AlbumProgressView(Context context) {
            super(context);
            trackPaint.setColor(0x33FFFFFF);
            fillPaint.setColor(0xFFFFFFFF);
        }

        public void setDurationMs(long ms) {
            durationMs = ms;
        }

        public void setProgress(float p) {
            progress = Math.max(0f, Math.min(1f, p));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            float r = h / 2f;
            rectF.set(0, 0, w, h);
            canvas.drawRoundRect(rectF, r, r, trackPaint);
            int fillW = (int) (w * progress);
            if (fillW > 0) {
                rectF.set(0, 0, fillW, h);
                canvas.drawRoundRect(rectF, r, r, fillPaint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float p = getWidth() > 0 ? event.getX() / getWidth() : 0f;
                MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
                if (playing != null) {
                    MediaController.getInstance().seekToProgress(playing, Math.max(0f, Math.min(1f, p)));
                }
            }
            return true;
        }
    }

    private static int LYRICS_IMPORT_REQUEST = 17251;
    private static PendingImport pendingImport;

    private static class PendingImport {
        final String key;
        final Utilities.Callback<Boolean> callback;
        PendingImport(String key, Utilities.Callback<Boolean> callback) {
            this.key = key;
            this.callback = callback;
        }
    }

    public static void requestImportLrc(Context context, MessageObject messageObject, Utilities.Callback<Boolean> onDone) {
        if (messageObject == null) {
            if (onDone != null) onDone.run(false);
            return;
        }
        String key = buildKey(messageObject.getMusicAuthor(), messageObject.getMusicTitle());
        pendingImport = new PendingImport(key, onDone);
        NotificationCenter.getGlobalInstance().addObserver(importDelegate, NotificationCenter.onActivityResultReceived);
        try {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            AndroidUtilities.getActivity(context).startActivityForResult(intent, LYRICS_IMPORT_REQUEST);
        } catch (Exception e) {
            FileLog.e(e);
            finishImport(false);
        }
    }

    private static final NotificationCenter.NotificationCenterDelegate importDelegate = new NotificationCenter.NotificationCenterDelegate() {
        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id != NotificationCenter.onActivityResultReceived) {
                return;
            }
            if (args != null && args.length >= 3 && (Integer) args[0] == LYRICS_IMPORT_REQUEST && (Integer) args[1] == Activity.RESULT_OK) {
                Intent intent = (Intent) args[2];
                handleImportFileResult(intent);
            }
        }
    };

    private static void handleImportFileResult(Intent data) {
        if (data == null || data.getData() == null || pendingImport == null) {
            finishImport(false);
            return;
        }
        final Uri uri = data.getData();
        final Context ctx = ApplicationLoader.applicationContext;
        Utilities.globalQueue.postRunnable(() -> {
            String content = null;
            try (InputStream is = ctx.getContentResolver().openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
                content = builder.toString();
            } catch (Exception e) {
                FileLog.e(e);
            }
            final String text = content != null ? content.replace("\r\n", "\n").replace("\r", "\n").trim() : "";
            AndroidUtilities.runOnUIThread(() -> {
                if (text.isEmpty()) {
                    Toast.makeText(ctx, "Не удалось прочитать файл", Toast.LENGTH_SHORT).show();
                    finishImport(false);
                    return;
                }
                boolean isLrc = LRC_TIME.matcher(text).find() && text.indexOf('[') >= 0 && text.indexOf(']') > 0;
                if (!isLrc) {
                    Toast.makeText(ctx, "Выберите файл .lrc", Toast.LENGTH_SHORT).show();
                    finishImport(false);
                    return;
                }
                List<SyncLine> lines = parseLrc(text);
                if (lines.isEmpty()) {
                    Toast.makeText(ctx, "Не удалось разобрать .lrc", Toast.LENGTH_SHORT).show();
                    finishImport(false);
                    return;
                }
                Collections.sort(lines, (a, b) -> Long.compare(a.timeMs, b.timeMs));
                String key = pendingImport != null ? pendingImport.key : null;
                if (key != null) {
                    putCustomLyrics(key, text);
                    LyricsData d = new LyricsData();
                    d.lines = lines;
                    d.synced = true;
                    putCached(key, d);
                }
                Toast.makeText(ctx, "Текст импортирован", Toast.LENGTH_SHORT).show();
                finishImport(true);
            });
        });
    }

    public static void finishImport(boolean success) {
        pendingImport = null;
        NotificationCenter.getGlobalInstance().removeObserver(importDelegate, NotificationCenter.onActivityResultReceived);
    }
}
