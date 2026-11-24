package arc.struct;

import arc.func.Longc;

import java.util.NoSuchElementException;

/** Queue for longs. */
public class LongQueue extends BaseQueue<LongQueue.LongQueueIterator, LongQueue> implements Iterable<Long>{
    /** Contains the values in the queue. Head and tail indices go in a circle around this array, wrapping at the end. */
    public long[] values;

    /** Creates a new Queue which can hold 16 values without needing to resize backing array. */
    public LongQueue(){
        this(16);
    }

    /** Creates a new Queue which can hold the specified number of values without needing to resize backing array. */
    public LongQueue(int initialSize){
        this.values = new long[initialSize];
    }

    public LongQueue(long[] array){
        this(Math.max(array.length, 16));
        addItems(array, 0, array.length);
    }

    public void each(Longc consumer){
        for(int i = 0; i < size; i++){
            consumer.get(values[index(i)]);
        }
    }

    public long sum(){
        long sum = 0;
        for(int i = 0; i < size; i++){
            sum += values[index(i)];
        }
        return sum;
    }

    /**
     * Append given object to the tail. (enqueue to tail) Unless backing array needs resizing, operates in O(1) time.
     * @param object can be null
     */
    public void addLast(long object){
        int at = addLastIndex();
        values[at] = object;
    }

    /**
     * Prepend given object to the head. (enqueue to head) Unless backing array needs resizing, operates in O(1) time.
     * @param object can be null
     * @see #addLast(long)
     */
    public void addFirst(long object){
        int at = addFirstIndex();
        values[at] = object;
    }

    /**
     * Reduces the size of the backing array to the size of the actual items.
     * This is useful to release memory when many items have been removed,
     * or if it is known that more items will not be added.
     * @return {@link #values}
     */
    public long[] shrink(){
        if(values.length != size) resize(size);
        return values;
    }

    @Override
    protected Object items(){
        return values;
    }

    @Override
    public int capacity(){
        return values.length;
    }

    /** Resize backing array. newSize must be bigger than current size. */
    @Override
    protected void resize(int newSize){
        long[] newArray = new long[newSize];
        copyTo(newArray, newSize);
        values = newArray;
    }

    @Override
    protected LongQueue create(int capacity){
        return new LongQueue(capacity);
    }

    @Override
    protected boolean sameType(Object other){
        return other instanceof LongQueue;
    }

    @Override
    public LongQueueIterator newIterator(){
        return new LongQueueIterator(this);
    }

    @Override
    protected void freeItems(int from, int to){
    }

    @Override
    protected boolean equalItem(LongQueue other, int otherIndex, int index){
        return values[index(index)] == other.values[other.index(otherIndex)];
    }

    @Override
    protected void appendItem(StringBuilder buffer, int at){
        buffer.append(values[at]);
    }

    @Override
    protected int hashItem(int at){
        long value = values[at];
        return (int)(value ^ value >>> 32);
    }

    /**
     * Remove the first item from the queue. (dequeue from head) Always O(1).
     * @return removed object
     * @throws NoSuchElementException when queue is empty
     */
    public long removeFirst(){
        int at = removeFirstIndex();
        long result = values[at];
        values[at] = 0;
        return result;
    }

    /**
     * Remove the last item from the queue. (dequeue from tail) Always O(1).
     * @return removed object
     * @throws NoSuchElementException when queue is empty
     * @see #removeFirst()
     */
    public long removeLast(){
        int at = removeLastIndex();
        long result = values[at];
        values[at] = 0;
        return result;
    }

    /**
     * Returns the index of first occurrence of value in the queue, or -1 if no such value exists.
     * @param identity If true, == comparison will be used. If false, .equals() comparison will be used.
     * @return An index of first occurrence of value in queue or -1 if no such value exists
     */
    public int indexOf(long value, boolean identity){
        for(int i = 0; i < size; i++){
            if(values[index(i)] == value) return i;
        }
        return -1;
    }

    /**
     * Removes the first instance of the specified value in the queue.
     * @param identity If true, == comparison will be used. If false, .equals() comparison will be used.
     * @return true if value was found and removed, false otherwise
     */
    public boolean removeValue(long value, boolean identity){
        int index = indexOf(value, identity);
        if(index == -1) return false;
        removeIndex(index);
        return true;
    }

    /** Removes and returns the item at the specified index. */
    public long removeIndex(int index){
        checkIndex(index);
        int at = index(index);
        long value = values[at];
        removeAt(at);
        return value;
    }

    /**
     * Returns the first (head) item in the queue (without removing it).
     * @throws NoSuchElementException when queue is empty
     * @see #addFirst(long)
     * @see #removeFirst()
     */
    public long first(){
        checkNotEmpty();
        return values[head];
    }

    /**
     * Returns the last (tail) item in the queue (without removing it).
     * @throws NoSuchElementException when queue is empty
     * @see #addLast(long)
     * @see #removeLast()
     */
    public long last(){
        checkNotEmpty();
        return values[lastIndex()];
    }

    /**
     * Retrieves the value in queue without removing it. Indexing is from the front to back, zero based.
     * Therefore get(0) is the same as {@link #first()}.
     * @throws IndexOutOfBoundsException when the index is negative or >= size
     */
    public long get(int index){
        checkIndex(index);
        return values[index(index)];
    }

    public void set(int index, long value){
        checkIndex(index);
        beforeModify();
        values[index(index)] = value;
    }

    public long[] toArray(){
        long[] out = new long[size];
        copyItems(out, 0);
        return out;
    }


    public static class LongQueueIterator extends QueueIterator<Long>{
        protected final LongQueue queue;

        public LongQueueIterator(LongQueue queue){
            super(queue);
            this.queue = queue;
        }

        @Override
        public Long next(){
            return nextLong();
        }

        public long nextLong(){
            return queue.get(advance());
        }
    }
}
