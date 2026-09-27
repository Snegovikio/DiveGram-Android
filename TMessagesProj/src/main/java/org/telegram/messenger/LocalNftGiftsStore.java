/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger;

import android.content.SharedPreferences;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LocalNftGiftsStore {

    private static ArrayList<TL_stars.TL_starGiftUnique> gifts;
    private static final ArrayList<Long> pinnedIds = new ArrayList<>();
    private static final Object lock = new Object();
    private static int loadedAccount = -1;

    private static int currentAccount() {
        return UserConfig.selectedAccount;
    }

    private static File getFile() {
        File dir = ApplicationLoader.getFilesDirFixed("");
        File perAccount = new File(dir, "nft_local_gifts_a" + currentAccount() + ".dat");
        if (!perAccount.exists()) {
            File legacy = new File(dir, "nft_local_gifts.dat");
            SharedPreferences p = MessagesController.getGlobalMainSettings();
            if (!p.getBoolean("dgNftLegacyMigrated", false) && legacy.exists()) {
                legacy.renameTo(perAccount);
                p.edit().putBoolean("dgNftLegacyMigrated", true).apply();
            }
        }
        return perAccount;
    }

    private static void load() {
        synchronized (lock) {
            final int account = currentAccount();
            if (gifts != null && loadedAccount == account) return;
            gifts = new ArrayList<>();
            pinnedIds.clear();
            loadedAccount = account;
            try {
                File file = getFile();
                if (file.exists() && file.length() > 0) {
                    byte[] data = new byte[(int) file.length()];
                    try (FileInputStream in = new FileInputStream(file)) {
                        in.read(data);
                    }
                    SerializedData data2 = new SerializedData(data);
                    int count = data2.readInt32(false);
                    for (int i = 0; i < count && data2.getPosition() < data.length; i++) {
                        TL_stars.SavedStarGift saved = TL_stars.SavedStarGift.TLdeserialize(data2, data2.readInt32(false), false);
                        if (saved != null && saved.gift instanceof TL_stars.TL_starGiftUnique) {
                            gifts.add((TL_stars.TL_starGiftUnique) saved.gift);
                        }
                    }
                    if (data2.getPosition() + 4 <= data.length && !gifts.isEmpty()) {
                        int pinnedCount = data2.readInt32(false);
                        for (int i = 0; i < pinnedCount && data2.getPosition() + 8 <= data.length; i++) {
                            pinnedIds.add(data2.readInt64(false));
                        }
                    }
                    data2.cleanup();
                }
            } catch (Throwable t) {
                FileLog.e(t);
                gifts = new ArrayList<>();
            }
        }
    }

    private static void save() {
        try {
            File file = getFile();
            SerializedData data = new SerializedData();
            data.writeInt32(gifts.size());
            for (TL_stars.TL_starGiftUnique gift : gifts) {
                TL_stars.TL_savedStarGift saved = new TL_stars.TL_savedStarGift();
                saved.gift = gift;
                saved.date = ConnectionsManager.getInstance(UserConfig.selectedAccount).getCurrentTime();
                saved.serializeToStream(data);
            }
            data.writeInt32(pinnedIds.size());
            for (Long id : pinnedIds) {
                data.writeInt64(id);
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(data.toByteArray());
            }
            data.cleanup();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static List<TL_stars.TL_starGiftUnique> getAll() {
        load();
        return new ArrayList<>(gifts);
    }

    public static boolean contains(long giftId) {
        load();
        for (TL_stars.TL_starGiftUnique g : gifts) {
            if (g.id == giftId) return true;
        }
        return false;
    }

    public static void add(TL_stars.TL_starGiftUnique gift) {
        if (gift == null || contains(gift.id)) return;
        load();
        gifts.add(0, makeOwned(gift));
        save();
        DiveGramNfts.scheduleUpload();
    }

    private static TL_stars.TL_starGiftUnique makeOwned(TL_stars.TL_starGiftUnique src) {
        TL_stars.TL_starGiftUnique gift = src;
        try {
            org.telegram.tgnet.SerializedData out = new org.telegram.tgnet.SerializedData();
            src.serializeToStream(out);
            byte[] bytes = out.toByteArray();
            org.telegram.tgnet.SerializedData in = new org.telegram.tgnet.SerializedData(bytes);
            TL_stars.StarGift parsed = TL_stars.StarGift.TLdeserialize(in, in.readInt32(false), false);
            if (parsed instanceof TL_stars.TL_starGiftUnique) {
                gift = (TL_stars.TL_starGiftUnique) parsed;
            }
            out.cleanup();
            in.cleanup();
        } catch (Throwable t) {
            FileLog.e(t);
        }
        TLRPC.User me = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
        if (me != null) {
            TLRPC.TL_peerUser peer = new TLRPC.TL_peerUser();
            peer.user_id = me.id;
            gift.owner_id = peer;
            gift.owner_name = UserObject.getUserName(me);
        }
        gift.host_id = null;
        gift.resell_amount = null;
        gift.resell_min_stars = 0;
        gift.offer_min_stars = 0;
        return gift;
    }

    public static void remove(long giftId) {
        load();
        Iterator<TL_stars.TL_starGiftUnique> it = gifts.iterator();
        while (it.hasNext()) {
            if (it.next().id == giftId) {
                it.remove();
                save();
                DiveGramNfts.scheduleUpload();
                return;
            }
        }
    }

    public static List<TL_stars.TL_starGiftUnique> getPinned() {
        load();
        ArrayList<TL_stars.TL_starGiftUnique> out = new ArrayList<>();
        for (TL_stars.TL_starGiftUnique g : gifts) {
            if (pinnedIds.contains(g.id)) out.add(g);
        }
        return out;
    }

    public static boolean isPinned(long giftId) {
        load();
        return pinnedIds.contains(giftId);
    }

    public static void setPinned(long giftId, boolean pinned) {
        load();
        if (pinned) {
            if (!pinnedIds.contains(giftId)) pinnedIds.add(giftId);
        } else {
            pinnedIds.remove((Long) giftId);
        }
        save();
        DiveGramNfts.scheduleUpload();
    }

    public static String giftToBase64(TL_stars.TL_starGiftUnique gift) {
        if (gift == null) return null;
        try {
            SerializedData out = new SerializedData();
            gift.serializeToStream(out);
            byte[] bytes = out.toByteArray();
            out.cleanup();
            return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    public static TL_stars.TL_starGiftUnique giftFromBase64(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP);
            SerializedData in = new SerializedData(bytes);
            TL_stars.StarGift parsed = TL_stars.StarGift.TLdeserialize(in, in.readInt32(false), false);
            TL_stars.TL_starGiftUnique gift = parsed instanceof TL_stars.TL_starGiftUnique ? (TL_stars.TL_starGiftUnique) parsed : null;
            in.cleanup();
            return gift;
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }
}
