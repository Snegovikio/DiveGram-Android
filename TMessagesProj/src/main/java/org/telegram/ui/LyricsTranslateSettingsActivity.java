/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;

public class LyricsTranslateSettingsActivity extends BaseFragment {

    private static final String[] LANG_NAMES = {
            "Как в приложении", "Русский", "English", "Українська", "Deutsch", "Français",
            "Español", "Italiano", "Português", "Türkçe", "Polski", "中文", "日本語", "العربية"
    };
    private static final String[] LANG_CODES = {
            "", "ru", "en", "uk", "de", "fr", "es", "it", "pt", "tr", "pl", "zh-CN", "ja", "ar"
    };

    private TextCheckCell translateCheck;
    private TextView langButtonTitle;
    private TextView langButtonSubtitle;
    private LinearLayout langCard;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Перевод лирики");
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

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        frameLayout.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        translateCheck = new TextCheckCell(context);
        translateCheck.setTextAndCheckAndSubText("Переводить текст песни",
                "Автоматически переводить текст песни через Google Переводчик. Перевод показывается под каждой строкой меньшим размером.",
                SharedConfig.playerLyricsTranslate, false);
        translateCheck.setOnClickListener(v -> {
            SharedConfig.setPlayerLyricsTranslate(!SharedConfig.playerLyricsTranslate);
            translateCheck.setChecked(SharedConfig.playerLyricsTranslate);
            updateLangCardState();
        });
        content.addView(wrapCard(context, translateCheck), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        langCard = new LinearLayout(context);
        langCard.setOrientation(LinearLayout.VERTICAL);
        langCard.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(14), AndroidUtilities.dp(20), AndroidUtilities.dp(14));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cardBg.setCornerRadius(AndroidUtilities.dp(12));
        langCard.setBackground(cardBg);

        langButtonTitle = new TextView(context);
        langButtonTitle.setText("Язык перевода");
        langButtonTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        langButtonTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        langButtonTitle.setTypeface(AndroidUtilities.bold());
        langCard.addView(langButtonTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        langButtonSubtitle = new TextView(context);
        langButtonSubtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        langButtonSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = AndroidUtilities.dp(4);
        langCard.addView(langButtonSubtitle, subLp);

        langCard.setOnClickListener(v -> showLanguageDialog());
        content.addView(langCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        TextView hint = new TextView(context);
        hint.setText("Перевод сохраняется для каждого трека: при повторном открытии сеть не используется, пока язык не сменён.");
        hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hint.setGravity(Gravity.CENTER);
        content.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 16, 24, 0));

        updateLangCardState();
        return fragmentView;
    }

    private View wrapCard(Context context, View cell) {
        FrameLayout wrapper = new FrameLayout(context);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        background.setCornerRadius(AndroidUtilities.dp(12));
        wrapper.setBackground(background);
        wrapper.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return wrapper;
    }

    private void updateLangCardState() {
        langCard.setAlpha(SharedConfig.playerLyricsTranslate ? 1f : 0.5f);
        langCard.setEnabled(SharedConfig.playerLyricsTranslate);
        langButtonSubtitle.setText(currentLanguageName() + " — нажмите, чтобы изменить");
    }

    private String currentLanguageName() {
        for (int i = 0; i < LANG_CODES.length; i++) {
            if (LANG_CODES[i].equals(SharedConfig.playerLyricsTranslateLang)) {
                return LANG_NAMES[i];
            }
        }
        try {
            String lang = LocaleController.getInstance().getCurrentLocale().getLanguage();
            return "Как в приложении (" + new java.util.Locale(lang).getDisplayLanguage(new java.util.Locale("ru")) + ")";
        } catch (Exception e) {
            return "Как в приложении";
        }
    }

    private void showLanguageDialog() {
        if (getParentActivity() == null) {
            return;
        }
        int selected = 0;
        for (int i = 0; i < LANG_CODES.length; i++) {
            if (LANG_CODES[i].equals(SharedConfig.playerLyricsTranslateLang)) {
                selected = i;
                break;
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Язык перевода");
        CharSequence[] items = new CharSequence[LANG_NAMES.length];
        for (int i = 0; i < LANG_NAMES.length; i++) {
            items[i] = (i == selected ? "✓ " : "") + LANG_NAMES[i];
        }
        builder.setItems(items, (dialog, which) -> {
            SharedConfig.setPlayerLyricsTranslateLang(LANG_CODES[which]);
            updateLangCardState();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }
}
