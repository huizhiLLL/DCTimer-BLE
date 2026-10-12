package com.dctimer.model;

import com.dctimer.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.dctimer.APP.smartCubeSolveMethod;

import cs.min2phase.CubieCube;
import cs.min2phase.Util;

public class SmartCubeSolveReconstruction {
    private static final int METHOD_CFOP = 0;
    private static final int METHOD_ROUX = 1;
    private static final String SOLVED_FACELET = "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB";
    private static final String PRETTY_FACES = SmartCubeReconstructionEngine.FAMILIES;
    private static final String SUFFIXES = " 2'";
    private static final String[] CFOP_PHASE_NAMES = {"Cross", "F2L 1", "F2L 2", "F2L 3", "F2L 4", "OLL", "PLL"};
    private static final String[] ROUX_PHASE_NAMES = {"FB", "SB", "CMLL", "L6E"};

    private static final int[][] CROSS_MASK = toEqus("----U--------R--R-----F--F--D-DDD-D-----L--L-----B--B-");
    private static final int[][] F2L1_MASK = toEqus("----U-------RR-RR-----FF-FF-DDDDD-D-----L--L-----B--B-");
    private static final int[][] F2L2_MASK = toEqus("----U--------R--R----FF-FF-DD-DDD-D-----LL-LL----B--B-");
    private static final int[][] F2L3_MASK = toEqus("----U--------RR-RR----F--F--D-DDD-DD----L--L----BB-BB-");
    private static final int[][] F2L4_MASK = toEqus("----U--------R--R-----F--F--D-DDDDD----LL-LL-----BB-BB");
    private static final int[][] F2L_MASK = toEqus("----U-------RRRRRR---FFFFFFDDDDDDDDD---LLLLLL---BBBBBB");
    private static final int[][] OLL_MASK = toEqus("UUUUUUUUU---RRRRRR---FFFFFFDDDDDDDDD---LLLLLL---BBBBBB");
    private static final int[][] SOLVED_MASK = toEqus(SOLVED_FACELET);
    private static final int[][] ROUX_FB_MASK = toEqus("---------------------F--F--D--D--D-----LLLLLL-----B--B");
    private static final int[][] ROUX_SB_MASK = toEqus("------------RRRRRR---F-FF-FD-DD-DD-D---LLLLLL---B-BB-B");
    private static final int[][] ROUX_CMLL_MASK = toEqus("U-U---U-Ur-rRRRRRRf-fF-FF-FD-DD-DD-Dl-lLLLLLLb-bB-BB-B");

    private final List<MoveEvent> rawMoves;
    private final List<PrettyMove> reconstructedMoves;
    private final List<Phase> phases;
    private final int solveMethod;
    private final String prettySolve;
    private final String moveSequence;
    private final int moveCount;
    private final String inspection;
    private final int wastedRawMoves, wastedMs;

    private SmartCubeSolveReconstruction(List<MoveEvent> rawMoves, List<PrettyMove> reconstructedMoves, List<Phase> phases, int solveMethod, SmartCubeReconstructionEngine.Result result) {
        this.rawMoves = rawMoves;
        this.reconstructedMoves = reconstructedMoves;
        this.phases = phases;
        this.solveMethod = solveMethod;
        this.inspection = result.inspection;
        this.wastedRawMoves = result.wastedRawMoves;
        this.wastedMs = result.wastedMs;
        this.prettySolve = (inspection.isEmpty() ? "" : inspection + " // Inspection\n")
                + buildPrettySolve(phases, reconstructedMoves);
        this.moveSequence = buildMoveSequence(reconstructedMoves);
        this.moveCount = countNonRotationMoves(reconstructedMoves);
    }

    public static SmartCubeSolveReconstruction fromRawMoves(String startFacelet, List<MoveEvent> rawMoves) {
        return fromRawMoves(startFacelet, rawMoves, new ArrayList<GyroSample>());
    }

