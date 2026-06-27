package arc.util;

/** Keeps track of a time interval. */
public class Timekeeper{
    private final long intervalMs;
    private long lastTime;

    Timekeeper(long ms){
        intervalMs = ms;
    }

    public static Timekeeper ofMillis(long ms){
        return new Timekeeper(ms);
    }

    public static Timekeeper ofTicks(float ticks){
        return ofSeconds(ticks / Time.toSeconds);
    }

    public static Timekeeper ofSeconds(float seconds){
        return new Timekeeper((long)(seconds * Time.millisPerSecond));
    }

    /** @return true if the interval has passed since the last reset(); resets the timer if true */
    public boolean poll(){
        boolean result = exceeded();
        if(result) reset();
        return result;
    }


    public long interval(){
        return intervalMs;
    }

    /**
     * @return true if the interval has passed since the last reset().
     *
     * @deprecated use {@link #exceeded()} instead.
     */
    @Deprecated
    public boolean get(){
        return exceeded();
    }

    public boolean exceeded(){
        return elapsed() > intervalMs;
    }

    public long elapsed(){
        return Time.millisSinceNanos(lastTime);
    }

    public long remaining(){
        return Math.max(intervalMs - elapsed(), 0);
    }

    public long last(){
        return Time.nanosToMillis(lastTime);
    }

    /** resets the timer; the interval will need to pass until get() returns true again. */
    public void reset(){
        lastTime = Time.nanos();
    }

    public void zero(){
        lastTime = 0;
    }
}