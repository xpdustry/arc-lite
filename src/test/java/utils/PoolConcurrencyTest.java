// By Claude Opus 5.5
package utils;

import arc.util.pooling.*;
import org.junit.*;

import java.nio.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.Assert.*;

public class PoolConcurrencyTest{
    static class Item{
        final AtomicBoolean used = new AtomicBoolean();
    }

    /**
     * Small capacity to stress the wrap-around of the ring.
     * An object must never be given to two threads at the same time, and the pool must never spin forever.
     */
    @Test(timeout = 60000)
    public void noDuplicateNoLivelock() throws Exception{
        Pool<Item> pool = new Pool<Item>(8){
            @Override
            protected Item newObject(){
                return new Item();
            }
        };
        int threads = 4, iterations = 200_000;
        AtomicInteger duplicates = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        Future<?>[] futures = new Future[threads];
        for(int t = 0; t < threads; t++){
            futures[t] = executor.submit(() -> {
                start.await();
                for(int i = 0; i < iterations; i++){
                    Item item = pool.obtain();
                    if(!item.used.compareAndSet(false, true)) duplicates.incrementAndGet();
                    Thread.yield();
                    item.used.set(false);
                    pool.free(item);
                }
                return null;
            });
        }
        start.countDown();
        for(Future<?> f : futures) f.get();
        executor.shutdown();
        assertEquals("same object obtained by two threads", 0, duplicates.get());
        assertTrue(pool.getFree() <= pool.capacity);
    }

    @Test
    public void capacityIsPowerOfTwo(){
        assertEquals(8, new Pool.NullablePool<>(5).capacity);
        assertEquals(1, new Pool.NullablePool<>(0).capacity);
        Pool<Object> pool = new Pool.NullablePool<>(2);
        assertTrue(pool.offer(new Object()));
        assertTrue(pool.offer(new Object()));
        assertFalse("full", pool.offer(new Object()));
        assertNotNull(pool.poll());
        assertNotNull(pool.poll());
        assertNull(pool.poll());
    }

    @Test
    public void pools(){
        Pool<StringBuilder> a = Pools.get(StringBuilder.class, StringBuilder::new);
        assertSame(a, Pools.get(StringBuilder.class, StringBuilder::new));
    }

    /** Buffers must keep the default java order, as the network protocol depends on it. */
    @Test
    public void byteBufferOrder(){
        ByteBufferPool pool = new ByteBufferPool(1024, 2);
        ByteBuffer heap = pool.obtain(10, false);
        assertEquals(ByteOrder.BIG_ENDIAN, heap.order());
        heap.order(ByteOrder.LITTLE_ENDIAN);
        assertTrue(pool.release(heap));
        assertEquals(ByteOrder.BIG_ENDIAN, pool.obtain(10, false).order());
        assertEquals(ByteOrder.BIG_ENDIAN, pool.obtain(10, true).order());
    }

    @Test
    public void byteBufferBucketCapacity(){
        ByteBufferPool pool = new ByteBufferPool(1024, 2);
        assertTrue(pool.release(ByteBuffer.allocate(1024)));
        assertTrue(pool.release(ByteBuffer.allocate(1024)));
        assertFalse("bucketCap must be used", pool.release(ByteBuffer.allocate(1024)));
    }
}
