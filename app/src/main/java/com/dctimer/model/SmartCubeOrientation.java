package com.dctimer.model;

import com.dctimer.util.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Discrete physical-to-viewer frames. Quaternion input uses the protocol's renderer basis. */
final class SmartCubeOrientation {
    static final int[][] FRAMES = new int[24][6];
    private static final int[][] VECTORS = {{0,1,0},{1,0,0},{0,0,1},{0,-1,0},{-1,0,0},{0,0,-1}};
    // WCA x/y/z are clockwise looking at R/U/F (negative right-handed angles).
    private static final int[][] ROTATIONS = {{5,1,0,2,4,3},{0,2,4,3,5,1},{1,3,2,4,0,5}};
    static final int IDENTITY;
    static {
        for (int i = 0; i < 24; i++) {
            for (int f = 0; f < 6; f++) FRAMES[i][f] = Utils.orientSmartCubeMove(f * 3, i) / 3;
        }
        IDENTITY = find(new int[]{0,1,2,3,4,5});
    }

    static int find(int[] map) {
        for (int i = 0; i < 24; i++) if (Arrays.equals(FRAMES[i], map)) return i;
        throw new IllegalArgumentException("Invalid cube frame");
    }

    static int compose(int a, int b) {
        int[] map = new int[6];
        for (int f = 0; f < 6; f++) map[f] = FRAMES[a][FRAMES[b][f]];
        return find(map);
    }

    static int inverse(int frame) {
        int[] map = new int[6];
        for (int f = 0; f < 6; f++) map[FRAMES[frame][f]] = f;
        return find(map);
    }

    static int turn(int axis, int quarters) {
        int frame = IDENTITY;
        int rotation = find(ROTATIONS[axis]);
        for (int i = 0; i < quarters; i++) frame = compose(frame, rotation);
        return frame;
    }

    /** Shortest rotation path, including a two-axis jump when packets skip an intermediate pose. */
    static List<Integer> path(int frame) {
        List<Integer> out = new ArrayList<>();
        if (frame == IDENTITY) return out;
        for (int a = 0; a < 9; a++) {
            if (turn(a / 3, a % 3 + 1) == frame) { out.add(a); return out; }
        }
        for (int a = 0; a < 9; a++) for (int b = 0; b < 9; b++) {
            if (compose(turn(a / 3, a % 3 + 1), turn(b / 3, b % 3 + 1)) == frame) {
                out.add(a); out.add(b); return out;
            }
        }
        throw new IllegalArgumentException("Unreachable cube frame");
    }

    static double[] normalize(double x, double y, double z, double w) {
        double norm = Math.sqrt(x*x+y*y+z*z+w*w);
        if (Double.isNaN(norm) || Double.isInfinite(norm) || norm < 1e-8) return null;
        return new double[]{x/norm,y/norm,z/norm,w/norm};
    }

    static double[] relative(double[] base, double[] q) {
        double x=-base[0], y=-base[1], z=-base[2], w=base[3];
        return new double[]{w*q[0]+x*q[3]+y*q[2]-z*q[1],
                w*q[1]-x*q[2]+y*q[3]+z*q[0], w*q[2]+x*q[1]-y*q[0]+z*q[3],
                w*q[3]-x*q[0]-y*q[1]-z*q[2]};
    }

    static int nearest(double[] q, double maxDegrees) {
        double x=q[0], y=q[1], z=q[2], w=q[3];
        double[][] m = {{1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)},
                {2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)},
                {2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)}};
        int best = -1;
        double bestTrace = -4;
        for (int i = 0; i < 24; i++) {
            double trace=0;
            for (int f = 0; f < 3; f++) for (int a = 0; a < 3; a++) for (int b = 0; b < 3; b++)
                trace += VECTORS[FRAMES[i][f]][a] * m[a][b] * VECTORS[f][b];
            if (trace > bestTrace) { bestTrace=trace; best=i; }
        }
        return bestTrace >= 1+2*Math.cos(Math.toRadians(maxDegrees)) ? best : -1;
    }
}
