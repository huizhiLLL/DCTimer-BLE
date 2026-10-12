package com.dctimer.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cs.min2phase.CubieCube;
import cs.min2phase.Util;

import static com.dctimer.model.SmartCubeOrientation.*;

/** Relative notation reconstructed from encoders and an optional, in-memory core track.
 * Follows cuberoot's core/gesture separation, closed-frame slice planning and state-loop errors.
 */
final class SmartCubeReconstructionEngine {
    static final String FAMILIES = "URFDLBEMSxyzurfdlb";
    static final int CORE_SLACK_MS = 250;
    static final int WIDE_SLACK_MS = 120;

    static final class Token {
        int axis, quarters, from, to, phase, startMs, endMs;
        boolean waste;
        Token(int axis, int quarters, int from, int to, int phase, int startMs, int endMs) {
            this.axis=axis; this.quarters=quarters; this.from=from; this.to=to;
            this.phase=phase; this.startMs=startMs; this.endMs=endMs;
        }
        boolean rotation() { return axis >= 9 && axis <= 11; }
        String text() { return ""+FAMILIES.charAt(axis)+(quarters==2 ? "2" : quarters==3 ? "'" : ""); }
    }

    static final class CoreTurn {
        final int axis, quarters, time;
        int claimed, quarterDirection;
        CoreTurn(int axis, int quarters, int time) { this.axis=axis; this.quarters=quarters; this.time=time; }
    }

    static final class Result {
        final List<Token> tokens = new ArrayList<>();
        String inspection = "";
        int wastedRawMoves, wastedMs;
    }

    static Result reconstruct(String start, List<SmartCubeSolveReconstruction.MoveEvent> moves,
                              int[] phases, int fallbackFrame, List<SmartCubeSolveReconstruction.GyroSample> samples) {
        return reconstruct(start, moves, phases, fallbackFrame, samples, true);
    }

