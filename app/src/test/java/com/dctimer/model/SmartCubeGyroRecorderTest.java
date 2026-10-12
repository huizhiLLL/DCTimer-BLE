package com.dctimer.model;

import org.junit.Test;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class SmartCubeGyroRecorderTest {
    @Test public void finalSliceCanUseLatePostureWithoutExtendingSolveTime() {
        SmartCubeGyroRecorder recorder=new SmartCubeGyroRecorder();
        recorder.accept(800,0,0,0,1);
        recorder.begin(900);
        recorder.accept(1100,1,0,0,0);
        recorder.accept(1300,0,0,0,1); // Outside the 250 ms tail.
        List<SmartCubeSolveReconstruction.MoveEvent> moves=Arrays.asList(
                new SmartCubeSolveReconstruction.MoveEvent(4,0,0),
                new SmartCubeSolveReconstruction.MoveEvent(13,30,30));
        List<SmartCubeSolveReconstruction.GyroSample> samples=recorder.finish(Arrays.asList(1000L,1030L),moves);
        assertEquals(2,samples.size());
        assertEquals(100,samples.get(1).elapsedMs);
        SmartCubeReconstructionEngine.Result result=SmartCubeReconstructionEngine.reconstruct(
                "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB",moves,
                new int[]{1,1},SmartCubeOrientation.IDENTITY,samples);
        assertEquals(1,result.tokens.size());
        assertEquals("M2",result.tokens.get(0).text());
        assertEquals(30,result.tokens.get(0).endMs);
    }

    @Test public void finishedSnapshotIsIndependentOfResetAndSubsequentMoves() {
        SmartCube cube=new SmartCube();
        cube.markSolved();
        cube.markScrambled();
        cube.applyMove(4,0,null);
        cube.applyMove(13,80,null);
        SmartCube finished=cube.snapshotForReconstruction();
        assertEquals(80,finished.getResult());
        cube.markSolved();
        cube.applyMove(0,900,null);
        finished.calcResult();
        assertEquals(80,finished.getResult());
        assertEquals(2,finished.getMovesCount());
        cube.releaseReconstruction(finished);
    }

    @Test public void recalibrationRemovesDriftAndPreservesAllTrainingFrames() {
        double angle=Math.toRadians(37)/2;
        float z=(float)Math.sin(angle), w=(float)Math.cos(angle);
        for(int frame=0;frame<24;frame++) {
            SmartCubeGyroRecorder recorder=new SmartCubeGyroRecorder();
            recorder.accept(0,0,0,0,1);
            recorder.begin(10);
            recorder.accept(100,0,0,z,w);
            assertTrue(recorder.recalibrate(0,0,z,w,frame));
            recorder.begin(200);
            recorder.accept(300,0,0,z,w);
            // Sensor pose after a local x turn: drift baseline multiplied by x.
            float s=(float)Math.sqrt(0.5);
            recorder.accept(400,-w*s,-z*s,z*s,w*s);
            List<SmartCubeSolveReconstruction.GyroSample> samples=recorder.finish(Arrays.asList(500L),
                    Arrays.asList(new SmartCubeSolveReconstruction.MoveEvent(0,0,0)));
            assertEquals(3,samples.size());
            assertEquals(-300,samples.get(0).elapsedMs);
            assertEquals(frame,SmartCubeOrientation.nearest(samples.get(0).quaternion,1));
            assertEquals(frame,SmartCubeOrientation.nearest(samples.get(1).quaternion,1));
            assertEquals(SmartCubeOrientation.compose(frame,SmartCubeOrientation.turn(0,1)),
                    SmartCubeOrientation.nearest(samples.get(2).quaternion,1));
        }
    }

    @Test public void repeatedCalibrationDiscardsOldAttemptAndRejectsInvalidPose() {
        SmartCubeGyroRecorder recorder=new SmartCubeGyroRecorder();
        assertTrue(recorder.recalibrate(0,0,0,1,0));
        recorder.begin(100);
        recorder.accept(200,0,0,0.70710677f,0.70710677f);
        assertFalse(recorder.recalibrate(Float.NaN,0,0,1,0));
        assertFalse(recorder.recalibrate(0,0,0,1,24));
        List<SmartCubeSolveReconstruction.MoveEvent> moves=Arrays.asList(new SmartCubeSolveReconstruction.MoveEvent(0,0,0));
        assertEquals(2,recorder.finish(Arrays.asList(300L),moves).size());
        assertTrue(recorder.recalibrate(0,0,0.70710677f,0.70710677f,0));
        assertTrue(recorder.finish(Arrays.asList(400L),moves).isEmpty());
        recorder.begin(500);
        assertEquals(SmartCubeOrientation.IDENTITY,
                SmartCubeOrientation.nearest(recorder.finish(Arrays.asList(600L),moves).get(0).quaternion,1));
    }

    @Test public void alignsHostSamplesToDeviceTimeAndClearsFinishedAttempt() {
        SmartCubeGyroRecorder recorder=new SmartCubeGyroRecorder();
        recorder.accept(100,0,0,0,1);
        recorder.begin(500);
        recorder.accept(1500,0,0,0,1);
        List<Long> arrivals=Arrays.asList(1000L,2000L);
        List<SmartCubeSolveReconstruction.MoveEvent> moves=Arrays.asList(
                new SmartCubeSolveReconstruction.MoveEvent(0,0,0),
                new SmartCubeSolveReconstruction.MoveEvent(3,600,600));
        List<SmartCubeSolveReconstruction.GyroSample> samples=recorder.finish(arrivals,moves);
        assertEquals(-500,samples.get(0).elapsedMs);
        assertEquals(300,samples.get(1).elapsedMs);
        assertTrue(recorder.finish(arrivals,moves).isEmpty());
    }

    @Test public void cancelledAttemptDoesNotLeakObservationIntoNextSolve() {
        SmartCubeGyroRecorder recorder=new SmartCubeGyroRecorder();
        recorder.accept(100,0,0,0,1);
        recorder.begin(200);
        recorder.accept(300,0.70710677f,0,0,0.70710677f);
        recorder.clearAttempt();
        recorder.begin(1000);
        List<SmartCubeSolveReconstruction.GyroSample> samples=recorder.finish(Arrays.asList(1100L),
                Arrays.asList(new SmartCubeSolveReconstruction.MoveEvent(0,0,0)));
        assertEquals(1,samples.size());
        assertEquals(-100,samples.get(0).elapsedMs);
        assertEquals(SmartCubeOrientation.turn(0,3),SmartCubeOrientation.nearest(samples.get(0).quaternion,1));
    }

    @Test public void invalidSensorDataDoesNotCreateCapabilityAndFieldsAreTransient() throws Exception {
        SmartCubeGyroRecorder recorder=new SmartCubeGyroRecorder();
        recorder.accept(100,Float.NaN,0,0,1);
        recorder.begin(200);
        assertTrue(recorder.finish(Arrays.asList(300L),Arrays.asList(new SmartCubeSolveReconstruction.MoveEvent(0,0,0))).isEmpty());
        assertTrue(Modifier.isTransient(SmartCube.class.getDeclaredField("gyroRecorder").getModifiers()));
        assertTrue(Modifier.isTransient(SmartCube.class.getDeclaredField("moveArrivals").getModifiers()));
    }
}
