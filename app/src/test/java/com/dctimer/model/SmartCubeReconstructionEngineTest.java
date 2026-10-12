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

    @Test public void inspectionCombinesQuarterTurnsIntoShortestHalfTurn() {
        SmartCubeReconstructionEngine.Result result=run(moves(0,0),
                pose(-500,0,0),pose(-300,0,-90),pose(-100,0,-180));
        assertEquals("x2",result.inspection);
        assertEquals("D",text(result));
    }

    @Test public void inspectionRoundTripDisappearsWithoutRemovingSolveRotations() {
        SmartCubeReconstructionEngine.Result result=run(moves(3,0,0,600),
                pose(-500,0,0),pose(-300,0,-90),pose(-100,0,0),pose(300,0,-90));
        assertEquals("",result.inspection);
        assertEquals("R x B",text(result));
    }

    @Test public void inspectionIncludesInitialGripWhenChoosingShortestPath() {
        SmartCubeReconstructionEngine.Result result=run(moves(0,0),
                pose(-500,0,-90),pose(-300,0,-180),pose(-100,0,-270));
        assertEquals("x'",result.inspection);
        assertEquals("F",text(result));
    }

    @Test public void tiltedFirstObservationSampleDoesNotDiscardLaterWhiteBottomPose() {
        SmartCubeReconstructionEngine.Result result=run(moves(0,0,6,600),
                new SmartCubeSolveReconstruction.GyroSample(-700,new double[]{Math.sqrt(0.125),Math.sqrt(0.125),0,Math.sqrt(0.75)}),
                pose(-400,0,-180),pose(-100,0,-180),pose(300,0,-270));
        assertEquals("x2",result.inspection);
        assertEquals("D x D",text(result));
    }

    @Test public void lateRecognizablePoseDoesNotInventAnInspectionRotation() {
        SmartCubeReconstructionEngine.Result result=run(moves(3,0,0,600),
                new SmartCubeSolveReconstruction.GyroSample(-700,new double[]{Math.sqrt(0.125),Math.sqrt(0.125),0,Math.sqrt(0.75)}),
                pose(300,0,-180));
        assertEquals("",result.inspection);
        assertEquals("R x2 D",text(result));
    }

    @Test public void sliceConsumesCoreRotationAndRenamesFollowingMoves() {
        assertEquals("M F",text(run(moves(3,200,14,230,0,600),pose(-500,0,0),pose(210,0,90))));
    }

    @Test public void stationaryCoreNeverTurnsClosedOppositeLayersIntoSlicesInAnyPhase() {
        for(boolean crossFirst:new boolean[]{true,false}) for(int phase=0;phase<7;phase++) {
            for(int face=0;face<3;face++) {
                // Opposite outer layers followed by their inverses: implied rotations close,
                // but a stationary (or slightly shaking) core is evidence against slice turns.
                List<SmartCubeSolveReconstruction.MoveEvent> raw=moves(
                        face*3,200,(face+3)*3+2,230,face*3+2,600,(face+3)*3,630);
                SmartCubeReconstructionEngine.Result result=SmartCubeReconstructionEngine.reconstruct(
                        SOLVED,raw,new int[]{phase,phase,phase,phase},IDENTITY,
                        Arrays.asList(pose(-500,0,0),pose(220,0,10),pose(620,0,0)),crossFirst);
                assertEquals(4,result.tokens.size());
                for(int i=0;i<4;i++) {
                    assertEquals(raw.get(i).move/3,result.tokens.get(i).axis);
                    assertEquals(raw.get(i).move%3+1,result.tokens.get(i).quarters);
                }
            }
        }
    }

    @Test public void closedOppositeLayersStillAllowInferenceWithoutGyro() {
        SmartCubeReconstructionEngine.Result result=run(moves(0,200,11,230,2,600,9,630));
        assertEquals("E E'",text(result));
    }

    @Test public void wideConsumesOppositeFaceAndCoreExactlyOnce() {
        assertEquals("r B",text(run(moves(12,200,0,600),pose(-500,0,0),pose(200,0,-90))));
    }

    @Test public void halfTurnCoreSupportsTwoSlicePairs() {
        assertEquals("M2 D",text(run(moves(3,200,14,220,3,250,14,280,0,600),
                pose(-500,0,0),pose(240,0,180))));
    }

    @Test public void sliceHalfConsumesTwoCoreQuartersBeforeWideRecognition() {
        assertEquals("M2 D",text(run(moves(4,200,13,240,0,600),
                pose(-500,0,0),pose(190,0,90),pose(250,0,180))));
    }

    @Test public void moyuStyleInterleavedEncodersReconstructM2UM2() {
        SmartCubeReconstructionEngine.Result result=run(
                moves(5,180,12,200,12,220,5,240,9,500,3,680,14,700,14,720,3,760),
                pose(-500,0,0),pose(150,0,-90),pose(250,0,-180),pose(650,0,-90),pose(750,0,0));
        assertEquals("M2 U M2",text(result));
        cs.min2phase.CubieCube cube=new cs.min2phase.CubieCube();
        for(int move:new int[]{5,12,12,5,9,3,14,14,3}) cube=cube.move(move);
        String actual=SOLVED;
        for(SmartCubeReconstructionEngine.Token token:result.tokens) actual=applyNotation(actual,token);
        assertEquals(cs.min2phase.Util.toFaceCube(cube),actual);
    }

    @Test public void interleavedHalfSliceRequiresEvidenceAndOnePhase() {
        List<SmartCubeSolveReconstruction.MoveEvent> raw=moves(5,180,12,200,12,220,5,240);
        SmartCubeReconstructionEngine.Result noGyro=run(raw);
        for(SmartCubeReconstructionEngine.Token token:noGyro.tokens) assertTrue(token.axis!=7);
        SmartCubeReconstructionEngine.Result splitPhase=SmartCubeReconstructionEngine.reconstruct(SOLVED,raw,
                new int[]{1,1,2,2},IDENTITY,Arrays.asList(pose(-500,0,0),pose(150,0,-90),pose(230,0,-180)));
        int[] phases={1,1,2,2};
        for(SmartCubeReconstructionEngine.Token token:splitPhase.tokens) {
            assertFalse(token.axis==7 && token.quarters==2);
            assertEquals(phases[token.from],phases[token.to]);
        }
    }

    @Test public void interleavedHalfSlicesWorkForEveryFaceAndCoreDirection() {
        for(int face=0;face<6;face++) for(int sign:new int[]{-1,1}) {
            int opposite=(face+3)%6,axis=new int[]{1,0,2}[face%3];
            List<SmartCubeSolveReconstruction.MoveEvent> raw=moves(face*3+2,180,opposite*3,200,opposite*3,220,face*3+2,240);
            SmartCubeReconstructionEngine.Result result=run(raw,pose(-500,0,0),pose(150,axis,sign*90),pose(230,axis,sign*180));
            assertEquals(1,result.tokens.size());
            assertEquals(6+face%3,result.tokens.get(0).axis);
            assertEquals(2,result.tokens.get(0).quarters);
            cs.min2phase.CubieCube cube=new cs.min2phase.CubieCube();
            for(SmartCubeSolveReconstruction.MoveEvent move:raw) cube=cube.move(move.move);
            assertEquals(com.dctimer.util.Utils.orientFacelets(cs.min2phase.Util.toFaceCube(cube),turn(axis,2)),
                    applyNotation(SOLVED,result.tokens.get(0)));
        }
    }

    @Test public void interleavedHalfSliceDoesNotCrossIndependentRotation() {
        List<SmartCubeSolveReconstruction.MoveEvent> raw=moves(5,180,12,200,12,220,5,240);
        SmartCubeReconstructionEngine.Result result=run(raw,pose(-500,0,0),pose(190,0,-180),
                new SmartCubeSolveReconstruction.GyroSample(210,new double[]{Math.sqrt(0.5),0,-Math.sqrt(0.5),0}));
        for(SmartCubeReconstructionEngine.Token token:result.tokens) assertTrue(token.axis!=7);
        cs.min2phase.CubieCube cube=new cs.min2phase.CubieCube();
        for(SmartCubeSolveReconstruction.MoveEvent move:raw) cube=cube.move(move.move);
        String actual=SOLVED;
        for(SmartCubeReconstructionEngine.Token token:result.tokens) actual=applyNotation(actual,token);
        assertEquals(com.dctimer.util.Utils.orientFacelets(cs.min2phase.Util.toFaceCube(cube),
                nearest(new double[]{Math.sqrt(0.5),0,-Math.sqrt(0.5),0},1)),actual);
    }

    @Test public void wideHalfTurnMayArriveAsTwoQuarterTurns() {
        assertEquals("r2 D",text(run(moves(12,200,12,240,0,600),
                pose(-500,0,0),pose(220,0,-180))));
    }

    @Test public void wideHalfTurnAcceptsTwoCoreQuartersAroundEncoderHalfTurn() {
        assertEquals("R r2 D",text(run(moves(3,0,13,200,0,600),
                pose(-500,0,0),pose(150,0,90),pose(250,0,180))));
    }

    @Test public void splitCoreWideHalfTurnsPreserveStateOnEveryAxisAndDirection() {
        for(int face=0;face<6;face++) for(int sign:new int[]{-1,1}) {
            int axis=new int[]{1,0,2}[face%3];
            List<SmartCubeSolveReconstruction.MoveEvent> raw=moves(face*3+1,200,0,600);
            SmartCubeReconstructionEngine.Result result=run(raw,
                    pose(-500,0,0),pose(150,axis,sign*90),pose(250,axis,sign*180));
            assertEquals(12+(face+3)%6,result.tokens.get(0).axis);
            String actual=SOLVED;
            for(SmartCubeReconstructionEngine.Token token:result.tokens) actual=applyNotation(actual,token);
            cs.min2phase.CubieCube cube=new cs.min2phase.CubieCube().move(face*3+1).move(0);
            assertEquals(com.dctimer.util.Utils.orientFacelets(cs.min2phase.Util.toFaceCube(cube),turn(axis,2)),actual);
        }
    }

    @Test public void splitCoreDoesNotAbsorbDistantOrInterruptedRegrips() {
        SmartCubeReconstructionEngine.Result distant=run(moves(6,0,13,300,0,1000),
                pose(-500,0,0),pose(100,0,90),pose(600,0,180));
        assertTrue(text(distant).contains("x' L2 x'"));
        SmartCubeReconstructionEngine.Result interrupted=run(moves(6,0,13,200,0,230,6,600),
                pose(-500,0,0),pose(150,0,90),pose(250,0,180));
        for(SmartCubeReconstructionEngine.Token token:interrupted.tokens) assertTrue(token.axis<12);
    }

    @Test public void rotationFarFromEncoderIsNotAWide() {
        assertEquals("L x B",text(run(moves(12,0,0,1000),pose(-500,0,0),pose(500,0,-90))));
    }

    @Test public void wideMatchingAllowsGyroToLeadOrLagBy250ms() {
        for(int offset:new int[]{-250,-200,200,250}) {
            assertEquals("r B",text(run(moves(12,400,0,1000),
                    pose(-500,0,0),pose(400+offset,0,-90))));
        }
        for(int offset:new int[]{-251,251}) {
            SmartCubeReconstructionEngine.Result result=run(moves(12,400,0,1000),
                    pose(-500,0,0),pose(400+offset,0,-90));
            for(SmartCubeReconstructionEngine.Token token:result.tokens) assertTrue(token.axis<12);
        }
    }

    @Test public void splitCoreHalfUsesWiderWindowWithoutLosingPriority() {
        assertEquals("R r2 D",text(run(moves(3,0,13,400,0,1100),
                pose(-500,0,0),pose(200,0,90),pose(600,0,180))));
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

    @Test public void inverseTurnsAcrossPhasesAreNotWaste() {
        SmartCubeReconstructionEngine.Result result=SmartCubeReconstructionEngine.reconstruct(SOLVED,
                moves(3,0,5,200),new int[]{1,2},IDENTITY,new ArrayList<SmartCubeSolveReconstruction.GyroSample>());
        assertEquals("R R'",text(result));
        for(SmartCubeReconstructionEngine.Token token:result.tokens) assertFalse(token.waste);
        assertEquals(0,result.wastedRawMoves);
        assertEquals(0,result.wastedMs);
    }

    @Test public void crossPhaseLoopDoesNotHideLoopWithinNextPhase() {
        SmartCubeReconstructionEngine.Result result=SmartCubeReconstructionEngine.reconstruct(SOLVED,
                moves(3,0,0,100,2,200,5,300),new int[]{1,2,2,2},IDENTITY,
                new ArrayList<SmartCubeSolveReconstruction.GyroSample>());
        assertEquals("R U U' R'",text(result));
        assertFalse(result.tokens.get(0).waste);
        assertTrue(result.tokens.get(1).waste);
        assertTrue(result.tokens.get(2).waste);
        assertFalse(result.tokens.get(3).waste);
        assertEquals(2,result.wastedRawMoves);
        assertEquals(200,result.wastedMs);
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
