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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Collections;

public class ClassicSideMenuView extends FrameLayout {

    private final MainTabsActivity mainTabsActivity;
    private final View scrimView;
    private final LinearLayout drawerPanel;

    private int drawerWidth;
    private int currentPosition;
    private boolean isOpen;
    private boolean storiesMenuHasStories;
    private float storiesMenuProgress;
    private int insetLeft;
    private int insetTop;
    private static final int CONTENT_TOP_EXTRA_PADDING = 72;

    public ClassicSideMenuView(MainTabsActivity activity) {
        super(activity.getContext());
        mainTabsActivity = activity;

        scrimView = new View(getContext());
        scrimView.setBackgroundColor(0x00000000);
        scrimView.setVisibility(GONE);

        drawerPanel = new LinearLayout(getContext());
        drawerPanel.setOrientation(LinearLayout.VERTICAL);
        applyTopPadding();
        drawerPanel.setBackground(Theme.createRoundRectDrawable(0, AndroidUtilities.dp(16), AndroidUtilities.dp(16), 0, ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhite), 0xFFFFFFFF, 0.07f)));
        drawerPanel.addView(createContent());

        addView(scrimView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.LEFT | Gravity.TOP);
        panelLp.leftMargin = 0;
        panelLp.topMargin = 0;
        addView(drawerPanel, panelLp);

        drawerWidth = 0;
        setVisibility(GONE);
    }

    private int getHiddenTranslationX() {
        return -(drawerWidth + insetLeft) - AndroidUtilities.dp(8);
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()) != null) {
            setInsetLeft(insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()).left);
            setInsetTop(insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()).top);
        }
        return super.onApplyWindowInsets(insets);
    }

    private void applyTopPadding() {
        drawerPanel.setPadding(insetLeft, insetTop + AndroidUtilities.dp(25 + CONTENT_TOP_EXTRA_PADDING), 0, 0);
    }

    public void setInsetTop(int top) {
        if (insetTop == top) {
            return;
        }
        insetTop = top;
        applyTopPadding();
    }

    public void setInsetLeft(int left) {
        if (insetLeft == left) {
            return;
        }
        insetLeft = left;
        applyTopPadding();
        if (!isOpen && drawerWidth > 0) {
            drawerPanel.setTranslationX(getHiddenTranslationX());
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        int w = (int) (getMeasuredWidth() * 0.78f);
        if (w > 0 && w != drawerWidth) {
            drawerWidth = w;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) drawerPanel.getLayoutParams();
            lp.width = drawerWidth;
            drawerPanel.setLayoutParams(lp);
            if (!isOpen) {
                drawerPanel.setTranslationX(getHiddenTranslationX());
            }
        }
    }

    private View createContent() {
        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);

        content.addView(createHeader());

        content.addView(createAccountBar());

        LinearLayout rowList = new LinearLayout(getContext());
        rowList.setOrientation(LinearLayout.VERTICAL);
        rowList.addView(createRow(R.drawable.msg_archive, LocaleController.getString(R.string.Archive), this::openArchive));
        rowList.addView(createRow(R.drawable.outline_saved_24, LocaleController.getString(R.string.SavedMessages), this::openSavedMessages));
        rowList.addView(createRow(R.drawable.msg_contacts_name, LocaleController.getString(R.string.Contacts), this::openContacts));
        rowList.addView(createRow(R.drawable.msg_calls, LocaleController.getString(R.string.Calls), this::openCalls));
        rowList.addView(createRow(R.drawable.settings_group, LocaleController.getString(R.string.NewGroup), this::openCreateGroup));
        rowList.addView(createRow(R.drawable.settings_channel, LocaleController.getString(R.string.NewChannel), this::openCreateChannel));
        rowList.addView(createRow(R.drawable.settings_invite, LocaleController.getString(R.string.InviteFriends), this::openInvite));
        rowList.addView(createRow(R.drawable.outline_profile_settings, LocaleController.getString(R.string.Settings), this::openSettings));
        content.addView(rowList, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        return content;
    }

    private View createHeader() {
        final int account = mainTabsActivity.getCurrentAccount();
        TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        if (user == null) {
            user = MessagesController.getInstance(account).getUser(UserConfig.getInstance(account).getClientUserId());
        }
        String name = user != null ? UserObject.getUserName(user) : "";
        String sub;
        if (user != null && user.username != null) {
            sub = "@" + user.username;
        } else {
            sub = user != null && user.phone != null ? "+" + user.phone : "";
        }

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        header.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector), 0, 0));
        header.setOnClickListener(v -> openProfile());

        FrameLayout avatarContainer = new FrameLayout(getContext());
        BackupImageView avatarView = new BackupImageView(getContext());
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        if (user != null) {
            avatarDrawable.setInfo(user);
            avatarView.setForUserOrChat(user, avatarDrawable);
        }
        avatarView.setRoundRadius(AndroidUtilities.dp(21));
        avatarView.getImageReceiver().setCurrentAccount(account);
        avatarContainer.addView(avatarView, LayoutHelper.createFrame(42, 42, Gravity.CENTER));
        header.addView(avatarContainer, LayoutHelper.createLinear(42, 42, Gravity.CENTER_VERTICAL));

        LinearLayout texts = new LinearLayout(getContext());
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView nameView = new TextView(getContext());
        nameView.setText(name);
        nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameView.setTextSize(16);
        nameView.setTypeface(AndroidUtilities.bold());
        texts.addView(nameView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView subView = new TextView(getContext());
        subView.setText(sub);
        subView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subView.setTextSize(13);
        texts.addView(subView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        header.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 10, 0, 0, 0));

        return header;
    }

    private View createRow(int icon, String text, Runnable action) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(8));
        row.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector), 0, 0));

        ImageView iconView = new ImageView(getContext());
        iconView.setImageResource(icon);
        iconView.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        row.addView(iconView, LayoutHelper.createLinear(22, 22, Gravity.CENTER_VERTICAL, 0, 0, 4, 0));

        TextView textView = new TextView(getContext());
        textView.setText(text);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(15);
        row.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));

        row.setOnClickListener(v -> {
            if (action != null) {
                action.run();
            }
        });
        return row;
    }

    private View createAccountBar() {
        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(2), AndroidUtilities.dp(6), AndroidUtilities.dp(2));

        final ArrayList<Integer> accountNumbers = new ArrayList<>();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                accountNumbers.add(a);
            }
        }
        Collections.sort(accountNumbers, (o1, o2) -> {
            long l1 = UserConfig.getInstance(o1).loginTime;
            long l2 = UserConfig.getInstance(o2).loginTime;
            if (l1 > l2) return 1;
            else if (l1 < l2) return -1;
            return 0;
        });

        int current = UserConfig.selectedAccount;

        for (int acc : accountNumbers) {
            final int account = acc;
            bar.addView(createAccountRow(account, account == current));
        }

        if (UserConfig.getActivatedAccountsCount() < UserConfig.MAX_ACCOUNT_COUNT) {
            bar.addView(createAddAccountRow());
        }

        return bar;
    }

    private View createAccountRow(final int account, boolean selected) {
        TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        if (user == null) {
            user = MessagesController.getInstance(account).getUser(UserConfig.getInstance(account).getClientUserId());
        }

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(5), AndroidUtilities.dp(10), AndroidUtilities.dp(5));
        row.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector), 0, 0));

        row.addView(accountAvatar(account, selected), LayoutHelper.createLinear(50, 50, Gravity.CENTER_VERTICAL));

        LinearLayout texts = new LinearLayout(getContext());
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView nameView = new TextView(getContext());
        nameView.setText(user != null ? UserObject.getUserName(user) : "");
        nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameView.setTextSize(15);
        if (selected) {
            nameView.setTypeface(AndroidUtilities.bold());
        }
        texts.addView(nameView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView subView = new TextView(getContext());
        String sub = user != null && user.username != null ? "@" + user.username : (user != null && user.phone != null ? "+" + user.phone : "");
        subView.setText(sub);
        subView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subView.setTextSize(13);
        texts.addView(subView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 10, 0, 0, 0));

        row.setOnClickListener(v -> {
            close();
            if (account != mainTabsActivity.getCurrentAccount() && LaunchActivity.instance != null) {
                LaunchActivity.instance.switchToAccount(account, true);
            }
        });
        return row;
    }

    private View createAddAccountRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(5), AndroidUtilities.dp(10), AndroidUtilities.dp(5));
        row.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector), 0, 0));

        FrameLayout plusFrame = new FrameLayout(getContext());
        ImageView addIcon = new ImageView(getContext());
        addIcon.setImageResource(R.drawable.msg_addbot);
        addIcon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        plusFrame.addView(addIcon, LayoutHelper.createFrame(36, 36, Gravity.CENTER));
        row.addView(plusFrame, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL));

        TextView addText = new TextView(getContext());
        addText.setText(LocaleController.getString(R.string.AddAccount));
        addText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        addText.setTextSize(15);
        row.addView(addText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 10, 0, 0, 0));

        row.setOnClickListener(v -> openAddAccount());
        return row;
    }

    private FrameLayout accountAvatar(final int account, final boolean selected) {
        TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        if (user == null) {
            user = MessagesController.getInstance(account).getUser(UserConfig.getInstance(account).getClientUserId());
        }
        final AvatarDrawable avatarDrawable = new AvatarDrawable();
        if (user != null) {
            avatarDrawable.setInfo(user);
        }
        final FrameLayout container = new FrameLayout(getContext()) {
            private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                super.dispatchDraw(canvas);
                if (selected && UserConfig.selectedAccount == account) {
                    selectedPaint.setStyle(Paint.Style.STROKE);
                    selectedPaint.setStrokeWidth(AndroidUtilities.dp(3.5f));
                    selectedPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                    canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, AndroidUtilities.dp(20), selectedPaint);
                }
            }
        };
        container.setWillNotDraw(false);
        final BackupImageView avatarView = new BackupImageView(getContext());
        if (user != null) {
            avatarView.setForUserOrChat(user, avatarDrawable);
        }
        avatarView.setRoundRadius(AndroidUtilities.dp(18));
        avatarView.getImageReceiver().setCurrentAccount(account);
        container.addView(avatarView, LayoutHelper.createFrame(36, 36, Gravity.CENTER));
        return container;
    }

    public void updatePosition(int position) {
        currentPosition = position;
        if (!SharedConfig.oldDesign) {
            if (isOpen) {
                close(true);
            }
            setVisibility(GONE);
            return;
        }
        if (position == 0) {
            if (!isOpen) {
                if (getVisibility() != VISIBLE) {
                    setVisibility(VISIBLE);
                }
                drawerPanel.setTranslationX(getHiddenTranslationX());
                scrimView.setVisibility(GONE);
            }
        } else {
            if (isOpen) {
                close(true);
            }
            scrimView.setVisibility(GONE);
            setVisibility(VISIBLE);
        }
    }

    public void updatePositionAnimated(float position) {
        if (!SharedConfig.oldDesign || isOpen || position < 0) {
            return;
        }
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }
        currentPosition = Math.round(position);
    }

    public void setStoriesMenu(boolean hasStories, float expansionProgress) {
        storiesMenuHasStories = hasStories;
        storiesMenuProgress = expansionProgress;
    }

    public void setSearchShown(boolean shown) {
    }

    public void toggle() {
        if (isOpen) {
            close();
        } else {
            open();
        }
    }

    public void open() {
        if (isOpen || getVisibility() != VISIBLE || drawerWidth <= 0) {
            return;
        }
        isOpen = true;
        scrimView.setVisibility(VISIBLE);
        scrimView.setClickable(true);
        scrimView.setOnClickListener(v -> close());
        drawerPanel.setTranslationX(getHiddenTranslationX());
        drawerPanel.animate().setInterpolator(new CubicBezierInterpolator(0.4f, 0.0f, 0.2f, 1f)).setDuration(240).translationX(0f).start();
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void close() {
        close(false);
    }

    public void close(boolean immediate) {
        if (!isOpen) {
            return;
        }
        isOpen = false;
        if (immediate || drawerWidth <= 0) {
            drawerPanel.setTranslationX(getHiddenTranslationX());
            scrimView.setVisibility(GONE);
            scrimView.setClickable(false);
        } else {
            drawerPanel.animate().setInterpolator(new CubicBezierInterpolator(0.4f, 0.0f, 0.2f, 1f)).setDuration(200).translationX(getHiddenTranslationX()).start();
            scrimView.animate().setInterpolator(new CubicBezierInterpolator(0.4f, 0.0f, 0.2f, 1f)).setDuration(160).alpha(0f).withEndAction(() -> {
                scrimView.setVisibility(GONE);
                scrimView.setClickable(false);
            }).start();
        }
    }

    private void openArchive() {
        Bundle args = new Bundle();
        args.putInt("folderId", 1);
        present(new DialogsActivity(args));
    }

    private void openSavedMessages() {
        Bundle args = new Bundle();
        args.putLong("user_id", UserConfig.getInstance(mainTabsActivity.getCurrentAccount()).getClientUserId());
        present(new ChatActivity(args));
    }

    private void openContacts() {
        Bundle args = new Bundle();
        args.putBoolean("needPhonebook", true);
        args.putBoolean("needFinishFragment", false);
        args.putBoolean("hasMainTabs", true);
        present(new ContactsActivity(args));
    }

    private void openCalls() {
        Bundle args = new Bundle();
        args.putBoolean("needFinishFragment", false);
        args.putBoolean("hasMainTabs", true);
        present(new CallLogActivity(args));
    }

    private void openCreateGroup() {
        present(new GroupCreateActivity(new Bundle()));
    }

    private void openCreateChannel() {
        present(new ChannelCreateActivity(new Bundle()));
    }

    private void openInvite() {
        close();
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT, "https://telegram.org/dl");
        if (LaunchActivity.instance != null) {
            try {
                LaunchActivity.instance.startActivity(Intent.createChooser(sendIntent, LocaleController.getString(R.string.InviteFriends)));
            } catch (Exception ignore) {
            }
        }
    }

    private void openSettings() {
        Bundle args = new Bundle();
        args.putBoolean("hasMainTabs", true);
        present(new SettingsActivity(args));
    }

    private void openProfile() {
        Bundle args = new Bundle();
        args.putLong("user_id", UserConfig.getInstance(mainTabsActivity.getCurrentAccount()).getClientUserId());
        args.putBoolean("my_profile", true);
        args.putBoolean("hasMainTabs", true);
        present(new ProfileActivity(args));
    }

    private void openAddAccount() {
        int freeAccounts = 0;
        Integer availableAccount = null;
        for (int a = UserConfig.MAX_ACCOUNT_COUNT - 1; a >= 0; a--) {
            if (!UserConfig.getInstance(a).isClientActivated()) {
                freeAccounts++;
                if (availableAccount == null) {
                    availableAccount = a;
                }
            }
        }
        if (!UserConfig.hasPremiumOnAccounts()) {
            freeAccounts -= (UserConfig.MAX_ACCOUNT_COUNT - UserConfig.MAX_ACCOUNT_DEFAULT_COUNT);
        }
        if (freeAccounts > 0 && availableAccount != null) {
            present(new LoginActivity(availableAccount));
        } else {
            close();
        }
    }

    private void present(BaseFragment fragment) {
        close();
        AndroidUtilities.runOnUIThread(() -> {
            final DialogsActivity dialogs = mainTabsActivity != null ? mainTabsActivity.getDialogsActivity() : null;
            if (dialogs != null) {
                dialogs.presentFragment(fragment);
            }
        }, 200);
    }
}