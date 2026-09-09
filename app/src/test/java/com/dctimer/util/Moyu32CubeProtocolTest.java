package com.dctimer.util;

import android.bluetooth.BluetoothGattCharacteristic;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Moyu32CubeProtocolTest {
    @Test
    public void buildsVerifiedSolvedStateRequest() {
        assertArrayEquals(
                hex("A20000002492494924926DB6DB924924B6DB6D00"),
                Moyu32CubeProtocol.buildSolvedResetRequest());
    }

    @Test
    public void acceptsStateFrameDuringInitializationOrReset() {
        assertTrue(Moyu32CubeProtocol.shouldApplyStateFrame(-1, false));
        assertTrue(Moyu32CubeProtocol.shouldApplyStateFrame(42, true));
        assertFalse(Moyu32CubeProtocol.shouldApplyStateFrame(42, false));
    }

    @Test
    public void onlyAcceptsSolvedStateAsResetConfirmation() {
        assertTrue(Moyu32CubeProtocol.isExpectedResetState(
                "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB"));
        assertFalse(Moyu32CubeProtocol.isExpectedResetState(
                "UUUUUUUURURRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB"));
    }

    @Test
    public void resetRequestUsesExistingCipherPath() throws Exception {
        Moyu32Cipher cipher = new Moyu32Cipher();
        cipher.init("CF:30:16:00:12:34");
        byte[] request = Moyu32CubeProtocol.buildSolvedResetRequest();

        assertArrayEquals(request, cipher.decode(cipher.encode(request)));
    }

    @Test
    public void prefersWriteWithResponseAndFallsBackToNoResponse() {
        int both = BluetoothGattCharacteristic.PROPERTY_WRITE
                | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;

        assertEquals(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                Moyu32CubeProtocol.resolveWriteType(both));
        assertEquals(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
                Moyu32CubeProtocol.resolveWriteType(
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE));
        assertEquals(-1, Moyu32CubeProtocol.resolveWriteType(0));
    }

    private static byte[] hex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }
}
