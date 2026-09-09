package com.dctimer.util;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.text.TextUtils;
import android.util.Log;

import com.dctimer.activity.MainActivity;
import com.dctimer.model.SmartCube;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.UUID;

public class Moyu32CubeProtocol implements SmartCubeProtocol {
    private static final String TAG = "Moyu32";
    public static final UUID SERVICE_UUID = UUID.fromString("0783b03e-7735-b5a0-1760-a305d2795cb0");
    public static final UUID READ_UUID = UUID.fromString("0783b03e-7735-b5a0-1760-a305d2795cb1");
    public static final UUID WRITE_UUID = UUID.fromString("0783b03e-7735-b5a0-1760-a305d2795cb2");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final float GYRO_SCALE = 1073741824f;
    private static final String SOLVED_STATE = "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB";

    private final MainActivity context;
    private final SmartCube smartCube;
    private final Moyu32Cipher cipher = new Moyu32Cipher();
    private final ArrayDeque<byte[]> requestQueue = new ArrayDeque<>();

    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic readCharacteristic;
    private BluetoothGattCharacteristic writeCharacteristic;
    private boolean notificationsReady;
    private boolean writePending;
    private boolean fallbackTried;
    private boolean initialStateShown;
    private boolean resetStatePending;
    private int pendingWriteOpcode = -1;
    private String deviceName;
    private String deviceMac;

    private int moveCnt = -1;
    private int prevMoveCnt = -1;

    public Moyu32CubeProtocol(MainActivity context, SmartCube smartCube) {
        this.context = context;
        this.smartCube = smartCube;
    }

