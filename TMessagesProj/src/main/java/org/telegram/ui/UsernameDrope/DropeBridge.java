/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui.UsernameDrope;

public class DropeBridge {

    public interface PayloadListener {
        void onPeerPayload(byte[] payload);
    }

    private static volatile byte[] myPayload;
    private static volatile PayloadListener peerListener;

    public static void setMyPayload(byte[] payload) {
        myPayload = payload;
    }

    public static byte[] getMyPayload() {
        return myPayload;
    }

    public static void setPeerListener(PayloadListener listener) {
        peerListener = listener;
    }

    public static void dispatchPeerPayload(byte[] payload) {
        PayloadListener listener = peerListener;
        if (listener != null && payload != null && payload.length > 0) {
            listener.onPeerPayload(payload);
        }
    }

    public static void clear() {
        myPayload = null;
        peerListener = null;
    }
}