/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;

public class AutoReplyActivity extends BaseFragment {

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Автоответчик");
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
        frameLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        TextCheckCell checkCell = new TextCheckCell(context);
        checkCell.setTextAndCheckAndSubText("Включить автоответчик", "Автоматически отвечать на сообщения в личных чатах, как в Telegram Premium.", SharedConfig.autoReplyEnabled, true);
        checkCell.setOnClickListener(v -> {
            SharedConfig.setAutoReplyEnabled(!SharedConfig.autoReplyEnabled);
            checkCell.setChecked(SharedConfig.autoReplyEnabled);
        });
        linearLayout.addView(checkCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        HeaderCell headerCell = new HeaderCell(context);
        headerCell.setText("Текст автоответа");
        linearLayout.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        EditText editText = new EditText(context);
        editText.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setGravity(Gravity.TOP | Gravity.START);
        editText.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        editText.setHint("Введите текст автоответа…");
        editText.setMaxLines(8);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setText(SharedConfig.autoReplyText);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                SharedConfig.setAutoReplyText(s.toString());
            }
        });
        linearLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 160, 0, 0, 0, 8));

        HeaderCell cooldownHeader = new HeaderCell(context);
        cooldownHeader.setText("Защита от спама");
        linearLayout.addView(cooldownHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextCell cooldownCell = new TextCell(context);
        cooldownCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cooldownCell.setTextAndValue("Пауза между ответами одному человеку", cooldownName(SharedConfig.autoReplyCooldownSec), true);
        cooldownCell.setOnClickListener(v -> {
            final String[] options = {"1 минута", "5 минут", "10 минут", "30 минут", "1 час"};
            final int[] values = {60, 300, 600, 1800, 3600};
            org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity(), getResourceProvider());
            builder.setTitle("Пауза между ответами");
            builder.setItems(options, (dialog, which) -> {
                SharedConfig.setAutoReplyCooldownSec(values[which]);
                cooldownCell.setTextAndValue("Пауза между ответами одному человеку", cooldownName(SharedConfig.autoReplyCooldownSec), true);
            });
            showDialog(builder.create());
        });
        linearLayout.addView(cooldownCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        org.telegram.ui.Cells.TextInfoPrivacyCell infoCell = new org.telegram.ui.Cells.TextInfoPrivacyCell(context);
        infoCell.setText("Анти-спам: одному человеку автоответ отправляется не чаще выбранной паузы, а всего — не более 20 автоответов в час на все чаты.");
        infoCell.setBackground(Theme.getThemedDrawableByKey(getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        linearLayout.addView(infoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        return fragmentView;
    }

    private static String cooldownName(int seconds) {
        if (seconds >= 3600) {
            return (seconds / 3600) + " ч.";
        }
        if (seconds >= 60) {
            return (seconds / 60) + " мин.";
        }
        return seconds + " сек.";
    }
}