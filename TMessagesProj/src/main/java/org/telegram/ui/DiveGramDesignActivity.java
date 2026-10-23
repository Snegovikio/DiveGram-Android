package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

import java.util.ArrayList;
import java.util.List;

public class DiveGramDesignActivity extends BaseFragment {

    private static final String[] MD3_KEYS = {
            "loading", "sliders", "switches", "chat_header", "nav_bar", "list_items", "music_player", "mini_player"
    };
    private static final String[] MD3_TITLES = {
            "Индикаторы загрузки", "Слайдеры", "Переключатели", "Шапка чата",
            "Панель навигации", "Элементы списков", "Музыкальный плеер", "Мини-плеер"
    };

    private LinearLayout md3Children;
    private LinearLayout iosChildren;
    private TextView md3Count;
    private TextView md3Arrow;
    private TextView iosCount;
    private TextView iosArrow;
    private final List<CheckRow> md3Rows = new ArrayList<>();
    private Switch iosJellySwitch;
    private Switch iosPerChatSwitch;
    private boolean syncingIos;

    private interface Flag {
        boolean get();
        void set(boolean value);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Оформление");
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

        final ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        ((FrameLayout) fragmentView).addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        final LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(24));
        scrollView.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        addCard(content, createToggleRow(context, "Системные шрифты", SharedConfig.dgUseSystemFont, (v, checked) -> {
            SharedConfig.setDgUseSystemFont(checked);
            Toast.makeText(context, "Шрифт применится после перезапуска", Toast.LENGTH_SHORT).show();
        }));

        final LinearLayout md3Header = createParentRow(context, "Material Design 3", () -> md3Count, v -> md3Arrow);
        md3Count = (TextView) md3Header.getTag(R.id.md3_count);
        md3Arrow = (TextView) md3Header.getTag(R.id.md3_arrow);
        final Switch md3Switch = (Switch) md3Header.getTag(R.id.md3_switch);
        md3Switch.setChecked(SharedConfig.dgMd3Master, false);
        md3Switch.setOnCheckedChangeListener((view, checked) -> {
            SharedConfig.setDgMd3Flag("master", checked);
            for (String key : MD3_KEYS) {
                SharedConfig.setDgMd3Flag(key, checked);
            }
            updateMd3Rows();
            updateMd3Count();
        });
        md3Header.setOnClickListener(v -> toggleExpanded(md3Children, md3Arrow));
        addCard(content, md3Header);

        md3Children = new LinearLayout(context);
        md3Children.setOrientation(LinearLayout.VERTICAL);
        content.addView(md3Children, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        for (int i = 0; i < MD3_KEYS.length; i++) {
            final String key = MD3_KEYS[i];
            final CheckRow row = new CheckRow(context, MD3_TITLES[i], () -> {
                SharedConfig.setDgMd3Flag(key, !getMd3Value(key));
                refreshMd3State();
            });
            row.setChecked(getMd3Value(key));
            md3Rows.add(row);
            addCard(md3Children, row);
        }
        updateMd3Count();
        setChildrenVisible(md3Children, md3Arrow, false);

        final LinearLayout iosHeader = createParentRow(context, "iOS Design", () -> iosCount, v -> iosArrow);
        iosCount = (TextView) iosHeader.getTag(R.id.md3_count);
        iosArrow = (TextView) iosHeader.getTag(R.id.md3_arrow);
        final Switch iosSwitch = (Switch) iosHeader.getTag(R.id.md3_switch);
        iosSwitch.setChecked(SharedConfig.dgJellyAvatars && SharedConfig.dgPerChatThemes, false);
        iosSwitch.setOnCheckedChangeListener((view, checked) -> {
            if (syncingIos) {
                return;
            }
            SharedConfig.setDgIosFlag("jelly", checked);
            SharedConfig.setDgIosFlag("per_chat", checked);
            updateIosSwitchRows(checked);
            updateIosCount();
        });
        iosHeader.setOnClickListener(v -> toggleExpanded(iosChildren, iosArrow));
        addCard(content, iosHeader);

        iosChildren = new LinearLayout(context);
        iosChildren.setOrientation(LinearLayout.VERTICAL);
        content.addView(iosChildren, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        final LinearLayout jellyRow = createToggleRow(context, "«Желейная» анимация аватарок", SharedConfig.dgJellyAvatars, (v, checked) -> {
            if (syncingIos) {
                return;
            }
            SharedConfig.setDgIosFlag("jelly", checked);
            updateIosCount();
            syncIosMaster(iosHeader);
        });
        iosJellySwitch = (Switch) jellyRow.getTag(R.id.md3_switch);
        jellyRow.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(4), AndroidUtilities.dp(16), AndroidUtilities.dp(4));
        addCard(iosChildren, jellyRow);
        final LinearLayout perChatRow = createToggleRow(context, "Индивидуальные темы в чатах", SharedConfig.dgPerChatThemes, (v, checked) -> {
            if (syncingIos) {
                return;
            }
            SharedConfig.setDgIosFlag("per_chat", checked);
            updateIosCount();
            syncIosMaster(iosHeader);
        });
        iosPerChatSwitch = (Switch) perChatRow.getTag(R.id.md3_switch);
        perChatRow.setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(4), AndroidUtilities.dp(16), AndroidUtilities.dp(4));
        addCard(iosChildren, perChatRow);
        updateIosCount();
        setChildrenVisible(iosChildren, iosArrow, false);

