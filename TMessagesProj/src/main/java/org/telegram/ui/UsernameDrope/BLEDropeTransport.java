/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.ui.UsernameDrope;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;

public class BLEDropeTransport {

    private static final String TAG = "DropeBLE";
    private static final UUID SERVICE_UUID = UUID.fromString("0000F001-0000-1000-8000-00805F9B34FB");
    private static final UUID CHAR_UUID = UUID.fromString("0000F002-0000-1000-8000-00805F9B34FB");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");

    private static final int MANUFACTURER_ID = 0x6A32;
    private static final byte[] MAGIC = {'D', 'I', 'V', 'E'};
    private static final int RSSI_THRESHOLD = -55;
    private static final int STRONG_HITS = 3;

    private final Context context;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter adapter;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothLeScanner scanner;
    private BluetoothGattServer gattServer;
    private BluetoothGatt gattClient;
    private BluetoothGattCharacteristic exchangeCharacteristic;
    private BluetoothGatt pendingMtuGatt;
    private boolean connecting;
    private boolean running;
    private boolean scanRestartScheduled;
    private ScanSettings scanSettings;
    private volatile boolean permissionDenied;
    private final HashMap<String, Integer> rssiHits = new HashMap<>();

    public BLEDropeTransport(Context context) {
        this.context = context.getApplicationContext();
        try {
            bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager != null) {
                adapter = bluetoothManager.getAdapter();
            }
            if (adapter != null) {
                advertiser = adapter.getBluetoothLeAdvertiser();
                scanner = adapter.getBluetoothLeScanner();
            }
        } catch (SecurityException e) {
            permissionDenied = true;
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static boolean isBleSupported(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false;
        }
        try {
            BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            return adapter != null && adapter.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasAllPermissions(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false;
        }
        for (String permission : new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE}) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static String[] getMissingPermissions(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return new String[0];
        }
        ArrayList<String> missing = new ArrayList<>();
        for (String permission : new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE}) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        return missing.toArray(new String[0]);
    }

    public boolean isAvailable() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || permissionDenied) {
            return false;
        }
        if (adapter == null) {
            return false;
        }
        try {
            return adapter.isEnabled();
        } catch (SecurityException e) {
            permissionDenied = true;
            return false;
        }
    }

    public void start() {
        if (running) {
            FileLog.d("drope ble start fail: already running");
            return;
        }
        if (!isAvailable()) {
            FileLog.d("drope ble start fail: not available");
            return;
        }
        if (!hasAllPermissions(context)) {
            FileLog.d("drope ble start fail: missing permissions");
            return;
        }
        running = true;
        rssiHits.clear();
        FileLog.d("drope ble start ok");
        setupServer();
        startAdvertising();
        startScanning();
    }

    public void stop() {
        running = false;
        connecting = false;
        try {
            if (scanner != null) {
                scanner.stopScan(scanCallback);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            if (advertiser != null) {
                advertiser.stopAdvertising(advertiseCallback);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        closeGattClient();
        try {
            if (gattServer != null) {
                gattServer.close();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        gattServer = null;
    }

    private void setupServer() {
        try {
            if (adapter == null) {
                return;
            }
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback);
            BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
            exchangeCharacteristic = new BluetoothGattCharacteristic(CHAR_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ
                            | BluetoothGattCharacteristic.PROPERTY_WRITE
                            | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
            service.addCharacteristic(exchangeCharacteristic);
            gattServer.addService(service);
        } catch (SecurityException e) {
            permissionDenied = true;
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private byte[] buildAdvertisingPayload() {
        ByteBuffer buffer = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(MAGIC);
        buffer.put((byte) 1);
        buffer.putInt(DropeController.INSTANCE.getMySessionId().hashCode());
        long uid = DropeController.INSTANCE.getUserConfig().getClientUserId();
        buffer.putInt((int) (uid & 0xFFFFFFFFL));
        return buffer.array();
    }

    private void startAdvertising() {
        try {
            if (advertiser == null) {
                return;
            }
            AdvertiseData data = new AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .addManufacturerData(MANUFACTURER_ID, buildAdvertisingPayload())
                    .build();
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .build();
            advertiser.startAdvertising(settings, data, advertiseCallback);
        } catch (SecurityException e) {
            permissionDenied = true;
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void startScanning() {
        try {
            if (scanner == null) {
                return;
            }
            scanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            ScanFilter filter = new ScanFilter.Builder()
                    .setManufacturerData(MANUFACTURER_ID, MAGIC, new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF})
                    .build();
            scanner.startScan(Collections.singletonList(filter), scanSettings, scanCallback);
        } catch (SecurityException e) {
            permissionDenied = true;
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void maybeConnect(BluetoothDevice device) {
        if (!running || connecting) {
            return;
        }
        try {
            String key = device.getAddress();
            Integer hits = rssiHits.get(key);
            int newHits = (hits == null ? 0 : hits) + 1;
            rssiHits.put(key, newHits);
            if (newHits < STRONG_HITS) {
                return;
            }
            connecting = true;
            rssiHits.clear();
            gattClient = device.connectGatt(context, false, gattClientCallback);
        } catch (SecurityException e) {
            permissionDenied = true;
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void closeGattClient() {
        try {
            if (gattClient != null) {
                gattClient.disconnect();
                gattClient.close();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        gattClient = null;
    }

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
        }

        @Override
        public void onStartFailure(int errorCode) {
            FileLog.d("drope ble advertise failure " + errorCode);
        }
    };

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            ScanRecord record = result.getScanRecord();
            if (record == null) {
                return;
            }
            byte[] manufacturerData = record.getManufacturerSpecificData(MANUFACTURER_ID);
            if (manufacturerData == null || manufacturerData.length < 6) {
                return;
            }
            if (result.getRssi() < RSSI_THRESHOLD) {
                return;
            }
            byte[] mfr = record.getManufacturerSpecificData(MANUFACTURER_ID);
            if (mfr == null || mfr.length < 5 || mfr[0] != MAGIC[0] || mfr[1] != MAGIC[1] || mfr[2] != MAGIC[2] || mfr[3] != MAGIC[3]) {
                return;
            }
            maybeConnect(result.getDevice());
        }

        @Override
        public void onScanFailed(int errorCode) {
            FileLog.d("drope ble scan failure " + errorCode);
            if (running && !scanRestartScheduled) {
                scanRestartScheduled = true;
                AndroidUtilities.runOnUIThread(() -> {
                    scanRestartScheduled = false;
                    try {
                        if (running && scanner != null) {
                            scanner.startScan(null, scanSettings, scanCallback);
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }, 1500);
            }
        }
    };

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            try {
                byte[] myPayload = DropeBridge.getMyPayload();
                if (myPayload == null) {
                    myPayload = new byte[0];
                }
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, myPayload);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            if (value != null && value.length > 0 && characteristic.getUuid().equals(CHAR_UUID)) {
                byte[] myPayload = DropeBridge.getMyPayload();
                try {
                    if (myPayload != null && myPayload.length > 0 && device != null) {
                        characteristic.setValue(myPayload);
                        gattServer.notifyCharacteristicChanged(device, characteristic, false);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                final byte[] finalValue = value;
                AndroidUtilities.runOnUIThread(() -> DropeBridge.dispatchPeerPayload(finalValue));
            }
            if (responseNeeded) {
                try {
                    gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
    };

    private final BluetoothGattCallback gattClientCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                closeGattClient();
                connecting = false;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                closeGattClient();
                connecting = false;
                return;
            }
            BluetoothGattService service = gatt.getService(SERVICE_UUID);
            if (service == null) {
                closeGattClient();
                connecting = false;
                return;
            }
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHAR_UUID);
            if (characteristic == null) {
                closeGattClient();
                connecting = false;
                return;
            }
            exchangeCharacteristic = characteristic;
            try {
                gatt.setCharacteristicNotification(characteristic, true);
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(descriptor);
                }
                pendingMtuGatt = gatt;
                boolean mtuRequested = gatt.requestMtu(512);
                if (!mtuRequested) {
                    pendingMtuGatt = null;
                    writePayloadToServer(gatt, characteristic);
                }
            } catch (SecurityException e) {
                permissionDenied = true;
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            pendingMtuGatt = null;
            if (status == BluetoothGatt.GATT_SUCCESS && exchangeCharacteristic != null) {
                writePayloadToServer(gatt, exchangeCharacteristic);
            } else {
                Log.d(TAG, "MTU negotiation failed status=" + status + ", trying direct write");
                if (exchangeCharacteristic != null) {
                    writePayloadToServer(gatt, exchangeCharacteristic);
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(CHAR_UUID)) {
                byte[] value = characteristic.getValue();
                if (value != null && value.length > 0) {
                    Log.d(TAG, "onCharacteristicChanged: got " + value.length + " bytes");
                    final byte[] finalValue = value;
                    AndroidUtilities.runOnUIThread(() -> DropeBridge.dispatchPeerPayload(finalValue));
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.getUuid().equals(CHAR_UUID)) {
                byte[] value = characteristic.getValue();
                if (value != null && value.length > 0) {
                    Log.d(TAG, "onCharacteristicRead: got " + value.length + " bytes");
                    final byte[] finalValue = value;
                    AndroidUtilities.runOnUIThread(() -> DropeBridge.dispatchPeerPayload(finalValue));
                }
            } else {
                Log.d(TAG, "onCharacteristicRead failed status=" + status);
            }
        }
    };

    private void writePayloadToServer(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        try {
            byte[] myPayload = DropeBridge.getMyPayload();
            if (myPayload != null && myPayload.length > 0) {
                Log.d(TAG, "writePayloadToServer: writing " + myPayload.length + " bytes");
                if (Build.VERSION.SDK_INT >= 33) {
                    gatt.writeCharacteristic(characteristic, myPayload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                } else {
                    characteristic.setValue(myPayload);
                    gatt.writeCharacteristic(characteristic);
                }
            }
            gatt.readCharacteristic(characteristic);
        } catch (SecurityException e) {
            permissionDenied = true;
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}