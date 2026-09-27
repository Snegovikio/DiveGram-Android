/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import org.telegram.ui.ActionBar.Theme;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextUtils;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Locale;

public class ProfileMusicCardView extends View {

    private static final int CARD_HEIGHT = 78;
    private static final int CARD_MARGIN = 12;
    private static final int CARD_RADIUS = 22;
    private static final int COVER_SIZE = 56;
    private static final int COVER_RADIUS = 12;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backgroundImagePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vinylPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vinylStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path coverClipPath = new Path();
    private final RectF cardRect = new RectF();
    private final RectF coverRect = new RectF();
    private final RectF bitmapRect = new RectF();

    private Bitmap backgroundBitmap;
    private int appliedBlurRadius = -1;
    private boolean blurScheduled;
    private ValueAnimator entranceAnimator;
    private boolean entranceAnimationStarted;
    private float coverScale = 1f;

    private final ImageReceiver imageReceiver = new ImageReceiver(this);

    private Text title;
    private Text artist;
    private Text album;

    private CharSequence rawTitle = "";
    private CharSequence rawArtist = "";
    private CharSequence rawAlbum = "";
    private float appliedTextMaxWidth = -1;

    private void applyTexts() {
        title.setText(rawTitle);
        artist.setText(rawArtist);
        album.setText(rawAlbum);
        if (appliedTextMaxWidth > 0) {
            title.ellipsize(appliedTextMaxWidth);
            artist.ellipsize(appliedTextMaxWidth);
            album.ellipsize(appliedTextMaxWidth);
        }
    }

    private final ButtonBounce bounce = new ButtonBounce(this);

    private final ArrayList<MessageObject> playlist = new ArrayList<>();

    public void setPlaylist(ArrayList<MessageObject> list) {
        playlist.clear();
        if (list != null) {
            playlist.addAll(list);
        }
        MessageObject messageObject = playlist.isEmpty() ? null : playlist.get(0);
        if (messageObject != null) {
            setMusicObject(messageObject);
            int duration = (int) messageObject.getDuration();
            rawAlbum = duration > 0 ? AndroidUtilities.formatDuration(duration, false, false) : "";
        } else {
            rawTitle = "";
            rawArtist = "";
            rawAlbum = "";
            title.setText("");
            artist.setText("");
            album.setText("");
            backgroundBitmap = null;
            imageReceiver.clearImage();
        }
        applyTexts();
        invalidate();
    }

    public ProfileMusicCardView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        imageReceiver.setParentView(this);

        backgroundImagePaint.setFilterBitmap(true);
        coverPaint.setFilterBitmap(true);
        scrimPaint.setColor(0x80000000);

        imageReceiver.setDelegate((imageReceiver, set, thumb, memCache) -> {
            if (set) {
                prepareBackground();
            }
        });

        title = new Text("", 16, AndroidUtilities.bold());
        artist = new Text("", 12);
        album = new Text("", 11);