        final TextView footer = new TextView(context);
        footer.setText("Каждый чат будет показывать выбранную для него тему.");
        footer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        footer.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        footer.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), AndroidUtilities.dp(16));
        content.addView(footer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final TextView sectionsHeader = new TextView(context);
        sectionsHeader.setText("Секции");
        sectionsHeader.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        sectionsHeader.setTypeface(AndroidUtilities.bold());
        sectionsHeader.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        sectionsHeader.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        content.addView(sectionsHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        addCard(content, createButtonRow(context, "Форма аватарки", "Изменить форму аватарки в профиле", () -> presentFragment(new DiveGramAvatarShapeActivity())));

        return fragmentView;
    }

    private boolean getMd3Value(String key) {
        switch (key) {
            case "loading": return SharedConfig.dgMd3Loading;
            case "sliders": return SharedConfig.dgMd3Sliders;
            case "switches": return SharedConfig.dgMd3Switches;
            case "chat_header": return SharedConfig.dgMd3ChatHeader;
            case "nav_bar": return SharedConfig.dgMd3NavBar;
            case "list_items": return SharedConfig.dgMd3ListItems;
            case "music_player": return SharedConfig.dgMd3MusicPlayer;
            case "mini_player": return SharedConfig.dgMd3MiniPlayer;
            default: return false;
        }
    }

    private void refreshMd3State() {
        for (int i = 0; i < md3Rows.size(); i++) {
            md3Rows.get(i).setChecked(getMd3Value(MD3_KEYS[i]));
        }
        updateMd3Count();
    }

    private void updateMd3Rows() {
        for (int i = 0; i < md3Rows.size(); i++) {
            md3Rows.get(i).setChecked(getMd3Value(MD3_KEYS[i]));
        }
    }

    private void updateMd3Count() {
        int count = 0;
        for (String key : MD3_KEYS) {
            if (getMd3Value(key)) {
                count++;
            }
        }
        if (md3Count != null) {
            md3Count.setText(count + "/" + MD3_KEYS.length);
        }
    }

    private void updateIosSwitchRows(boolean checked) {
        syncingIos = true;
        if (iosJellySwitch != null) {
            iosJellySwitch.setChecked(checked, true);
        }
        if (iosPerChatSwitch != null) {
            iosPerChatSwitch.setChecked(checked, true);
        }
        syncingIos = false;
    }

    private void syncIosMaster(LinearLayout header) {
        final Switch master = (Switch) header.getTag(R.id.md3_switch);
        if (master != null) {
            syncingIos = true;
            master.setChecked(SharedConfig.dgJellyAvatars && SharedConfig.dgPerChatThemes, true);
            syncingIos = false;
        }
    }

    private void updateIosCount() {
        int count = 0;
        if (SharedConfig.dgJellyAvatars) {
            count++;
        }
        if (SharedConfig.dgPerChatThemes) {
            count++;
        }
        if (iosCount != null) {
            iosCount.setText(count + "/2");
        }
    }

    private void toggleExpanded(View children, TextView arrow) {
        setChildrenVisible(children, arrow, children.getVisibility() != View.VISIBLE);
    }

    private void setChildrenVisible(View children, TextView arrow, boolean visible) {
        if (children != null) {
            children.animate().cancel();
            if (visible) {
                children.setVisibility(View.VISIBLE);
                children.setAlpha(0f);
                children.setTranslationY(-AndroidUtilities.dp(8));
                children.animate().alpha(1f).translationY(0f)
                        .setDuration(180).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
            } else {
                children.animate().alpha(0f).translationY(-AndroidUtilities.dp(8))
                        .setDuration(140).setInterpolator(CubicBezierInterpolator.EASE_IN)
                        .withEndAction(() -> {
                            if (children.getAlpha() == 0f) {
                                children.setVisibility(View.GONE);
                            }
                        }).start();
            }
        }
        if (arrow != null) {
            arrow.animate().rotation(visible ? 180f : 0f).setDuration(180)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
            arrow.setText("⌄");
        }
    }

    private void addCard(LinearLayout parent, View view) {
        final FrameLayout card = new FrameLayout(getContext());
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        background.setCornerRadius(AndroidUtilities.dp(16));
        card.setBackground(background);
        card.setClipToOutline(true);
        card.addView(view, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
        parent.addView(card, lp);
    }

    private LinearLayout createToggleRow(Context context, String title, boolean checked, Switch.OnCheckedChangeListener listener) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(4), AndroidUtilities.dp(16), AndroidUtilities.dp(4));
        row.setMinimumHeight(AndroidUtilities.dp(54));

        final TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        row.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        final Switch switchView = new Switch(context);
        switchView.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        switchView.setChecked(checked, false);
        switchView.setOnCheckedChangeListener(listener);
        row.addView(switchView, LayoutHelper.createLinear(37, 20, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));
        row.setTag(R.id.md3_switch, switchView);
        row.setOnClickListener(v -> switchView.setChecked(!switchView.isChecked(), true));
        return row;
    }

    private LinearLayout createParentRow(Context context, String title, java.util.function.Supplier<TextView> countSupplier, java.util.function.Function<View, TextView> arrowSupplier) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(4), AndroidUtilities.dp(16), AndroidUtilities.dp(4));
        row.setMinimumHeight(AndroidUtilities.dp(54));

        final TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        row.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        final TextView countView = new TextView(context);
        countView.setTag(R.id.md3_count);
        countView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        countView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        row.addView(countView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 6, 0, 0, 0));

        final TextView arrowView = new TextView(context);
        arrowView.setTag(R.id.md3_arrow);
        arrowView.setText("⌄");
        arrowView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        arrowView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        row.addView(arrowView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 4, 0, 0, 0));

        final View spacer = new View(context);
        row.addView(spacer, LayoutHelper.createLinear(0, 1, 1f));

        final Switch switchView = new Switch(context);
        switchView.setTag(R.id.md3_switch);
        switchView.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        switchView.setOnClickListener(v -> switchView.setChecked(!switchView.isChecked(), true));
        row.addView(switchView, LayoutHelper.createLinear(37, 20, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));

        row.setTag(R.id.md3_count, countView);
        row.setTag(R.id.md3_arrow, arrowView);
        row.setTag(R.id.md3_switch, switchView);
        return row;
    }

    private LinearLayout createButtonRow(Context context, String title, String subtitle, Runnable onClick) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        final TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        row.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        final TextView subtitleView = new TextView(context);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        row.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
        row.setOnClickListener(v -> onClick.run());
        return row;
    }

    private static class CheckRow extends LinearLayout {

        private final ImageView checkView;
        private final TextView titleView;

        CheckRow(Context context, String title, Runnable onToggle) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(AndroidUtilities.dp(32), AndroidUtilities.dp(4), AndroidUtilities.dp(16), AndroidUtilities.dp(4));
            setMinimumHeight(AndroidUtilities.dp(52));

            checkView = new ImageView(context);
            checkView.setImageResource(R.drawable.iv_checkbox_on);
            addView(checkView, LayoutHelper.createLinear(22, 22, Gravity.CENTER_VERTICAL));

            titleView = new TextView(context);
            titleView.setText(title);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 16, 0, 0, 0));

            setOnClickListener(v -> onToggle.run());
        }

        void setChecked(boolean checked) {
            checkView.setAlpha(checked ? 1f : 0f);
            titleView.setAlpha(checked ? 1f : 0.6f);
        }
    }
}
