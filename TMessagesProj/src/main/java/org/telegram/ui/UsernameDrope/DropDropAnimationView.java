/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui.UsernameDrope;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class DropDropAnimationView extends View {

    public static final int STATE_LISTEN = 0;
    public static final int STATE_MERGE = 1;
    public static final int STATE_DONE = 2;
    public static final int STATE_ERROR = 3;

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private int colorMain;
    private int colorAccent;
    private int colorSuccess;
    private int colorError;

    private int state = STATE_LISTEN;
    private long mergeStart = -1;
    private long stateSince;
    private ValueAnimator animator;

    public DropDropAnimationView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(AndroidUtilities.dp(2.5f));
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        bodyPaint.setStyle(Paint.Style.FILL);
        glowPaint.setStyle(Paint.Style.FILL);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(AndroidUtilities.dp(3));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setStyle(Paint.Style.STROKE);
        checkPaint.setStrokeWidth(AndroidUtilities.dp(6));
        checkPaint.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setStrokeJoin(Paint.Join.ROUND);

        flashPaint.setStyle(Paint.Style.FILL);

        ripplePaint.setStyle(Paint.Style.STROKE);
        ripplePaint.setStrokeWidth(AndroidUtilities.dp(3));
        ripplePaint.setStrokeCap(Paint.Cap.ROUND);

        colorMain = Theme.getColor(Theme.key_avatar_backgroundCyan);
        colorAccent = Theme.getColor(Theme.key_avatar_backgroundBlue);
        colorSuccess = Theme.getColor(Theme.key_avatar_backgroundGreen);
        colorError = Theme.getColor(Theme.key_text_RedBold);

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1600);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(animation -> invalidate());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!animator.isStarted()) {
            animator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        animator.cancel();
    }

    public void setState(int newState, boolean restartMerge) {
        if (state != newState) {
            state = newState;
            stateSince = SystemClock.uptimeMillis();
        }
        if (newState == STATE_MERGE && restartMerge) {
            mergeStart = -1;
        }
        invalidate();
    }

    public int getState() {
        return state;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        if (state == STATE_LISTEN) {
            drawRadar(canvas, cx, cy);
            drawCentralPhone(canvas, cx, cy);
        } else if (state == STATE_MERGE) {
            drawMerge(canvas, cx, cy);
        } else if (state == STATE_DONE) {
            drawDone(canvas, cx, cy);
        } else {
            drawError(canvas, cx, cy);
        }
    }

    private void drawRadar(Canvas canvas, float cx, float cy) {
        long now = SystemClock.uptimeMillis();
        float base = Math.min(getWidth(), getHeight());
        for (int i = 0; i < 3; i++) {
            float phase = ((now + (long) (i * 480)) % 2100) / 2100f;
            float radius = base * (0.12f + phase * 0.34f);
            int alpha = (int) (90 * (1f - phase));
            ringPaint.setColor(colorMain);
            ringPaint.setAlpha(alpha);
            canvas.drawCircle(cx, cy, radius, ringPaint);
        }
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 500.0);
        glowPaint.setColor(colorAccent);
        glowPaint.setAlpha((int) (28 + 26 * pulse));
        canvas.drawCircle(cx, cy, base * 0.13f, glowPaint);

        float r1 = base * 0.2f, r2 = base * 0.27f;
        linePaint.setColor(colorMain);
        for (int i = 0; i < 2; i++) {
            float phase = ((now + (long) (i * 850)) % 2100) / 2100f;
            float angle = (float) (phase * 2 * Math.PI);
            float r = i == 0 ? r1 : r2;
            float ox = cx + (float) Math.cos(angle) * r;
            float oy = cy + (float) Math.sin(angle) * r;
            int alpha = i == 0 ? 200 : 120;
            linePaint.setAlpha(alpha);
            canvas.drawCircle(ox, oy, AndroidUtilities.dp(i == 0 ? 3.5f : 2.5f), linePaint);
        }
    }

    private void drawCentralPhone(Canvas canvas, float cx, float cy) {
        long now = SystemClock.uptimeMillis();
        float pulse = 1f + 0.06f * (float) Math.sin(now / 380.0);
        drawPhone(canvas, cx, cy, pulse, colorAccent, 255);
    }

    private void drawMerge(Canvas canvas, float cx, float cy) {
        long now = SystemClock.uptimeMillis();
        if (mergeStart < 0) {
            mergeStart = now;
        }
        float elapsed = now - mergeStart;
        float base = Math.min(getWidth(), getHeight());
        float halfW = base * 0.24f;

        float approachT = Math.min(1f, elapsed / 500f);
        float eased = approachT * approachT * (3 - 2 * approachT);

        float x1 = cx - halfW * (1f - eased);
        float x2 = cx + halfW * (1f - eased);
        drawPhone(canvas, x1, cy, 1f, colorAccent, 255);
        drawPhone(canvas, x2, cy, 1f, colorMain, 255);

        if (approachT < 1f) {
            return;
        }

        float beamElapsed = elapsed - 500f;

        float flashT = Math.min(1f, beamElapsed / 400f);
        float flashAlpha = flashT < 0.25f
                ? flashT / 0.25f
                : Math.max(0f, 1f - (flashT - 0.25f) / 0.75f);
        float flashRadius = base * (0.05f + flashT * 0.35f);
        int flashColor = 0xFFE0EAFF;
        flashPaint.setColor(flashColor);
        flashPaint.setAlpha((int) (220 * flashAlpha));
        canvas.drawCircle(cx, cy, flashRadius, flashPaint);

        float glowAlpha = flashAlpha * 0.6f;
        glowPaint.setColor(0xFFFFFFFF);
        glowPaint.setAlpha((int) (180 * glowAlpha));
        canvas.drawCircle(cx, cy, flashRadius * 0.45f, glowPaint);

        int rippleCount = 4;
        for (int i = 0; i < rippleCount; i++) {
            float delay = i * 200f;
            float rT = beamElapsed - delay;
            if (rT < 0) continue;
            float rPhase = Math.min(1f, rT / 1200f);
            float rRadius = base * (0.08f + rPhase * 0.42f);
            int rAlpha = (int) (160 * (1f - rPhase) * (1f - flashAlpha * 0.5f));
            if (rAlpha <= 0) continue;
            ripplePaint.setColor(i % 2 == 0 ? 0xFF64B5F6 : 0xFF90CAF9);
            ripplePaint.setAlpha(rAlpha);
            ripplePaint.setStrokeWidth(AndroidUtilities.dp(3f - 1.5f * rPhase));
            canvas.drawCircle(cx, cy, rRadius, ripplePaint);
        }

        float pulse = 1f + 0.15f * (float) Math.sin(now / 200.0);
        float phoneAlpha = Math.min(1f, beamElapsed / 300f);
        glowPaint.setColor(colorAccent);
        glowPaint.setAlpha((int) (50 * phoneAlpha));
        canvas.drawCircle(cx, cy, base * 0.14f * pulse, glowPaint);
        drawPhone(canvas, cx, cy, pulse, colorAccent, 255);

        if (beamElapsed > 50f && beamElapsed < 800f) {
            float rayAlpha = Math.min(1f, (beamElapsed - 50f) / 200f)
                    * (1f - Math.min(1f, (beamElapsed - 500f) / 300f));
            if (rayAlpha > 0f) {
                linePaint.setColor(0xFFB3D4FC);
                linePaint.setAlpha((int) (100 * rayAlpha));
                linePaint.setStrokeWidth(AndroidUtilities.dp(2));
                int rayCount = 12;
                for (int i = 0; i < rayCount; i++) {
                    float angle = (float) (i * Math.PI * 2 / rayCount + now / 800.0);
                    float innerR = base * 0.06f;
                    float outerR = base * (0.18f + rayAlpha * 0.2f);
                    canvas.drawLine(
                            cx + (float) Math.cos(angle) * innerR,
                            cy + (float) Math.sin(angle) * innerR,
                            cx + (float) Math.cos(angle) * outerR,
                            cy + (float) Math.sin(angle) * outerR,
                            linePaint
                    );
                }
            }
        }
    }

    private void drawDone(Canvas canvas, float cx, float cy) {
        long now = SystemClock.uptimeMillis();
        float base = Math.min(getWidth(), getHeight());

        for (int i = 0; i < 3; i++) {
            float wavePhase = ((now + (long) (i * 600)) % 2000) / 2000f;
            float radius = base * (0.15f + wavePhase * 0.35f);
            int alpha = (int) (70 * (1f - wavePhase));
            ripplePaint.setColor(0xFF90CAF9);
            ripplePaint.setAlpha(alpha);
            ripplePaint.setStrokeWidth(AndroidUtilities.dp(2));
            canvas.drawCircle(cx, cy, radius, ripplePaint);
        }

        float s = base * 0.14f;
        float pulse = 1f + 0.06f * (float) Math.sin(now / 400.0);
        bodyPaint.setColor(colorSuccess);
        bodyPaint.setAlpha((int) (60 + 40 * (float) Math.sin(now / 400.0)));
        canvas.drawCircle(cx, cy, s * pulse * 1.2f, bodyPaint);

        checkPaint.setColor(0xFFFFFFFF);
        float r = s * 0.62f;
        float startX = cx - r * 0.45f, startY = cy + r * 0.05f;
        float midX = cx - r * 0.12f, midY = cy + r * 0.32f;
        float endX = cx + r * 0.5f, endY = cy - r * 0.28f;
        path.reset();
        path.moveTo(startX, startY);
        path.lineTo(midX, midY);
        path.lineTo(endX, endY);
        canvas.drawPath(path, checkPaint);
    }

    private void drawError(Canvas canvas, float cx, float cy) {
        long now = SystemClock.uptimeMillis();
        float base = Math.min(getWidth(), getHeight());
        float s = base * 0.13f;
        float shake = (float) Math.sin(now / 90.0) * AndroidUtilities.dp(1.5f);
        float alpha = 165 + 90 * (0.5f + 0.5f * (float) Math.sin(now / 300.0));
        linePaint.setColor(colorError);
        linePaint.setAlpha((int) alpha);
        linePaint.setStrokeWidth(AndroidUtilities.dp(7));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        float x = cx + shake;
        canvas.drawLine(x - s * 0.5f, cy - s * 0.5f, x + s * 0.5f, cy + s * 0.5f, linePaint);
        canvas.drawLine(x + s * 0.5f, cy - s * 0.5f, x - s * 0.5f, cy + s * 0.5f, linePaint);
    }

    private void drawPhone(Canvas canvas, float cx, float cy, float scale, int color, int alpha) {
        float w = AndroidUtilities.dp(56) * scale;
        float h = AndroidUtilities.dp(104) * scale;
        float left = cx - w / 2f;
        float top = cy - h / 2f;
        float radius = AndroidUtilities.dp(10) * scale;
        bodyPaint.setColor(color);
        bodyPaint.setAlpha(alpha);
        canvas.drawRoundRect(left, top, left + w, top + h, radius, radius, bodyPaint);
        float sW = AndroidUtilities.dp(12) * scale;
        float sH = AndroidUtilities.dp(2.5f) * scale;
        bodyPaint.setColor(0xFF000000);
        bodyPaint.setAlpha(70);
        canvas.drawRoundRect(cx - sW / 2f, top + AndroidUtilities.dp(8) * scale, cx + sW / 2f, top + AndroidUtilities.dp(8) * scale + sH, sH / 2f, sH / 2f, bodyPaint);
        float bW = AndroidUtilities.dp(12) * scale;
        bodyPaint.setAlpha(60);
        canvas.drawRoundRect(cx - bW / 2f, top + h - AndroidUtilities.dp(10) * scale, cx + bW / 2f, top + h - AndroidUtilities.dp(7) * scale, sH / 2f, sH / 2f, bodyPaint);
    }
}