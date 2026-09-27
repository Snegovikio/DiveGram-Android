/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui.UsernameDrope;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

public class NFCDropeTransport implements NfcAdapter.ReaderCallback {

    private static final String TAG = "DropeNFC";
    private static final byte[] AID = {(byte) 0xF0, 0x01, 0x44, 0x72, 0x6F, 0x70, 0x65};

    private static final int INS_SELECT = 0xA4;
    private static final int INS_EXCHANGE = 0xD5;

    private static final int FLAGS = NfcAdapter.FLAG_READER_NFC_A
            | NfcAdapter.FLAG_READER_NFC_B
            | NfcAdapter.FLAG_READER_NFC_F
            | NfcAdapter.FLAG_READER_NFC_V
            | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;

    private static final long MIN_WINDOW_MS = 600;
    private static final long MAX_WINDOW_MS = 2000;
    private static final long MIN_HCE_WINDOW_MS = 2000;
    private static final long MAX_HCE_WINDOW_MS = 4000;

    private final Activity activity;
    private final NfcAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Runnable roleToggler = this::toggleRole;

    private volatile boolean running;
    private volatile boolean readerEnabled;
    private volatile boolean inExchange;

    public NFCDropeTransport(Activity activity) {
        this.activity = activity;
        this.adapter = getDefaultAdapter(activity);
    }

    public static NfcAdapter getDefaultAdapter(Activity activity) {
        try {
            return NfcAdapter.getDefaultAdapter(activity);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public boolean isSupported() {
        return adapter != null && adapter.isEnabled();
    }

    public void start() {
        if (adapter == null) {
            Log.d(TAG, "start FAIL: adapter is null");
            return;
        }
        if (!adapter.isEnabled()) {
            Log.d(TAG, "start FAIL: adapter disabled");
            return;
        }
        if (running) {
            Log.d(TAG, "start FAIL: already running");
            return;
        }
        running = true;
        Log.d(TAG, "start OK, registering reader");
        handler.post(() -> {
            enableReader();
            scheduleNextToggle();
        });
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(roleToggler);
        if (adapter != null && readerEnabled) {
            try {
                adapter.disableReaderMode(activity);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        readerEnabled = false;
    }

    private void scheduleNextToggle() {
        if (!running) {
            return;
        }
        handler.removeCallbacks(roleToggler);
        long nextDelay = MIN_WINDOW_MS + random.nextInt((int) (MAX_WINDOW_MS - MIN_WINDOW_MS));
        handler.postDelayed(roleToggler, nextDelay);
    }

    private void toggleRole() {
        if (!running) {
            return;
        }
        if (!inExchange) {
            if (readerEnabled) {
                disableReader();
                long hceWindow = MIN_HCE_WINDOW_MS + random.nextInt((int) (MAX_HCE_WINDOW_MS - MIN_HCE_WINDOW_MS));
                handler.postDelayed(() -> {
                    if (running && !readerEnabled && !inExchange) {
                        enableReader();
                        scheduleNextToggle();
                    }
                }, hceWindow);
                return;
            } else {
                enableReader();
            }
        }
        scheduleNextToggle();
    }

    private void enableReader() {
        if (readerEnabled || adapter == null || !adapter.isEnabled()) {
            return;
        }
        try {
            adapter.enableReaderMode(activity, this, FLAGS, null);
            readerEnabled = true;
            Log.d(TAG, "reader ON");
        } catch (Exception e) {
            readerEnabled = false;
            FileLog.e(e);
        }
    }

    private void disableReader() {
        if (!readerEnabled || adapter == null) {
            return;
        }
        try {
            adapter.disableReaderMode(activity);
            readerEnabled = false;
            Log.d(TAG, "reader OFF");
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        byte[] myPayload = DropeBridge.getMyPayload();
        if (myPayload == null || myPayload.length == 0) {
            Log.d(TAG, "onTagDiscovered: no payload to send");
            return;
        }
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) {
            Log.d(TAG, "tag has no IsoDep tech");
            return;
        }
        inExchange = true;
        Log.d(TAG, "onTagDiscovered: exchanging " + myPayload.length + " byte payload");
        try {
            isoDep.connect();
            isoDep.setTimeout(3000);
            byte[] select = buildSelectApdu();
            byte[] selectResponse = isoDep.transceive(select);
            if (!isStatusOk(selectResponse)) {
                Log.d(TAG, "SELECT rejected, resp=" + (selectResponse != null ? selectResponse.length : "null"));
                return;
            }
            Log.d(TAG, "SELECT OK, sending EXCHANGE");
            byte[] exchange = buildExchangeApdu(myPayload);
            byte[] exchangeResponse = isoDep.transceive(exchange);
            byte[] peerPayload = extractData(exchangeResponse);
            if (peerPayload != null && peerPayload.length > 0) {
                Log.d(TAG, "payload received " + peerPayload.length + " bytes");
                final byte[] finalPayload = peerPayload;
                AndroidUtilities.runOnUIThread(() -> DropeBridge.dispatchPeerPayload(finalPayload));
            } else {
                Log.d(TAG, "EXCHANGE response empty or invalid, resp=" + (exchangeResponse != null ? exchangeResponse.length : "null"));
            }
        } catch (IOException e) {
            Log.d(TAG, "exchange IO error: " + e.getMessage());
        } catch (IllegalStateException e) {
            Log.d(TAG, "exchange state error: " + e.getMessage());
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            try {
                isoDep.close();
            } catch (IOException ignore) {
            }
            inExchange = false;
            disableReader();
        }
    }

    private static byte[] buildSelectApdu() {
        byte[] apdu = new byte[6 + AID.length];
        apdu[0] = 0x00;
        apdu[1] = (byte) INS_SELECT;
        apdu[2] = 0x04;
        apdu[3] = 0x00;
        apdu[4] = (byte) AID.length;
        System.arraycopy(AID, 0, apdu, 5, AID.length);
        apdu[5 + AID.length] = 0x00;
        return apdu;
    }

    private static byte[] buildExchangeApdu(byte[] payload) {
        byte[] apdu = new byte[5 + payload.length + 1];
        apdu[0] = 0x00;
        apdu[1] = (byte) INS_EXCHANGE;
        apdu[2] = 0x00;
        apdu[3] = 0x00;
        apdu[4] = (byte) payload.length;
        System.arraycopy(payload, 0, apdu, 5, payload.length);
        apdu[5 + payload.length] = 0x00;
        return apdu;
    }

    private static boolean isStatusOk(byte[] response) {
        return response != null && response.length >= 2
                && (response[response.length - 2] & 0xFF) == 0x90
                && (response[response.length - 1] & 0xFF) == 0x00;
    }

    private static byte[] extractData(byte[] response) {
        if (!isStatusOk(response)) {
            return null;
        }
        return Arrays.copyOfRange(response, 0, response.length - 2);
    }
}