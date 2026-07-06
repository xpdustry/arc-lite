package arc.util;

import arc.*;
import arc.struct.*;
import arc.func.*;
import arc.util.Timer.*;
import arc.util.pooling.Pool.*;
import arc.util.pooling.*;

public class Time{
    /** Conversion factors for ticks to other unit values. */
    public static final float toSeconds = 60f, toMinutes = 60f * 60f, toHours = 60f * 60f * 60f;

    /** Global delta value. Do not change. */
    public static float delta = 1f;
    /** Global time values. Do not change. */
    public static float time, globalTime;

    public static final long millisPerSecond = 1000, nanosPerMilli = 1_000_000, nanosPerSecond = 1_000_000_000;

    private static double timeRaw, globalTimeRaw;

    private static Seq<DelayRun> runs = new Seq<>();
    private static Seq<DelayRun> removal = new Seq<>();
    private static LongSeq marks = new LongSeq();
    private static Floatp deltaimpl = () -> Math.min(Core.app.getDeltaTime() * toSeconds, 3f);

    public static Seq<DelayRun> getRuns(){
        return runs;
    }

    public static void setRuns(Seq<DelayRun> runs){
        Time.runs = runs;
    }

    /** Runs a task with a delay of several ticks. If Time.clear() is called, this task will be cancelled. */
    public static void run(float delay, Runnable r){
        DelayRun run = Pools.obtain(DelayRun.class, DelayRun::new);
        run.finish = r;
        run.delay = delay;
        runs.add(run);
    }

    /** Runs a task with a delay of several ticks. Unless the application is closed, this task will always complete. */
    public static Task runTask(float delay, Runnable r){
        return Timer.schedule(r, delay / toSeconds);
    }

    public static void mark(){
        marks.add(nanos());
    }

    /** A value of -1 means mark() wasn't called beforehand. */
    public static float elapsed(){
        if(marks.size == 0){
            return -1;
        }else{
            return timeSinceNanos(marks.pop()) / (float)nanosPerMilli;
        }
    }

    public static void updateGlobal(){
        globalTimeRaw += Core.app.getDeltaTime() * toSeconds;
        delta = deltaimpl.get();

        if(Double.isInfinite(timeRaw) || Double.isNaN(timeRaw)){
            timeRaw = 0;
        }

        time = (float)timeRaw;
        globalTime = (float)globalTimeRaw;
    }

    public static void update(){
        timeRaw += delta;
        removal.clear();

        if(Double.isInfinite(timeRaw) || Double.isNaN(timeRaw)){
            timeRaw = 0;
        }

        time = (float)timeRaw;
        globalTime = (float)globalTimeRaw;

        for(DelayRun run : runs){
            run.delay -= delta;

            if(run.delay <= 0){
                run.finish.run();
                removal.add(run);
                Pools.free(run);
            }
        }

        runs.removeAll(removal);
    }

    public static double getInternalTime(){
        return timeRaw;
    }

    public static void setInternalTime(double timeInternal){
        timeRaw = timeInternal;
        time = (float)timeInternal;
    }

    public static void clear(){
        runs.clear();
    }

    public static void setDeltaProvider(Floatp impl){
        deltaimpl = impl;
        delta = impl.get();
    }

    /** @return The current value of the system timer, in nanoseconds. */
    public static long nanos(){
        return System.nanoTime();
    }

    /** @return The current value of the system timer, in milliseconds. */
    public static long nanosMillis(){
        return nanosToMillis(System.nanoTime());
    }

    /** @return the difference, measured in milliseconds, between the current time and midnight, January 1, 1970 UTC. */
    public static long millis(){
        return System.currentTimeMillis();
    }

    /**
     * Convert nanoseconds time to milliseconds
     * @param nanos must be nanoseconds
     * @return time value in milliseconds
     */
    public static long nanosToMillis(long nanos){
        return nanos / nanosPerMilli;
    }

    /**
     * Convert milliseconds time to nanoseconds
     * @param millis must be milliseconds
     * @return time value in nanoseconds
     */
    public static long millisToNanos(long millis){
        return millis * nanosPerMilli;
    }

    /**
     * Get the time in nanos passed since a previous time
     * @param prevTime must be nanoseconds
     * @return time passed since prevTime in nanoseconds
     * @deprecated use {@link #sinceNanos(long)} instead.
     */
    @Deprecated
    public static long timeSinceNanos(long prevTime){
        return sinceNanos(prevTime);
    }

    /**
     * Get the time in nanos passed since a previous time
     * @param prevTime must be nanoseconds
     * @return time passed since prevTime in nanoseconds
     */
    public static long sinceNanos(long prevTime){
        return nanos() - prevTime;
    }

    /**
     * Get the time in nanos passed since a previous time
     * @param prevTime must be nanoseconds
     * @return time passed since prevTime in milliseconds
     */
    public static long millisSinceNanos(long prevTime){
        return (nanos() - prevTime) / nanosPerMilli;
    }

    /**
     * Get the time in millis passed since a previous time
     * @param prevTime must be milliseconds
     * @return time passed since prevTime in milliseconds
     * @deprecated use {@link #sinceMillis(long)} instead.
     */
    @Deprecated
    public static long timeSinceMillis(long prevTime){
        return sinceMillis(prevTime);
    }

    /**
     * Get the time in millis passed since a previous time
     * @param prevTime must be milliseconds
     * @return time passed since prevTime in milliseconds
     */
    public static long sinceMillis(long prevTime){
        return millis() - prevTime;
    }

    public static class DelayRun implements Poolable{
        float delay;
        Runnable finish;

        @Override
        public void reset(){
            delay = 0;
            finish = null;
        }
    }
}
