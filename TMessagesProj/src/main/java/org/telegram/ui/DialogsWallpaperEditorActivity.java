/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;

public class DialogsWallpaperEditorActivity extends BaseFragment {

    private static final int MENU_DONE = 1;

    private final Bitmap sourceBitmap;

    private EditorView editorView;
    private SeekBarView blurSlider;
    private TextView blurValue;
    private Bitmap blurredBitmap;
    private boolean computingBlur;
    private int blur = 0;

    public DialogsWallpaperEditorActivity(Bitmap bitmap) {
        sourceBitmap = bitmap;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Редактор обоев списка");
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItemWithWidth(MENU_DONE, R.drawable.ic_ab_done, AndroidUtilities.dp(56));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_DONE) {
                    applyWallpaper();
                }
            }
        });

        fragmentView = new LinearLayout(context);
        LinearLayout rootLayout = (LinearLayout) fragmentView;
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(0xff000000);

        editorView = new EditorView(context);
        rootLayout.addView(editorView, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, 0, 1f));

        LinearLayout bottomBar = new LinearLayout(context);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        GradientDrawable barBackground = new GradientDrawable();
        barBackground.setColor(0xF21B1B1F);
        barBackground.setCornerRadius(AndroidUtilities.dp(14));
        bottomBar.setBackground(barBackground);
        rootLayout.addView(bottomBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 10, 6, 10, 10));

        LinearLayout blurRow = new LinearLayout(context);
        blurRow.setOrientation(LinearLayout.VERTICAL);
        TextView blurTitle = new TextView(context);
        blurTitle.setText("Блюр");
        blurTitle.setTextColor(0xFFFFFFFF);
        blurTitle.setTextSize(15);
        blurTitle.setTypeface(AndroidUtilities.bold());
        blurRow.addView(blurTitle, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        blurSlider = new SeekBarView(context);
        blurSlider.setReportChanges(true);
        LinearLayout.LayoutParams sliderLp = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        sliderLp.topMargin = AndroidUtilities.dp(8);
        blurRow.addView(blurSlider, sliderLp);
        blurValue = new TextView(context);
        blurValue.setTextColor(0xB3FFFFFF);
        blurValue.setTextSize(12);
        blurValue.setGravity(Gravity.RIGHT);
        blurRow.addView(blurValue, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        bottomBar.addView(blurRow, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        blurSlider.setProgress(0);
        blurValue.setText("0%");
        blurSlider.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                blur = Math.round(progress * 100);
                blurValue.setText(blur + "%");
                if (stop) {
                    recomputeBlurredPreview();
                } else {
                    editorView.invalidate();
                }
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {
            }
        });

        TextView applyButton = createRoundedButton(context, "Установить");
        applyButton.setOnClickListener(v -> applyWallpaper());
        LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(46));
        applyLp.topMargin = AndroidUtilities.dp(12);
        bottomBar.addView(applyButton, applyLp);

        TextView hint = new TextView(context);
        hint.setText("Двигайте картинку пальцем, приближайте щипком");
        hint.setTextColor(0x80FFFFFF);
        hint.setTextSize(12);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        hintLp.topMargin = AndroidUtilities.dp(8);
        bottomBar.addView(hint, hintLp);

        return fragmentView;
    }

    private TextView createRoundedButton(Context context, String text) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(0xFFFFFFFF);
        button.setClickable(true);
        button.setFocusable(true);
        int radius = AndroidUtilities.dp(23);
        GradientDrawable normal = new GradientDrawable();
        normal.setShape(GradientDrawable.RECTANGLE);
        normal.setColor(0xFF2F7CF6);
        normal.setCornerRadius(radius);
        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(GradientDrawable.RECTANGLE);
        pressed.setColor(0xFF2565CF);
        pressed.setCornerRadius(radius);
        StateListDrawable stateList = new StateListDrawable();
        stateList.addState(new int[]{android.R.attr.state_pressed}, pressed);
        stateList.addState(new int[]{}, normal);
        button.setBackground(stateList);
        return button;
    }

    private void recomputeBlurredPreview() {
        if (sourceBitmap == null || sourceBitmap.isRecycled()) {
            return;
        }
        if (blur <= 0) {
            blurredBitmap = null;
            editorView.invalidate();
            return;
        }
        if (computingBlur) {
            return;
        }
        computingBlur = true;
        final int radius = 1 + Math.round(blur / 8f);
        Utilities.globalQueue.postRunnable(() -> {
            Bitmap result = null;
            try {
                Bitmap small = Bitmap.createBitmap(Math.max(1, sourceBitmap.getWidth() / 2), Math.max(1, sourceBitmap.getHeight() / 2), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(small);
                Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
                canvas.drawBitmap(sourceBitmap, null, new RectF(0, 0, small.getWidth(), small.getHeight()), paint);
                Utilities.stackBlurBitmap(small, radius);
                result = small;
            } catch (Throwable t) {
                org.telegram.messenger.FileLog.e(t);
            }
            final Bitmap finalResult = result;
            AndroidUtilities.runOnUIThread(() -> {
                computingBlur = false;
                blurredBitmap = finalResult;
                if (editorView != null) {
                    editorView.invalidate();
                }
            });
        });
    }

    private static void drawWithEdgeExtension(Canvas canvas, Bitmap bmp, RectF rect, Paint paint) {
        canvas.drawBitmap(bmp, null, rect, paint);
        if (rect.top > 0.5f) {
            canvas.drawBitmap(bmp, new Rect(0, 0, bmp.getWidth(), 1), new RectF(rect.left, 0, rect.right, rect.top), paint);
        }
        float canvasH = canvas.getHeight();
        if (rect.bottom < canvasH - 0.5f) {
            canvas.drawBitmap(bmp, new Rect(0, bmp.getHeight() - 1, bmp.getWidth(), bmp.getHeight()), new RectF(rect.left, rect.bottom, rect.right, canvasH), paint);
        }
    }

    private void applyWallpaper() {
        if (sourceBitmap == null || sourceBitmap.isRecycled() || editorView == null) {
            return;
        }
        final int viewW = Math.max(1, editorView.getMeasuredWidth());
        final int viewH = Math.max(1, editorView.getMeasuredHeight());
        final int fullH;
        if (fragmentView != null && fragmentView.getHeight() > 0) {
            fullH = fragmentView.getHeight();
        } else {
            fullH = Math.max(viewH, AndroidUtilities.displaySize.y);
        }
        int previewTop = 0;
        if (fragmentView != null) {
            View vp = editorView;
            while (vp != null && vp != fragmentView) {
                previewTop += vp.getTop();
                ViewParent parent = vp.getParent();
                vp = parent instanceof View ? (View) parent : null;
            }
        } else {
            previewTop = (fullH - viewH) / 2;
        }
        final float zoom = Math.max(1f, Math.min(2f, 1440f / viewW));
        final int outW = Math.round(viewW * zoom);
        final int outH = Math.round(fullH * zoom);
        final int outBlur = blur;
        final float outBaseScale = editorView.baseScale * zoom;
        final float outUserScale = editorView.userScale;
        final float outTx = editorView.tx * zoom;
        final float outTy = (editorView.ty + previewTop) * zoom;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(out);
                Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
                float bw = sourceBitmap.getWidth() * outBaseScale * outUserScale;
                float bh = sourceBitmap.getHeight() * outBaseScale * outUserScale;
                float left = (outW - bw) / 2f + outTx;
                float top = (outH - bh) / 2f + outTy;
                drawWithEdgeExtension(canvas, sourceBitmap, new RectF(left, top, left + bw, top + bh), paint);
                if (outBlur > 0) {
                    Bitmap small = Bitmap.createBitmap(Math.max(1, outW / 2), Math.max(1, outH / 2), Bitmap.Config.ARGB_8888);
                    Canvas smallCanvas = new Canvas(small);
                    smallCanvas.drawBitmap(out, null, new RectF(0, 0, small.getWidth(), small.getHeight()), paint);
                    out.recycle();
                    Utilities.stackBlurBitmap(small, 1 + Math.round(outBlur / 8f));
                    out = small;
                }
                java.io.File file = SharedConfig.getDialogsOwnWallpaperFile();
                try (java.io.OutputStream os = new java.io.FileOutputStream(file)) {
                    out.compress(Bitmap.CompressFormat.JPEG, 90, os);
                }
                out.recycle();
                AndroidUtilities.runOnUIThread(() -> {
                    SharedConfig.setDialogsWallpaperScale(100);
                    SharedConfig.setDialogsWallpaperOffsetX(0);
                    SharedConfig.setDialogsWallpaperOffsetY(0);
                    SharedConfig.setDialogsWallpaperBlur(0);
                    SharedConfig.setDialogsOwnWallpaper(true);
                    SharedConfig.setDialogsWallpaper(false);
                    NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.chatPlatesNeedUpdate);
                    Toast.makeText(getParentActivity() != null ? getParentActivity() : getContext(), "Обои списка чатов установлены", Toast.LENGTH_SHORT).show();
                    finishFragment();
                });
            } catch (Throwable t) {
                org.telegram.messenger.FileLog.e(t);
                AndroidUtilities.runOnUIThread(() ->
                        Toast.makeText(getParentActivity() != null ? getParentActivity() : getContext(), "Не удалось сохранить обои: " + t.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private class EditorView extends View {

        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final ScaleGestureDetector scaleDetector;
        private float baseScale = 1f;
        private float userScale = 1f;
        private float tx;
        private float ty;
        private float lastX;
        private float lastY;
        private boolean dragging;
        private int viewW;
        private int viewH;

        EditorView(Context context) {
            super(context);
            scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    float factor = detector.getScaleFactor();
                    float newScale = Math.max(1f, Math.min(4f, userScale * factor));
                    float focusX = detector.getFocusX() - viewW / 2f;
                    float focusY = detector.getFocusY() - viewH / 2f;
                    float scale = baseScale * userScale;
                    float bx = (focusX - tx) / scale;
                    float by = (focusY - ty) / scale;
                    userScale = newScale;
                    scale = baseScale * userScale;
                    tx = focusX - bx * scale;
                    ty = focusY - by * scale;
                    clampTranslate();
                    invalidate();
                    return true;
                }
            });
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            viewW = w;
            viewH = h;
            if (sourceBitmap != null && !sourceBitmap.isRecycled() && w > 0 && h > 0) {
                int fullH = h;
                if (fragmentView != null && fragmentView.getHeight() > 0) {
                    fullH = fragmentView.getHeight();
                }
                baseScale = Math.max(w / (float) sourceBitmap.getWidth(), fullH / (float) sourceBitmap.getHeight());
            }
            clampTranslate();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    dragging = true;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    dragging = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (dragging && event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
                        tx += event.getX() - lastX;
                        ty += event.getY() - lastY;
                        lastX = event.getX();
                        lastY = event.getY();
                        clampTranslate();
                        invalidate();
                    } else if (event.getPointerCount() == 1 && dragging) {
                        lastX = event.getX();
                        lastY = event.getY();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    break;
            }
            return true;
        }

        private void clampTranslate() {
            if (sourceBitmap == null || sourceBitmap.isRecycled() || viewW <= 0 || viewH <= 0) {
                return;
            }
            float scale = baseScale * userScale;
            float maxTx = Math.max(0, (sourceBitmap.getWidth() * scale - viewW) / 2f);
            float maxTy = Math.max(0, (sourceBitmap.getHeight() * scale - viewH) / 2f);
            tx = Math.max(-maxTx, Math.min(maxTx, tx));
            ty = Math.max(-maxTy, Math.min(maxTy, ty));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (sourceBitmap == null || sourceBitmap.isRecycled() || viewW == 0 || viewH == 0) {
                return;
            }
            Bitmap bmp = blur > 0 && blurredBitmap != null && !blurredBitmap.isRecycled() ? blurredBitmap : sourceBitmap;
            float scale = baseScale * userScale * (sourceBitmap.getWidth() / (float) bmp.getWidth());
            float bw = bmp.getWidth() * scale;
            float bh = bmp.getHeight() * scale;
            float left = (viewW - bw) / 2f + tx;
            float top = (viewH - bh) / 2f + ty;
            drawWithEdgeExtension(canvas, bmp, new RectF(left, top, left + bw, top + bh), paint);
            if (!Theme.isCurrentThemeDark()) {
                canvas.drawColor(0xD9EFEFEF);
            }
        }
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }
}
