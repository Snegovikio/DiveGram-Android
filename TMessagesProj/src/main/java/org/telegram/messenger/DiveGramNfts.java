/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger;

import org.telegram.tgnet.tl.TL_stars;

import java.util.Collections;
import java.util.List;

public class DiveGramNfts {

    public static List<TL_stars.TL_starGiftUnique> getRemoteGifts(long userId) {
        return Collections.emptyList();
    }

    public static List<TL_stars.TL_starGiftUnique> getRemotePinnedGifts(long userId) {
        return Collections.emptyList();
    }

    public static boolean isRemotePinned(long userId, long giftId) {
        return false;
    }

    public static List<TL_stars.TL_starGiftUnique> getRemoteGiftsForDialog(long dialogId) {
        return getRemoteGifts(dialogId);
    }

    public static void fetch() {
    }

    public static void scheduleUpload() {
    }

    public static void notifyChanged() {
    }
}
