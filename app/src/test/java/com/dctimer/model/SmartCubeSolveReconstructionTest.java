package com.dctimer.model;

import com.dctimer.APP;
import com.dctimer.util.Utils;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SmartCubeSolveReconstructionTest {
    private static final String SOLVED = "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB";

    @Test
    public void mergesAdjacentSameFaceTurnsIntoOneMove() {
        List<SmartCubeSolveReconstruction.MoveEvent> raw = new ArrayList<>();
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(3, 0, 0));
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(3, 80, 80));

        SmartCubeSolveReconstruction reconstruction = SmartCubeSolveReconstruction.fromRawMoves(SOLVED, raw);

        // Automatic cross normalization puts the untouched opposite cross down.
        assertEquals("U2", reconstruction.getMoveSequence());
        assertEquals(1, reconstruction.getMoveCount());
    }

    @Test
    public void keepsUnclosedOppositePairEvenWithinOldWindow() {
        List<SmartCubeSolveReconstruction.MoveEvent> raw = new ArrayList<>();
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(0, 0, 0));
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(11, 90, 90));

        SmartCubeSolveReconstruction reconstruction = SmartCubeSolveReconstruction.fromRawMoves(SOLVED, raw);

        assertEquals("U D'", reconstruction.getMoveSequence());
        assertEquals(2, reconstruction.getMoveCount());
    }

    @Test
    public void keepsOppositeLayerTurnsSeparateOutsideWindow() {
        List<SmartCubeSolveReconstruction.MoveEvent> raw = new ArrayList<>();
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(0, 0, 0));
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(11, 120, 120));

        SmartCubeSolveReconstruction reconstruction = SmartCubeSolveReconstruction.fromRawMoves(SOLVED, raw);

        assertEquals("U D'", reconstruction.getMoveSequence());
        assertEquals(2, reconstruction.getMoveCount());
    }

    @Test
    public void keepsFinalAufTurnsInsidePllPhase() {
        List<SmartCubeSolveReconstruction.MoveEvent> raw = new ArrayList<>();
        raw.add(new SmartCubeSolveReconstruction.MoveEvent(0, 0, 0));

        SmartCubeSolveReconstruction reconstruction = SmartCubeSolveReconstruction.fromRawMoves(SOLVED, raw);

        assertTrue(reconstruction.getPrettySolve().contains("// PLL"));
        assertTrue(!reconstruction.getPrettySolve().contains("// AUF"));
    }

    @Test
    public void emitsPhaseMetadataJson() {
        int originalMethod = APP.smartCubeSolveMethod;
        try {
            APP.smartCubeSolveMethod = 0;
            List<SmartCubeSolveReconstruction.MoveEvent> raw = new ArrayList<>();
            raw.add(new SmartCubeSolveReconstruction.MoveEvent(3, 0, 0));

            SmartCubeSolveReconstruction reconstruction = SmartCubeSolveReconstruction.fromRawMoves(SOLVED, raw);

            assertTrue(reconstruction.toJson(1000).contains("\"method\":\"333-smart-cf4op\""));
        } finally {
            APP.smartCubeSolveMethod = originalMethod;
        }
    }

    @Test
    public void rouxMethodEmitsRouxMetadataAndPhases() {
        int originalMethod = APP.smartCubeSolveMethod;
        try {
            APP.smartCubeSolveMethod = 1;
            List<SmartCubeSolveReconstruction.MoveEvent> raw = new ArrayList<>();
            raw.add(new SmartCubeSolveReconstruction.MoveEvent(3, 0, 0));

            SmartCubeSolveReconstruction reconstruction = SmartCubeSolveReconstruction.fromRawMoves(SOLVED, raw);

            assertTrue(reconstruction.toJson(1000).contains("\"method\":\"333-smart-roux\""));
            assertTrue(reconstruction.getPrettySolve().contains("// L6E"));
        } finally {
            APP.smartCubeSolveMethod = originalMethod;
        }
    }

    @Test
    public void prettySolveOmitsPhaseMoveCountsAndAppendsSolveStats() {
        int originalMethod = APP.smartCubeSolveMethod;
        try {
            APP.smartCubeSolveMethod = 0;
            List<SmartCubeSolveReconstruction.MoveEvent> raw = new ArrayList<>();
            raw.add(new SmartCubeSolveReconstruction.MoveEvent(3, 0, 0));

            SmartCubeSolveReconstruction reconstruction = SmartCubeSolveReconstruction.fromRawMoves(SOLVED, raw);
            String prettySolve = reconstruction.getPrettySolve(1000);

            assertTrue(prettySolve.contains("STM: 1 moves"));
            assertTrue(prettySolve.contains("TPS: 1.0"));
            assertTrue(!prettySolve.contains("move(s)"));
        } finally {
            APP.smartCubeSolveMethod = originalMethod;
        }
    }

    @Test
    public void cf4opProgressDoesNotCountOneSolvedF2lSlotAsEverySlot() throws Exception {
        char[] facelets = SOLVED.toCharArray();
        facelets[21] = 'B';
        facelets[14] = 'F';
        facelets[39] = 'B';

        assertEquals(5, invokeCf4opProgress(new String(facelets)));
    }

    @Test
    public void rouxProgressRecognizesSolvedState() throws Exception {
        assertEquals(0, invokeRouxProgress(SOLVED));
    }

    @Test
    public void phaseTimesIncludeGapSincePreviousPhase() throws Exception {
        List<Object> phases = new ArrayList<>();
        phases.add(newPhase("Cross", 8, 100, 1000));
        phases.add(newPhase("F2L 1", 6, 1400, 2000));
        phases.add(newPhase("F2L 2", 7, 2500, 3000));
        phases.add(newPhase("F2L 3", 6, 3600, 4000));
        phases.add(newPhase("F2L 4", 7, 4600, 5000));

        List<?> adjusted = invokeIncludePhaseGaps(phases);

        assertEquals(0, getPhaseStartMs(adjusted.get(0)));
        assertEquals(1000, getPhaseStartMs(adjusted.get(1)));
        assertEquals(2000, getPhaseStartMs(adjusted.get(2)));
        assertEquals(3000, getPhaseStartMs(adjusted.get(3)));
        assertEquals(4000, getPhaseStartMs(adjusted.get(4)));
        int f2lTimeMs = 0;
        for (int i = 1; i <= 4; i++) {
            f2lTimeMs += getPhaseEndMs(adjusted.get(i)) - getPhaseStartMs(adjusted.get(i));
        }
        assertEquals(4000, f2lTimeMs);
    }

    private static int invokeCf4opProgress(String facelets) throws Exception {
        Method method = SmartCubeSolveReconstruction.class.getDeclaredMethod("getCf4opProgress", String.class);
        method.setAccessible(true);
        return (Integer) method.invoke(null, facelets);
    }

    private static int invokeRouxProgress(String facelets) throws Exception {
        Method method = SmartCubeSolveReconstruction.class.getDeclaredMethod("getRouxProgress", String.class);
        method.setAccessible(true);
        return (Integer) method.invoke(null, facelets);
    }

    private static int findOrientation(int top, int front) {
        for (int i = 0; i < Utils.SMART_CUBE_ORIENTATION_FACES.length; i++) {
            int[] pair = Utils.SMART_CUBE_ORIENTATION_FACES[i];
            if (pair[0] == top && pair[1] == front) {
                return i;
            }
        }
        return 0;
    }

    private static Object newPhase(String name, int moveCount, int startMs, int endMs) throws Exception {
        Class<?> phaseClass = Class.forName("com.dctimer.model.SmartCubeSolveReconstruction$Phase");
        Constructor<?> constructor = phaseClass.getDeclaredConstructor(String.class, String.class, int.class, int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(name, name, moveCount, startMs, endMs);
    }

    private static List<?> invokeIncludePhaseGaps(List<Object> phases) throws Exception {
        Method method = SmartCubeSolveReconstruction.class.getDeclaredMethod("includePhaseGaps", List.class);
        method.setAccessible(true);
        return (List<?>) method.invoke(null, phases);
    }

    private static int getPhaseStartMs(Object phase) throws Exception {
        return getPhaseIntField(phase, "startMs");
    }

    private static int getPhaseEndMs(Object phase) throws Exception {
        return getPhaseIntField(phase, "endMs");
    }

    private static int getPhaseIntField(Object phase, String fieldName) throws Exception {
        Field field = phase.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Integer) field.get(phase);
    }

}
