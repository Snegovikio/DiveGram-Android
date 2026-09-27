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
import android.widget.Toast;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashSet;

public class CleanChatsActivity extends BaseFragment {

    private static final long INACTIVE_DAYS = 30;
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CHECK = 1;
    private static final int TYPE_BUTTON = 2;

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    private final ArrayList<Row> rows = new ArrayList<>();
    private final HashSet<Long> selected = new HashSet<>();
    private final ArrayList<Long> deletedAccountIds = new ArrayList<>();

    private static class Row {
        final int type;
        final long dialogId;
        final String title;
        final String subtitle;

        Row(int type, long dialogId, String title, String subtitle) {
            this.type = type;
            this.dialogId = dialogId;
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Очистить список чатов");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listAdapter = new ListAdapter(context);
        rebuild();

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutAnimation(null);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= rows.size() || !view.isEnabled()) {
                return;
            }
            Row row = rows.get(position);
            if (row.type == TYPE_BUTTON) {
                performClear();
                return;
            }
            if (row.type != TYPE_CHECK) {
                return;
            }
            boolean value = !selected.contains(row.dialogId);
            if (value) {
                selected.add(row.dialogId);
            } else {
                selected.remove(row.dialogId);
            }
            TextCheckCell cell = findCheckCell(view);
            if (cell != null) {
                cell.setChecked(value);
            }
            updateButtonText();
        });
        return fragmentView;
    }

    private static TextCheckCell findCheckCell(View view) {
        if (view instanceof TextCheckCell) {
            return (TextCheckCell) view;
        }
        if (view instanceof ViewGroup) {
            View child = ((ViewGroup) view).getChildAt(0);
            if (child instanceof TextCheckCell) {
                return (TextCheckCell) child;
            }
        }
        return null;
    }

    private void updateButtonText() {
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (rows.get(i).type != TYPE_BUTTON) {
                continue;
            }
            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(i);
            if (holder != null && holder.itemView instanceof TextButtonCell) {
                TextButtonCell cell = (TextButtonCell) holder.itemView;
                int count = selectedCount();
                cell.setTitle(count > 0 ? rows.get(i).title + " (" + count + ")" : rows.get(i).title);
                cell.setSubtitle(count > 0 ? "Будет очищено чатов: " + count : "Выберите чаты для очистки");
            }
            break;
        }
    }

    private void performClear() {
        int count = 0;
        for (Row row : rows) {
            if (row.type == TYPE_CHECK && selected.contains(row.dialogId)) {
                count++;
            }
        }
        if (count == 0) {
            Toast.makeText(getParentActivity(), "Ничего не выбрано", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Очистить список чатов");
        builder.setMessage("Будет очищено чатов: " + count + ".\nИз групп и каналов вы выйдете, личные диалоги будут удалены, боты заблокированы и удалены.");
        builder.setPositiveButton("Очистить", (dialog, which) -> {
            MessagesController messagesController = MessagesController.getInstance(getCurrentAccount());
            TLRPC.User selfUser = UserConfig.getInstance(getCurrentAccount()).getCurrentUser();
            TLRPC.InputPeer selfPeer = selfUser != null ? MessagesController.getInputPeer(selfUser) : null;
            int cleared = 0;
            for (Row row : rows) {
                if (row.type != TYPE_CHECK || !selected.contains(row.dialogId)) {
                    continue;
                }
                long did = row.dialogId;
                if (did == 0) {
                    for (long d : deletedAccountIds) {
                        messagesController.deleteDialog(d, 0);
                    }
                    cleared += deletedAccountIds.size();
                } else if (did < 0) {
                    TLRPC.Chat chat = messagesController.getChat(-did);
                    if (chat != null && selfPeer != null) {
                        messagesController.deleteParticipantFromChat(-did, selfPeer, false, false);
                    } else if (chat != null) {
                        messagesController.deleteDialog(did, 0);
                    }
                    cleared++;
                } else {
                    TLRPC.User user = messagesController.getUser(did);
                    if (user != null && user.bot) {
                        messagesController.blockPeer(did);
                    }
                    messagesController.deleteDialog(did, 0);
                    cleared++;
                }
            }
            Toast.makeText(getParentActivity(), "Очищено чатов: " + cleared, Toast.LENGTH_SHORT).show();
            rebuild();
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void rebuild() {
        ArrayList<Row> newRows = buildRows();
        if (listAdapter == null) {
            rows.clear();
            rows.addAll(newRows);
            return;
        }
        if (rows.isEmpty()) {
            rows.clear();
            rows.addAll(newRows);
            listAdapter.notifyDataSetChanged();
            return;
        }
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return rows.size();
            }

            @Override
            public int getNewListSize() {
                return newRows.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                Row a = rows.get(oldItemPosition);
                Row b = newRows.get(newItemPosition);
                return a.type == b.type && a.dialogId == b.dialogId && a.title.equals(b.title);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Row a = rows.get(oldItemPosition);
                Row b = newRows.get(newItemPosition);
                if (!a.title.equals(b.title)) {
                    return false;
                }
                if (a.subtitle == null) {
                    return b.subtitle == null;
                }
                return a.subtitle.equals(b.subtitle);
            }
        });
        rows.clear();
        rows.addAll(newRows);
        diffResult.dispatchUpdatesTo(listAdapter);
    }

    private ArrayList<Row> buildRows() {
        ArrayList<Row> newRows = new ArrayList<>();
        selected.clear();
        deletedAccountIds.clear();

        MessagesController messagesController = MessagesController.getInstance(getCurrentAccount());
        long now = System.currentTimeMillis() / 1000;
        ArrayList<Long> groups = new ArrayList<>();
        ArrayList<Long> users = new ArrayList<>();
        ArrayList<Long> bots = new ArrayList<>();

        for (int a = 0; a < messagesController.dialogs_dict.size(); a++) {
            long did = messagesController.dialogs_dict.keyAt(a);
            TLRPC.Dialog dialog = messagesController.dialogs_dict.valueAt(a);
            boolean inactive = dialog.last_message_date <= 0 || now - dialog.last_message_date > INACTIVE_DAYS * 86400L;
            if (did > 0) {
                TLRPC.User user = messagesController.getUser(did);
                if (user == null) {
                    continue;
                }
                if (UserObject.isDeleted(user)) {
                    deletedAccountIds.add(did);
                } else if (user.bot) {
                    if (inactive) {
                        bots.add(did);
                    }
                } else {
                    if (inactive) {
                        users.add(did);
                    }
                }
            } else {
                TLRPC.Chat chat = messagesController.getChat(-did);
                if (chat != null && inactive) {
                    groups.add(did);
                }
            }
        }

        if (!deletedAccountIds.isEmpty()) {
            newRows.add(new Row(TYPE_HEADER, 0, "Удалённые аккаунты", null));
            newRows.add(new Row(TYPE_CHECK, 0, "Удалённые аккаунты (" + deletedAccountIds.size() + ")", "Диалоги с удалёнными аккаунтами будут удалены"));
            selected.add(0L);
        }
        if (!groups.isEmpty()) {
            newRows.add(new Row(TYPE_HEADER, 0, "Группы и каналы", null));
            for (long did : groups) {
                TLRPC.Chat chat = messagesController.getChat(-did);
                if (chat == null) {
                    continue;
                }
                newRows.add(new Row(TYPE_CHECK, did, chat.title != null ? chat.title : "Чат " + (-did), lastMessageText(messagesController.dialogs_dict.get(did))));
            }
        }
        if (!users.isEmpty()) {
            newRows.add(new Row(TYPE_HEADER, 0, "Личные сообщения", null));
            for (long did : users) {
                TLRPC.User user = messagesController.getUser(did);
                if (user == null) {
                    continue;
                }
                newRows.add(new Row(TYPE_CHECK, did, ContactsController.formatName(user.first_name, user.last_name), lastMessageText(messagesController.dialogs_dict.get(did))));
            }
        }
        if (!bots.isEmpty()) {
            newRows.add(new Row(TYPE_HEADER, 0, "Боты", null));
            for (long did : bots) {
                TLRPC.User user = messagesController.getUser(did);
                if (user == null) {
                    continue;
                }
                newRows.add(new Row(TYPE_CHECK, did, ContactsController.formatName(user.first_name, user.last_name), lastMessageText(messagesController.dialogs_dict.get(did))));
            }
        }
        if (!newRows.isEmpty()) {
            newRows.add(new Row(TYPE_BUTTON, 0, "Очистить чаты", null));
        } else {
            newRows.add(new Row(TYPE_HEADER, 0, "Нет чатов для очистки", null));
        }
        return newRows;
    }

    private String lastMessageText(TLRPC.Dialog dialog) {
        if (dialog == null || dialog.last_message_date <= 0) {
            return "Давно не было активности";
        }
        return "Последняя активность: " + LocaleController.formatDateChat(dialog.last_message_date);
    }

    private int selectedCount() {
        int count = 0;
        for (Row row : rows) {
            if (row.type == TYPE_CHECK && selected.contains(row.dialogId)) {
                count++;
            }
        }
        return count;
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

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            if (position < 0 || position >= rows.size()) {
                return false;
            }
            return rows.get(position).type != TYPE_HEADER;
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
                default:
                    view = createRoundedCard(mContext, new TextCheckCell(mContext));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    cell.setText(row.title);
                    break;
                }
                case TYPE_BUTTON: {
                    TextButtonCell cell = (TextButtonCell) holder.itemView;
                    int count = selectedCount();
                    cell.setTitle(count > 0 ? row.title + " (" + count + ")" : row.title);
                    cell.setSubtitle(count > 0 ? "Будет очищено чатов: " + count : "Выберите чаты для очистки");
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
                    cell.setTextAndCheckAndSubText(row.title, row.subtitle, selected.contains(row.dialogId), false);
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).type;
        }
    }
}