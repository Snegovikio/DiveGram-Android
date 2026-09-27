/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LocalNftGiftsStore;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Gifts.ResaleGiftsFragment;
import org.telegram.ui.PeerColorActivity;
import org.telegram.ui.Stars.StarsController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class DiveGramLocalGiftsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_GIFT = 1;
    private static final int TYPE_SLUG = 2;
    private static final int TYPE_INFO = 3;
    private static final int TYPE_LOADING = 4;

    private static final ArrayList<TL_stars.TL_starGiftUnique> cachedGifts = new ArrayList<>();
    private static final HashSet<Long> cachedSeenIds = new HashSet<>();
    private static int cachedAccount = -1;

    private final ArrayList<TL_stars.TL_starGiftUnique> allGifts = new ArrayList<>();
    private final ArrayList<TL_stars.TL_starGiftUnique> filteredGifts = new ArrayList<>();
    private String searchQuery = "";
    private final HashSet<Long> seenIds = new HashSet<>();
    private final HashSet<Long> requestedPreviews = new HashSet<>();
    private final ArrayList<ResaleGiftsFragment.ResaleGiftsList> lists = new ArrayList<>();
    private final HashSet<ResaleGiftsFragment.ResaleGiftsList> finishedLists = new HashSet<>();
    private final HashMap<ResaleGiftsFragment.ResaleGiftsList, Integer> loadAttempts = new HashMap<>();
    private int loadingLists;
    private boolean catalogRequested;
    private ListAdapter adapter;
    private RecyclerListView listView;

    {
        restoreCache();
    }

    private void restoreCache() {
        if (cachedAccount != -1 && cachedAccount != currentAccount) {
            cachedGifts.clear();
            cachedSeenIds.clear();
        }
        cachedAccount = currentAccount;
        allGifts.clear();
        seenIds.clear();
        allGifts.addAll(cachedGifts);
        seenIds.addAll(cachedSeenIds);
    }

    private void updateCache() {
        cachedAccount = currentAccount;
        cachedGifts.clear();
        cachedGifts.addAll(allGifts);
        cachedSeenIds.clear();
        cachedSeenIds.addAll(seenIds);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Добавить НФТ локально");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    showSlugDialog();
                }
            }
        });
        actionBar.createMenu().addItem(1, R.drawable.outline_header_search);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        adapter = new ListAdapter();
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new GridLayoutManager(context, 3));
        listView.setAdapter(adapter);
        listView.setClipToPadding(false);
        listView.setPadding(0, AndroidUtilities.dp(48), 0, AndroidUtilities.dp(16));
        listView.setOnItemClickListener((view, position) -> {
            if (position >= 2 && position - 2 < filteredGifts.size()) {
                showGiftActions(filteredGifts.get(position - 2));
            }
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        android.widget.EditText searchField = new android.widget.EditText(context);
        searchField.setHint("Поиск: название или номер…");
        searchField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        searchField.setSingleLine(true);
        searchField.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        searchField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        searchField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        searchField.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        searchField.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                searchQuery = s.toString().trim().toLowerCase();
                refilter();
                notifyChanged();
            }
        });
        frameLayout.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        return fragmentView;
    }

    private void refilter() {
        filteredGifts.clear();
        for (TL_stars.TL_starGiftUnique g : allGifts) {
            if (matchesSearch(g)) {
                filteredGifts.add(g);
            }
        }
    }

    private boolean matchesSearch(TL_stars.TL_starGiftUnique g) {
        if (searchQuery.isEmpty()) return true;
        String q = searchQuery.replace("#", "").trim();
        try {
            if (g.num != 0 && String.valueOf(g.num).equals(q)) {
                return true;
            }
        } catch (Exception ignored) {}
        for (TL_stars.StarGiftAttribute attr : g.attributes) {
            if (attr instanceof TL_stars.starGiftAttributeModel && ((TL_stars.starGiftAttributeModel) attr).name != null
                    && ((TL_stars.starGiftAttributeModel) attr).name.toLowerCase().contains(searchQuery)) return true;
            if (attr instanceof TL_stars.starGiftAttributePattern && ((TL_stars.starGiftAttributePattern) attr).name != null
                    && ((TL_stars.starGiftAttributePattern) attr).name.toLowerCase().contains(searchQuery)) return true;
            if (attr instanceof TL_stars.starGiftAttributeBackdrop && ((TL_stars.starGiftAttributeBackdrop) attr).name != null
                    && ((TL_stars.starGiftAttributeBackdrop) attr).name.toLowerCase().contains(searchQuery)) return true;
        }
        if (g.title != null && g.title.toLowerCase().contains(searchQuery)) return true;
        if (g.slug != null && g.slug.toLowerCase().contains(searchQuery)) return true;
        return false;
    }

    @Override
    public void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();
        requestCatalog();
    }

    @Override
    public void onResume() {
        super.onResume();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starGiftsLoaded);
        requestCatalog();
    }

    @Override
    public void onPause() {
        super.onPause();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starGiftsLoaded);
        AndroidUtilities.cancelRunOnUIThread(loadingWatchdog);
    }

    private void requestCatalog() {
        if (!catalogRequested) {
            catalogRequested = true;
            StarsController.getInstance(currentAccount).loadStarGifts();
        } else {
            StarsController.getInstance(currentAccount).loadStarGifts();
        }
        startLoadingWatchdog();
        if (lists.isEmpty()) {
            buildResaleLists();
        }
    }

    private void buildResaleLists() {
        if (!lists.isEmpty()) return;
        ArrayList<TL_stars.StarGift> catalog = StarsController.getInstance(currentAccount).gifts;
        for (TL_stars.StarGift gift : catalog) {
            if (gift instanceof TL_stars.TL_starGift && gift.availability_resale > 0) {
                final ResaleGiftsFragment.ResaleGiftsList[] holder = new ResaleGiftsFragment.ResaleGiftsList[1];
                holder[0] = new ResaleGiftsFragment.ResaleGiftsList(currentAccount, gift.id, first -> {
                    if (first != null && first && holder[0] != null) {
                        mergeList(holder[0]);
                    }
                });
                lists.add(holder[0]);
            }
        }
        loadingLists = lists.size();
        for (int i = lists.size() - 1; i >= 0; i--) {
            lists.get(i).load();
        }
        notifyChanged();
    }

    private final Runnable loadingWatchdog = new Runnable() {
        @Override
        public void run() {
            if (lists.isEmpty()) {
                return;
            }
            boolean anyPending = false;
            for (ResaleGiftsFragment.ResaleGiftsList list : lists) {
                if (finishedLists.contains(list)) {
                    continue;
                }
                if (list.loading) {
                    anyPending = true;
                    continue;
                }
                Integer attemptsBox = loadAttempts.get(list);
                int attempts = attemptsBox == null ? 0 : attemptsBox;
                if (attempts < 3) {
                    loadAttempts.put(list, attempts + 1);
                    list.load();
                    anyPending = true;
                }
            }
            if (anyPending) {
                AndroidUtilities.runOnUIThread(this, 5000);
            } else {
                loadingLists = 0;
                notifyChanged();
            }
        }
    };

    private void startLoadingWatchdog() {
        AndroidUtilities.cancelRunOnUIThread(loadingWatchdog);
        AndroidUtilities.runOnUIThread(loadingWatchdog, 5000);
    }

    @SuppressWarnings("unchecked")
    private void mergeList(ResaleGiftsFragment.ResaleGiftsList list) {
        finishedLists.add(list);
        loadingLists--;
        boolean changed = false;
        for (TL_stars.TL_starGiftUnique g : list.gifts) {
            if (seenIds.add(g.id)) {
                allGifts.add(g);
                changed = true;
            }
        }
        if (changed) {
            refilter();
            updateCache();
        }
        AndroidUtilities.runOnUIThread(this::notifyChanged);
    }

    private void updateLoadingCells() {
        notifyChanged();
    }

    private void notifyChanged() {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void showSlugDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle("Добавить по slug");
        builder.setMessage("Введите slug подарка из ссылки t.me/nft/…");
        final EditText input = new EditText(getParentActivity());
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray3, getResourceProvider()));
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setSingleLine(true);
        input.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(12), AndroidUtilities.dp(24), AndroidUtilities.dp(12));
        input.setHint("Slug НФТ-подарка");
        FrameLayout container = new FrameLayout(getParentActivity());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        container.addView(input, lp);
        builder.setView(container);
        builder.setPositiveButton("Найти", (d, w) -> {
            String slug = input.getText().toString().trim();
            if (!slug.isEmpty()) {
                findBySlug(slug);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void findBySlug(String slug) {
        AlertDialog progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progressDialog.showDelayed(200);
        TL_stars.TL_inputSavedStarGiftSlug input = new TL_stars.TL_inputSavedStarGiftSlug();
        input.slug = slug;
        StarsController.getInstance(currentAccount).getUserStarGift(input, savedStarGift -> AndroidUtilities.runOnUIThread(() -> {
            try {
                progressDialog.dismiss();
            } catch (Exception ignored) {
            }
            if (savedStarGift == null || !(savedStarGift.gift instanceof TL_stars.TL_starGiftUnique)) {
                BulletinFactory.of(DiveGramLocalGiftsActivity.this)
                        .createErrorBulletin("Подарок не найден")
                        .show();
                return;
            }
            TL_stars.TL_starGiftUnique foundGift = (TL_stars.TL_starGiftUnique) savedStarGift.gift;
            if (LocalNftGiftsStore.contains(foundGift.id)) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Уже есть в профиле").show();
            } else {
                LocalNftGiftsStore.add(foundGift);
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Добавлено в профиль").show();
            }
            refreshProfileGiftsCounter();
        }));
    }

    private void showGiftActions(TL_stars.TL_starGiftUnique gift) {
        if (getParentActivity() == null) return;
        String title = (gift.title != null ? gift.title : "Подарок") + (gift.num > 0 ? " #" + gift.num : "");
        org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity(), getResourceProvider());
        builder.setTitle(title);
        boolean alreadyAdded = LocalNftGiftsStore.contains(gift.id);
        CharSequence[] items = new CharSequence[]{
                alreadyAdded ? "Убрать из профиля" : "Добавить в мой профиль (локально)"
        };
        builder.setItems(items, (d, w) -> {
            if (w == 0) {
                if (alreadyAdded) {
                    LocalNftGiftsStore.remove(gift.id);
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Убрано из профиля").show();
                } else {
                    LocalNftGiftsStore.add(gift);
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Добавлено в профиль").show();
                }
            }
            refreshProfileGiftsCounter();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void refreshProfileGiftsCounter() {
        if (parentLayout == null) return;
        for (BaseFragment f : parentLayout.getFragmentStack()) {
            if (f instanceof ProfileActivity) {
                ((ProfileActivity) f).updateSelectedMediaTabText();
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.starGiftsLoaded) {
            buildResaleLists();
            notifyChanged();
        }
    }

    private class ListAdapter extends RecyclerView.Adapter {

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_HEADER:
                    HeaderCell headerCell = new HeaderCell(getContext(), getResourceProvider());
                    headerCell.setText("Все НФТ-подарки Telegram");
                    headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = headerCell;
                    break;
                case TYPE_GIFT:
                    PeerColorActivity.GiftCell cell = new PeerColorActivity.GiftCell(getContext(), true, getResourceProvider());
                    view = cell;
                    break;
                case TYPE_SLUG:
                    TextCell textCell = new TextCell(getContext());
                    textCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    textCell.setText("Добавить по ссылке/slug…", false);
                    textCell.setOnClickListener(v -> showSlugDialog());
                    view = textCell;
                    break;
                case TYPE_INFO:
                    TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(getContext());
                    infoCell.setText("Подарок применяется к профилю и сохраняется локально. Тапни по подарку, чтобы надеть его.");
                    infoCell.setBackground(Theme.getThemedDrawableByKey(getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    view = infoCell;
                    break;
                case TYPE_LOADING:
                default:
                    FlickerLoadingView flicker = new FlickerLoadingView(getContext());
                    flicker.setViewType(FlickerLoadingView.STAR_GIFT);
                    flicker.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = flicker;
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == TYPE_GIFT && position - 2 < filteredGifts.size()) {
                ((PeerColorActivity.GiftCell) holder.itemView).set(position - 2, filteredGifts.get(position - 2));
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) return TYPE_HEADER;
            if (position == 1) return TYPE_SLUG;
            int giftCount = filteredGifts.size();
            if (position < 2 + giftCount) return TYPE_GIFT;
            if (loadingLists > 0) return TYPE_LOADING;
            return TYPE_INFO;
        }

        @Override
        public int getItemCount() {
            int count = 2 + filteredGifts.size();
            if (loadingLists > 0) count += 9;
            else count += 1;
            return count;
        }
    }
}
