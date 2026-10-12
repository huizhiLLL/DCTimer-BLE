package com.dctimer.model;

import org.junit.Test;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class SmartCubeGyroRecorderTest {
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
