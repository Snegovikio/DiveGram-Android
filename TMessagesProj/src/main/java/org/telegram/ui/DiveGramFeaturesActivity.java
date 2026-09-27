/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.AudioCodecs;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.TgWsProxy;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Paint.ColorPickerBottomSheet;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.UsernameDrope.UsernameDropeActivity;

import java.util.ArrayList;
import java.util.List;

public class DiveGramFeaturesActivity extends BaseFragment {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CHECK = 1;
    private static final int TYPE_BUTTON = 2;
    private static final int TYPE_SLIDER = 3;

    private static class Row {
        final int type;
        final String id;
        final String title;

        Row(int type, String id, String title) {
            this.type = type;
            this.id = id;
            this.title = title;
        }
    }

    private final List<Row> rows = new ArrayList<>();

    private static final String[] SECTION_TITLES = {"Обход", "Приватность", "Внешний вид", "Функции"};
    private int currentSection = 1;

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private LinearLayout tabsBar;
    private final TextView[] tabViews = new TextView[SECTION_TITLES.length];
    private final View[] tabIndicators = new View[SECTION_TITLES.length];

    private void buildRows() {
        rows.clear();
        switch (currentSection) {
            case 0:
                rows.add(new Row(TYPE_CHECK, "tgws", null));
                break;
            case 1:
                rows.add(new Row(TYPE_CHECK, "number_protection", null));
                rows.add(new Row(TYPE_CHECK, "save_deleted", null));
                rows.add(new Row(TYPE_SLIDER, "deleted_opacity", null));
                rows.add(new Row(TYPE_BUTTON, "purge_deleted", null));
                rows.add(new Row(TYPE_CHECK, "ghost_online", null));
                rows.add(new Row(TYPE_CHECK, "ghost_read", null));
                rows.add(new Row(TYPE_CHECK, "ghost_typing", null));
                rows.add(new Row(TYPE_CHECK, "typing_location", null));
                rows.add(new Row(TYPE_CHECK, "hide_camera_picker", null));
                break;
            case 2:
                rows.add(new Row(TYPE_BUTTON, "app_name", null));
                rows.add(new Row(TYPE_CHECK, "chat_plates", null));
                rows.add(new Row(TYPE_BUTTON, "chat_plate_color", null));
                rows.add(new Row(TYPE_SLIDER, "chat_plate_opacity", null));
                rows.add(new Row(TYPE_CHECK, "dialogs_wallpaper", null));
                rows.add(new Row(TYPE_BUTTON, "dialogs_own_wallpaper", null));
                rows.add(new Row(TYPE_SLIDER, "menu_tabs_opacity", null));
                rows.add(new Row(TYPE_CHECK, "centered_title", null));
                rows.add(new Row(TYPE_SLIDER, "music_blur", null));
                rows.add(new Row(TYPE_CHECK, "player_lyrics", null));
                rows.add(new Row(TYPE_CHECK, "player_lyrics_controls", null));
                rows.add(new Row(TYPE_BUTTON, "lyrics_editor", null));
                rows.add(new Row(TYPE_CHECK, "hide_all_chats", null));
                rows.add(new Row(TYPE_CHECK, "hide_contacts", null));
                rows.add(new Row(TYPE_CHECK, "circle_camera", null));
                rows.add(new Row(TYPE_CHECK, "old_design", null));
                break;
            case 3:
                rows.add(new Row(TYPE_BUTTON, "auto_reply", null));
                rows.add(new Row(TYPE_CHECK, "local_premium", null));
                rows.add(new Row(TYPE_CHECK, "quick_replies", null));
                rows.add(new Row(TYPE_CHECK, "mention_all", null));
                rows.add(new Row(TYPE_CHECK, "mention_all_split", null));
                rows.add(new Row(TYPE_BUTTON, "lyrics_translate", null));
                rows.add(new Row(TYPE_BUTTON, "weather", null));
                rows.add(new Row(TYPE_BUTTON, "ai_chat", null));
                rows.add(new Row(TYPE_BUTTON, "local_gift", null));
                rows.add(new Row(TYPE_BUTTON, "clean_deleted", null));
                rows.add(new Row(TYPE_BUTTON, "username_drope", null));
                rows.add(new Row(TYPE_SLIDER, "drope_ripple", null));
                rows.add(new Row(TYPE_BUTTON, "audio_codec", null));
                rows.add(new Row(TYPE_BUTTON, "voice_bitrate", null));
                rows.add(new Row(TYPE_CHECK, "player_bitrate", null));
                break;
        }
    }