    static Result reconstruct(String start, List<SmartCubeSolveReconstruction.MoveEvent> moves,
                              int[] phases, int fallbackFrame, List<SmartCubeSolveReconstruction.GyroSample> samples,
                              boolean crossFirst) {
        Result original = new Result();
        boolean[] waste = findWaste(start, moves, original);
        List<SmartCubeSolveReconstruction.MoveEvent> grouped = new ArrayList<>();
        List<Integer> first = new ArrayList<>(), last = new ArrayList<>(), groupPhases = new ArrayList<>();
        for (int i = 0; i < moves.size(); i++) {
            int from = i, face = moves.get(i).move / 3, q = moves.get(i).move % 3 + 1;
            if (!waste[i] && i + 1 < moves.size() && !waste[i+1] && phases[i] == phases[i+1]
                    && moves.get(i+1).move / 3 == face && q != 2 && moves.get(i+1).move % 3 + 1 == q
                    && moves.get(i+1).elapsedMs - moves.get(i).elapsedMs <= WIDE_SLACK_MS) {
                q = 2;
                i++;
            }
            grouped.add(new SmartCubeSolveReconstruction.MoveEvent(face*3+q-1, 0, moves.get(i).elapsedMs));
            first.add(from); last.add(i); groupPhases.add(phases[i]);
        }
        int[] ids = new int[grouped.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = groupPhases.get(i);
        Result result = reconstructGrouped(start, grouped, ids, fallbackFrame, samples, crossFirst);
        for (Token token : result.tokens) {
            token.from = first.get(token.from); token.to = last.get(token.to);
            if (!token.rotation()) {
                token.startMs = moves.get(token.from).elapsedMs;
                token.endMs = moves.get(token.to).elapsedMs;
                for (int i = token.from; i <= token.to; i++) token.waste |= waste[i];
            }
        }
        result.wastedRawMoves = original.wastedRawMoves;
        result.wastedMs = original.wastedMs;
        return result;
    }

    private static Result reconstructGrouped(String start, List<SmartCubeSolveReconstruction.MoveEvent> moves,
                              int[] phases, int fallbackFrame, List<SmartCubeSolveReconstruction.GyroSample> samples,
                              boolean crossFirst) {
        Result out = new Result();
        List<CoreTurn> core = new ArrayList<>();
        int frame = fallbackFrame;
        boolean measured = !samples.isEmpty();
        if (measured) {
            int initial = nearest(samples.get(0).quaternion, 45);
            if (initial < 0) { measured=false; }
            else {
                frame = initial;
                StringBuilder inspection = new StringBuilder();
                appendPath(inspection, IDENTITY, initial);
                int previous=initial;
                for (SmartCubeSolveReconstruction.GyroSample s : samples) {
                    int next=nearest(s.quaternion, 35);
                    if (next < 0 || next==previous) continue;
                    int delta=compose(inverse(previous), next);
                    for (int rotation : path(delta)) {
                        int axis=rotation/3, q=rotation%3+1;
                        if (s.elapsedMs < 0) {
                            Token token=rotationToken(previous, axis, q, -1, 0, s.elapsedMs);
                            if (inspection.length()>0) inspection.append(' ');
                            inspection.append(token.text());
                            frame=compose(previous, turn(axis,q));
                        } else core.add(new CoreTurn(axis,q,s.elapsedMs));
                        previous=compose(previous,turn(axis,q));
                    }
                }
                out.inspection=inspection.toString();
            }
        }
        boolean[] waste = findWaste(start,moves,out);
        int[] pairEvidence=new int[moves.size()], wideEvidence=new int[moves.size()];
        Arrays.fill(pairEvidence,-1); Arrays.fill(wideEvidence,-1);
        // Reserve slice evidence before classifying one-face compensation as a wide gesture.
        for (int i=0; i+1<moves.size(); i++) {
            if (phases[i]!=phases[i+1] || !isPair(moves,i)) continue;
            int[] rotation=pairRotation(moves.get(i).move);
            int event=findCore(core,rotation[0],rotation[1],moves.get(i).elapsedMs,
                    moves.get(i+1).elapsedMs,CORE_SLACK_MS);
            if (event>=0 && !hasOtherCoreWithin(core, moves.get(i).elapsedMs, moves.get(i+1).elapsedMs, event)) {
                pairEvidence[i]=event; claim(core.get(event),rotation[1]); i++;
            }
        }
        // A regrip between encoder turns breaks the gesture; keep its timestamp and frame update.
        boolean[] interruptedPairs=new boolean[moves.size()];
        for (int i=0;i+1<moves.size();i++) {
            interruptedPairs[i]=hasOtherCoreWithin(core,moves.get(i).elapsedMs,moves.get(i+1).elapsedMs,pairEvidence[i]);
        }
        for (int i=0; i<moves.size(); i++) {
            int face=moves.get(i).move/3, q=moves.get(i).move%3+1;
            int axis=face%3==0 ? 1 : face%3==1 ? 0 : 2;
            int rotationQ=face<3 ? 4-q : q;
            int event=findCore(core,axis,rotationQ,moves.get(i).elapsedMs,moves.get(i).elapsedMs,WIDE_SLACK_MS);
            if (event>=0 && (i==0 || pairEvidence[i-1]<0) && pairEvidence[i]<0) {
                wideEvidence[i]=event; claim(core.get(event),rotationQ);
            }
        }
        boolean[] pairPlan=new boolean[moves.size()];
        for (int from=0; from<moves.size();) {
            int end=from+1;
            while (end<moves.size() && phases[end]==phases[from]) end++;
            boolean[] algebra=planPairs(moves,from,end,pairEvidence,wideEvidence,interruptedPairs,false,crossFirst && measured && phases[from]==0);
            boolean[] forced=planPairs(moves,from,end,pairEvidence,wideEvidence,interruptedPairs,true,crossFirst && measured && phases[from]==0);
            boolean[] plan=count(forced)>count(algebra) ? forced : algebra;
            for (int i=from;i<end;i++) pairPlan[i]=plan[i-from];
            from=end;
        }
        // Release evidence for pairs that the phase plan did not use.
        for (int i=0;i<moves.size();i++) if (pairEvidence[i]>=0 && !pairPlan[i]) core.get(pairEvidence[i]).claimed -= amount(pairRotation(moves.get(i).move)[1]);
        int cursor=0;
        for (int i=0;i<moves.size();i++) {
            SmartCubeSolveReconstruction.MoveEvent move=moves.get(i);
            while (cursor<core.size() && core.get(cursor).time<=move.elapsedMs) {
                CoreTurn e=core.get(cursor++);
                if (e.claimed < amount(e.quarters)) {
                    int remaining = e.claimed == 0 ? e.quarters : e.quarterDirection;
                    out.tokens.add(rotationToken(frame,e.axis,remaining,i,phases[i],e.time));
                    frame=compose(frame,turn(e.axis,remaining));
                }
            }
            int face=move.move/3, q=move.move%3+1;
            Token token;
            if (pairPlan[i]) {
                int[] rotation=pairRotation(move.move);
                // M follows L, E follows D, S follows F.
                int normal=face%3==0 ? 3 : face%3==1 ? 4 : 2;
                int sliceQ=face%3==2 ? (face<3 ? 4-q : q) : (face<3 ? q : 4-q);
                int mapped=FRAMES[frame][normal];
                int axis=mapped%3==0 ? 6 : mapped%3==1 ? 7 : 8;
                int positive=axis==6 ? 3 : axis==7 ? 4 : 2;
                token=new Token(axis,mapped==positive?sliceQ:4-sliceQ,i,i+1,phases[i],move.elapsedMs,moves.get(i+1).elapsedMs);
                frame=compose(frame,turn(rotation[0],rotation[1]));
                token.waste=waste[i] || waste[i+1]; i++;
            } else if (wideEvidence[i]>=0) {
                CoreTurn e=core.get(wideEvidence[i]);
                int wideFace=(face+3)%6;
                token=new Token(12+FRAMES[frame][wideFace],q,i,i,phases[i],move.elapsedMs,move.elapsedMs);
                token.waste=waste[i]; frame=compose(frame,turn(e.axis,face<3?4-q:q));
            } else {
                token=new Token(FRAMES[frame][face],q,i,i,phases[i],move.elapsedMs,move.elapsedMs);
                token.waste=waste[i];
            }
            out.tokens.add(token);
        }
        // Do not invent post-solve rotations from late packets.
        markOverturns(out.tokens);
        mergeTurns(out.tokens);
        return out;
    }

    private static void appendPath(StringBuilder text,int frame,int target) {
        for (int r:path(compose(inverse(frame),target))) {
            if (text.length()>0) text.append(' ');
            text.append(rotationToken(frame,r/3,r%3+1,-1,0,0).text());
            frame=compose(frame,turn(r/3,r%3+1));
        }
    }

    private static Token rotationToken(int frame,int axis,int q,int index,int phase,int time) {
        int mapped=FRAMES[frame][new int[]{1,0,2}[axis]];
        int viewerAxis=mapped%3==0?1:mapped%3==1?0:2;
        return new Token(9+viewerAxis,mapped<3?q:4-q,index,index,phase,time,time);
    }

    private static int findCore(List<CoreTurn> core,int axis,int q,int start,int end,int slack) {
        int found=-1, distance=Integer.MAX_VALUE;
        for (int i=0;i<core.size();i++) {
            CoreTurn e=core.get(i);
            if (e.axis!=axis || e.time<start-slack || e.time>end+slack
                    || e.claimed + amount(q) > amount(e.quarters)) continue;
            if (e.quarters != q && !(e.quarters == 2 && q != 2
                    && (e.claimed == 0 || e.quarterDirection == q))) continue;
            int d=Math.abs(e.time-(start+end)/2);
            if(d<distance) { found=i; distance=d; }
        }
        return found;
    }

    private static int amount(int q) { return q == 2 ? 2 : 1; }

    private static boolean hasOtherCoreWithin(List<CoreTurn> core, int start, int end, int matched) {
        for (int i=0;i<core.size();i++) {
            CoreTurn event=core.get(i);
            if (event.time>end) break;
            if (i!=matched && event.time>start) return true;
        }
        return false;
    }

    private static void claim(CoreTurn event, int q) {
        event.claimed += amount(q);
        if (q != 2) event.quarterDirection = q;
    }

    private static boolean isPair(List<SmartCubeSolveReconstruction.MoveEvent> moves,int i) {
        int a=moves.get(i).move,b=moves.get(i+1).move;
        return a/3!=b/3 && a/3%3==b/3%3 && (a%3+b%3)==2;
    }

    private static int[] pairRotation(int move) {
        int face=move/3,q=move%3+1;
        return new int[]{face%3==0?1:face%3==1?0:2,face<3?4-q:q};
    }

    /** Maximal non-overlapping pair plan whose implied rotations close at the phase boundary. */
    private static boolean[] planPairs(List<SmartCubeSolveReconstruction.MoveEvent> moves,int from,int end,
                                       int[] evidence,int[] wide,boolean[] interrupted,boolean force,boolean requireEvidence) {
        int n=end-from;
        int[][] score=new int[n+1][24], previous=new int[n+1][24], steps=new int[n+1][24];
        for(int[] row:score) Arrays.fill(row,-100000);
        score[0][IDENTITY]=0;
        for(int p=0;p<n;p++) for(int frame=0;frame<24;frame++) {
            if(score[p][frame]<0) continue;
            int i=from+p;
            boolean pair=p+1<n && !interrupted[i] && isPair(moves,i) && wide[i]<0 && wide[i+1]<0
                    && (!requireEvidence || evidence[i]>=0);
            if (!(force && pair && evidence[i]>=0)) relax(score,previous,steps,p+1,frame,score[p][frame],frame,1);
            if(pair) {
                int[] r=pairRotation(moves.get(i).move);
                int next=compose(frame,turn(r[0],r[1]));
                relax(score,previous,steps,p+2,next,score[p][frame]+1,frame,2);
            }
        }
        int frame=IDENTITY;
        if(score[n][frame]<0) for(int f=0;f<24;f++) if(score[n][f]>score[n][frame]) frame=f;
        boolean[] selected=new boolean[n];
        for(int p=n;p>0;) {
            int step=steps[p][frame];
            if(step==0) break;
            if(step==2) selected[p-2]=true;
            frame=previous[p][frame]; p-=step;
        }
        return selected;
    }

    private static void relax(int[][] score,int[][] previous,int[][] steps,int p,int frame,int value,int prev,int step) {
        if(value>score[p][frame]) { score[p][frame]=value; previous[p][frame]=prev; steps[p][frame]=step; }
    }

    private static int count(boolean[] values) { int n=0; for(boolean v:values) if(v)n++; return n; }

    private static boolean[] findWaste(String start,List<SmartCubeSolveReconstruction.MoveEvent> moves,Result out) {
        boolean[] waste=new boolean[moves.size()];
        CubieCube cube=new CubieCube();
        if(start==null || Util.toCubieCube(start,cube)!=0) return waste;
        Map<String,Integer> seen=new HashMap<>();
        seen.put(start,-1);
        for(int i=0;i<moves.size();i++) {
            cube=cube.move(moves.get(i).move);
            String state=Util.toFaceCube(cube);
            Integer previous=seen.put(state,i);
            if(previous!=null) for(int j=previous+1;j<=i;j++) waste[j]=true;
        }
        for(int i=0;i<waste.length;i++) if(waste[i]) {
            out.wastedRawMoves++;
            out.wastedMs+=i==0?0:Math.max(0,moves.get(i).elapsedMs-moves.get(i-1).elapsedMs);
        }
        return waste;
    }

    private static void markOverturns(List<Token> tokens) {
        for(int from=0;from<tokens.size();) {
            Token first=tokens.get(from);
            int end=from,travel=0,net=0;
            while(end<tokens.size() && tokens.get(end).axis==first.axis && tokens.get(end).phase==first.phase) {
                int q=tokens.get(end++).quarters;
                travel+=q==3?1:q; net+=q;
            }
            net%=4;
            if(!first.rotation() && travel>Math.min(net,4-net)) for(int i=from;i<end;i++) tokens.get(i).waste=true;
            from=end;
        }
    }

    private static void mergeTurns(List<Token> tokens) {
        for(int i=1;i<tokens.size();) {
            Token a=tokens.get(i-1),b=tokens.get(i);
            if(!a.waste && !b.waste && !a.rotation() && a.axis==b.axis && a.phase==b.phase) {
                int q=(a.quarters+b.quarters)%4;
                if(q!=0) { a.quarters=q; a.to=b.to; a.endMs=b.endMs; tokens.remove(i); continue; }
            }
            i++;
        }
    }
}
