package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.DiveGramAvatarShapes;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

public class DiveGramAvatarShapeActivity extends BaseFragment {

    private PreviewAvatarView previewView;
    private ShapeAdapter adapter;
    private int selectedShape;

    @Override
    public View createView(Context context) {
        selectedShape = DiveGramAvatarShapes.getSelected();

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Форма аватарки");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        final FrameLayout root = (FrameLayout) fragmentView;

        final LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        final FrameLayout previewCard = new FrameLayout(context);
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cardBackground.setCornerRadius(AndroidUtilities.dp(24));
        previewCard.setBackground(cardBackground);

        previewView = new PreviewAvatarView(context);
        previewCard.addView(previewView, LayoutHelper.createFrame(140, 140, Gravity.CENTER));
        content.addView(previewCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 220, 12, 12, 12, 0));

        final TextView hint = new TextView(context);
        hint.setText("Выберите форму аватарки");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        hint.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(14), AndroidUtilities.dp(24), AndroidUtilities.dp(6));
        content.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        adapter = new ShapeAdapter(context);
        RecyclerListView grid = new RecyclerListView(context);
        grid.setLayoutManager(new GridLayoutManager(context, 3));
        grid.setAdapter(adapter);
        grid.setVerticalScrollBarEnabled(false);
        grid.setOnItemClickListener((view, position) -> selectShape(position));
        content.addView(grid, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        root.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        previewView.setUser(getUserConfig().getCurrentUser());
        updatePreviewOutline();
        return fragmentView;
    }

    private void selectShape(int shape) {
        if (shape < 0 || shape >= DiveGramAvatarShapes.COUNT) {
            return;
        }
        selectedShape = shape;
        DiveGramAvatarShapes.setSelected(shape);
        getNotificationCenter().postNotificationName(NotificationCenter.diveGramAvatarShapeChanged);
        if (previewView != null) {
            previewView.invalidate();
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void updatePreviewOutline() {
        if (previewView != null) {
            previewView.invalidate();
        }
    }

    private class PreviewAvatarView extends BackupImageView {

        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();

        PreviewAvatarView(Context context) {
            super(context);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(AndroidUtilities.dp(1));
            strokePaint.setColor(Theme.getColor(Theme.key_divider));
        }

        void setUser(TLRPC.User user) {
            if (user != null) {
                setForUserOrChat(user, new AvatarDrawable(user));
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final int w = getMeasuredWidth();
            final int h = getMeasuredHeight();
            rect.set(0, 0, w, h);
            DiveGramAvatarShapes.buildPath(path, selectedShape, rect);
            canvas.save();
            canvas.clipPath(path);
            super.onDraw(canvas);
            canvas.restore();
            canvas.drawPath(path, strokePaint);
        }
    }

    private class ShapeAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        ShapeAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            return DiveGramAvatarShapes.COUNT;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new ShapeCell(mContext));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((ShapeCell) holder.itemView).setShape(position, position == selectedShape);
        }
    }

    private static class ShapeCell extends LinearLayout {

        private final ShapeBlobView blobView;
        private final TextView nameView;

        ShapeCell(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8));

            blobView = new ShapeBlobView(context);
            addView(blobView, LayoutHelper.createLinear(72, 72, Gravity.CENTER_HORIZONTAL));

            nameView = new TextView(context);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            nameView.setGravity(Gravity.CENTER);
            nameView.setMaxLines(2);
            nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
            addView(nameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
        }

        void setShape(int shape, boolean selected) {
            blobView.setShape(shape, selected);
            nameView.setText(DiveGramAvatarShapes.NAMES[shape]);
            nameView.setTextColor(Theme.getColor(selected ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_windowBackgroundWhiteGrayText3));
        }
    }

    private static class ShapeBlobView extends View {

        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();
        private int shape;
        private boolean selected;

        ShapeBlobView(Context context) {
            super(context);
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(AndroidUtilities.dp(2));
            strokePaint.setColor(Theme.getColor(Theme.key_switchTrackChecked));
        }

        void setShape(int shape, boolean selected) {
            this.shape = shape;
            this.selected = selected;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float pad = AndroidUtilities.dp(2);
            rect.set(pad, pad, getWidth() - pad, getHeight() - pad);
            DiveGramAvatarShapes.buildPath(path, shape, rect);
            canvas.drawPath(path, fillPaint);
            if (selected) {
                canvas.drawPath(path, strokePaint);
            }
        }
    }
}
