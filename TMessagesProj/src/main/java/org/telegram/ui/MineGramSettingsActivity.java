/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.method.LinkMovementMethod;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Random;

public class MineGramSettingsActivity extends BaseFragment {

    private static final int[] ICON_EASTER_EGG_DRAWABLES = {
            R.drawable.photo_6, R.drawable.photo_7, R.drawable.photo_8,
            R.drawable.photo_9, R.drawable.photo_10
    };
    private int iconTapCount;
    private boolean iconEasterEggActive;
    private int linksTapCount;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("DiveGram");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        frameLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 16, 0, 16));

        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("easter_egg", Context.MODE_PRIVATE);
        iconEasterEggActive = prefs.getBoolean("dg_icon_active", false);

        ImageView iconView = new ImageView(context);
        if (iconEasterEggActive) {
            iconView.setImageResource(prefs.getInt("dg_icon_res", R.drawable.photo_6));
        } else {
            iconView.setImageResource(R.mipmap.icon_1_foreground);
        }
        iconView.setOnClickListener(v -> {
            iconTapCount++;
            if (iconTapCount >= 5) {
                iconTapCount = 0;
                iconEasterEggActive = !iconEasterEggActive;
                SharedPreferences.Editor editor = prefs.edit();
                if (iconEasterEggActive) {
                    int res = ICON_EASTER_EGG_DRAWABLES[new Random().nextInt(ICON_EASTER_EGG_DRAWABLES.length)];
                    iconView.setImageResource(res);
                    editor.putBoolean("dg_icon_active", true);
                    editor.putInt("dg_icon_res", res);
                } else {
                    iconView.setImageResource(R.mipmap.icon_1_foreground);
                    editor.putBoolean("dg_icon_active", false);
                }
                editor.apply();
            }
        });
        linearLayout.addView(iconView, LayoutHelper.createLinear(96, 96, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));

        TextView appNameView = new TextView(context);
        appNameView.setText("DiveGram");
        appNameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        appNameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        appNameView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        appNameView.setGravity(Gravity.CENTER);
        linearLayout.addView(appNameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 8));

        TextView subtitleView = new TextView(context);
        subtitleView.setText("Мессенджер на базе Telegram");
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setGravity(Gravity.CENTER);
        linearLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 20));

        TextCell settingsCell = new TextCell(context);
        settingsCell.setText("Настройки", false);
        settingsCell.setSubtitle("Все фичи DiveGram");
        settingsCell.setOnClickListener(v -> presentFragment(new DiveGramFeaturesActivity()));
        linearLayout.addView(createRoundedCard(context, settingsCell));

        TextView linksHeaderView = new TextView(context);
        linksHeaderView.setText("Ссылки");
        linksHeaderView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        linksHeaderView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        linksHeaderView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        linksHeaderView.setPadding(AndroidUtilities.dp(21), 0, 0, 0);
        linksHeaderView.setOnClickListener(v -> {
            linksTapCount++;
            if (linksTapCount >= 3) {
                linksTapCount = 0;
                showSupportDialog(context);
            }
        });
        linearLayout.addView(linksHeaderView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 8));

        TextCell channelCell = new TextCell(context);
        channelCell.setText("Основной", false);
        channelCell.setSubtitle("@divegram — Telegram-канал проекта");
        channelCell.setOnClickListener(v -> MessagesController.getInstance(getCurrentAccount()).openByUserName("divegram", MineGramSettingsActivity.this, 1));
        linearLayout.addView(createRoundedCard(context, channelCell));

        TextCell downloadsCell = new TextCell(context);
        downloadsCell.setText("Канал с загрузкой", false);
        downloadsCell.setSubtitle("@divegramdownloads — свежие сборки клиента");
        downloadsCell.setOnClickListener(v -> MessagesController.getInstance(getCurrentAccount()).openByUserName("divegramdownloads", MineGramSettingsActivity.this, 1));
        linearLayout.addView(createRoundedCard(context, downloadsCell));

        TextCell faqCell = new TextCell(context);
        faqCell.setText("FAQ", false);
        faqCell.setSubtitle("@divefaq — ответы на частые вопросы");
        faqCell.setOnClickListener(v -> MessagesController.getInstance(getCurrentAccount()).openByUserName("divefaq", MineGramSettingsActivity.this, 1));
        linearLayout.addView(createRoundedCard(context, faqCell));

        TextCell forumCell = new TextCell(context);
        forumCell.setText("Форум", false);
        forumCell.setSubtitle("@divegramforum — обсуждение и поддержка");
        forumCell.setOnClickListener(v -> MessagesController.getInstance(getCurrentAccount()).openByUserName("divegramforum", MineGramSettingsActivity.this, 1));
        linearLayout.addView(createRoundedCard(context, forumCell));

        TextCell devCell = new TextCell(context);
        devCell.setText("Dev канал", false);
        devCell.setSubtitle("@divegramdev — новости разработки");
        devCell.setOnClickListener(v -> MessagesController.getInstance(getCurrentAccount()).openByUserName("divegramdev", MineGramSettingsActivity.this, 1));
        linearLayout.addView(createRoundedCard(context, devCell));

        return fragmentView;
    }

    private void showSupportDialog(Context context) {
        BottomSheet sheet = new BottomSheet(context, false);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, AndroidUtilities.dp(24), 0, AndroidUtilities.dp(20));

        ImageView avatarView = new ImageView(context);
        Drawable avatar = createCircularAvatar(AndroidUtilities.dp(96));
        avatarView.setImageDrawable(avatar);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(AndroidUtilities.dp(96), AndroidUtilities.dp(96));
        avatarLp.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(avatarView, avatarLp);

        TextView messageView = new TextView(context);
        messageView.setTextSize(18);
        messageView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        messageView.setGravity(Gravity.CENTER);
        messageView.setLineSpacing(0, 1.1f);
        messageView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        messageView.setHighlightColor(0x00000000);
        messageView.setMovementMethod(LinkMovementMethod.getInstance());

        String text = "Создано при моральной поддержке in3shot";
        SpannableString spannable = new SpannableString(text);
        int start = text.indexOf("in3shot");
        int end = start + "in3shot".length();
        spannable.setSpan(new ClickableSpan() {
            @Override
            public void onClick(android.view.View widget) {
                Browser.openUrl(getContext(), "https://t.me/in3shot");
            }
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        messageView.setText(spannable, TextView.BufferType.SPANNABLE);

        LinearLayout.LayoutParams messageLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageLp.topMargin = AndroidUtilities.dp(18);
        content.addView(messageView, messageLp);

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

    private Drawable createCircularAvatar(int sizePx) {
        android.graphics.Bitmap src = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.dg_support_avatar);
        if (src == null) {
            return null;
        }
        android.graphics.Bitmap bitmap = src;
        int w = src.getWidth();
        int h = src.getHeight();
        if (w != h) {
            int side = Math.min(w, h);
            int left = (w - side) / 2;
            int top = (h - side) / 2;
            bitmap = android.graphics.Bitmap.createBitmap(src, left, top, side, side);
        }
        android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, sizePx, sizePx, true);
        RoundedBitmapDrawable rounded = RoundedBitmapDrawableFactory.create(getContext().getResources(), scaled);
        rounded.setCircular(true);
        return rounded;
    }

    private View createRoundedCard(Context context, View content) {
        FrameLayout wrapper = new FrameLayout(context);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        background.setCornerRadius(AndroidUtilities.dp(12));
        wrapper.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        params.setMargins(0, 0, 0, AndroidUtilities.dp(8));
        wrapper.setLayoutParams(params);
        wrapper.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        return wrapper;
    }
}