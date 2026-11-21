package arc.util;

import arc.struct.Seq;

/** @author xoppa */
public class PerformanceCounters{
    public final Seq<PerformanceCounter> counters = new Seq<>();
    private long lastTick = 0L;

    public PerformanceCounter add(final String name, final int windowSize){
        PerformanceCounter result = new PerformanceCounter(name, windowSize);
        counters.add(result);
        return result;
    }

    public PerformanceCounter add(final String name){
        PerformanceCounter result = new PerformanceCounter(name);
        counters.add(result);
        return result;
    }

    public void tick(){
        final long t = Time.nanos();
        if(lastTick > 0L) tick((t - lastTick) / (float)Time.nanosPerSecond);
        lastTick = t;
    }

    public void tick(final float deltaTime){
        counters.each(c -> c.tick(deltaTime));
    }

    @Override
    public String toString(){
        counters.sort(c -> -c.load.value);
        StringBuilder sb = new StringBuilder();
        Strings.toSentence(sb, counters, (builder, counter) -> counter.toString(builder), "\n", "\n");
        return sb.toString();
    }
}