    private void updateTabs() {
        for (int i = 0; i < SECTION_TITLES.length; i++) {
            boolean selected = i == currentSection;
            TextView tab = tabViews[i];
            if (tab != null) {
                tab.setTextColor(Theme.getColor(selected ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_windowBackgroundWhiteGrayText3));
                tab.setTypeface(selected ? AndroidUtilities.bold() : Typeface.DEFAULT);
            }
            if (tabIndicators[i] != null) {
                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.RECTANGLE);
                drawable.setCornerRadius(AndroidUtilities.dp(2));
                drawable.setColor(selected ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText) : 0x00000000);
                tabIndicators[i].setBackground(drawable);
            }
        }
    }

    private void selectSection(int section) {
        if (section == currentSection || section < 0 || section >= SECTION_TITLES.length) {
            return;
        }
        currentSection = section;
        buildRows();
        updateTabs();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        if (listView != null) {
            listView.scrollToPosition(0);
        }
    }

    private int indexOf(String id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public View createView(Context context) {
        buildRows();

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Настройки");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listAdapter = new ListAdapter(context);

        fragmentView = new LinearLayout(context);
        LinearLayout rootLayout = (LinearLayout) fragmentView;
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        tabsBar = new LinearLayout(context);
        tabsBar.setOrientation(LinearLayout.HORIZONTAL);
        tabsBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        tabsBar.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), 0);
        for (int i = 0; i < SECTION_TITLES.length; i++) {
            final int section = i;
            LinearLayout tab = new LinearLayout(context);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView label = new TextView(context);
            label.setText(SECTION_TITLES[i]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            label.setGravity(Gravity.CENTER);
            label.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(6));
            tab.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            View indicator = new View(context);
            LinearLayout.LayoutParams indicatorLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(3));
            indicatorLp.topMargin = AndroidUtilities.dp(3);
            tab.addView(indicator, indicatorLp);
            tab.setOnClickListener(v -> selectSection(section));
            tabsBar.addView(tab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            tabViews[i] = label;
            tabIndicators[i] = indicator;
        }
        rootLayout.addView(tabsBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        updateTabs();

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        rootLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (!view.isEnabled() || position < 0 || position >= rows.size()) {
                return;
            }
            Row row = rows.get(position);
            TextCheckCell cell = null;
            if (view instanceof TextCheckCell) {
                cell = (TextCheckCell) view;
            } else if (view instanceof ViewGroup) {
                View child = ((ViewGroup) view).getChildAt(0);
                if (child instanceof TextCheckCell) {
                    cell = (TextCheckCell) child;
                }
            }
            switch (row.id) {
                case "lyrics_editor":
                    try {
                        android.content.Intent intent = new android.content.Intent(getParentActivity(), org.telegram.ui.Components.LyricsAlbumEditorActivity.class);
                        getParentActivity().startActivity(intent);
                    } catch (Exception e) {
                        org.telegram.messenger.FileLog.e(e);
                    }
                    break;
                case "auto_reply":
                    presentFragment(new AutoReplyActivity());
                    break;
                case "lyrics_translate":
                    presentFragment(new LyricsTranslateSettingsActivity());
                    break;
                case "weather":
                    presentFragment(new WeatherSettingsActivity());
                    break;
                case "ai_chat":
                    presentFragment(new AiChatAnalysisActivity());
                    break;
                case "local_gift":
                    presentFragment(new DiveGramLocalGiftsActivity());
                    break;
                case "clean_deleted":
                    presentFragment(new CleanChatsActivity());
                    break;
                case "username_drope":
                    presentFragment(new UsernameDropeActivity());
                    break;
                case "audio_codec":
                    showAudioCodecDialog();
                    break;
                case "voice_bitrate":
                    showVoiceBitrateDialog();
                    break;
                case "player_bitrate":
                    SharedConfig.setPlayerBitrate(!SharedConfig.playerBitrate);
                    if (cell != null) cell.setChecked(SharedConfig.playerBitrate);
                    NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.diveGramPlayerBitrateChanged);
                    break;
                case "app_name":
                    showAppNameDialog();
                    break;
                case "chat_plate_color":
                    showPlateColorDialog();
                    break;
                case "dialogs_own_wallpaper":
                    showDialogsWallpaperDialog();
                    break;
                case "tgws":
                    if (TgWsProxy.isRunning()) {
                        TgWsProxy.stop();
                        if (cell != null) cell.setChecked(false);
                        Toast.makeText(getParentActivity(), "TG WS Proxy: выключен", Toast.LENGTH_SHORT).show();
                    } else {
                        TgWsProxy.start();
                        if (cell != null) cell.setChecked(true);
                        Toast.makeText(getParentActivity(), "TG WS Proxy: включён (127.0.0.1:" + SharedConfig.tgwsProxyPort + ")", Toast.LENGTH_SHORT).show();
                    }
                    break;
                case "number_protection":
                    SharedConfig.setNumberProtection(!SharedConfig.numberProtection);
                    if (cell != null) cell.setChecked(SharedConfig.numberProtection);
                    break;
                case "save_deleted": {
                    boolean enabling = !SharedConfig.saveDeletedMessages;
                    SharedConfig.setSaveDeletedMessages(enabling);
                    if (cell != null) cell.setChecked(enabling);
                    if (!enabling) {
                        MessagesController.getInstance(getCurrentAccount()).purgeSavedDeletedMessages();
                        Toast.makeText(getParentActivity() != null ? getParentActivity() : getContext(), "Сохранённые удалённые сообщения убраны из чатов", Toast.LENGTH_SHORT).show();
                    }
                    updateRow("deleted_opacity");
                    break;
                }
                case "ghost_online":
                    SharedConfig.setGhostHideOnline(!SharedConfig.ghostHideOnline);
                    if (cell != null) cell.setChecked(SharedConfig.ghostHideOnline);
                    break;
                case "ghost_read":
                    SharedConfig.setGhostHideRead(!SharedConfig.ghostHideRead);
                    if (cell != null) cell.setChecked(SharedConfig.ghostHideRead);
                    break;
                case "ghost_typing":
                    SharedConfig.setGhostHideTyping(!SharedConfig.ghostHideTyping);
                    if (cell != null) cell.setChecked(SharedConfig.ghostHideTyping);
                    break;
                case "typing_location":
                    SharedConfig.setShowTypingLocation(!SharedConfig.showTypingLocation);
                    if (cell != null) cell.setChecked(SharedConfig.showTypingLocation);
                    NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_USER_PRINT | MessagesController.UPDATE_MASK_STATUS);
                    break;
                case "hide_camera_picker":
                    SharedConfig.setHideCameraInPicker(!SharedConfig.hideCameraInPicker);
                    if (cell != null) cell.setChecked(SharedConfig.hideCameraInPicker);
                    break;
                case "local_premium":
                    SharedConfig.setLocalPremium(!SharedConfig.localPremium);
                    if (cell != null) cell.setChecked(SharedConfig.localPremium);
                    break;
                case "chat_plates":
                    SharedConfig.setChatPlates(!SharedConfig.chatPlates);
                    if (cell != null) cell.setChecked(SharedConfig.chatPlates);
                    NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.chatPlatesNeedUpdate);
                    break;
                case "dialogs_wallpaper":
                    SharedConfig.setDialogsWallpaper(!SharedConfig.dialogsWallpaper);
                    if (SharedConfig.dialogsWallpaper && SharedConfig.dialogsOwnWallpaper) {
                        SharedConfig.setDialogsOwnWallpaper(false);
                        updateRow("dialogs_own_wallpaper");
                    }
                    if (cell != null) cell.setChecked(SharedConfig.dialogsWallpaper);
                    NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.chatPlatesNeedUpdate);
                    break;
                case "quick_replies":
                    SharedConfig.setQuickRepliesEnabled(!SharedConfig.quickRepliesEnabled);
                    if (cell != null) cell.setChecked(SharedConfig.quickRepliesEnabled);
                    break;
                case "mention_all":
                    SharedConfig.setMentionAllEnabled(!SharedConfig.mentionAllEnabled);
                    if (cell != null) cell.setChecked(SharedConfig.mentionAllEnabled);
                    break;
                case "mention_all_split":
                    SharedConfig.setMentionAllSplitEnabled(!SharedConfig.mentionAllSplitEnabled);
                    if (cell != null) cell.setChecked(SharedConfig.mentionAllSplitEnabled);
                    break;
                case "hide_all_chats":
                    SharedConfig.setHideAllChatsFolder(!SharedConfig.hideAllChatsFolder);
                    if (cell != null) cell.setChecked(SharedConfig.hideAllChatsFolder);
                    NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.dialogFiltersUpdated);
                    break;
                case "hide_contacts":
                    SharedConfig.setHideContactsBar(!SharedConfig.hideContactsBar);
                    if (cell != null) cell.setChecked(SharedConfig.hideContactsBar);
                    NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.diveGramContactsBarToggled);
                    break;
                case "circle_camera":
                    SharedConfig.setCircleVideoBackCamera(!SharedConfig.circleVideoBackCamera);
                    if (cell != null) cell.setChecked(SharedConfig.circleVideoBackCamera);
                    break;
                case "old_design":
                    SharedConfig.setOldDesign(!SharedConfig.oldDesign);
                    if (cell != null) cell.setChecked(SharedConfig.oldDesign);
                    NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.diveGramOldDesignChanged);
                    break;
                case "centered_title":
                    SharedConfig.setDiveGramCentered(!SharedConfig.diveGramCentered);
                    if (cell != null) cell.setChecked(SharedConfig.diveGramCentered);
                    NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.diveGramTitleChanged);
                    break;
                case "player_lyrics":
                    SharedConfig.setPlayerLyricsEnabled(!SharedConfig.playerLyricsEnabled);
                    if (cell != null) cell.setChecked(SharedConfig.playerLyricsEnabled);
                    break;
                case "player_lyrics_controls":
                    SharedConfig.setPlayerLyricsHideControls(!SharedConfig.playerLyricsHideControls);
                    if (cell != null) cell.setChecked(SharedConfig.playerLyricsHideControls);
                    break;
            }
        });
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void showAudioCodecDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Звуковой кодек");
        builder.setItems(AudioCodecs.CODEC_TITLES, (dialog, which) -> {
            if (which < 0 || which >= AudioCodecs.getCodecCount()) {
                return;
            }
            SharedConfig.setAudioCodec(which);
            updateRow("audio_codec");
            updateRow("voice_bitrate");
        });
        showDialog(builder.create());
    }

    private void showVoiceBitrateDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Качество записи голосовых");
        builder.setItems(AudioCodecs.VOICE_BITRATE_TITLES, (dialog, which) -> {
            if (which < 0 || which >= AudioCodecs.VOICE_BITRATE_VALUES.length) {
                return;
            }
            SharedConfig.setVoiceRecordBitrate(AudioCodecs.VOICE_BITRATE_VALUES[which]);
            updateRow("voice_bitrate");
        });
        showDialog(builder.create());
    }

    private void showAppNameDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Надпись в списке чатов");
        builder.setItems(new CharSequence[]{"DiveGram", "Своё имя", "Свой юзернейм", "Свой вариант…"}, (dialog, which) -> {
            if (which == 3) {
                showCustomAppNameDialog();
            } else {
                SharedConfig.setAppNameMode(which);
                NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.diveGramTitleChanged);
                updateRow("app_name");
            }
        });
        showDialog(builder.create());
    }

    private void showCustomAppNameDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Своя надпись");
        final EditText editText = new EditText(getParentActivity());
        editText.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setText(SharedConfig.appNameCustom);
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        FrameLayout container = new FrameLayout(getParentActivity());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        container.addView(editText, lp);
        builder.setView(container);
        builder.setPositiveButton("OK", (dialog, which) -> {
            String value = editText.getText().toString().trim();
            SharedConfig.setAppNameCustom(value);
            SharedConfig.setAppNameMode(3);
            NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.diveGramTitleChanged);
            updateRow("app_name");
        });
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

    private void updateRow(String id) {
        int pos = indexOf(id);
        if (listAdapter != null && pos >= 0) {
            listAdapter.notifyItemChanged(pos);
        }
    }

    private void previewDropeRipple(int strength) {
        if (strength <= 0 || listView == null || getParentActivity() == null) {
            return;
        }
        try {
            int[] loc = new int[2];
            listView.getLocationInWindow(loc);
            LaunchActivity.makeRipple(
                    loc[0] + listView.getWidth() / 2f,
                    loc[1] + listView.getHeight() / 2f,
                    1.5f * 2.5f * strength / 100f);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static final int REQUEST_DIALOGS_WALLPAPER = 47291;

    private void showPlateColorDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final int[] colors = {
                0xff1c1c1e, 0xff2c2c2e, 0xff101a2c, 0xff1c2b26, 0xff2e1c22,
                0xffffffff, 0xfff2f2f7, 0xffe8eaf6, 0xffe0f2f1, 0xfffff3e0, 0xfffce4ec
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Цвет плашек чатов");
        LinearLayout root = new LinearLayout(getParentActivity());
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(20);
        root.setPadding(pad, pad, pad, pad);

        TextView autoButton = new TextView(getParentActivity());
        autoButton.setText(SharedConfig.chatPlateColor == 0 ? "Авто (по теме) — выбрано" : "Авто (по теме)");
        autoButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        autoButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        autoButton.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
        autoButton.setOnClickListener(v -> {
            SharedConfig.setChatPlateColor(0);
            NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.chatPlatesNeedUpdate);
            updateRow("chat_plate_color");
            dismissCurrentDialog();
        });
        root.addView(autoButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = null;
        for (int i = 0; i < colors.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(getParentActivity());
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = AndroidUtilities.dp(12);
                root.addView(row, lp);
            }
            final int color = colors[i];
            View swatch = new View(getParentActivity());
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(color);
            drawable.setStroke(AndroidUtilities.dp(SharedConfig.chatPlateColor == color ? 3 : 1),
                    SharedConfig.chatPlateColor == color ? 0xFF8AB4F8 : 0x33888888);
            swatch.setBackground(drawable);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(AndroidUtilities.dp(44), AndroidUtilities.dp(44));
            int margin = AndroidUtilities.dp(10);
            lp.setMargins(margin, 0, margin, 0);
            swatch.setLayoutParams(lp);
            swatch.setOnClickListener(v -> {
                SharedConfig.setChatPlateColor(color);
                NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.chatPlatesNeedUpdate);
                updateRow("chat_plate_color");
                dismissCurrentDialog();
            });
            row.addView(swatch);
        }

        boolean customSelected = SharedConfig.chatPlateColor != 0;
        for (int c : colors) {
            if (c == SharedConfig.chatPlateColor) {
                customSelected = false;
                break;
            }
        }
        FrameLayout plusWrap = new FrameLayout(getParentActivity());
        GradientDrawable plusBg = new GradientDrawable();
        plusBg.setShape(GradientDrawable.OVAL);
        plusBg.setColor(0x00000000);
        plusBg.setStroke(AndroidUtilities.dp(customSelected ? 3 : 1), customSelected ? 0xFF8AB4F8 : 0x33888888);
        plusWrap.setBackground(plusBg);
        TextView plusLabel = new TextView(getParentActivity());
        plusLabel.setText("+");
        plusLabel.setGravity(Gravity.CENTER);
        plusLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        plusLabel.setTypeface(Typeface.DEFAULT_BOLD);
        plusLabel.setTextColor(0xFF8AB4F8);
        plusWrap.addView(plusLabel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams plusLp = new LinearLayout.LayoutParams(AndroidUtilities.dp(44), AndroidUtilities.dp(44));
        plusLp.setMargins(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        plusWrap.setLayoutParams(plusLp);
        plusWrap.setOnClickListener(v -> {
            final ColorPickerBottomSheet.PipetteDelegate pipette = new ColorPickerBottomSheet.PipetteDelegate() {
                @Override
                public void onStartColorPipette() {
                }

                @Override
                public void onStopColorPipette() {
                }

                @Override
                public ViewGroup getContainerView() {
                    return null;
                }

                @Override
                public View getSnapshotDrawingView() {
                    return null;
                }

                @Override
                public void onDrawImageOverCanvas(Bitmap bitmap, Canvas canvas) {
                }

                @Override
                public boolean isPipetteVisible() {
                    return false;
                }

                @Override
                public boolean isPipetteAvailable() {
                    return false;
                }

                @Override
                public void onColorSelected(int color) {
                }
            };
            int current = SharedConfig.chatPlateColor != 0 ? SharedConfig.chatPlateColor : 0xff1c1c1e;
            new ColorPickerBottomSheet(getParentActivity(), null)
                .setColor(current)
                .setPipetteDelegate(pipette)
                .setColorListener(color -> {
                    if (color != 0) {
                        SharedConfig.setChatPlateColor(color);
                        NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.chatPlatesNeedUpdate);
                        updateRow("chat_plate_color");
                    }
                })
                .show();
        });
        if (row != null && row.getChildCount() < 4) {
            row.addView(plusWrap);
        } else {
            LinearLayout extraRow = new LinearLayout(getParentActivity());
            extraRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams extraLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            extraLp.topMargin = AndroidUtilities.dp(12);
            root.addView(extraRow, extraLp);
            extraRow.addView(plusWrap);
        }
        builder.setView(root);
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

    private void showHexColorDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Код цвета");
        LinearLayout root = new LinearLayout(getParentActivity());
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(20);
        root.setPadding(pad, AndroidUtilities.dp(4), pad, pad);

        EditText hexInput = new EditText(getParentActivity());
        hexInput.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        hexInput.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        hexInput.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        hexInput.setHint("#RRGGBB (например #1C1C1E)");
        hexInput.setSingleLine(true);
        hexInput.setInputType(InputType.TYPE_CLASS_TEXT);
        hexInput.setFilters(new android.text.InputFilter[]{(source, start, end, dest, dstart, dend) -> {
            StringBuilder out = new StringBuilder();
            for (int i = start; i < end; i++) {
                char c = source.charAt(i);
                if (c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F' || c == '#') {
                    out.append(c);
                }
            }
            return out.length() == source.length() ? source : out.toString();
        }});
        if (SharedConfig.chatPlateColor != 0) {
            hexInput.setText(String.format("#%06X", SharedConfig.chatPlateColor & 0xFFFFFF));
        }
        root.addView(hexInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        builder.setView(root);
        builder.setPositiveButton("Применить", (dialog, which) -> {
            String text = hexInput.getText().toString().trim().replace("#", "");
            if (text.isEmpty()) {
                return;
            }
            try {
                int color;
                if (text.length() == 6) {
                    color = (int) Long.parseLong(text, 16) | 0xFF000000;
                } else if (text.length() == 8) {
                    color = (int) Long.parseLong(text, 16);
                } else {
                    Toast.makeText(getParentActivity(), "Введите 6 символов, например 1C1C1E", Toast.LENGTH_SHORT).show();
                    return;
                }
                SharedConfig.setChatPlateColor(color);
                NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.chatPlatesNeedUpdate);
                updateRow("chat_plate_color");
            } catch (NumberFormatException e) {
                Toast.makeText(getParentActivity(), "Некорректный код цвета", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

    private void showDialogsWallpaperDialog() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Свои обои для списка чатов");
        boolean hasOwn = SharedConfig.dialogsOwnWallpaper && SharedConfig.getDialogsOwnWallpaperFile().exists();
        builder.setItems(new CharSequence[]{
                "Выбрать изображение из галереи",
                hasOwn ? "Убрать свои обои" : "Убрать свои обои (не заданы)"
        }, (dialog, which) -> {
            if (which == 0) {
                try {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*");
                    getParentActivity().startActivityForResult(intent, REQUEST_DIALOGS_WALLPAPER);
                } catch (Exception e) {
                    org.telegram.messenger.FileLog.e(e);
                }
            } else {
                SharedConfig.getDialogsOwnWallpaperFile().delete();
                SharedConfig.setDialogsOwnWallpaper(false);
                SharedConfig.setDialogsWallpaperScale(100);
                SharedConfig.setDialogsWallpaperOffsetX(0);
                SharedConfig.setDialogsWallpaperOffsetY(0);
                SharedConfig.setDialogsWallpaperBlur(0);
                NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.chatPlatesNeedUpdate);
                updateRow("dialogs_own_wallpaper");
            }
        });
        showDialog(builder.create());
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_DIALOGS_WALLPAPER && resultCode == android.app.Activity.RESULT_OK && data != null && data.getData() != null) {
            final android.net.Uri uri = data.getData();
            Utilities.globalQueue.postRunnable(() -> {
                try {
                    android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    try (java.io.InputStream in = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri)) {
                        android.graphics.BitmapFactory.decodeStream(in, null, bounds);
                    }
                    android.graphics.BitmapFactory.Options real = new android.graphics.BitmapFactory.Options();
                    real.inSampleSize = Math.max(1, Math.round(bounds.outWidth / 1440f));
                    android.graphics.Bitmap bitmap;
                    try (java.io.InputStream in = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri)) {
                        bitmap = android.graphics.BitmapFactory.decodeStream(in, null, real);
                    }
                    if (bitmap == null) {
                        throw new Exception("не удалось прочитать изображение");
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        if (getParentActivity() != null) {
                            presentFragment(new DialogsWallpaperEditorActivity(bitmap));
                        }
                    });
                } catch (Exception e) {
                    org.telegram.messenger.FileLog.e(e);
                    AndroidUtilities.runOnUIThread(() ->
                            Toast.makeText(getParentActivity() != null ? getParentActivity() : getContext(), "Не удалось загрузить изображение: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
        }
    }

    private static class TextButtonCell extends LinearLayout {

        private final TextView titleView;
        private final TextView subtitleView;

        TextButtonCell(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

            GradientDrawable background = new GradientDrawable();
            background.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            background.setCornerRadius(AndroidUtilities.dp(12));
            setBackground(background);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
            setLayoutParams(params);

            titleView = new TextView(context);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setSingleLine(false);
            titleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            addView(titleView, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            subtitleView = new TextView(context);
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitleView.setSingleLine(false);
            subtitleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
            lp.topMargin = AndroidUtilities.dp(4);
            addView(subtitleView, lp);
        }

        public void setTitle(CharSequence text) {
            titleView.setText(text);
        }

        public void setSubtitle(CharSequence text) {
            subtitleView.setText(text);
        }
    }

    private static View createRoundedCard(Context context, View content) {
        FrameLayout wrapper = new FrameLayout(context);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        background.setCornerRadius(AndroidUtilities.dp(12));
        wrapper.setBackground(background);
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
        wrapper.setLayoutParams(params);
        wrapper.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return wrapper;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position >= 0 && position < rows.size() && rows.get(position).type != TYPE_HEADER && rows.get(position).type != TYPE_SLIDER;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
                    break;
                case TYPE_BUTTON:
                    view = new TextButtonCell(mContext);
                    break;
                case TYPE_SLIDER: {
                    LinearLayout container = new LinearLayout(mContext);
                    container.setOrientation(LinearLayout.VERTICAL);
                    container.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    bg.setCornerRadius(AndroidUtilities.dp(12));
                    container.setBackground(bg);
                    RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.setMargins(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
                    container.setLayoutParams(lp);
                    TextView title = new TextView(mContext);
                    title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                    container.addView(title, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    TextView subtitle = new TextView(mContext);
                    subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
                    subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                    LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
                    slp.topMargin = AndroidUtilities.dp(4);
                    container.addView(subtitle, slp);
                    SeekBarView seekBar = new SeekBarView(mContext);
                    seekBar.setReportChanges(true);
                    LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
                    seekLp.topMargin = AndroidUtilities.dp(12);
                    container.addView(seekBar, seekLp);
                    TextView value = new TextView(mContext);
                    value.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
                    value.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                    value.setGravity(Gravity.RIGHT);
                    container.addView(value, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    container.setTag(new Object[]{seekBar, value, title, subtitle});
                    view = container;
                    break;
                }
                default:
                    view = createRoundedCard(mContext, new TextCheckCell(mContext));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (position < 0 || position >= rows.size()) {
                return;
            }
            Row row = rows.get(position);
            switch (row.type) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    cell.setText(row.title);
                    break;
                }
                case TYPE_BUTTON: {
                    TextButtonCell cell = (TextButtonCell) holder.itemView;
                    switch (row.id) {
                        case "lyrics_editor":
                            cell.setTitle("Редактор альбомной лирики");
                            cell.setSubtitle("Двигайте пальцем обложку и текст, меняйте размер щипком — как настройка управления в играх.");
                            break;
                        case "auto_reply":
                            cell.setTitle("Автоответчик");
                            cell.setSubtitle("Автоматически отвечает на сообщения в личных чатах, как в Telegram Premium.");
                            break;
                        case "lyrics_translate":
                            cell.setTitle("Перевод лирики");
                            cell.setSubtitle((SharedConfig.playerLyricsTranslate ? "Включён" : "Выключен")
                                    + " — перевод текста песни через Google Переводчик; нажмите, чтобы настроить язык.");
                            break;
                        case "purge_deleted": {
                            int count = SharedConfig.getDeletedMessageKeysCopy().size();
                            cell.setTitle("Удалить сохранённые удалённые");
                            cell.setSubtitle(count > 0
                                    ? "Сейчас сохранено " + count + " — нажмите, чтобы убрать их из чатов"
                                    : "Сохранённых удалённых сообщений нет");
                            break;
                        }
                        case "clean_deleted":
                            cell.setTitle("Очистить список чатов");
                            cell.setSubtitle("Группы, каналы, личные чаты и боты без активности, а также удалённые аккаунты — выбирайте и удаляйте.");
                            break;
                        case "app_name":
                            cell.setTitle("Надпись в списке чатов");
                            cell.setSubtitle("Сейчас: " + SharedConfig.getAppName(getUserConfig().getCurrentUser()) + " — нажмите, чтобы изменить");
                            break;
                        case "chat_plate_color":
                            cell.setTitle("Цвет плашек чатов");
                            cell.setSubtitle(SharedConfig.chatPlateColor == 0
                                    ? "Авто (по теме) — нажмите, чтобы выбрать цвет"
                                    : "Свой цвет — нажмите, чтобы изменить (#" + Integer.toHexString(SharedConfig.chatPlateColor).substring(2) + ")");
                            break;
                        case "dialogs_own_wallpaper":
                            cell.setTitle("Загрузить обои на фон списка");
                            cell.setSubtitle(SharedConfig.dialogsOwnWallpaper && SharedConfig.getDialogsOwnWallpaperFile().exists()
                                    ? "Заданы — нажмите, чтобы заменить или убрать. При загрузке откроется редактор."
                                    : "Выберите изображение — откроется редактор: двигайте и приближайте картинку пальцами, настройте блюр.");
                            break;
                        case "weather":
                            cell.setTitle("Погода");
                            String cached = SharedConfig.weatherText;
                            cell.setSubtitle("Город: " + (SharedConfig.weatherCity.isEmpty() ? "не указан" : SharedConfig.weatherCity)
                                    + (cached.isEmpty() ? "" : " · сейчас: " + cached));
                            break;
                        case "ai_chat":
                            cell.setTitle("ИИ анализ чата");
                            cell.setSubtitle(SharedConfig.aiChatEnabled ? "Включён" : "Выключен — спросите ИИ о том, что было в переписке, через меню чата");
                            break;
                        case "local_gift":
                            cell.setTitle("Добавить НФТ-подарок локально");
                            cell.setSubtitle(SharedConfig.wornGiftCollectibleId != 0
                                ? "Надет локально (id " + SharedConfig.wornGiftCollectibleId + ") — нажмите, чтобы выбрать другой"
                                : "Выберите любой НФТ-подарок и наденьте его на свой профиль");
                            break;
                        case "username_drope":
                            cell.setTitle("Username Drope");
                            cell.setSubtitle("Поднесите второй телефон в этом же режиме — обменяйтесь именами и добавьте друг друга в контакты.");
                            break;
                        case "audio_codec":
                            cell.setTitle("Звуковой кодек");
                            cell.setSubtitle(AudioCodecs.CODEC_TITLES[SharedConfig.audioCodec] + " — " + AudioCodecs.getCurrentOutputDescription());
                            break;
                        case "voice_bitrate":
                            cell.setTitle("Качество записи голосовых");
                            cell.setSubtitle(AudioCodecs.VOICE_BITRATE_TITLES[AudioCodecs.getVoiceBitrateIndex()] + ". Влияет на битрейт голосовых сообщений.");
                            break;
                    }
                    break;
                }
                case TYPE_SLIDER: {
                    LinearLayout container = (LinearLayout) holder.itemView;
                    Object[] tag = (Object[]) container.getTag();
                    SeekBarView seekBar = (SeekBarView) tag[0];
                    TextView value = (TextView) tag[1];
                    TextView title = (TextView) tag[2];
                    TextView subtitle = (TextView) tag[3];
                    switch (row.id) {
                        case "music_blur":
                            title.setText("Блюр фона карточки музыки");
                            subtitle.setText("Тяните ползунок — фон становится более размытым");
                            seekBar.setProgress(SharedConfig.musicCardBlur / 100f);
                            value.setText(SharedConfig.musicCardBlur + "%");
                            seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                                @Override
                                public void onSeekBarDrag(boolean stop, float progress) {
                                    int blur = Math.round(progress * 100);
                                    SharedConfig.setMusicCardBlur(blur);
                                    value.setText(blur + "%");
                                    if (stop) {
                                        NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.dialogsNeedReload);
                                    }
                                }
                                @Override
                                public void onSeekBarPressed(boolean pressed) {}
                            });
                            break;
                        case "chat_plate_opacity":
                            title.setText("Прозрачность плашек чатов");
                            subtitle.setText("100% — непрозрачные, меньше — сквозь плашки видно фон списка.");
                            seekBar.setProgress(SharedConfig.chatPlateOpacity / 100f);
                            value.setText(SharedConfig.chatPlateOpacity + "%");
                            seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                                @Override
                                public void onSeekBarDrag(boolean stop, float progress) {
                                    int v = Math.round(progress * 100);
                                    SharedConfig.setChatPlateOpacity(v);
                                    value.setText(SharedConfig.chatPlateOpacity + "%");
                                    if (stop) {
                                        NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.chatPlatesNeedUpdate);
                                    }
                                }
                                @Override
                                public void onSeekBarPressed(boolean pressed) {}
                            });
                            break;
                        case "menu_tabs_opacity":
                            title.setText("Прозрачность плашки меню");
                            subtitle.setText("Плашка внизу с кнопками «Профиль», «Настройки» и т.д.: 100% — плотная, меньше — прозрачнее.");
                            seekBar.setProgress((SharedConfig.mainTabsOpacity - 10) / 90f);
                            value.setText(SharedConfig.mainTabsOpacity + "%");
                            seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                                @Override
                                public void onSeekBarDrag(boolean stop, float progress) {
                                    int v = 10 + Math.round(progress * 90);
                                    SharedConfig.setMainTabsOpacity(v);
                                    value.setText(v + "%");
                                    if (stop) {
                                        NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.diveGramMainTabsOpacityChanged);
                                    }
                                }
                                @Override
                                public void onSeekBarPressed(boolean pressed) {}
                            });
                            break;
                        case "drope_ripple": {
                            final boolean rippleSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
                            title.setText("Сила рипл-анимации в Username Drope");
                            if (rippleSupported) {
                                subtitle.setText("0% — выключено. Отпустите ползунок, чтобы увидеть рипл прямо здесь.");
                            } else {
                                subtitle.setText("Ползунок доступен только на Android 13 и новее.");
                            }
                            seekBar.setEnabled(rippleSupported);
                            seekBar.setAlpha(rippleSupported ? 1f : 0.4f);
                            value.setAlpha(rippleSupported ? 1f : 0.4f);
                            seekBar.setProgress(SharedConfig.dropeRippleStrength / 200f);
                            value.setText(SharedConfig.dropeRippleStrength + "%");
                            seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                                @Override
                                public void onSeekBarDrag(boolean stop, float progress) {
                                    if (!rippleSupported) {
                                        return;
                                    }
                                    int v = Math.round(progress * 200);
                                    SharedConfig.setDropeRippleStrength(v);
                                    value.setText(v + "%");
                                    if (stop) {
                                        previewDropeRipple(v);
                                    }
                                }
                                @Override
                                public void onSeekBarPressed(boolean pressed) {}
                            });
                            break;
                        }
                        case "deleted_opacity":
                            title.setText("Прозрачность удалённых сообщений");
                            subtitle.setText("Насколько бледными показывать сохранённые удалённые сообщения (нужно «Сохранять удалённые сообщения»).");
                            seekBar.setProgress(SharedConfig.deletedMessagesOpacity / 100f);
                            value.setText(SharedConfig.deletedMessagesOpacity + "%");
                            seekBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                                @Override
                                public void onSeekBarDrag(boolean stop, float progress) {
                                    int v = Math.round(progress * 100);
                                    SharedConfig.setDeletedMessagesOpacity(v);
                                    value.setText(SharedConfig.deletedMessagesOpacity + "%");
                                    if (stop) {
                                        NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
                                    }
                                }
                                @Override
                                public void onSeekBarPressed(boolean pressed) {}
                            });
                            break;
                    }
                    break;
                }
                default: {
                    View itemView = holder.itemView;
                    TextCheckCell cell;
                    if (itemView instanceof TextCheckCell) {
                        cell = (TextCheckCell) itemView;
                    } else {
                        cell = (TextCheckCell) ((ViewGroup) itemView).getChildAt(0);
                    }
                    switch (row.id) {
                        case "number_protection":
                            cell.setTextAndCheckAndSubText("Защита номера", "Скрывает ваш номер телефона в профиле и настройках (показывает только последние цифры).", SharedConfig.numberProtection, false);
                            break;
                        case "tgws":
                            cell.setTextAndCheckAndSubText("TG WS Proxy (обход DPI)", "Проксирует соединения через локальный WebSocket-сервер, чтобы обойти блокировку мессенджера.", TgWsProxy.isRunning(), false);
                            break;
                        case "player_bitrate":
                            cell.setTextAndCheckAndSubText("Показывать битрейт аудиофайла", "Рядом с длительностью в плеере показывать средний битрейт трека в кбит/с. Помогает понять качество исходного файла.", SharedConfig.playerBitrate, false);
                            break;
                        case "save_deleted":
                            cell.setTextAndCheckAndSubText("Сохранять удалённые сообщения", "Удалённые собеседником сообщения не исчезают: помечаются корзиной, а при ответе цитата сохраняется. При выключении все сохранённые удаляются из чатов.", SharedConfig.saveDeletedMessages, false);
                            break;
                case "purge_deleted": {
                    final int count = SharedConfig.getDeletedMessageKeysCopy().size();
                    if (getParentActivity() == null) {
                        break;
                    }
                    if (count == 0) {
                        Toast.makeText(getParentActivity(), "Сохранённых удалённых сообщений нет", Toast.LENGTH_SHORT).show();
                        break;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle("Удалить сохранённые удалённые?");
                    builder.setMessage("Будет убрано " + count + " сохранённых удалённых сообщений. Их больше нельзя будет процитировать в ответе. Сами сообщения в Telegram при этом не изменятся.");
                    builder.setPositiveButton("Удалить", (dialog, which) -> {
                        SharedConfig.clearDeletedMessages();
                        MessagesController.getInstance(getCurrentAccount()).purgeSavedDeletedMessages();
                        NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
                        updateRow("purge_deleted");
                        updateRow("deleted_opacity");
                        Toast.makeText(getParentActivity(), "Сохранённые удалённые убраны", Toast.LENGTH_SHORT).show();
                    });
                    builder.setNegativeButton("Отмена", null);
                    builder.show();
                    break;
                }
                case "ghost_online":
                            cell.setTextAndCheckAndSubText("Режим призрака: не показывать «в сети»", "Скрывает ваш статус «в сети» от других пользователей.", SharedConfig.ghostHideOnline, false);
                            break;
                        case "ghost_read":
                            cell.setTextAndCheckAndSubText("Режим призрака: не отправлять «прочитано»", "Собеседники не видят, что вы прочитали их сообщения.", SharedConfig.ghostHideRead, false);
                            break;
                        case "ghost_typing":
                            cell.setTextAndCheckAndSubText("Режим призрака: не отправлять «печатает…»", "Скрывает индикатор «печатает…» при наборе сообщения.", SharedConfig.ghostHideTyping, false);
                            break;
                        case "typing_location":
                            cell.setTextAndCheckAndSubText("Показывать, где печатает собеседник", "Если человек печатает не в личке, а в группе/канале — в его чате будет «печатает в «Название»».", SharedConfig.showTypingLocation, false);
                            break;
                        case "hide_camera_picker":
                            cell.setTextAndCheckAndSubText("Убрать камеру из выбора медиа", "Скрывает кнопку камеры при выборе фото или видео для отправки в чат.", SharedConfig.hideCameraInPicker, false);
                            break;
                        case "local_premium":
                            cell.setTextAndCheckAndSubText("Локальный Telegram Premium", "Разблокирует премиум-функции локально (лимиты папок и закреплённых, реакции, анимированные эмоции и т.д.) — без реальной подписки.", SharedConfig.localPremium, false);
                            break;
                        case "chat_plates":
                            cell.setTextAndCheckAndSubText("Плашки чатов", "Показывать диалоги в виде плашек с закруглёнными углами на чёрном фоне.", SharedConfig.chatPlates, false);
                            break;
                        case "dialogs_wallpaper":
                            cell.setTextAndCheckAndSubText("Обои чата как фон списка",
                                    SharedConfig.dialogsOwnWallpaper
                                            ? "Сейчас работают свои обои списка — этот переключатель выключен."
                                            : "Применить выбранные вами обои чата на задний фон списка диалогов.",
                                    SharedConfig.dialogsWallpaper, false);
                            break;
                        case "quick_replies":
                            cell.setTextAndCheckAndSubText("Быстрые ответы", "Удерживайте чат — появится плашка с шаблонными ответами; нажмите, чтобы отправить. Шаблоны можно менять там же («Изменить шаблоны…»).", SharedConfig.quickRepliesEnabled, false);
                            break;
                        case "mention_all":
                            cell.setTextAndCheckAndSubText("Упомянуть всех (@all)", "Если в сообщении написать @all, при отправке оно заменится на упоминания всех участников чата.", SharedConfig.mentionAllEnabled, false);
                            break;
                        case "mention_all_split":
                            cell.setTextAndCheckAndSubText("Отправлять отметки частями", "Если включено, отметки @all будут отправляться несколькими сообщениями по 20 смайликов — чтобы боты не удаляли сообщение из-за множества смайлов.", SharedConfig.mentionAllSplitEnabled, false);
                            break;
                        case "circle_camera":
                            cell.setTextAndCheckAndSubText("Кружок на заднюю камеру", "При записи видеосообщения использовать заднюю камеру вместо фронтальной.", SharedConfig.circleVideoBackCamera, false);
                            break;
                        case "centered_title":
                            cell.setTextAndCheckAndSubText("Надпись посередине", "Сместить надпись со звездой в центр верхней панели.", SharedConfig.diveGramCentered, false);
                            break;
                        case "player_lyrics":
                            cell.setTextAndCheckAndSubText("Текст песни в плеере", "Кнопка с кавычками в плеере открывает текст на весь экран с размытой обложкой. Синхронная подсветка текущей строчки, клик по строке — переход к ней, альбомный режим (обложка слева, текст справа).", SharedConfig.playerLyricsEnabled, false);
                            break;
                        case "player_lyrics_controls":
                            cell.setTextAndCheckAndSubText("Скрывать кнопки в лирике", "Делает невидимыми кнопку сворачивания и кнопку переворота (альбомный режим) в окне с текстом песни.", SharedConfig.playerLyricsHideControls, false);
                            break;
                        case "hide_all_chats":
                            cell.setTextAndCheckAndSubText("Скрыть папку «Все чаты»", "Убирает вкладку «Все чаты» из списка папок (нужна хотя бы одна своя папка).", SharedConfig.hideAllChatsFolder, false);
                            break;
case "hide_contacts":
                    cell.setTextAndCheckAndSubText("Скрыть «Контакты» внизу", "Прячет кнопку «Контакты» из нижней панели навигации.", SharedConfig.hideContactsBar, false);
                    break;
                case "old_design":
                    cell.setTextAndCheckAndSubText("Боковое меню", "Показывать классическое боковое меню на экране чатов: аккаунты, Архив, создать группу/канал, Контакты, Звонки, Настройки.", SharedConfig.oldDesign, false);
                    break;
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position < 0 || position >= rows.size()) {
                return TYPE_CHECK;
            }
            return rows.get(position).type;
        }
    }
}
