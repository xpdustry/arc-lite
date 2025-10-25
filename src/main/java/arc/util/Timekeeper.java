package arc.util;

public class Timekeeper{
    private final long intervalms;
    private long time;

    public Timekeeper(long ms){ 
        intervalms = ms; 
    }

    public Timekeeper(float seconds){ 
        intervalms = (long)(seconds * 1000); 
    }

    public long interval(){ 
        return intervalms; 
    }

    /** @deprecated use {@link #exceeded()} instead. */
    @Deprecated
    public boolean get(){ 
        return exceeded(); 
    }

    public boolean exceeded(){ 
        return elapsed() > intervalms; 
    }

    public long elapsed(){ 
        return Time.timeSinceMillis(time); 
    }

    public long remaining(){ 
        return Math.max(intervalms - elapsed(), 0); 
    }

    public long last(){ 
        return time; 
    }

    public void reset(){
        time = Time.millis(); 
    }

    public void zero(){ 
        time = 0; 
    }
}
