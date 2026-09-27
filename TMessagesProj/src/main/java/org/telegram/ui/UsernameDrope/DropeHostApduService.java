/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui.UsernameDrope;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;

import java.util.Arrays;

public class DropeHostApduService extends HostApduService {

    private static final byte[] AID = {(byte) 0xF0, 0x01, 0x44, 0x72, 0x6F, 0x70, 0x65};
    private static final byte[] SELECT_OK = {(byte) 0x90, 0x00};
    private static final byte[] UNKNOWN = {0x6A, (byte) 0x82};

    private boolean selected;

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        if (commandApdu == null || commandApdu.length < 5) {
            return UNKNOWN;
        }
        int cla = commandApdu[0] & 0xFF;
        int ins = commandApdu[1] & 0xFF;
        int p1 = commandApdu[2] & 0xFF;
        int p2 = commandApdu[3] & 0xFF;

        if (cla == 0x00 && ins == 0xA4 && p1 == 0x04 && p2 == 0x00) {
            int aidLength = commandApdu[4] & 0xFF;
            if (aidLength != AID.length || commandApdu.length < 5 + aidLength) {
                return UNKNOWN;
            }
            boolean match = true;
            for (int i = 0; i < AID.length; i++) {
                if (commandApdu[5 + i] != AID[i]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                selected = true;
                return SELECT_OK;
            }
            return UNKNOWN;
        }

        if (selected && cla == 0x00 && ins == 0xD5 && p1 == 0x00 && p2 == 0x00) {
            int lc = commandApdu[4] & 0xFF;
            if (lc == 0 && (commandApdu[0] & 0x10) != 0) {
                lc = ((commandApdu[5] & 0xFF) << 8) | (commandApdu[6] & 0xFF);
            }
            if (commandApdu.length >= 5 + lc) {
                try {
                    final byte[] peerPayload = Arrays.copyOfRange(commandApdu, 5, 5 + lc);
                    if (peerPayload.length > 0) {
                        AndroidUtilities.runOnUIThread(() -> DropeBridge.dispatchPeerPayload(peerPayload));
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                byte[] myPayload = DropeBridge.getMyPayload();
                if (myPayload == null) {
                    myPayload = new byte[0];
                }
                byte[] response = new byte[myPayload.length + 2];
                System.arraycopy(myPayload, 0, response, 0, myPayload.length);
                response[myPayload.length] = (byte) 0x90;
                response[myPayload.length + 1] = 0x00;
                return response;
            }
        }
        return UNKNOWN;
    }

    @Override
    public void onDeactivated(int reason) {
        selected = false;
    }
}