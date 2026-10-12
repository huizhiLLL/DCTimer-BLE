package com.dctimer.model;

import java.util.ArrayList;
import java.util.List;

/** Session-only sensor data. Neither this recorder nor its samples enter solve metadata. */
final class SmartCubeGyroRecorder {
    private double[] origin, latest;
    private boolean recording, overflow;
    private final List<Sample> samples = new ArrayList<>();

    private static final class Sample {
        final long time;
        final double[] q;
        Sample(long time,double[] q) { this.time=time; this.q=q; }
    }

    synchronized void accept(long time,float x,float y,float z,float w) {
        double[] q=SmartCubeOrientation.normalize(x,y,z,w);
        if(q==null) return;
        if(origin==null) origin=q;
        latest=SmartCubeOrientation.relative(origin,q);
        if(!recording || overflow) return;
        // Retain at most 100 Hz; a corrupt/never-ending attempt cannot grow without bound.
        if(!samples.isEmpty() && time-samples.get(samples.size()-1).time<10) return;
        if(samples.size()>=60000) { samples.clear(); overflow=true; return; }
        samples.add(new Sample(time,latest));
    }

    synchronized void begin(long time) {
        samples.clear(); overflow=false; recording=true;
        if(latest!=null) samples.add(new Sample(time,latest));
    }

    synchronized void ensureStarted(long time) { if(!recording) begin(time); }

    synchronized void clearAttempt() { samples.clear(); recording=false; overflow=false; }

    synchronized List<SmartCubeSolveReconstruction.GyroSample> finish(List<Long> arrivals,
                    List<SmartCubeSolveReconstruction.MoveEvent> moves) {
        List<SmartCubeSolveReconstruction.GyroSample> out=new ArrayList<>();
        if(!arrivals.isEmpty() && !moves.isEmpty() && !overflow) {
            int cursor=0;
            long first=arrivals.get(0),last=arrivals.get(arrivals.size()-1);
            for(Sample sample:samples) {
                if(sample.time>last) break;
                int elapsed;
                if(sample.time<first) elapsed=(int)Math.max(Integer.MIN_VALUE,sample.time-first);
                else {
                    while(cursor+1<arrivals.size() && arrivals.get(cursor+1)<=sample.time) cursor++;
                    elapsed=moves.get(cursor).elapsedMs;
                    if(cursor+1<arrivals.size()) {
                        long span=arrivals.get(cursor+1)-arrivals.get(cursor);
                        if(span>0) elapsed+=(int)((sample.time-arrivals.get(cursor))
                                *(moves.get(cursor+1).elapsedMs-elapsed)/span);
                    }
                }
                out.add(new SmartCubeSolveReconstruction.GyroSample(elapsed,sample.q));
            }
        }
        clearAttempt();
        return out;
    }
}
