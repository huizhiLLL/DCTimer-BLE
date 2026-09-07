package com.dctimer.util;

import com.dctimer.APP;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StringUtilsTest {
    @Test
    public void parseManualInputTime_splitsPlainDigitsByAccuracy() {
        APP.timerAccuracy = 1;
        assertEquals(1234, StringUtils.parseManualInputTime("1234"));
        assertEquals(82334, StringUtils.parseManualInputTime("122334"));

        APP.timerAccuracy = 0;
        assertEquals(12340, StringUtils.parseManualInputTime("1234"));
        assertEquals(82340, StringUtils.parseManualInputTime("12234"));
    }

    @Test
    public void parseManualInputTime_keepsExplicitSeparatorsAsTimeText() {
        APP.timerAccuracy = 1;
        assertEquals(12340, StringUtils.parseManualInputTime("12.34"));
        assertEquals(83450, StringUtils.parseManualInputTime("1:23.45"));
    }

    @Test
    public void getScrambleDisplaySubIndex_alignsWcaLargeCubes() {
        assertEquals(7, StringUtils.getScrambleDisplaySubIndex(-1, 7));
        assertEquals(12, StringUtils.getScrambleDisplaySubIndex(-1, 13));
        assertEquals(13, StringUtils.getScrambleDisplaySubIndex(-1, 14));
        assertEquals(13, StringUtils.getScrambleDisplaySubIndex(0, 13));
    }
}