    public static SmartCubeSolveReconstruction fromRawMoves(String startFacelet, List<MoveEvent> rawMoves,
                                                            List<GyroSample> gyro) {
        return fromRawMoves(startFacelet, rawMoves, gyro, smartCubeSolveMethod);
    }

    static SmartCubeSolveReconstruction fromRawMoves(String startFacelet, List<MoveEvent> rawMoves,
                                                     List<GyroSample> gyro, int solveMethod) {
        List<MoveEvent> moves = rawMoves == null ? new ArrayList<MoveEvent>() : new ArrayList<>(rawMoves);
        int method = normalizeSolveMethod(solveMethod);
        String[] names = getPhaseNames(method);
        int[] phaseIds = new int[moves.size()];
        CubieCube cube = new CubieCube();
        boolean valid = startFacelet != null && startFacelet.length() == 54 && Util.toCubieCube(startFacelet, cube) == 0;
        int status = valid ? updatePhaseStatus(names.length, getMethodProgress(startFacelet, method)) : names.length;
        for (int i = 0; i < moves.size(); i++) {
            phaseIds[i] = names.length - status;
            if (valid) {
                cube = cube.move(moves.get(i).move);
                status = updatePhaseStatus(status, getMethodProgress(Util.toFaceCube(cube), method));
            }
        }
        SmartCubeReconstructionEngine.Result result = SmartCubeReconstructionEngine.reconstruct(
                startFacelet, moves, phaseIds, inferCrossOrientation(startFacelet, moves),
                gyro == null ? new ArrayList<GyroSample>() : gyro, method == METHOD_CFOP);
        List<PrettyMove> all = new ArrayList<>();
        List<Phase> phases = new ArrayList<>();
        for (int phase = 0; phase < names.length; phase++) {
            List<PrettyMove> group = new ArrayList<>();
            for (SmartCubeReconstructionEngine.Token token : result.tokens) {
                if (token.phase != phase) continue;
                PrettyMove move = new PrettyMove(token.axis, token.quarters - 1, token.from, token.to, token.startMs, token.endMs);
                move.waste = token.waste;
                group.add(move);
            }
            all.addAll(group);
            phases.add(createPhase(names[phase], group, 0, group.size()-1));
        }
        return new SmartCubeSolveReconstruction(moves, all, includePhaseGaps(phases), method, result);
    }

    // Compare the six cross colors by earliest F2L, then cross; D wins ties.
    // The first frame for that bottom color supplies a deterministic, arbitrary front.
    private static int inferCrossOrientation(String start, List<MoveEvent> moves) {
        CubieCube cube = new CubieCube();
        if (start == null || start.length() != 54 || Util.toCubieCube(start, cube) != 0) return 0;
        int[] cross = new int[24], f2l = new int[24];
        java.util.Arrays.fill(cross, Integer.MAX_VALUE);
        java.util.Arrays.fill(f2l, Integer.MAX_VALUE);
        for (int i = 0; i < moves.size(); i++) {
            cube = cube.move(moves.get(i).move);
            String state = Util.toFaceCube(cube);
            for (int frame = 0; frame < 24; frame++) {
                String oriented = Utils.orientFacelets(state, frame);
                if (cross[frame] == Integer.MAX_VALUE && !isUnsolvedForMask(oriented, CROSS_MASK)) cross[frame] = i;
                if (f2l[frame] == Integer.MAX_VALUE && !isUnsolvedForMask(oriented, F2L_MASK)) f2l[frame] = i;
            }
        }
        int best = SmartCubeOrientation.IDENTITY;
        for (int frame = 0; frame < 24; frame++) {
            if (f2l[frame] < f2l[best] || (f2l[frame] == f2l[best] && cross[frame] < cross[best])) best = frame;
        }
        return best;
    }

