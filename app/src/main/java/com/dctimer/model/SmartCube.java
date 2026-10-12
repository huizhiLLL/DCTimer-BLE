package com.dctimer.model;

import android.util.Log;

import com.dctimer.util.Utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import cs.min2phase.CubieCube;
import cs.min2phase.Util;

public class SmartCube implements Serializable {
    private int type;
    private int version;
    private String deviceName;
    private String cubeState;
    private int batteryValue;
    private List<Integer> rawData;
    private CubieCube cc;
    private int preIdx;
    private int result;
    private int moves;
    private int reconstructedMoves;
    private List<Integer> preMoveList;
    private List<Integer> moveList;
    private String solveStartState;
    private SmartCubeSolveReconstruction reconstruction;
    private StateChangedCallback callback;
    private CompletionChecker completionChecker;
    private String targetState;
    private boolean scrambledNotified;
    private transient SmartCubeGyroRecorder gyroRecorder = new SmartCubeGyroRecorder();
    private transient List<Long> moveArrivals = new ArrayList<>();
    private transient SmartCube pendingReconstruction;
    private transient long reconstructionDeadline;
    public static final int RECONSTRUCTION_TAIL_MS = SmartCubeGyroRecorder.TAIL_MS;

    private synchronized SmartCubeGyroRecorder gyroRecorder() {
        if (gyroRecorder == null) gyroRecorder = new SmartCubeGyroRecorder();
        return gyroRecorder;
    }

    public synchronized void recordGyro(float x, float y, float z, float w) {
        long now=System.nanoTime()/1000000L;
        gyroRecorder().accept(now, x, y, z, w);
        if(pendingReconstruction!=null && now<=reconstructionDeadline) {
            pendingReconstruction.gyroRecorder().accept(now,x,y,z,w);
        }
    }

    /** Freeze this solve while its final posture packet is still in flight. */
    public synchronized SmartCube snapshotForReconstruction() {
        SmartCube snapshot=new SmartCube();
        snapshot.rawData=new ArrayList<>(rawData);
        snapshot.moveArrivals=new ArrayList<>(moveArrivals);
        snapshot.preIdx=preIdx;
        snapshot.solveStartState=solveStartState;
        snapshot.gyroRecorder=gyroRecorder();
        gyroRecorder=snapshot.gyroRecorder.copyBaseline();
        pendingReconstruction=snapshot;
        reconstructionDeadline=System.nanoTime()/1000000L+RECONSTRUCTION_TAIL_MS;
        for(int i=preIdx+1;i<rawData.size();i++) snapshot.result+=rawData.get(i)&0xffff;
        result=snapshot.result;
        clearLastReconstruction();
        return snapshot;
    }

    public synchronized void releaseReconstruction(SmartCube snapshot) {
        if(pendingReconstruction==snapshot) pendingReconstruction=null;
    }

    public boolean calibrateGyroForScramble(float x, float y, float z, float w, int orientation) {
        return gyroRecorder().recalibrate(x, y, z, w, orientation);
    }

    public synchronized void clearGyroSession() {
        pendingReconstruction = null;
        gyroRecorder = new SmartCubeGyroRecorder();
    }


