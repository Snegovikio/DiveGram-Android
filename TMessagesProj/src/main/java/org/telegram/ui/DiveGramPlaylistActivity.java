package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.DiveGramPlaylist;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ProfileMusicCardView;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;

public class DiveGramPlaylistActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter adapter;
    private TextView emptyView;
    private final ArrayList<DiveGramPlaylist.Item> items = new ArrayList<>();

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Плейлист");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    confirmClear();
                }
            }
        });
        final ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(1, R.drawable.ic_ab_other);

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        adapter = new ListAdapter(context);
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < items.size()) {
                DiveGramPlaylist.play(currentAccount, position);
            }
        });
        ((FrameLayout) fragmentView).addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setText("Плейлист пуст\nДобавляйте треки из меню плеера");
        emptyView.setTextSize(15);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        ((FrameLayout) fragmentView).addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 0, 32, 0));

        loadItems();
        return fragmentView;
    }

    private void loadItems() {
        items.clear();
        items.addAll(DiveGramPlaylist.getItems());
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (emptyView != null) {
            emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void confirmClear() {
        if (items.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(getParentActivity())
                .setTitle("Очистить плейлист?")
                .setPositiveButton("Очистить", (dialog, which) -> {
                    while (!items.isEmpty()) {
                        DiveGramPlaylist.remove(0);
                        items.remove(0);
                    }
                    loadItems();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void confirmRemove(int position) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        new AlertDialog.Builder(getParentActivity())
                .setTitle(items.get(position).title)
                .setMessage("Удалить трек из плейлиста?")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    DiveGramPlaylist.remove(position);
                    loadItems();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new PlaylistCardCell(mContext));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((PlaylistCardCell) holder.itemView).setItem(items.get(position), position);
        }
    }

    private class PlaylistCardCell extends FrameLayout {

        private final ProfileMusicCardView cardView;

        PlaylistCardCell(Context context) {
            super(context);
            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(102)));

            cardView = new ProfileMusicCardView(context, null);
            addView(cardView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        void setItem(DiveGramPlaylist.Item item, int position) {
            setOnClickListener(v -> DiveGramPlaylist.play(currentAccount, position));
            final MessageObject messageObject = DiveGramPlaylist.toMessageObject(currentAccount, item);
            if (messageObject != null) {
                cardView.setPlaylist(new ArrayList<>(Collections.singletonList(messageObject)));
            }
            cardView.setOnClickListener(v -> DiveGramPlaylist.play(currentAccount, position));
            cardView.setOnLongClickListener(v -> {
                confirmRemove(position);
                return true;
            });
        }
    }
}