    public boolean start(BluetoothGatt gatt, BluetoothGattService service, String deviceName, String deviceAddress) {
        this.gatt = gatt;
        this.deviceName = deviceName == null ? "" : deviceName.trim();
        this.deviceMac = deviceAddress;
        this.moveCnt = -1;
        this.prevMoveCnt = -1;
        this.notificationsReady = false;
        this.writePending = false;
        this.fallbackTried = false;
        this.initialStateShown = false;
        this.resetStatePending = false;
        this.pendingWriteOpcode = -1;
        requestQueue.clear();
        readCharacteristic = service.getCharacteristic(READ_UUID);
        writeCharacteristic = service.getCharacteristic(WRITE_UUID);
        if (readCharacteristic == null || writeCharacteristic == null) {
            Log.e(TAG, "MoYu32 特征不存在");
            return false;
        }
        int writeType = resolveWriteType(writeCharacteristic.getProperties());
        if (writeType == -1) {
            Log.e(TAG, "MoYu32 写特征不支持写入");
            return false;
        }
        writeCharacteristic.setWriteType(writeType);
        tryInitCipher(deviceMac);
        if (!gatt.setCharacteristicNotification(readCharacteristic, true)) {
            Log.e(TAG, "MoYu32 无法开启通知");
            return false;
        }
        BluetoothGattDescriptor descriptor = readCharacteristic.getDescriptor(CCCD_UUID);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (!gatt.writeDescriptor(descriptor)) {
                Log.e(TAG, "MoYu32 通知描述符写入失败，直接继续");
                onNotificationsEnabled();
            }
        } else {
            onNotificationsEnabled();
        }
        return true;
    }

    public void clear() {
        requestQueue.clear();
        notificationsReady = false;
        writePending = false;
        readCharacteristic = null;
        writeCharacteristic = null;
        gatt = null;
        deviceName = null;
        deviceMac = null;
        moveCnt = -1;
        prevMoveCnt = -1;
        fallbackTried = false;
        initialStateShown = false;
        resetStatePending = false;
        pendingWriteOpcode = -1;
    }

    public void onDescriptorWrite(BluetoothGattDescriptor descriptor, int status) {
        if (descriptor == null || descriptor.getCharacteristic() == null) {
            return;
        }
        if (READ_UUID.equals(descriptor.getCharacteristic().getUuid())) {
            onNotificationsEnabled();
        }
    }

    public void onCharacteristicWrite(BluetoothGattCharacteristic characteristic, int status) {
        if (characteristic == null || !WRITE_UUID.equals(characteristic.getUuid())) {
            return;
        }
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.e(TAG, "MoYu32 写入失败，status=" + status);
            if (pendingWriteOpcode == 0xA2) {
                resetStatePending = false;
            }
        }
        pendingWriteOpcode = -1;
        writePending = false;
        sendNextRequest();
    }

    public void onCharacteristicChanged(BluetoothGattCharacteristic characteristic) {
        if (characteristic == null || !READ_UUID.equals(characteristic.getUuid())) {
            return;
        }
        try {
            parseData(characteristic.getValue());
        } catch (Exception e) {
            Log.e(TAG, "MoYu32 数据解析失败", e);
        }
    }

    @Override
    public void onLocalCubeReset(String cubeState) {
        if (gatt == null || writeCharacteristic == null) {
            Log.w(TAG, "MoYu32 reset ignored: protocol is not connected");
            return;
        }
        resetStatePending = true;
        enqueueRequest(buildSolvedResetRequest());
        Log.w(TAG, "MoYu32 已请求同步还原状态: " + cubeState);
    }

    private void onNotificationsEnabled() {
        if (notificationsReady) {
            return;
        }
        notificationsReady = true;
        enqueueSimpleRequest(161);
        enqueueSimpleRequest(163);
        enqueueSimpleRequest(164);
        enqueueSimpleRequest(161);
        enqueueSimpleRequest(163);
        enqueueSimpleRequest(164);
        enqueueGyroEnableRequest();
        enqueueSimpleRequest(163);
    }

    private void enqueueSimpleRequest(int opcode) {
        byte[] req = new byte[20];
        req[0] = (byte) opcode;
        enqueueRequest(req);
    }

    private void enqueueGyroEnableRequest() {
        byte[] req = new byte[20];
        req[0] = (byte) 172;
        req[2] = 1;
        enqueueRequest(req);
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
            int opcode = request[0] & 0xff;
            if (writePending) {
                pendingWriteOpcode = opcode;
            } else {
                Log.e(TAG, "MoYu32 无法发起写入请求");
                if (opcode == 0xA2) {
                    resetStatePending = false;
                }
            }
        } catch (GeneralSecurityException e) {
            Log.e(TAG, "MoYu32 请求加密失败", e);
        }
    }

    private void parseData(byte[] value) throws GeneralSecurityException {
        byte[] decoded = cipher.decode(value);
        if (decoded.length >= 17 && (decoded[0] & 0xff) == 171) {
            handleGyro(decoded);
            return;
        }
        StringBuilder bits = new StringBuilder(decoded.length * 8);
        for (byte datum : decoded) {
            bits.append(String.format(Locale.US, "%8s", Integer.toBinaryString(datum & 0xff)).replace(' ', '0'));
        }
        String data = bits.toString();
        int msgType = parseBits(data, 0, 8);
        switch (msgType) {
            case 161:
                Log.w(TAG, "收到 MoYu32 设备信息");
                break;
            case 163:
                handleInitialState(data);
                break;
            case 164:
                handleBattery(data);
                break;
            case 165:
                handleMove(data);
                break;
            case 171:
                handleGyro(decoded);
                break;
            default:
                Log.w(TAG, "未知 MoYu32 消息类型: " + msgType);
                if (prevMoveCnt == -1) {
                    tryFallbackCipher();
                }
                break;
        }
    }

    private void handleInitialState(String data) {
        boolean resetResponse = resetStatePending;
        if (!shouldApplyStateFrame(prevMoveCnt, resetResponse)) {
            return;
        }
        moveCnt = parseBits(data, 152, 8);
        String facelet = parseFacelet(data.substring(8, 152));
        if (resetResponse && !isExpectedResetState(facelet)) {
            Log.w(TAG, "MoYu32 忽略重置请求前遗留的状态帧: " + facelet);
            return;
        }
        int check = smartCube.setCubeState(facelet);
        if (check != 0) {
            Log.e(TAG, resetResponse ? "MoYu32 重置状态校验失败" : "MoYu32 初始状态校验失败");
            if (!resetResponse && !tryFallbackCipher()) {
                smartCube.setCubeState(SOLVED_STATE);
            }
            return;
        }
        prevMoveCnt = moveCnt;
        resetStatePending = false;
        Log.w(TAG, resetResponse ? "MoYu32 重置状态已同步: " + facelet : "MoYu32 初始状态: " + facelet);
        if (resetResponse) {
            context.refreshSmartCubeStateUi();
        }
        if (!initialStateShown) {
            initialStateShown = true;
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    context.showCubeStateDialog();
                }
            });
        }
    }

    private void handleBattery(String data) {
        int battery = parseBits(data, 8, 8);
        smartCube.setBatteryValue(battery);
        Log.w(TAG, "MoYu32 电量: " + battery);
        context.refreshSmartCubeStateUi();
    }

    private void handleMove(String data) {
        moveCnt = parseBits(data, 88, 8);
        if (resetStatePending) {
            resetStatePending = false;
            Log.w(TAG, "MoYu32 重置确认前收到转动，继续使用原计步基线");
        }
        if (prevMoveCnt == -1 || moveCnt == prevMoveCnt) {
            return;
        }
        int[] timeOffsets = new int[5];
        int[] rawMoves = new int[5];
        boolean invalidMove = false;
        for (int i = 0; i < 5; i++) {
            timeOffsets[i] = parseBits(data, 8 + i * 16, 16);
            rawMoves[i] = parseBits(data, 96 + i * 5, 5);
            if (rawMoves[i] >= 12) {
                invalidMove = true;
            }
        }
        if (invalidMove) {
            Log.w(TAG, "MoYu32 收到非法转动数据");
            return;
        }
        int moveDiff = (moveCnt - prevMoveCnt) & 0xff;
        prevMoveCnt = moveCnt;
        if (moveDiff > rawMoves.length) {
            moveDiff = rawMoves.length;
        }
        for (int i = moveDiff - 1; i >= 0; i--) {
            int move = convertMove(rawMoves[i]);
            context.moveCube(smartCube, move, timeOffsets[i]);
            Log.w(TAG, "MoYu32 move=" + move + " time=" + timeOffsets[i]);
        }
    }

    private void handleGyro(byte[] decoded) {
        if (decoded == null || decoded.length < 17) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN);
        float w = buffer.getInt(1) / GYRO_SCALE;
        float x = buffer.getInt(5) / GYRO_SCALE;
        float y = buffer.getInt(9) / GYRO_SCALE;
        float z = buffer.getInt(13) / GYRO_SCALE;
        float length = (float) Math.sqrt(w * w + x * x + y * y + z * z);
        if (length <= 0f || Float.isNaN(length) || Float.isInfinite(length)) {
            return;
        }
        context.onSmartCubeGyroChanged(x / length, z / length, -y / length, w / length);
    }

    private int convertMove(int rawMove) {
        char face = "FBUDLR".charAt(rawMove >> 1);
        int suffix = (rawMove & 1) == 0 ? 0 : 2;
        return "URFDLB".indexOf(face) * 3 + suffix;
    }

    private int parseBits(String data, int start, int len) {
        return Integer.parseInt(data.substring(start, start + len), 2);
    }

    static boolean shouldApplyStateFrame(int previousMoveCount, boolean resetPending) {
        return previousMoveCount == -1 || resetPending;
    }

    static boolean isExpectedResetState(String facelet) {
        return SOLVED_STATE.equals(facelet);
    }

    static int resolveWriteType(int properties) {
        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
            return BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
        }
        if ((properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            return BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
        }
        return -1;
    }

    static byte[] buildSolvedResetRequest() {
        return new byte[]{
                (byte) 0xA2, 0x00, 0x00, 0x00, 0x24, (byte) 0x92, 0x49, 0x49,
                0x24, (byte) 0x92, 0x6D, (byte) 0xB6, (byte) 0xDB, (byte) 0x92,
                0x49, 0x24, (byte) 0xB6, (byte) 0xDB, 0x6D, 0x00
        };
    }

    private String parseFacelet(String faceletBits) {
        StringBuilder state = new StringBuilder(54);
        int[] faces = {2, 5, 0, 3, 4, 1};
        for (int i = 0; i < 6; i++) {
            String face = faceletBits.substring(faces[i] * 24, faces[i] * 24 + 24);
            for (int j = 0; j < 8; j++) {
                state.append("FBUDLR".charAt(parseBits(face, j * 3, 3)));
                if (j == 3) {
                    state.append("FBUDLR".charAt(faces[i]));
                }
            }
        }
        return state.toString();
    }

    private void tryInitCipher(String mac) {
        if (TextUtils.isEmpty(mac)) {
            return;
        }
        try {
            cipher.init(mac);
            Log.w(TAG, "MoYu32 使用地址初始化: " + mac);
        } catch (Exception e) {
            Log.e(TAG, "MoYu32 初始化地址失败: " + mac, e);
        }
    }

    private boolean tryFallbackCipher() {
        if (fallbackTried) {
            return false;
        }
        String fallbackMac = getFallbackMac(deviceName);
        if (TextUtils.isEmpty(fallbackMac) || fallbackMac.equalsIgnoreCase(deviceMac)) {
            fallbackTried = true;
            return false;
        }
        fallbackTried = true;
        try {
            cipher.init(fallbackMac);
            deviceMac = fallbackMac;
            moveCnt = -1;
            prevMoveCnt = -1;
            requestQueue.clear();
            writePending = false;
            resetStatePending = false;
            pendingWriteOpcode = -1;
            enqueueSimpleRequest(163);
            enqueueSimpleRequest(164);
            Log.w(TAG, "MoYu32 使用备用 MAC: " + fallbackMac);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "MoYu32 备用 MAC 初始化失败", e);
            return false;
        }
    }

    private String getFallbackMac(String deviceName) {
        if (TextUtils.isEmpty(deviceName) || !deviceName.matches("^WCU_MY32_[0-9A-Fa-f]{4}$")) {
            return null;
        }
        String suffix = deviceName.substring(deviceName.length() - 4).toUpperCase(Locale.US);
        return "CF:30:16:00:" + suffix.substring(0, 2) + ":" + suffix.substring(2, 4);
    }
}
