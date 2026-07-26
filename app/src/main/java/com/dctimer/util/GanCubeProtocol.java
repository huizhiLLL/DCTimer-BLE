package com.dctimer.util;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.dctimer.activity.MainActivity;
import com.dctimer.model.SmartCube;

import java.security.GeneralSecurityException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import cs.min2phase.CubieCube;
import cs.min2phase.Util;

public class GanCubeProtocol implements SmartCubeProtocol {
    private static final String TAG = "GanCube";
    public static final UUID SERVICE_UUID_V2DATA = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dc4179");
    public static final UUID CHRCT_UUID_V2READ = UUID.fromString("28be4cb6-cd67-11e9-a32f-2a2ae2dbcce4");
    public static final UUID CHRCT_UUID_V2WRITE = UUID.fromString("28be4a4a-cd67-11e9-a32f-2a2ae2dbcce4");
    public static final UUID SERVICE_UUID_V3DATA = UUID.fromString("8653000a-43e6-47b7-9cb0-5fc21d4ae340");
    public static final UUID CHRCT_UUID_V3READ = UUID.fromString("8653000b-43e6-47b7-9cb0-5fc21d4ae340");
    public static final UUID CHRCT_UUID_V3WRITE = UUID.fromString("8653000c-43e6-47b7-9cb0-5fc21d4ae340");
    public static final UUID SERVICE_UUID_V4DATA = UUID.fromString("00000010-0000-fff7-fff6-fff5fff4fff0");
    public static final UUID CHRCT_UUID_V4READ = UUID.fromString("0000fff6-0000-1000-8000-00805f9b34fb");
    public static final UUID CHRCT_UUID_V4WRITE = UUID.fromString("0000fff5-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final byte[] BASE_KEY = {
            1, 2, 66, 40, 49, (byte) 145, 22, 7,
            32, 5, 24, 84, 66, 17, 18, 83
    };
    private static final byte[] BASE_IV = {
            17, 3, 50, 40, 33, 1, 118, 39,
            32, (byte) 149, 120, 20, 50, 18, 2, 67
    };
    private static final byte[] AICUBE_KEY = {
            5, 18, 2, 69, 2, 1, 41, 86,
            18, 120, 18, 118, (byte) 129, 1, 8, 3
    };
    private static final byte[] AICUBE_IV = {
            1, 68, 40, 6, (byte) 134, 33, 34, 40,
            81, 5, 8, 49, (byte) 130, 2, 33, 6
    };
    private static final int[] AXIS_MASKS = {2, 32, 8, 1, 16, 4};
    private static final int MAX_MOVE_DELTA_MS = 0xffff;
    private static final float MIN_GYRO_QUATERNION_NORM = 1.0e-6f;

    private final MainActivity context;
    private final SmartCube smartCube;
    private final GanCubeCipher cipher = new GanCubeCipher();
    private final ArrayDeque<byte[]> requestQueue = new ArrayDeque<>();
    private final ArrayDeque<MoveEvent> moveBuffer = new ArrayDeque<>();

    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic readCharacteristic;
    private BluetoothGattCharacteristic writeCharacteristic;
    private Variant variant;
    private String deviceName;
    private String deviceMac;
    private boolean notificationsReady;
    private boolean writePending;
    private boolean initialStateShown;
    private int prevMoveCnt = -1;
    private int currentMoveCnt = -1;
    private long lastEmittedDeviceTs = -1L;
    private long lastEmittedLocTime = -1L;
    private long prevMoveLocTime = -1L;
    private int batteryLevel;
    private final ArrayDeque<Long> historyEstimateLocQueue = new ArrayDeque<>();

    private enum Variant {
        V2, V3, V4
    }

    private static class MoveEvent {
        final int moveCnt;
        final int move;
        Long deviceTs;
        final long receiveLocTime;

        MoveEvent(int moveCnt, int move, Long deviceTs) {
            this(moveCnt, move, deviceTs, -1L);
        }

        MoveEvent(int moveCnt, int move, Long deviceTs, long receiveLocTime) {
            this.moveCnt = moveCnt;
            this.move = move;
            this.deviceTs = deviceTs;
            this.receiveLocTime = receiveLocTime;
        }
    }

    public GanCubeProtocol(MainActivity context, SmartCube smartCube) {
        this.context = context;
        this.smartCube = smartCube;
    }

    public static boolean matchesDeviceName(String deviceName) {
        if (deviceName == null) {
            return false;
        }
        String normalized = deviceName.trim().toUpperCase(Locale.US);
        return normalized.startsWith("GAN")
                || normalized.startsWith("MG")
                || normalized.startsWith("AICUBE");
    }

