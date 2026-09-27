/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui.Components;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashMap;
import java.util.Map;

public class LyricsAlbumEditorActivity extends Activity {

    private EditorView editorView;
    private android.widget.TextView resetButton;

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow().getDecorView().setSystemUiVisibility(flags);

        FrameLayout root = new FrameLayout(this);
        editorView = new EditorView(this);
        root.addView(editorView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        resetButton = new android.widget.TextView(this);
        resetButton.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        resetButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        resetButton.setTextColor(0xFFFFFFFF);
        resetButton.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        resetButton.setBackground(org.telegram.ui.ActionBar.Theme.createRoundRectDrawable(AndroidUtilities.dp(20), 0x33FFFFFF));
        resetButton.setText("Сбросить по умолчанию");
        resetButton.setOnClickListener(v -> {
            SharedConfig.resetAlbumLayout();
            editorView.resetTo(SharedConfig.albumCoverX, SharedConfig.albumCoverY, SharedConfig.albumCoverSize,
                SharedConfig.albumLyricsX, SharedConfig.albumLyricsY, SharedConfig.albumLyricsWidth);
            Toast.makeText(this, "Сброшено", Toast.LENGTH_SHORT).show();
        });
        root.addView(resetButton, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT));
        ((FrameLayout.LayoutParams) resetButton.getLayoutParams()).setMargins(0, AndroidUtilities.dp(40), AndroidUtilities.dp(16), 0);
        setContentView(root);
    }

    @Override
    public void onBackPressed() {
        editorView.save();
        Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show();
        super.onBackPressed();
    }

    private static class Element {
        float x, y, sizeOrW;
        final boolean isLyrics;
        Element(boolean lyrics, float x, float y, float s) {
            isLyrics = lyrics;
            this.x = x;
            this.y = y;
            this.sizeOrW = s;
        }
    }

    private class EditorView extends View {
        private final Bitmap coverBitmap;
        private final Paint coverPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Element cover = new Element(false,
            SharedConfig.albumCoverX, SharedConfig.albumCoverY, SharedConfig.albumCoverSize);
        private final Element lyrics = new Element(true,
            SharedConfig.albumLyricsX, SharedConfig.albumLyricsY, SharedConfig.albumLyricsWidth);

        private static final int NONE = 0, DRAG = 1, RESIZE = 2;
        private int mode = NONE;
        private Element active;
        private int activePointerId = -1;
        private float lastX, lastY;
        private float pinchStartDist;
        private float pinchStartSize;

        EditorView(Context context) {
            super(context);
            coverBitmap = LyricsSheet.createFallbackCoverBitmap(AndroidUtilities.dp(256));
            bgPaint.setColor(0xFF1E1B2E);
            textPaint.setColor(0xE6FFFFFF);
            textPaint.setTextSize(AndroidUtilities.dp(22));
            textPaint.setTypeface(AndroidUtilities.bold());
            hintPaint.setColor(0xB3FFFFFF);
            hintPaint.setTextSize(AndroidUtilities.dp(13));
            handlePaint.setStyle(Paint.Style.STROKE);
            handlePaint.setColor(0x88FFFFFF);
            handlePaint.setStrokeWidth(AndroidUtilities.dp(2));
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        private int coverSide() {
            int min = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
            return Math.max(AndroidUtilities.dp(60), (int) (cover.sizeOrW * min));
        }

        private RectF coverRect() {
            int side = coverSide();
            float cx = cover.x * getWidth();
            float cy = cover.y * getHeight();
            return new RectF(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f);
        }

        private RectF lyricsRect() {
            float cx = lyrics.x * getWidth();
            float cy = lyrics.y * getHeight();
            float w = lyrics.sizeOrW * AndroidUtilities.displaySize.x;
            float h = getHeight();
            return new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

            RectF cr = coverRect();
            if (coverBitmap != null && !coverBitmap.isRecycled()) {
                canvas.drawBitmap(coverBitmap, null, cr, coverPaint);
            }
            canvas.drawRect(cr, handlePaint);
            canvas.drawText("Обложка", cr.left, cr.top - AndroidUtilities.dp(4), hintPaint);

            RectF lr = lyricsRect();
            canvas.save();
            canvas.clipRect(lr);
            String sample = "Текст песни\nДвигайте и меняйте размер\nпальцами как в играх";
            float ty = lr.top + AndroidUtilities.dp(8);
            for (String line : sample.split("\n")) {
                canvas.drawText(line, lr.left + AndroidUtilities.dp(8), ty, textPaint);
                ty += AndroidUtilities.dp(28);
            }
            canvas.restore();
            canvas.drawRect(lr, handlePaint);
            canvas.drawText("Текст", lr.left, lr.top - AndroidUtilities.dp(4), hintPaint);

            String help = mode == RESIZE ? "Масштабирование" : "Перетаскивание";
            canvas.drawText(help, AndroidUtilities.dp(16), getHeight() - AndroidUtilities.dp(16), hintPaint);
            canvas.drawText("Назад — сохранить", getWidth() - AndroidUtilities.dp(180), getHeight() - AndroidUtilities.dp(16), hintPaint);
        }

        private Element hitTest(float x, float y) {
            if (coverRect().contains(x, y)) return cover;
            if (lyricsRect().contains(x, y)) return lyrics;
            return null;
        }

        private float dist(MotionEvent e, int a, int b) {
            float dx = e.getX(a) - e.getX(b);
            float dy = e.getY(a) - e.getY(b);
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int action = e.getActionMasked();
            int W = getWidth(), H = getHeight();
            int minDim = Math.min(W, H);

            if (action == MotionEvent.ACTION_DOWN) {
                active = hitTest(e.getX(), e.getY());
                if (active != null) {
                    mode = DRAG;
                    activePointerId = e.getPointerId(0);
                    lastX = e.getX();
                    lastY = e.getY();
                }
            } else if (action == MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount() == 2 && active != null) {
                mode = RESIZE;
                pinchStartDist = dist(e, 0, 1);
                pinchStartSize = active.sizeOrW;
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (mode == DRAG && active != null) {
                    float dx = (e.getX() - lastX) / W;
                    float dy = (e.getY() - lastY) / H;
                    active.x = clamp(active.x + dx, 0, 0.98f);
                    active.y = clamp(active.y + dy, 0, 0.98f);
                    lastX = e.getX();
                    lastY = e.getY();
                    invalidate();
                } else if (mode == RESIZE && active != null && e.getPointerCount() >= 2) {
                    float d = dist(e, 0, 1);
                    float scale = d / Math.max(1f, pinchStartDist);
                    float ns = pinchStartSize * scale;
                    active.sizeOrW = clamp(ns, 0.1f, 1.2f);
                    invalidate();
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                if (e.getPointerCount() <= 2) {
                    mode = NONE;
                    active = null;
                    activePointerId = -1;
                }
            }
            return true;
        }

        private float clamp(float v, float lo, float hi) {
            return Math.max(lo, Math.min(hi, v));
        }

        void save() {
            SharedConfig.setAlbumLayout(
                cover.x, cover.y, cover.sizeOrW,
                lyrics.x, lyrics.y, lyrics.sizeOrW);
        }

        void resetTo(float cx, float cy, float cs, float lx, float ly, float lw) {
            cover.x = cx;
            cover.y = cy;
            cover.sizeOrW = cs;
            lyrics.x = lx;
            lyrics.y = ly;
            lyrics.sizeOrW = lw;
            invalidate();
        }
    }
}
