/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.media.MediaPlayer;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;

public class MarinOverlayView extends View {

    private final Paint heartPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path heartPath = new Path();
    private final ArrayList<Heart> hearts = new ArrayList<>();
    private final Bitmap marinBitmap;

    private boolean active;
    private boolean marinSliding;
    private boolean marinSlidingOut;
    private float marinProgress;
    private float marinOutProgress;
    private float marinExitTimer;
    private float marinX;
    private float marinY;
    private float targetMarinY;
    private int marinW;
    private int marinH;
    private float spawnTimer;
    private MediaPlayer mediaPlayer;

    private static final int MAX_HEARTS = 32;
    private static final int INITIAL_HEART_COUNT = 14;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        private long lastTime;

        @Override
        public void doFrame(long frameTimeNanos) {
            if (!active) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            long dt = lastTime == 0 ? 16 : Math.min(50, now - lastTime);
            lastTime = now;
            update(dt);
            invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private static class Heart {
        float x;
        float y;
        float size;
        float alpha;
        float rotation;
        float rotVelocity;
        float velocity;
    }

    public MarinOverlayView(Context context) {
        super(context);
        heartPaint.setColor(0xFFFF6B81);
        heartPaint.setStyle(Paint.Style.FILL);
        setVisibility(INVISIBLE);
        setWillNotDraw(false);
        marinBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.marin_photoroom);
    }