    public static final class GyroSample {
        public final int elapsedMs;
        final double[] quaternion;
        public GyroSample(int elapsedMs, double[] quaternion) {
            this.elapsedMs = elapsedMs;
            this.quaternion = quaternion.clone();
        }
    }

    public String getPrettySolve() {
        return prettySolve;
    }

    public String getPrettySolve(int solveTimeMs) {
        return appendSolveStats(prettySolve, solveTimeMs);
    }

    public String getMoveSequence() {
        return moveSequence;
    }

    public int getMoveCount() {
        return moveCount;
    }

    public String toJson(int solveTimeMs) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        appendJsonField(sb, "version", "2");
        sb.append(',');
        appendJsonField(sb, "inspection", inspection);
        sb.append(",\"wastedRawMoves\":").append(wastedRawMoves);
        sb.append(",\"wastedMs\":").append(wastedMs);
        sb.append(',');
        appendJsonField(sb, "method", getMethodId(solveMethod));
        sb.append(',');
        sb.append("\"moveCount\":").append(moveCount);
        sb.append(',');
        sb.append("\"solveTimeMs\":").append(Math.max(0, solveTimeMs));
        sb.append(',');
        appendJsonField(sb, "moves", moveSequence);
        sb.append(',');
        appendJsonField(sb, "prettySolve", getPrettySolve(solveTimeMs));
        sb.append(',');
        sb.append("\"phases\":[");
        for (int i = 0; i < phases.size(); i++) {
            if (i > 0) sb.append(',');
            Phase phase = phases.get(i);
            sb.append('{');
            appendJsonField(sb, "name", phase.name);
            sb.append(',');
            sb.append("\"moveCount\":").append(phase.moveCount);
            sb.append(',');
            sb.append("\"startMs\":").append(phase.startMs);
            sb.append(',');
            sb.append("\"endMs\":").append(phase.endMs);
            sb.append(',');
            appendJsonField(sb, "moves", phase.moves);
            sb.append('}');
        }
        sb.append(']');
        sb.append('}');
        return sb.toString();
    }

    public static class MoveEvent {
        public final int move;
        public final int deltaMs;
        public final int elapsedMs;

        public MoveEvent(int move, int deltaMs, int elapsedMs) {
            this.move = move;
            this.deltaMs = deltaMs;
            this.elapsedMs = elapsedMs;
        }
    }

    private static class PrettyMove {
        final int axis;
        boolean waste;
        int pow;
        int startRawIndex;
        int endRawIndex;
        int startMs;
        int endMs;

        PrettyMove(int axis, int pow, int startRawIndex, int endRawIndex, int startMs, int endMs) {
            this.axis = axis;
            this.pow = pow;
            this.startRawIndex = startRawIndex;
            this.endRawIndex = endRawIndex;
            this.startMs = startMs;
            this.endMs = endMs;
        }

        String notation() {
            return String.valueOf(PRETTY_FACES.charAt(axis)) + SUFFIXES.charAt(pow);
        }

        boolean isRotation() {
            return axis >= 9 && axis <= 11;
        }
    }

    private static class Phase {
        final String name;
        final String moves;
        final int moveCount;
        final int startMs;
        final int endMs;

        Phase(String name, String moves, int moveCount, int startMs, int endMs) {
            this.name = name;
            this.moves = moves;
            this.moveCount = moveCount;
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }

    private static int updatePhaseStatus(int status, int progress) {
        int nextStatus = Math.min(progress, status);
        return nextStatus == 0 ? 1 : nextStatus;
    }

    private static List<Phase> createEmptyPhases(String[] phaseNames) {
        List<Phase> phases = new ArrayList<>();
        for (String phaseName : phaseNames) {
            phases.add(new Phase(phaseName, "", 0, 0, 0));
        }
        return phases;
    }

    private static String[] getPhaseNames(int solveMethod) {
        return solveMethod == METHOD_ROUX ? ROUX_PHASE_NAMES : CFOP_PHASE_NAMES;
    }

    private static int normalizeSolveMethod(int solveMethod) {
        return solveMethod == METHOD_ROUX ? METHOD_ROUX : METHOD_CFOP;
    }

    private static String getMethodId(int solveMethod) {
        return solveMethod == METHOD_ROUX ? "333-smart-roux" : "333-smart-cf4op";
    }

    private static int getMethodProgress(String facelet, int solveMethod) {
        return solveMethod == METHOD_ROUX ? getRouxProgress(facelet) : getCf4opProgress(facelet);
    }

    private static Phase createPhase(String name, List<PrettyMove> moves, int start, int end) {
        if (moves.isEmpty() || start >= moves.size() || end < start) {
            return new Phase(name, "", 0, 0, 0);
        }
        int safeEnd = Math.min(end, moves.size() - 1);
        String moveText = joinMoves(moves, start, safeEnd);
        int count = countNonRotationMoves(moves, start, safeEnd);
        return new Phase(name, moveText, count, moves.get(start).startMs, moves.get(safeEnd).endMs);
    }

    private static List<Phase> includePhaseGaps(List<Phase> phases) {
        List<Phase> result = new ArrayList<>();
        int previousEndMs = 0;
        for (Phase phase : phases) {
            if (phase.moveCount <= 0) {
                result.add(phase);
                continue;
            }
            result.add(new Phase(phase.name, phase.moves, phase.moveCount, previousEndMs, phase.endMs));
            previousEndMs = phase.endMs;
        }
        return result;
    }

    private static int getCf4opProgress(String facelet) {
        List<String> variants = Utils.getAxisOrientationVariants(facelet);
        if (variants.isEmpty()) {
            variants.add(facelet);
        }
        int minProgress = 99;
        for (String variant : variants) {
            int progress = getCf4opProgressForAxis(variant);
            if (progress < minProgress) {
                minProgress = progress;
            }
        }
        return minProgress == 99 ? 7 : minProgress;
    }

    private static int getCf4opProgressForAxis(String facelet) {
        if (isUnsolvedForMask(facelet, CROSS_MASK)) {
            return 7;
        } else if (isUnsolvedForMask(facelet, F2L_MASK)) {
            return 2
                    + (isUnsolvedForMask(facelet, F2L1_MASK) ? 1 : 0)
                    + (isUnsolvedForMask(facelet, F2L2_MASK) ? 1 : 0)
                    + (isUnsolvedForMask(facelet, F2L3_MASK) ? 1 : 0)
                    + (isUnsolvedForMask(facelet, F2L4_MASK) ? 1 : 0);
        } else if (isUnsolvedForMask(facelet, OLL_MASK)) {
            return 2;
        } else if (isUnsolvedForMask(facelet, SOLVED_MASK)) {
            return 1;
        }
        return 0;
    }

    private static int getRouxProgress(String facelet) {
        List<String> variants = Utils.getOrientationVariants(facelet);
        if (variants.isEmpty()) {
            variants.add(facelet);
        }
        int minProgress = 99;
        for (String variant : variants) {
            int progress = getRouxProgressForAxis(variant);
            if (progress < minProgress) {
                minProgress = progress;
            }
        }
        return minProgress == 99 ? 4 : minProgress;
    }

    private static int getRouxProgressForAxis(String facelet) {
        if (isUnsolvedForMask(facelet, ROUX_FB_MASK)) {
            return 4;
        } else if (isUnsolvedForMask(facelet, ROUX_SB_MASK)) {
            return 3;
        } else if (isUnsolvedForMask(facelet, ROUX_CMLL_MASK)) {
            return 2;
        } else if (isUnsolvedForMask(facelet, SOLVED_MASK)) {
            return 1;
        }
        return 0;
    }

    private static boolean isUnsolvedForMask(String facelet, int[][] mask) {
        return !isSolvedForMask(facelet, mask);
    }

    private static boolean isSolvedForMask(String facelet, int[][] mask) {
        if (isEmpty(facelet) || facelet.length() < 54) {
            return false;
        }
        for (int[] equ : mask) {
            if (equ.length == 0) {
                continue;
            }
            char color = facelet.charAt(equ[0]);
            for (int i = 1; i < equ.length; i++) {
                if (facelet.charAt(equ[i]) != color) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int[][] toEqus(String facelet) {
        List<int[]> result = new ArrayList<>();
        for (int i = 0; i < facelet.length(); i++) {
            char color = facelet.charAt(i);
            if (color == '-') {
                continue;
            }
            List<Integer> indices = new ArrayList<>();
            for (int j = i; j < facelet.length(); j++) {
                if (facelet.charAt(j) == color) {
                    indices.add(j);
                }
            }
            if (indices.size() > 1) {
                int[] equ = new int[indices.size()];
                for (int j = 0; j < indices.size(); j++) {
                    equ[j] = indices.get(j);
                }
                result.add(equ);
            }
            facelet = facelet.replace(color, '-');
        }
        return result.toArray(new int[result.size()][]);
    }

    private static String buildMoveSequence(List<PrettyMove> moves) {
        return joinMoves(moves, 0, moves.size() - 1);
    }

    private static String buildPrettySolve(List<Phase> phases, List<PrettyMove> moves) {
        if (phases.isEmpty()) {
            return buildMoveSequence(moves);
        }
        StringBuilder sb = new StringBuilder();
        for (Phase phase : phases) {
            if (isEmpty(phase.moves)) {
                continue;
            }
            if (sb.length() > 0) sb.append('\n');
            sb.append(phase.moves)
                    .append(" // ")
                    .append(phase.name);
        }
        if (sb.length() == 0) {
            return buildMoveSequence(moves);
        }
        return sb.toString();
    }

    private String appendSolveStats(String solveText, int solveTimeMs) {
        StringBuilder sb = new StringBuilder();
        if (!isEmpty(solveText)) {
            sb.append(solveText.trim());
            sb.append('\n');
        }
        sb.append("STM: ")
                .append(moveCount)
                .append(" moves");
        sb.append('\n');
        sb.append("TPS: ")
                .append(String.format(Locale.US, "%.1f", solveTimeMs > 0 ? moveCount * 1000f / solveTimeMs : 0f));
        return sb.toString();
    }

    private static String joinMoves(List<PrettyMove> moves, int start, int end) {
        if (moves == null || moves.isEmpty() || end < start) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(end, moves.size() - 1);
        for (int i = safeStart; i <= safeEnd; i++) {
            if (sb.length() > 0) sb.append(' ');
            boolean waste = moves.get(i).waste;
            if (waste && (i == safeStart || !moves.get(i-1).waste)) sb.append('(');
            sb.append(moves.get(i).notation().trim());
            if (waste && (i == safeEnd || !moves.get(i+1).waste)) sb.append(')');
        }
        return sb.toString();
    }

    private static int countNonRotationMoves(List<PrettyMove> moves) {
        return countNonRotationMoves(moves, 0, moves.size() - 1);
    }

    private static int countNonRotationMoves(List<PrettyMove> moves, int start, int end) {
        if (moves == null || moves.isEmpty() || end < start) {
            return 0;
        }
        int count = 0;
        int safeEnd = Math.min(end, moves.size() - 1);
        for (int i = Math.max(0, start); i <= safeEnd; i++) {
            if (!moves.get(i).isRotation()) {
                count++;
            }
        }
        return count;
    }

    private static void appendJsonField(StringBuilder sb, String key, String value) {
        sb.append('"').append(escapeJson(key)).append("\":\"").append(escapeJson(value)).append('"');
    }

    private static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        sb.append(String.format(Locale.US, "\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                    break;
            }
        }
        return sb.toString();
    }
}
