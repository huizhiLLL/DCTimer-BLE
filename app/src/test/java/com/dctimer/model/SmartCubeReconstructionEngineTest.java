package com.dctimer.model;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;
import static com.dctimer.model.SmartCubeOrientation.*;

public class SmartCubeReconstructionEngineTest {
    private static final String SOLVED="UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB";
    private static List<SmartCubeSolveReconstruction.MoveEvent> moves(int... values) {
        List<SmartCubeSolveReconstruction.MoveEvent> out=new ArrayList<>();
        for(int i=0;i<values.length;i+=2) out.add(new SmartCubeSolveReconstruction.MoveEvent(values[i],0,values[i+1]));
        return out;
    }
    private static SmartCubeSolveReconstruction.GyroSample pose(int time,int axis,double degrees) {
        double[] q={0,0,0,Math.cos(Math.toRadians(degrees)/2)};
        q[axis]=Math.sin(Math.toRadians(degrees)/2);
        return new SmartCubeSolveReconstruction.GyroSample(time,q);
    }
    private static SmartCubeReconstructionEngine.Result run(List<SmartCubeSolveReconstruction.MoveEvent> moves,
                                                            SmartCubeSolveReconstruction.GyroSample... samples) {
        int[] phases=new int[moves.size()]; Arrays.fill(phases,1);
        return SmartCubeReconstructionEngine.reconstruct(SOLVED,moves,phases,IDENTITY,Arrays.asList(samples));
    }
    private static String text(SmartCubeReconstructionEngine.Result result) {
        StringBuilder out=new StringBuilder();
        for(SmartCubeReconstructionEngine.Token token:result.tokens) {
            if(out.length()>0) out.append(' ');
            out.append(token.text());
        }
        return out.toString();
    }

    @Test public void quaternionAndDiscreteFramesAgreeForAllAxesAndSigns() {
        for(int axis=0;axis<3;axis++) for(int q=1;q<4;q++) {
            assertEquals(turn(axis,q),nearest(pose(0,axis,-90*q).quaternion,1));
            assertEquals(IDENTITY,compose(turn(axis,q),inverse(turn(axis,q))));
        }
        for(int frame=0;frame<24;frame++) {
            int composed=IDENTITY;
            for(int r:path(frame)) composed=compose(composed,turn(r/3,r%3+1));
            assertEquals(frame,composed);
        }
    }

    @Test public void wholeCubeRotationRenamesEveryFollowingMove() {
        assertEquals("R x B",text(run(moves(3,0,0,600),pose(-500,0,0),pose(300,0,-90))));
    }

    @Test public void inspectionRotationChangesInitialGripWithoutCountingAMove() {
        SmartCubeReconstructionEngine.Result result=run(moves(0,0),pose(-500,0,0),pose(-100,0,-90));
        assertEquals("x",result.inspection);
        assertEquals("B",text(result));
        assertFalse(result.tokens.get(0).rotation());
    }

    @Test public void sliceConsumesCoreRotationAndRenamesFollowingMoves() {
        assertEquals("M F",text(run(moves(3,200,14,230,0,600),pose(-500,0,0),pose(210,0,90))));
    }

    @Test public void wideConsumesOppositeFaceAndCoreExactlyOnce() {
        assertEquals("r B",text(run(moves(12,200,0,600),pose(-500,0,0),pose(200,0,-90))));
    }

    @Test public void halfTurnCoreSupportsTwoSlicePairs() {
        assertEquals("M2 D",text(run(moves(3,200,14,220,3,250,14,280,0,600),
                pose(-500,0,0),pose(240,0,180))));
    }

    @Test public void wideHalfTurnMayArriveAsTwoQuarterTurns() {
        assertEquals("r2 D",text(run(moves(12,200,12,240,0,600),
                pose(-500,0,0),pose(220,0,-180))));
    }

    @Test public void rotationFarFromEncoderIsNotAWide() {
        assertEquals("L x B",text(run(moves(12,0,0,1000),pose(-500,0,0),pose(500,0,-90))));
    }

    @Test public void algebraicSlicesMustCloseTheirFrameAndDoNotUseOld100msCutoff() {
        assertEquals("U D'",text(run(moves(0,0,11,30))));
        assertEquals("E E'",text(run(moves(0,0,11,350,2,700,9,1100))));
    }

    @Test public void sliceDoesNotMergeAcrossPhaseBoundaries() {
        List<SmartCubeSolveReconstruction.MoveEvent> moves=moves(3,200,14,230);
        SmartCubeReconstructionEngine.Result result=SmartCubeReconstructionEngine.reconstruct(SOLVED,moves,new int[]{1,2},IDENTITY,new ArrayList<SmartCubeSolveReconstruction.GyroSample>());
        assertEquals("R L'",text(result));
    }

    @Test public void rotationInsideFinalPairIsPreserved() {
        SmartCubeReconstructionEngine.Result result=run(moves(0,0,11,350,2,700,9,1100),
                pose(-500,0,0),pose(950,2,-90));
        String actual=SOLVED;
        for(SmartCubeReconstructionEngine.Token token:result.tokens) actual=applyNotation(actual,token);
        assertEquals(com.dctimer.util.Utils.orientFacelets(SOLVED,turn(2,1)),actual);
    }

