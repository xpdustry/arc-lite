package utils;

import arc.math.Rand;
import arc.util.TaskQueue;
import arc.util.Time;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.*;

import org.junit.Test;

import static org.junit.Assert.*;


public class TaskQueueTest extends TaskQueue{
    { TaskQueue.CHUNK = 8; }

    /** Needed because windows clock resolution is above the millisecond. */
    public static void busyWait(long nanos) {
        long start = Time.nanos();
        while (Time.timeSinceNanos(start) < nanos);
    }

    @Test
    public void fifoAndCount(){
        TaskQueue q = new TaskQueue();
        int n = 10_000;
        List<Integer> order = new ArrayList<>(n);
        for(int i = 0; i < n; i++){
            int v = i;
            q.post(() -> order.add(v));
        }

        assertEquals(n, q.size());
        q.run();
        assertEquals(0, q.size());
        assertEquals(n, order.size());
        for(int i = 0; i < n; i++) assertEquals(i, order.get(i).intValue()); // FIFO (single producer)
    }

    @Test
    public void deferredDuringRun(){
        TaskQueue q = new TaskQueue();
        AtomicInteger ran = new AtomicInteger();
        q.post(() -> {
            ran.incrementAndGet();
            q.post(ran::incrementAndGet);
        });

        q.run(); // only the batch present at entry
        assertEquals(1, ran.get());
        assertEquals(1, q.size()); // the re-posted task waits for the next run
        q.run();
        assertEquals(2, ran.get());
        assertEquals(0, q.size());
    }

    @Test
    public void throwingTask(){
        TaskQueue q = new TaskQueue();
        AtomicInteger ran = new AtomicInteger();
        q.post(() -> { throw new RuntimeException("boom"); });

        q.post(ran::incrementAndGet);
        try{
            q.run(); // exception aborts the batch
            fail("expected RuntimeException");
        }catch(RuntimeException expected){}

        q.run(); // but the queue is not corrupted
        assertEquals(1, ran.get());
        assertEquals(0, q.size());
    }

    @Override
    @Test
    public void clear(){
        TaskQueue q = new TaskQueue();
        AtomicInteger ran = new AtomicInteger();
        for(int i = 0; i < 100; i++) q.post(ran::incrementAndGet);

        q.clear();
        assertEquals(0, q.size());
        q.run();
        assertEquals(0, ran.get());
    }

    @Test(timeout = 60_000)
    public void concurrent() throws Exception{
        final int producers = 8, perProducer = 100_000, total = producers * perProducer;
        TaskQueue q = new TaskQueue();

        AtomicInteger executed = new AtomicInteger();
        AtomicIntegerArray seen = new AtomicIntegerArray(total); // exactly-once
        AtomicIntegerArray lastSeq = new AtomicIntegerArray(producers);
        for(int i = 0; i < producers; i++) lastSeq.set(i, -1);

        AtomicBoolean duplicate = new AtomicBoolean(), order = new AtomicBoolean(), negative = new AtomicBoolean();
        CountDownLatch start = new CountDownLatch(1);
        Thread[] prod = new Thread[producers];
        for(int p = 0; p < producers; p++){
            final int pid = p;
            prod[p] = new Thread(() -> {
                try{
                    start.await();
                }catch(InterruptedException e){
                    return;
                }
                Rand rand = new Rand();
                for(int s = 0; s < perProducer; s++){
                    final int seq = s, idx = pid * perProducer + s;
                    q.post(() -> { // runs on the consumer thread
                        // simulate processing
                        busyWait(rand.nextLong(10_000));

                        if(seq <= lastSeq.get(pid)) order.set(true); // per-producer FIFO
                        lastSeq.set(pid, seq);
                        if(!seen.compareAndSet(idx, 0, 1)) duplicate.set(true); // exactly-once
                        executed.incrementAndGet();
                    });
                }
            }, "producer-" + p);
            prod[p].start();
        }

        AtomicBoolean done = new AtomicBoolean();
        Thread consumer = new Thread(() -> {
            try{
                start.await();
            }catch(InterruptedException e){
                return;
            }
            while(!done.get() || q.size() > 0){
                if(q.size() < 0) negative.set(true);
                q.run();
            }
            q.run();
        }, "consumer");
        consumer.start();

        start.countDown();
        for(Thread t : prod) t.join();
        done.set(true);
        consumer.join();

        assertFalse("task executed more than once", duplicate.get());
        assertFalse("per-producer FIFO violated", order.get());
        assertFalse("size went negative", negative.get());
        assertEquals("lost tasks", total, executed.get());
        assertEquals(0, q.size());
    }
}