    public static BluetoothGattService findPrimaryService(BluetoothGatt gatt) {
        if (gatt == null) {
            return null;
        }
        BluetoothGattService service = gatt.getService(SERVICE_UUID_V2DATA);
        if (service != null) {
            return service;
        }
        service = gatt.getService(SERVICE_UUID_V3DATA);
        if (service != null) {
            return service;
        }
        return gatt.getService(SERVICE_UUID_V4DATA);
    }

    @Override
    public boolean start(BluetoothGatt gatt, BluetoothGattService service, String deviceName, String deviceAddress) {
        clear();
        this.gatt = gatt;
        this.deviceName = deviceName == null ? "" : deviceName.trim();
        this.deviceMac = normalizeMac(deviceAddress);
        this.variant = resolveVariant(service);
        if (variant == null || TextUtils.isEmpty(deviceMac)) {
            Log.e(TAG, "GAN 初始化失败，variant=" + variant + " mac=" + deviceMac);
            return false;
        }
        if (!initCipher()) {
            return false;
        }
        requestHighConnectionPriority();
        if (!bindCharacteristics(service)) {
            return false;
        }
        if (!gatt.setCharacteristicNotification(readCharacteristic, true)) {
            Log.e(TAG, "GAN 无法开启通知");
            return false;
        }
        BluetoothGattDescriptor descriptor = readCharacteristic.getDescriptor(CCCD_UUID);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (!gatt.writeDescriptor(descriptor)) {
                Log.e(TAG, "GAN 通知描述符写入失败，直接继续");
                onNotificationsEnabled();
            }
        } else {
            onNotificationsEnabled();
        }
        return true;
    }

    @Override
    public void clear() {
        requestQueue.clear();
        moveBuffer.clear();
        historyEstimateLocQueue.clear();
        gatt = null;
        readCharacteristic = null;
        writeCharacteristic = null;
        variant = null;
        deviceName = null;
        deviceMac = null;
        notificationsReady = false;
        writePending = false;
        initialStateShown = false;
        prevMoveCnt = -1;
        currentMoveCnt = -1;
        lastEmittedDeviceTs = -1L;
        lastEmittedLocTime = -1L;
        prevMoveLocTime = -1L;
        batteryLevel = 0;
    }

    @Override
    public void onDescriptorWrite(BluetoothGattDescriptor descriptor, int status) {
        if (descriptor == null || descriptor.getCharacteristic() == null) {
            return;
        }
        if (readCharacteristic != null && readCharacteristic.getUuid().equals(descriptor.getCharacteristic().getUuid())) {
            onNotificationsEnabled();
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGattCharacteristic characteristic, int status) {
        if (characteristic == null || writeCharacteristic == null || !writeCharacteristic.getUuid().equals(characteristic.getUuid())) {
            return;
        }
        writePending = false;
        sendNextRequest();
    }

    @Override
    public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        if (characteristic == null || readCharacteristic == null || !readCharacteristic.getUuid().equals(characteristic.getUuid())) {
            return;
        }
        try {
            switch (variant) {
                case V2:
                    parseV2Data(characteristic.getValue());
                    break;
                case V3:
                    parseV3Data(characteristic.getValue());
                    break;
                case V4:
                    parseV4Data(characteristic.getValue());
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "GAN 数据解析失败", e);
        }
    }

    @Override
    public void onLocalCubeReset(String cubeState) {
        moveBuffer.clear();
        historyEstimateLocQueue.clear();
        prevMoveCnt = -1;
        currentMoveCnt = -1;
        lastEmittedDeviceTs = -1L;
        lastEmittedLocTime = -1L;
        prevMoveLocTime = -1L;

        if (variant == null) {
            Log.w(TAG, "GAN reset ignored: protocol is not connected");
            return;
        }
        enqueueRequest(resetRequest());
    }

    private Variant resolveVariant(BluetoothGattService service) {
        if (service == null) {
            return null;
        }
        UUID uuid = service.getUuid();
        if (SERVICE_UUID_V2DATA.equals(uuid)) {
            return Variant.V2;
        }
        if (SERVICE_UUID_V3DATA.equals(uuid)) {
            return Variant.V3;
        }
        if (SERVICE_UUID_V4DATA.equals(uuid)) {
            return Variant.V4;
        }
        return null;
    }

    private boolean initCipher() {
        byte[] key = (variant == Variant.V2 && deviceName.startsWith("AiCube")) ? AICUBE_KEY : BASE_KEY;
        byte[] iv = (variant == Variant.V2 && deviceName.startsWith("AiCube")) ? AICUBE_IV : BASE_IV;
        try {
            cipher.init(key, iv, deviceMac);
            return true;
        } catch (GeneralSecurityException e) {
            Log.e(TAG, "GAN cipher 初始化失败", e);
            return false;
        }
    }

    private boolean bindCharacteristics(BluetoothGattService service) {
        switch (variant) {
            case V2:
                readCharacteristic = service.getCharacteristic(CHRCT_UUID_V2READ);
                writeCharacteristic = service.getCharacteristic(CHRCT_UUID_V2WRITE);
                break;
            case V3:
                readCharacteristic = service.getCharacteristic(CHRCT_UUID_V3READ);
                writeCharacteristic = service.getCharacteristic(CHRCT_UUID_V3WRITE);
                break;
            case V4:
                readCharacteristic = service.getCharacteristic(CHRCT_UUID_V4READ);
                writeCharacteristic = service.getCharacteristic(CHRCT_UUID_V4WRITE);
                break;
        }
        return readCharacteristic != null && writeCharacteristic != null;
    }

    private void requestHighConnectionPriority() {
        if (gatt == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
    }

    private void onNotificationsEnabled() {
        if (notificationsReady) {
            return;
        }
        notificationsReady = true;
        enqueueInitialRequests();
    }

    private void enqueueInitialRequests() {
        switch (variant) {
            case V2:
                enqueueRequest(v2SimpleRequest(5));
                enqueueRequest(v2SimpleRequest(4));
                enqueueRequest(v2SimpleRequest(9));
                break;
            case V3:
                enqueueRequest(v3SimpleRequest(4));
                enqueueRequest(v3SimpleRequest(1));
                enqueueRequest(v3SimpleRequest(7));
                break;
            case V4:
                enqueueRequest(v4RequestHardwareInfo());
                enqueueRequest(v4RequestFacelets());
                enqueueRequest(v4RequestBattery());
                break;
        }
    }

    private void enqueueRequest(byte[] request) {
        if (request == null) {
            return;
        }
        requestQueue.offer(request);
        sendNextRequest();
    }

    private void sendNextRequest() {
        if (!notificationsReady || writePending || gatt == null || writeCharacteristic == null || requestQueue.isEmpty()) {
            return;
        }
        byte[] request = requestQueue.poll();
        try {
            byte[] encoded = cipher.encode(request);
            writeCharacteristic.setValue(encoded);
            writePending = gatt.writeCharacteristic(writeCharacteristic);
            if (!writePending) {
                Log.e(TAG, "GAN 写入请求失败");
            }
        } catch (GeneralSecurityException e) {
            Log.e(TAG, "GAN 请求加密失败", e);
            writePending = false;
        }
    }

    private byte[] v2SimpleRequest(int opcode) {
        byte[] req = new byte[20];
        req[0] = (byte) opcode;
        return req;
    }

    private byte[] v2ResetRequest() {
        return new byte[]{
                0x0A, 0x05, 0x39, 0x77, 0x00, 0x00, 0x01, 0x23,
                0x45, 0x67, (byte) 0x89, (byte) 0xAB, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00
        };
    }

    private byte[] v3SimpleRequest(int opcode) {
        byte[] req = new byte[16];
        req[0] = 0x68;
        req[1] = (byte) opcode;
        return req;
    }

    private byte[] v3ResetRequest() {
        return new byte[]{
                0x68, 0x05, 0x05, 0x39, 0x77, 0x00, 0x00, 0x01,
                0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xAB, 0x00, 0x00, 0x00
        };
    }

    private byte[] v4RequestHardwareInfo() {
        byte[] req = new byte[20];
        req[0] = (byte) 0xDF;
        req[1] = 0x03;
        return req;
    }

    private byte[] v4RequestFacelets() {
        byte[] req = new byte[20];
        req[0] = (byte) 0xDD;
        req[1] = 0x04;
        req[3] = (byte) 0xED;
        return req;
    }

    private byte[] v4RequestBattery() {
        byte[] req = new byte[20];
        req[0] = (byte) 0xDD;
        req[1] = 0x04;
        req[3] = (byte) 0xEF;
        return req;
    }

    private byte[] v4ResetRequest() {
        byte[] req = new byte[20];
        req[0] = (byte) 0xD2;
        req[1] = 0x0D;
        req[2] = 0x05;
        req[3] = 0x39;
        req[4] = 0x77;
        req[7] = 0x01;
        req[8] = 0x23;
        req[9] = 0x45;
        req[10] = 0x67;
        req[11] = (byte) 0x89;
        req[12] = (byte) 0xAB;
        return req;
    }

    private byte[] resetRequest() {
        switch (variant) {
            case V2:
                return v2ResetRequest();
            case V3:
                return v3ResetRequest();
            case V4:
                return v4ResetRequest();
            default:
                return null;
        }
    }

    private byte[] v3RequestMoveHistory(int startMoveCnt, int numberOfMoves) {
        byte[] req = new byte[16];
        req[0] = 0x68;
        req[1] = 0x03;
        req[2] = (byte) startMoveCnt;
        req[4] = (byte) numberOfMoves;
        return req;
    }

    private byte[] v4RequestMoveHistory(int startMoveCnt, int numberOfMoves) {
        byte[] req = new byte[20];
        req[0] = (byte) 0xD1;
        req[1] = 0x04;
        req[2] = (byte) startMoveCnt;
        req[4] = (byte) numberOfMoves;
        return req;
    }

    private void parseV2Data(byte[] value) throws GeneralSecurityException {
        String bits = toBitString(cipher.decode(value));
        int mode = parseBits(bits, 0, 4);
        if (mode == 1) {
            notifyGyro(parseV2GyroEvent(bits));
        } else if (mode == 2) {
            handleV2Move(bits);
        } else if (mode == 4) {
            handleV2Facelets(bits);
        } else if (mode == 9) {
            updateBatteryLevel(parseBits(bits, 8, 8));
        }
    }

    private void handleV2Facelets(String bits) {
        if (prevMoveCnt != -1) {
            return;
        }
        int moveCnt = parseBits(bits, 4, 8);
        String facelet = parseFacelet(bits, 12, 33, 47, 91);
        if (TextUtils.isEmpty(facelet)) {
            return;
        }
        if (smartCube.setCubeState(facelet) != 0) {
            Log.e(TAG, "GAN v2 初始状态校验失败");
            return;
        }
        prevMoveCnt = moveCnt;
        showInitialStateDialogIfNeeded();
        Log.w(TAG, "GAN v2 初始状态: " + facelet);
    }

    private void handleV2Move(String bits) {
        int moveCnt = parseBits(bits, 4, 8);
        if (moveCnt == prevMoveCnt || prevMoveCnt == -1) {
            return;
        }
        int[] moves = new int[7];
        int[] timeOffsets = new int[7];
        for (int i = 0; i < 7; i++) {
            int rawMove = parseBits(bits, 12 + i * 5, 5);
            if (rawMove >= 12) {
                Log.w(TAG, "GAN v2 非法 move: " + rawMove);
                return;
            }
            moves[i] = convertQuarterMove(rawMove >> 1, rawMove & 1);
            timeOffsets[i] = parseBits(bits, 47 + i * 16, 16);
        }
        int moveDiff = (moveCnt - prevMoveCnt) & 0xff;
        prevMoveCnt = moveCnt;
        if (moveDiff > moves.length) {
            moveDiff = moves.length;
        }
        for (int i = moveDiff - 1; i >= 0; i--) {
            emitMove(moves[i], timeOffsets[i]);
        }
    }

    private void parseV3Data(byte[] value) throws GeneralSecurityException {
        long locTime = System.currentTimeMillis();
        String bits = toBitString(cipher.decode(value));
        int magic = parseBits(bits, 0, 8);
        int mode = parseBits(bits, 8, 8);
        int len = parseBits(bits, 16, 8);
        if (magic != 0x55 || len <= 0) {
            return;
        }
        if (mode == 1) {
            handleV3Move(bits, locTime);
        } else if (mode == 2) {
            handleV3Facelets(bits, locTime);
        } else if (mode == 6) {
            handleV3History(bits, len);
        } else if (mode == 16) {
            updateBatteryLevel(parseBits(bits, 24, 8));
        }
    }

    private void parseV4Data(byte[] value) throws GeneralSecurityException {
        long locTime = System.currentTimeMillis();
        String bits = toBitString(cipher.decode(value));
        int mode = parseBits(bits, 0, 8);
        int len = parseBits(bits, 8, 8);
        if (mode == 0xEC) {
            notifyGyro(parseV4GyroEvent(bits));
        } else if (mode == 0x01) {
            handleV4Move(bits, locTime);
        } else if (mode == 0xED) {
            handleV4Facelets(bits, locTime);
        } else if (mode == 0xD1) {
            handleV4History(bits, len);
        } else if (mode == 0xEF) {
            updateBatteryLevel(parseBits(bits, 8 + len * 8, 8));
        }
    }

    private void updateBatteryLevel(int level) {
        batteryLevel = level;
        smartCube.setBatteryValue(batteryLevel);
        context.refreshSmartCubeStateUi();
    }

    private void handleV3Move(String bits, long locTime) {
        int moveCnt = parseBits(bits, 64, 8) << 8 | parseBits(bits, 56, 8);
        moveCnt &= 0xff;
        currentMoveCnt = moveCnt;
        prevMoveLocTime = locTime;
        if (moveCnt == prevMoveCnt || prevMoveCnt == -1) {
            return;
        }
        long ts = parseBits(bits, 48, 8);
        ts = (ts << 8) | parseBits(bits, 40, 8);
        ts = (ts << 8) | parseBits(bits, 32, 8);
        ts = (ts << 8) | parseBits(bits, 24, 8);
        int pow = parseBits(bits, 72, 2);
        int axis = decodeAxisMask(parseBits(bits, 74, 6));
        if (axis < 0) {
            return;
        }
        int move = convertQuarterMove(axis, pow);
        moveBuffer.offer(new MoveEvent(moveCnt, move, ts, locTime));
        evictMoveBuffer(true);
    }

    private void handleV4Move(String bits, long locTime) {
        for (int offset = 0; offset + 72 <= bits.length(); offset += 72) {
            if (parseBits(bits, offset, 8) != 0x01) {
                break;
            }
            int moveCnt = parseBits(bits, offset + 56, 8) << 8 | parseBits(bits, offset + 48, 8);
            moveCnt &= 0xff;
            currentMoveCnt = moveCnt;
            prevMoveLocTime = locTime;
            if (moveCnt == prevMoveCnt || prevMoveCnt == -1) {
                continue;
            }
            long ts = parseBits(bits, offset + 40, 8);
            ts = (ts << 8) | parseBits(bits, offset + 32, 8);
            ts = (ts << 8) | parseBits(bits, offset + 24, 8);
            ts = (ts << 8) | parseBits(bits, offset + 16, 8);
            int pow = parseBits(bits, offset + 64, 2);
            int axis = decodeAxisMask(parseBits(bits, offset + 66, 6));
            if (axis < 0) {
                break;
            }
            int move = convertQuarterMove(axis, pow);
            moveBuffer.offer(new MoveEvent(moveCnt, move, ts, locTime));
        }
        evictMoveBuffer(true);
    }

    private void handleV3Facelets(String bits, long locTime) {
        int moveCnt = (parseBits(bits, 32, 8) << 8 | parseBits(bits, 24, 8)) & 0xff;
        currentMoveCnt = moveCnt;
        if (prevMoveCnt != -1) {
            int diff = (moveCnt - prevMoveCnt) & 0xff;
            if (prevMoveLocTime > 0 && locTime - prevMoveLocTime > 500 && diff > 0 && moveCnt != 0) {
                int startMoveCnt = moveBuffer.isEmpty() ? ((moveCnt + 1) & 0xff) : moveBuffer.peekFirst().moveCnt;
                Log.w(TAG, "GAN v3 facelet ahead, request history prev=" + prevMoveCnt
                        + " current=" + moveCnt
                        + " head=" + startMoveCnt
                        + " diff=" + diff
                        + " wait=" + (locTime - prevMoveLocTime)
                        + "ms");
                requestMoveHistory(startMoveCnt, diff + 1, locTime);
            }
            return;
        }
        String facelet = parseFacelet(bits, 40, 61, 77, 121);
        if (TextUtils.isEmpty(facelet)) {
            return;
        }
        if (smartCube.setCubeState(facelet) != 0) {
            Log.e(TAG, "GAN v3 初始状态校验失败");
            return;
        }
        prevMoveCnt = moveCnt;
        lastEmittedDeviceTs = -1L;
        showInitialStateDialogIfNeeded();
        Log.w(TAG, "GAN v3 初始状态: " + facelet);
    }

    private void handleV4Facelets(String bits, long locTime) {
        int moveCnt = (parseBits(bits, 24, 8) << 8 | parseBits(bits, 16, 8)) & 0xff;
        currentMoveCnt = moveCnt;
        if (prevMoveCnt != -1) {
            int diff = (moveCnt - prevMoveCnt) & 0xff;
            if (prevMoveLocTime > 0 && locTime - prevMoveLocTime > 500 && diff > 0 && moveCnt != 0) {
                int startMoveCnt = moveBuffer.isEmpty() ? ((moveCnt + 1) & 0xff) : moveBuffer.peekFirst().moveCnt;
                Log.w(TAG, "GAN v4 facelet ahead, request history prev=" + prevMoveCnt
                        + " current=" + moveCnt
                        + " head=" + startMoveCnt
                        + " diff=" + diff
                        + " wait=" + (locTime - prevMoveLocTime)
                        + "ms");
                requestMoveHistory(startMoveCnt, diff + 1, locTime);
            }
            return;
        }
        String facelet = parseFacelet(bits, 32, 53, 69, 113);
        if (TextUtils.isEmpty(facelet)) {
            return;
        }
        if (smartCube.setCubeState(facelet) != 0) {
            Log.e(TAG, "GAN v4 初始状态校验失败");
            return;
        }
        prevMoveCnt = moveCnt;
        lastEmittedDeviceTs = -1L;
        showInitialStateDialogIfNeeded();
        Log.w(TAG, "GAN v4 初始状态: " + facelet);
    }

    private void handleV3History(String bits, int len) {
        int startMoveCnt = parseBits(bits, 24, 8);
        int numberOfMoves = (len - 1) * 2;
        Log.w(TAG, "GAN v3 history event start=" + startMoveCnt + " count=" + numberOfMoves);
        injectHistoryMoves(bits, 32, startMoveCnt, numberOfMoves, false, pollHistoryEstimateLocTime());
        evictMoveBuffer(false);
    }

    private void handleV4History(String bits, int len) {
        int startMoveCnt = parseBits(bits, 16, 8);
        int numberOfMoves = (len - 1) * 2;
        Log.w(TAG, "GAN v4 history event start=" + startMoveCnt + " count=" + numberOfMoves);
        injectHistoryMoves(bits, 24, startMoveCnt, numberOfMoves, true, pollHistoryEstimateLocTime());
        evictMoveBuffer(false);
    }

    private long pollHistoryEstimateLocTime() {
        return historyEstimateLocQueue.isEmpty() ? -1L : historyEstimateLocQueue.poll();
    }

    private void injectHistoryMoves(String bits, int offset, int startMoveCnt, int numberOfMoves, boolean v4, long estimateLocTime) {
        for (int i = 0; i < numberOfMoves; i++) {
            int axis = parseBits(bits, offset + i * 4, 3);
            int pow = parseBits(bits, offset + i * 4 + 3, 1);
            if (axis >= 6) {
                continue;
            }
            int move = convertHistoryMove(axis, pow);
            int moveCnt = (startMoveCnt - i) & 0xff;
            injectLostMove(new MoveEvent(moveCnt, move, null, estimateLocTime));
        }
    }

    private void injectLostMove(MoveEvent move) {
        if (moveBuffer.isEmpty()) {
            if (currentMoveCnt < 0 || isMoveNumberInRange(prevMoveCnt, currentMoveCnt, move.moveCnt, false, true)) {
                moveBuffer.offerFirst(move);
            }
            return;
        }
        List<MoveEvent> list = new ArrayList<>(moveBuffer);
        for (MoveEvent event : list) {
            if (event.moveCnt == move.moveCnt) {
                return;
            }
        }
        MoveEvent head = list.get(0);
        if (!isMoveNumberInRange(prevMoveCnt, head.moveCnt, move.moveCnt, false, false)) {
            return;
        }
        if (move.moveCnt == ((head.moveCnt - 1) & 0xff)) {
            moveBuffer.offerFirst(move);
        }
    }

    private void evictMoveBuffer(boolean requestHistory) {
        while (!moveBuffer.isEmpty()) {
            MoveEvent head = moveBuffer.peekFirst();
            int diff = (head.moveCnt - prevMoveCnt) & 0xff;
            if (diff > 1) {
                Log.w(TAG, "GAN gap detected prev=" + prevMoveCnt
                        + " head=" + head.moveCnt
                        + " diff=" + diff
                        + " buffer=" + moveBuffer.size()
                        + " requestHistory=" + requestHistory);
                if (requestHistory) {
                    requestMoveHistory(head.moveCnt, diff);
                }
                break;
            }
            interpolateMissingDeviceTimes();
            head = moveBuffer.peekFirst();
            if (head.deviceTs == null && lastEmittedDeviceTs < 0) {
                break;
            }
            moveBuffer.pollFirst();
            int delta = computeDelta(head.deviceTs);
            emitMove(head.move, delta);
            if (head.receiveLocTime > 0) {
                lastEmittedLocTime = head.receiveLocTime;
            }
            prevMoveCnt = head.moveCnt;
        }
    }

    private void requestMoveHistory(int startMoveCnt, int numberOfMoves) {
        requestMoveHistory(startMoveCnt, numberOfMoves, -1L);
    }

    private void requestMoveHistory(int startMoveCnt, int numberOfMoves, long estimateLocTime) {
        if (variant != Variant.V3 && variant != Variant.V4) {
            return;
        }
        if (numberOfMoves <= 0) {
            return;
        }
        if (startMoveCnt % 2 == 0) {
            startMoveCnt = (startMoveCnt - 1) & 0xff;
        }
        if ((numberOfMoves & 1) == 1) {
            numberOfMoves++;
        }
        numberOfMoves = Math.min(numberOfMoves, startMoveCnt + 1);
        historyEstimateLocQueue.offer(estimateLocTime);
        enqueueRequest(variant == Variant.V3
                ? v3RequestMoveHistory(startMoveCnt, numberOfMoves)
                : v4RequestMoveHistory(startMoveCnt, numberOfMoves));
    }

    private void interpolateMissingDeviceTimes() {
        if (moveBuffer.isEmpty()) {
            return;
        }
        List<MoveEvent> list = new ArrayList<>(moveBuffer);
        Long previous = lastEmittedDeviceTs >= 0 ? lastEmittedDeviceTs : null;
        for (int i = 0; i < list.size(); i++) {
            MoveEvent current = list.get(i);
            if (current.deviceTs != null) {
                previous = current.deviceTs;
                continue;
            }
            int start = i;
            while (i < list.size() && list.get(i).deviceTs == null) {
                i++;
            }
            Long next = i < list.size() ? list.get(i).deviceTs : null;
            int missingCount = i - start;
            if (previous != null && next != null && next > previous) {
                long span = next - previous;
                for (int j = 0; j < missingCount; j++) {
                    list.get(start + j).deviceTs = previous + span * (j + 1) / (missingCount + 1);
                }
            } else if (previous != null) {
                interpolateTrailingMissingTimes(list, start, missingCount, previous);
            } else if (next != null) {
                for (int j = missingCount - 1; j >= 0; j--) {
                    list.get(start + j).deviceTs = Math.max(0L, next - (missingCount - j));
                }
            }
            if (i < list.size()) {
                previous = list.get(i).deviceTs;
            }
        }
    }

    private void interpolateTrailingMissingTimes(List<MoveEvent> list, int start, int missingCount, long previous) {
        long estimatedEnd = estimateTrailingDeviceTs(list, start, missingCount, previous);
        if (estimatedEnd > previous) {
            long span = estimatedEnd - previous;
            for (int j = 0; j < missingCount; j++) {
                list.get(start + j).deviceTs = previous + Math.max(1L, span * (j + 1) / missingCount);
            }
            return;
        }
        for (int j = 0; j < missingCount; j++) {
            list.get(start + j).deviceTs = previous + j + 1L;
        }
    }

    private long estimateTrailingDeviceTs(List<MoveEvent> list, int start, int missingCount, long previous) {
        if (lastEmittedLocTime <= 0) {
            return -1L;
        }
        for (int i = start + missingCount - 1; i >= start; i--) {
            long estimateLocTime = list.get(i).receiveLocTime;
            if (estimateLocTime > lastEmittedLocTime) {
                return previous + Math.min(MAX_MOVE_DELTA_MS, estimateLocTime - lastEmittedLocTime);
            }
        }
        return -1L;
    }

    private int computeDelta(Long deviceTs) {
        long current = deviceTs == null ? lastEmittedDeviceTs : deviceTs;
        if (current < 0) {
            return 0;
        }
        if (lastEmittedDeviceTs < 0) {
            lastEmittedDeviceTs = current;
            return 0;
        }
        long delta = current - lastEmittedDeviceTs;
        lastEmittedDeviceTs = current;
        if (delta < 0) {
            return 0;
        }
        return (int) Math.min(delta, MAX_MOVE_DELTA_MS);
    }

    private boolean isMoveNumberInRange(int start, int end, int moveCnt, boolean closedStart, boolean closedEnd) {
        int distance = (end - start) & 0xff;
        int moveDistance = (moveCnt - start) & 0xff;
        return distance >= moveDistance
                && (closedStart || ((start - moveCnt) & 0xff) > 0)
                && (closedEnd || ((end - moveCnt) & 0xff) > 0);
    }

    private String parseFacelet(String bits, int cornerPermStart, int cornerOriStart, int edgePermStart, int edgeOriStart) {
        int[] cp = new int[8];
        int[] co = new int[8];
        int[] ep = new int[12];
        int[] eo = new int[12];
        int cchk = 0xf00;
        for (int i = 0; i < 7; i++) {
            int perm = parseBits(bits, cornerPermStart + i * 3, 3);
            int ori = parseBits(bits, cornerOriStart + i * 2, 2);
            cchk -= ori << 3;
            cchk ^= perm;
            cp[i] = perm;
            co[i] = ori;
        }
        cp[7] = cchk & 0x7;
        co[7] = ((cchk & 0xff8) % 24) >> 3;
        int echk = 0;
        for (int i = 0; i < 11; i++) {
            int perm = parseBits(bits, edgePermStart + i * 4, 4);
            int ori = parseBits(bits, edgeOriStart + i, 1);
            echk ^= perm << 1 | ori;
            ep[i] = perm;
            eo[i] = ori;
        }
        ep[11] = echk >> 1;
        eo[11] = echk & 1;
        CubieCube cube = new CubieCube(cp, co, ep, eo);
        if (cube.verify() != 0) {
            return null;
        }
        return Util.toFaceCube(cube);
    }

    private void emitMove(int move, int delta) {
        context.moveCube(smartCube, move, Math.max(0, Math.min(delta, MAX_MOVE_DELTA_MS)));
    }

    static float[] parseV2GyroEvent(String bits) {
        try {
            if (!hasBits(bits, 0, 4) || parseBits(bits, 0, 4) != 0x1) {
                return null;
            }
            return parseGyroQuaternion(bits, 4);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static float[] parseV4GyroEvent(String bits) {
        try {
            if (!hasBits(bits, 0, 8) || parseBits(bits, 0, 8) != 0xEC) {
                return null;
            }
            return parseGyroQuaternion(bits, 16);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static float[] parseGyroQuaternion(String bits, int quaternionStartBit) {
        if (!hasBits(bits, quaternionStartBit, 64)) {
            return null;
        }
        try {
            float w = decodeGyroComponent(parseBits(bits, quaternionStartBit, 16));
            float x = decodeGyroComponent(parseBits(bits, quaternionStartBit + 16, 16));
            float y = decodeGyroComponent(parseBits(bits, quaternionStartBit + 32, 16));
            float z = decodeGyroComponent(parseBits(bits, quaternionStartBit + 48, 16));
            float norm = (float) Math.sqrt(w * w + x * x + y * y + z * z);
            if (norm < MIN_GYRO_QUATERNION_NORM
                    || Float.isNaN(norm) || Float.isInfinite(norm)
                    || Float.isNaN(w) || Float.isInfinite(w)
                    || Float.isNaN(x) || Float.isInfinite(x)
                    || Float.isNaN(y) || Float.isInfinite(y)
                    || Float.isNaN(z) || Float.isInfinite(z)) {
                return null;
            }
            return new float[] {x / norm, z / norm, -y / norm, w / norm};
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static float decodeGyroComponent(int raw) {
        float sign = (raw & 0x8000) == 0 ? 1f : -1f;
        return sign * (raw & 0x7fff) / 0x7fff;
    }

    private static boolean hasBits(String bits, int start, int length) {
        return bits != null && start >= 0 && length >= 0 && start <= bits.length() - length;
    }

    private void notifyGyro(float[] quaternion) {
        if (quaternion != null) {
            context.onSmartCubeGyroChanged(quaternion[0], quaternion[1], quaternion[2], quaternion[3]);
        }
    }

    private int decodeAxisMask(int mask) {
        for (int i = 0; i < AXIS_MASKS.length; i++) {
            if (AXIS_MASKS[i] == mask) {
                return i;
            }
        }
        return -1;
    }

    private int convertQuarterMove(int axis, int pow) {
        return axis * 3 + (pow == 0 ? 0 : 2);
    }

    private int convertHistoryMove(int axis, int pow) {
        char face = "DUBFLR".charAt(axis);
        int mappedAxis = "URFDLB".indexOf(face);
        if (mappedAxis < 0) {
            mappedAxis = 0;
        }
        return convertQuarterMove(mappedAxis, pow);
    }

    private String toBitString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 8);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%8s", Integer.toBinaryString(b & 0xff)).replace(' ', '0'));
        }
        return sb.toString();
    }

    private static int parseBits(String bits, int start, int len) {
        return Integer.parseInt(bits.substring(start, start + len), 2);
    }

    private String normalizeMac(String mac) {
        if (TextUtils.isEmpty(mac)) {
            return null;
        }
        String normalized = mac.trim().toUpperCase(Locale.US);
        return normalized.matches("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$") ? normalized : null;
    }

    private void showInitialStateDialogIfNeeded() {
        if (initialStateShown) {
            return;
        }
        initialStateShown = true;
        context.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                context.showCubeStateDialog();
            }
        });
    }
}