    public void activate() {
        if (active && !marinSlidingOut) {
            marinExitTimer = HOLD_MS;
            playAudio();
            return;
        }
        active = true;
        setVisibility(VISIBLE);
        bringToFront();
        if (getWidth() == 0 || getHeight() == 0) {
            requestLayout();
        }
        marinSliding = true;
        marinSlidingOut = false;
        marinProgress = 0f;
        marinOutProgress = 0f;
        marinExitTimer = 0f;
        spawnTimer = 0f;
        if (hearts.size() < INITIAL_HEART_COUNT) {
            for (int a = hearts.size(); a < INITIAL_HEART_COUNT && getHeight() > 0; a++) {
                spawnHeart(true);
            }
        }
        playAudio();
        postInvalidate();
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    public void deactivate() {
        if (!active) {
            return;
        }
        active = false;
        hearts.clear();
        stopAudio();
        setVisibility(INVISIBLE);
    }

    private void playAudio() {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer.create(getContext(), R.raw.marin_audio);
            }
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.seekTo(0);
                }
                mediaPlayer.start();
            }
        } catch (Exception ignore) {
        }
    }

    private void stopAudio() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } catch (Exception ignore) {
            mediaPlayer = null;
        }
    }

    private static final int HOLD_MS = 2500;

    private void update(long dt) {
        if (marinSliding) {
            marinProgress += dt / 700f;
            if (marinProgress >= 1f) {
                marinProgress = 1f;
                marinSliding = false;
                marinExitTimer = HOLD_MS;
            }
            float p = new DecelerateInterpolator().getInterpolation(marinProgress);
            marinX = -marinW * 1.2f + (-AndroidUtilities.dp(2) + marinW * 1.2f) * p;
            marinY = targetMarinY;
        } else if (marinExitTimer > 0) {
            marinExitTimer -= dt;
            if (marinExitTimer <= 0) {
                marinSlidingOut = true;
            }
        } else if (marinSlidingOut) {
            marinOutProgress += dt / 550f;
            if (marinOutProgress >= 1f) {
                marinOutProgress = 1f;
                active = false;
                hearts.clear();
                stopAudio();
                setVisibility(INVISIBLE);
                return;
            }
            float p = new DecelerateInterpolator().getInterpolation(marinOutProgress);
            marinX = -AndroidUtilities.dp(2) + (-marinW * 1.2f + AndroidUtilities.dp(2)) * p;
            marinY = targetMarinY;
        }

        spawnTimer -= dt;
        if (spawnTimer <= 0) {
            spawnTimer = 140 + Utilities.fastRandom.nextInt(200);
            spawnHeart(false);
        }

        for (int a = 0; a < hearts.size(); a++) {
            Heart h = hearts.get(a);
            h.y -= h.velocity * dt;
            h.rotation += h.rotVelocity * dt;
            float fromY = getHeight() + h.size;
            float toY = -h.size;
            float progress = (fromY - h.y) / (fromY - toY);
            if (progress >= 1f) {
                hearts.remove(a);
                a--;
                continue;
            }
            progress = Math.max(0f, progress);
            h.alpha = (float) Math.sin(Math.PI * Math.min(1f, Math.max(0f, progress)));
            if (progress > 0.6f) {
                float fade = 1f - (progress - 0.6f) / 0.4f;
                h.alpha *= fade * fade;
            }
        }
    }

    private void spawnHeart(boolean fullHeight) {
        if (hearts.size() >= MAX_HEARTS) {
            return;
        }
        Heart h = new Heart();
        float w = getWidth();
        float hgt = getHeight();
        h.x = AndroidUtilities.dp(16) + Math.max(0, w - AndroidUtilities.dp(32)) * Utilities.fastRandom.nextFloat();
        h.size = AndroidUtilities.dp(12 + Utilities.fastRandom.nextInt(22));
        h.velocity = AndroidUtilities.dp(0.55f + Utilities.fastRandom.nextFloat() * 0.45f);
        h.rotation = Utilities.fastRandom.nextInt(360);
        h.rotVelocity = (Utilities.fastRandom.nextBoolean() ? 1 : -1) * (0.04f + Utilities.fastRandom.nextFloat() * 0.08f);
        h.y = fullHeight ? 0f : hgt + h.size;
        if (fullHeight) {
            h.y = hgt - (hgt * (0.15f + 0.85f * hearts.size() / MAX_HEARTS));
        }
        h.alpha = 0f;
        hearts.add(h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        updateMarinGeometry(h);
    }

    private void updateMarinGeometry(int viewHeight) {
        if (marinBitmap == null || marinBitmap.isRecycled()) {
            return;
        }
        int screenHeight = Math.max(0, AndroidUtilities.displaySize.y);
        marinH = Math.min(screenHeight, AndroidUtilities.dp(150));
        if (marinH <= 0) {
            return;
        }
        float aspect = marinBitmap.getHeight() / (float) marinBitmap.getWidth();
        marinW = (int) (marinH / aspect);
        targetMarinY = screenHeight - marinH - AndroidUtilities.dp(26);
        if (viewHeight > 0) {
            targetMarinY = Math.min(targetMarinY, Math.max(0, viewHeight - marinH));
        }
        if (targetMarinY < 0) {
            targetMarinY = 0;
        }
        marinX = -marinW;
        marinY = targetMarinY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        for (int a = 0; a < hearts.size(); a++) {
            Heart h = hearts.get(a);
            canvas.save();
            canvas.translate(h.x, h.y);
            canvas.rotate(h.rotation);
            heartPath.reset();
            float s = h.size;
            heartPath.moveTo(0, s * 0.35f);
            heartPath.cubicTo(s * 0.9f, -s * 0.3f, s * 1.4f, s * 0.35f, 0, s * 1.1f);
            heartPath.cubicTo(-s * 1.4f, s * 0.35f, -s * 0.9f, -s * 0.3f, 0, s * 0.35f);
            heartPath.close();
            heartPaint.setAlpha((int) (255 * h.alpha));
            canvas.drawPath(heartPath, heartPaint);
            canvas.restore();
        }

if (marinBitmap != null && !marinBitmap.isRecycled()) {
            if (marinW <= 0) {
                updateMarinGeometry(getHeight());
            }
            if (marinY <= 0 && !marinSliding) {
                marinX = -AndroidUtilities.dp(2);
                marinY = targetMarinY;
            }
            if (marinW > 0) {
                canvas.drawBitmap(marinBitmap, null, new android.graphics.RectF(marinX, marinY, marinX + marinW, marinY + marinH), null);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        active = false;
        hearts.clear();
        stopAudio();
    }
}