    @Test public void measuredSliceCandidateDoesNotSwallowAnIndependentRegrip() {
        SmartCubeReconstructionEngine.Result result=run(moves(3,200,14,230),
                pose(-500,0,0),pose(210,0,90),
                new SmartCubeSolveReconstruction.GyroSample(220,new double[]{0.5,-0.5,-0.5,0.5}));
        String actual=SOLVED;
        for(SmartCubeReconstructionEngine.Token token:result.tokens) actual=applyNotation(actual,token);
        cs.min2phase.CubieCube cube=new cs.min2phase.CubieCube().move(3).move(14);
        assertEquals(com.dctimer.util.Utils.orientFacelets(cs.min2phase.Util.toFaceCube(cube),
                compose(turn(0,3),turn(1,1))),actual);
    }

    @Test public void wasteLoopIsPreservedAndCountedWithoutErasingItsTime() {
        SmartCubeReconstructionEngine.Result result=run(moves(3,0,0,100,2,250,5,400));
        assertEquals("R U U' R'",text(result));
        assertEquals(4,result.wastedRawMoves);
        assertEquals(400,result.wastedMs);
        for(SmartCubeReconstructionEngine.Token token:result.tokens) assertTrue(token.waste);
    }

    @Test public void overturnIsVisibleButDoesNotClaimWholeRunAsWastedTime() {
        SmartCubeReconstructionEngine.Result result=run(moves(0,0,0,100,0,200));
        assertEquals("U2 U",text(result));
        assertTrue(result.tokens.get(0).waste);
        assertEquals(0,result.wastedMs);
    }

    @Test public void jitterAndQuaternionSignDoNotCreateRotations() {
        assertEquals("R U",text(run(moves(3,0,0,500),pose(-500,0,0),pose(100,0,15),pose(200,0,360))));
    }

    @Test public void metadataContainsDerivedResultsAndNeverSensorSamples() {
        SmartCubeSolveReconstruction result=SmartCubeSolveReconstruction.fromRawMoves(SOLVED,moves(0,0,2,200),
                Arrays.asList(pose(-500,0,0),pose(-100,0,-90)));
        assertTrue(result.getPrettySolve().contains("x // Inspection"));
        assertTrue(result.getMoveSequence().contains("(B B')"));
        assertEquals(2,result.getMoveCount());
        assertFalse(result.toJson(200).contains("quaternion"));
        assertFalse(result.toJson(200).contains("samples"));
    }

    @Test public void reconstructedGesturesReplayToTheOriginalStateInTheMeasuredFrame() {
        for(int axis=0;axis<3;axis++) for(int q=1;q<4;q++) for(int move=0;move<18;move++) {
            List<SmartCubeSolveReconstruction.MoveEvent> raw=moves(move,200,0,600);
            SmartCubeReconstructionEngine.Result result=run(raw,pose(-500,0,0),pose(200,axis,-90*q));
            cs.min2phase.CubieCube cube=new cs.min2phase.CubieCube();
            for(SmartCubeSolveReconstruction.MoveEvent event:raw) cube=cube.move(event.move);
            String expected=com.dctimer.util.Utils.orientFacelets(cs.min2phase.Util.toFaceCube(cube),turn(axis,q));
            String actual=SOLVED;
            for(SmartCubeReconstructionEngine.Token token:result.tokens) actual=applyNotation(actual,token);
            assertEquals("axis="+axis+" q="+q+" move="+move+" notation="+text(result),expected,actual);
        }
    }

    // Independent sticker geometry checks WCA notation, not the production orientation tables.
    private static String applyNotation(String state,SmartCubeReconstructionEngine.Token token) {
        char family=SmartCubeReconstructionEngine.FAMILIES.charAt(token.axis);
        String faces="URFDLB";
        int face=faces.indexOf(Character.toUpperCase(family));
        int axis,sign;
        if(face>=0) { axis=new int[]{1,0,2}[face%3]; sign=face<3?1:-1; }
        else if(family=='M') { axis=0; sign=-1; }
        else if(family=='E') { axis=1; sign=-1; }
        else if(family=='S') { axis=2; sign=1; }
        else { axis="xyz".indexOf(family); sign=1; }
        char[] out=state.toCharArray();
        for(int i=0;i<54;i++) {
            int[] p=position(i),n=position(i/9*9+4);
            boolean selected=token.rotation() || (face<0 ? p[axis]==0
                    : Character.isLowerCase(family) ? p[axis]*sign>=0 : p[axis]==sign);
            if(selected) {
                int q=sign>0?token.quarters:4-token.quarters;
                for(int k=0;k<q;k++) { p=rotate(p,axis); n=rotate(n,axis); }
            }
            for(int j=0;j<54;j++) if(Arrays.equals(p,position(j)) && Arrays.equals(n,position(j/9*9+4))) {
                out[j]=state.charAt(i); break;
            }
        }
        return new String(out);
    }

    private static int[] rotate(int[] p,int axis) {
        if(axis==0) return new int[]{p[0],p[2],-p[1]};
        if(axis==1) return new int[]{-p[2],p[1],p[0]};
        return new int[]{p[1],-p[0],p[2]};
    }

    private static int[] position(int index) {
        int face=index/9,r=index%9/3,c=index%3;
        switch(face) {
            case 0: return new int[]{c-1,1,r-1};
            case 1: return new int[]{1,1-r,1-c};
            case 2: return new int[]{c-1,1-r,1};
            case 3: return new int[]{c-1,-1,1-r};
            case 4: return new int[]{-1,1-r,c-1};
            default: return new int[]{1-c,1-r,-1};
        }
    }
}