    public SmartCube() {
        rawData = new ArrayList<>();
        cc = new CubieCube();
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getCubeState() {
        return cubeState;
    }

    public int setCubeState(String state) {
        this.cubeState = state;
        int check = Util.toCubieCube(state, cc);
        if (check == 0 && isTargetScrambleState(cubeState)) {
            scrambledNotified = true;
            callback.onScrambled(this);
        }
        if (check == 0 && callback != null && completionChecker != null && isSolveComplete()) {
            callback.onSolved(this);
        }
        return check;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getBatteryValue() {
        return batteryValue;
    }

    public void setBatteryValue(int batteryValue) {
        this.batteryValue = batteryValue;
    }

    public int getResult() {
        return result;
    }

    public void setStateChangedCallback(StateChangedCallback callback) {
        this.callback = callback;
    }

    public void setCompletionChecker(CompletionChecker completionChecker) {
        this.completionChecker = completionChecker;
    }

    public int getMovesCount() {
        return moves;
    }

    public int getReconstructedMovesCount() {
        return reconstructedMoves;
    }

    public SmartCubeSolveReconstruction getReconstruction() {
        return reconstruction;
    }

    public void applyMove(int move, int time, String scramble) {
        // A new encoder gesture is outside the finished solve's sensor tail.
        synchronized(this) { pendingReconstruction=null; }
        if (moveArrivals == null) moveArrivals = new ArrayList<>();
        moveArrivals.add(System.nanoTime() / 1000000L);
        rawData.add(move << 16 | time);
        cc = cc.move(move);
        cubeState = Util.toFaceCube(cc);
        if (scramble != null && !scramble.equals(targetState)) {
            targetState = scramble;
            scrambledNotified = false;
        }
        if (isTargetScrambleState(cubeState)) {
            scrambledNotified = true;
            callback.onScrambled(this);
        }
        if (callback != null && isSolveComplete())
            callback.onSolved(this);
    }

    private boolean isSolveComplete() {
        if (completionChecker != null) {
            return completionChecker.isComplete(this);
        }
        return Utils.isSolvedIgnoringRotation(cubeState);
    }

    private boolean isTargetScrambleState(String state) {
        if (scrambledNotified || callback == null || state == null || targetState == null
                || state.length() == 0 || targetState.length() == 0) {
            return false;
        }
        return state.equals(targetState) || Utils.isSameStateIgnoringRotation(state, targetState);
    }

    public void markScrambled() {
        gyroRecorder().begin(System.nanoTime() / 1000000L);
        preIdx = rawData.size();
        solveStartState = cubeState;
        scrambledNotified = true;
    }

    public void markSolveStarted(String startState) {
        gyroRecorder().ensureStarted(System.nanoTime() / 1000000L);
        if (rawData.isEmpty()) {
            preIdx = 0;
        } else {
            preIdx = rawData.size() - 1;
        }
        solveStartState = startState;
    }

    public void markSolved() {
        gyroRecorder().clearAttempt();
        moveArrivals = new ArrayList<>();
        cubeState = "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB";
        cc = new CubieCube();
        rawData = new ArrayList<>();
        preIdx = 0;
        solveStartState = null;
        targetState = null;
        scrambledNotified = false;
    }

    public void resetSolveTracking() {
        gyroRecorder().clearAttempt();
        moveArrivals = new ArrayList<>();
        rawData = new ArrayList<>();
        preIdx = 0;
        solveStartState = cubeState;
        targetState = null;
        scrambledNotified = false;
    }

    public void clearLastReconstruction() {
        reconstruction = null;
        reconstructedMoves = 0;
    }

    public void calcResult() {
        result = 0;
        moves = rawData.size() - preIdx;
        reconstructedMoves = 0;
        reconstruction = null;
        preMoveList = new ArrayList<>();
        moveList = new ArrayList<>();
        List<SmartCubeSolveReconstruction.MoveEvent> solveMoves = new ArrayList<>();
        int elapsed = 0;
        for (int i = 0; i < preIdx; i++) {
            int move = rawData.get(i) >> 16;
            if (preMoveList.size() == 0) preMoveList.add(move);
            else if (preMoveList.get(preMoveList.size() - 1) == move) {
                if (move % 3 == 1) preMoveList.add(move);
                else {
                    int turn = move / 3;
                    preMoveList.add(turn * 3 + 1);
                }
            } else preMoveList.add(move);
        }
        //Log.w("dct", "start "+preIdx+" size "+rawData.size());
        for (int i = preIdx; i < rawData.size(); i++) {
            int delta = rawData.get(i) & 0xffff;
            if (i != preIdx) {
                result += delta;
                elapsed += delta;
            }
            //Log.w("dct", i+":"+rawData.get(i)+"/"+result);
            int move = rawData.get(i) >> 16;
            solveMoves.add(new SmartCubeSolveReconstruction.MoveEvent(move, delta, elapsed));
            if (moveList.size() == 0) moveList.add(move);
            else if (moveList.get(moveList.size() - 1) == move) {
                if (move % 3 == 1) moveList.add(move);
                else {
                    int turn = move / 3;
                    moveList.set(moveList.size() - 1, turn * 3 + 1);
                }
            } else moveList.add(move);
        }
        List<Long> arrivals = moveArrivals != null && moveArrivals.size() == rawData.size()
                ? new ArrayList<>(moveArrivals.subList(preIdx, moveArrivals.size())) : new ArrayList<Long>();
        reconstruction = SmartCubeSolveReconstruction.fromRawMoves(solveStartState, solveMoves,
                gyroRecorder().finish(arrivals, solveMoves));
        reconstructedMoves = reconstruction.getMoveCount();
    }

    public String getMoveSequence() {
        if (reconstruction != null && reconstruction.getPrettySolve() != null && reconstruction.getPrettySolve().length() > 0) {
            return reconstruction.getPrettySolve(result);
        }
        StringBuilder sb = new StringBuilder();
        String[] suff = {"", "2", "'"};
        for (int i = 0; i < moveList.size(); i++) {
            int move = moveList.get(i);
            sb.append("URFDLB".charAt(move / 3)).append(suff[move % 3]).append(" ");
        }
        return sb.toString();
    }

    public String getSolveMeta() {
        if (reconstruction == null) {
            return null;
        }
        return reconstruction.toJson(result);
    }

    public interface StateChangedCallback {
        void onScrambled(SmartCube cube);
        void onSolved(SmartCube cube);
    }

    public interface CompletionChecker {
        boolean isComplete(SmartCube cube);
    }
}