        backgroundPaint.setStyle(Paint.Style.FILL);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1));

        vinylPaint.setStyle(Paint.Style.FILL);
        vinylStroke.setStyle(Paint.Style.STROKE);
    }

    private void setMusicObject(MessageObject messageObject) {
        backgroundBitmap = null;
        TLRPC.Document document = messageObject != null ? messageObject.getDocument() : null;
        CharSequence author = ProfileMusicView.getAuthor(document);
        CharSequence trackTitle = ProfileMusicView.getTitle(document);
        if (TextUtils.isEmpty(author) && TextUtils.isEmpty(trackTitle)) {
            rawTitle = "";
            rawArtist = "";
        } else {
            rawTitle = TextUtils.isEmpty(trackTitle) ? "" : trackTitle;
            rawArtist = TextUtils.isEmpty(author) ? "" : author;
        }
        applyTexts();
        final int size = Math.max(dp(COVER_SIZE) * 2, 100);
        TLRPC.PhotoSize thumb = document != null ? FileLoader.getClosestPhotoSizeWithSize(document.thumbs, size, false) : null;
        if (thumb instanceof TLRPC.TL_photoSize || thumb instanceof TLRPC.TL_photoSizeProgressive) {
            SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, 0xFF4C9BE8, 0.3f);
            ImageLocation location = ImageLocation.getForDocument(thumb, document);
            imageReceiver.setImage(location, String.format(Locale.US, "%d_%d", size, size), svgThumb, 0, null, document, 0);
        } else if (messageObject != null && messageObject.audioCover != null) {
            imageReceiver.setImageBitmap(messageObject.audioCover);
        } else {
            String artworkUrl = messageObject != null ? MessageObject.getArtworkUrl(document, true) : null;
            if (!TextUtils.isEmpty(artworkUrl)) {
                imageReceiver.setImage(artworkUrl, String.format(Locale.US, "%d_%d", size, size), null, null, -1);
            } else {
                imageReceiver.clearImage();
            }
        }
        invalidate();
    }

    private void prepareBackground() {
        final Bitmap src = imageReceiver.getBitmap();
        if (src == null || src.isRecycled()) {
            backgroundBitmap = null;
            invalidate();
            return;
        }
        appliedBlurRadius = Math.max(1, org.telegram.messenger.SharedConfig.musicCardBlur);
        blurScheduled = false;
        float w = cardRect.width();
        float h = cardRect.height();
        if (w <= 0 || h <= 0) {
            w = Math.min(getWidth() - dp(CARD_MARGIN) * 2, dp(420));
            h = dp(CARD_HEIGHT);
        }
        if (w <= 0 || h <= 0) return;

        final int targetW = 96;
        final int targetH = Math.max(8, (int) (96f * h / w));

        float scale = Math.max(targetW / (float) src.getWidth(), targetH / (float) src.getHeight());
        int sw = Math.min(src.getWidth(), (int) Math.ceil(targetW / scale));
        int sh = Math.min(src.getHeight(), (int) Math.ceil(targetH / scale));
        int sx = (src.getWidth() - sw) / 2;
        int sy = (src.getHeight() - sh) / 2;
        final Rect srcRect = new Rect(sx, sy, sx + sw, sy + sh);
        final RectF dstRect = new RectF(0, 0, targetW, targetH);

        final Bitmap scaled = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        new Canvas(scaled).drawBitmap(src, srcRect, dstRect, p);

        final int radius = Math.max(2, Math.min(30, 2 + org.telegram.messenger.SharedConfig.musicCardBlur / 4));

        Utilities.globalQueue.postRunnable(() -> {
            Utilities.stackBlurBitmap(scaled, radius);
            AndroidUtilities.runOnUIThread(() -> {
                if (!scaled.isRecycled()) {
                    backgroundBitmap = scaled;
                    invalidate();
                }
            });
        });
    }

    private void checkBlurChanged() {
        final int blurRadius = Math.max(1, org.telegram.messenger.SharedConfig.musicCardBlur);
        if (blurRadius != appliedBlurRadius && imageReceiver.hasBitmapImage() && !blurScheduled) {
            blurScheduled = true;
            backgroundBitmap = null;
            invalidate();
            AndroidUtilities.runOnUIThread(this::prepareBackground, 120);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dp(CARD_HEIGHT) + dp(CARD_MARGIN) * 2, MeasureSpec.EXACTLY)
        );
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float cardW = Math.min(w - dp(CARD_MARGIN) * 2, dp(320));
        float textMaxWidth = Math.max(1, cardW - dp(10) - dp(COVER_SIZE) - dp(14) - dp(14));
        if (textMaxWidth != appliedTextMaxWidth) {
            appliedTextMaxWidth = textMaxWidth;
            applyTexts();
        }
        if (backgroundBitmap == null && imageReceiver.hasBitmapImage()) {
            prepareBackground();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        imageReceiver.onAttachedToWindow();
        if (!entranceAnimationStarted) {
            entranceAnimationStarted = true;
            setAlpha(0f);
            coverScale = 0f;
            if (entranceAnimator != null) {
                entranceAnimator.cancel();
            }
            entranceAnimator = ValueAnimator.ofFloat(0f, 1f);
            entranceAnimator.setDuration(420);
            entranceAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            entranceAnimator.addUpdateListener(animation -> {
                float p = (float) animation.getAnimatedValue();
                setAlpha(p);
                coverScale = p;
                invalidate();
            });
            entranceAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    coverScale = 1f;
                    setAlpha(1f);
                    invalidate();
                }
                @Override
                public void onAnimationCancel(android.animation.Animator animation) {
                    coverScale = 1f;
                    setAlpha(1f);
                    invalidate();
                }
            });
            entranceAnimator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        imageReceiver.onDetachedFromWindow();
        if (entranceAnimator != null) {
            entranceAnimator.cancel();
            entranceAnimator = null;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            bounce.setPressed(cardRect.contains(event.getX(), event.getY()));
        } else if (event.getAction() == MotionEvent.ACTION_MOVE && bounce.isPressed()) {
            if (!cardRect.contains(event.getX(), event.getY())) {
                bounce.setPressed(false);
            }
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            bounce.setPressed(false);
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (bounce.isPressed()) {
                performClick();
            }
            bounce.setPressed(false);
        }
        return bounce.isPressed();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        checkBlurChanged();
        final float scale = bounce.getScale(0.02f);

        float cardW = getWidth() - dp(CARD_MARGIN) * 2;
        if (cardW <= 0) {
            cardW = dp(320);
        }
        float cardLeft = (getWidth() - cardW) / 2f;
        cardRect.set(
            cardLeft,
            dp(CARD_MARGIN),
            cardLeft + cardW,
            getHeight() - dp(CARD_MARGIN)
        );
        float textMaxWidth = Math.max(1, cardW - dp(10) - dp(COVER_SIZE) - dp(14) - dp(14));
        if (textMaxWidth != appliedTextMaxWidth) {
            appliedTextMaxWidth = textMaxWidth;
            applyTexts();
        }

        canvas.save();
        canvas.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);

        drawBackground(canvas);
        drawCover(canvas);
        drawTexts(canvas);

        canvas.restore();
    }

    private void drawBackground(Canvas canvas) {
        canvas.save();
        coverClipPath.reset();
        coverClipPath.addRoundRect(cardRect, dp(CARD_RADIUS), dp(CARD_RADIUS), Path.Direction.CW);
        canvas.clipPath(coverClipPath);

        backgroundPaint.setColor(0xff1C1C1E);
        canvas.drawRect(cardRect, backgroundPaint);

        if (backgroundBitmap != null && !backgroundBitmap.isRecycled()) {
            canvas.drawBitmap(backgroundBitmap, null, cardRect, backgroundImagePaint);
            canvas.drawRect(cardRect, scrimPaint);
        } else {
            backgroundPaint.setColor(0xff1E1B2E);
            canvas.drawRect(cardRect, backgroundPaint);
        }
        canvas.restore();

        strokePaint.setColor(0x1FFFFFFF);
        canvas.drawRoundRect(cardRect, dp(CARD_RADIUS), dp(CARD_RADIUS), strokePaint);
    }

    private void drawCover(Canvas canvas) {
        coverRect.set(
            cardRect.left + dp(10),
            cardRect.top + (cardRect.height() - dp(COVER_SIZE)) / 2f,
            cardRect.left + dp(10) + dp(COVER_SIZE),
            cardRect.top + (cardRect.height() - dp(COVER_SIZE)) / 2f + dp(COVER_SIZE)
        );

        canvas.save();
        final float cx = coverRect.centerX();
        final float cy = coverRect.centerY();
        canvas.scale(coverScale, coverScale, cx, cy);
        coverClipPath.reset();
        coverClipPath.addCircle(cx, cy, coverRect.width() / 2f, Path.Direction.CW);
        canvas.clipPath(coverClipPath);

        coverPaint.setShader(null);
        coverPaint.setColor(0xFFE83033);
        canvas.drawCircle(cx, cy, coverRect.width() / 2f, coverPaint);

        if (imageReceiver.hasBitmapImage()) {
            Bitmap bitmap = imageReceiver.getBitmap();
            if (bitmap != null && !bitmap.isRecycled()) {
                Rect srcRect = new Rect();
                float bitmapAspect = (float) bitmap.getWidth() / bitmap.getHeight();
                float coverAspect = 1f;
                if (bitmapAspect > coverAspect) {
                    int srcW = (int) (bitmap.getHeight() * coverAspect);
                    int left = (bitmap.getWidth() - srcW) / 2;
                    srcRect.set(left, 0, left + srcW, bitmap.getHeight());
                } else {
                    int srcH = (int) (bitmap.getWidth() / coverAspect);
                    int top = (bitmap.getHeight() - srcH) / 2;
                    srcRect.set(0, top, bitmap.getWidth(), top + srcH);
                }
                canvas.drawBitmap(bitmap, srcRect, coverRect, coverPaint);
            } else {
                drawFallbackCover(canvas);
            }
        } else {
            drawFallbackCover(canvas);
        }
        canvas.restore();
    }

    private void drawFallbackCover(Canvas canvas) {
        float cx = coverRect.centerX();
        float cy = coverRect.centerY();
        float radius = coverRect.width() / 2f;

        float discR = radius * 0.95f;

        vinylPaint.setColor(0xff15151a);
        canvas.drawCircle(cx, cy, discR, vinylPaint);

        vinylStroke.setColor(0x12000000);
        for (int a = 1; a <= 3; a++) {
            vinylStroke.setStrokeWidth(dp(1));
            canvas.drawCircle(cx, cy, discR - dp(2) * a, vinylStroke);
        }

        vinylStroke.setColor(0x0dffffff);
        canvas.drawCircle(cx, cy, discR * 0.62f, vinylStroke);

        vinylPaint.setColor(0xff7C4DFF);
        canvas.drawCircle(cx, cy, discR * 0.36f, vinylPaint);

        vinylPaint.setColor(0xff15151a);
        canvas.drawCircle(cx, cy, discR * 0.12f, vinylPaint);
    }

    private void drawTexts(Canvas canvas) {
        final float textX = coverRect.right + dp(14);

        title.draw(canvas, textX, cardRect.top + dp(26), Color.WHITE, 1f);
        artist.draw(canvas, textX, cardRect.top + dp(45), 0xB3FFFFFF, 1f);
        album.draw(canvas, textX, cardRect.top + dp(61), 0xB3FFFFFF, 0.8f);
    }
}
