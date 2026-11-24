package arc.struct;

import arc.func.*;

import java.util.*;

/** Queue for ints. */
public class IntQueue extends BaseQueue<IntQueue.IntQueueIterator, IntQueue> implements Iterable<Integer>{
    /** Contains the values in the queue. Head and tail indices go in a circle around this array, wrapping at the end. */
    public int[] values;

    /** Creates a new Queue which can hold 16 values without needing to resize backing array. */
    public IntQueue(){
        this(16);
    }

    /** Creates a new Queue which can hold the specified number of values without needing to resize backing array. */
    public IntQueue(int initialSize){
        this.values = new int[initialSize];
    }

    public void each(Intc consumer){
        for(int i = 0; i < size; i++){
            consumer.get(values[index(i)]);
        }
    }

    public int sum(){
        int sum = 0;
        for(int i = 0; i < size; i++){
            sum += values[index(i)];
        }
        return sum;
    }

    /**
     * Append given object to the tail. (enqueue to tail) Unless backing array needs resizing, operates in O(1) time.
     * @param object can be null
     */
    public void addLast(int object){
        int at = addLastIndex();
        values[at] = object;
    }

    /**
     * Prepend given object to the head. (enqueue to head) Unless backing array needs resizing, operates in O(1) time.
     * @param object can be null
     * @see #addLast(int)
     */
    public void addFirst(int object){
        int at = addFirstIndex();
        values[at] = object;
    }

    /**
     * Reduces the size of the backing array to the size of the actual items.
     * This is useful to release memory when many items have been removed,
     * or if it is known that more items will not be added.
     * @return {@link #values}
     */
    public int[] shrink(){
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
        int[] newArray = new int[newSize];
        copyTo(newArray, newSize);
        values = newArray;
    }

    @Override
    protected IntQueue create(int capacity){
        return new IntQueue(capacity);
    }

    @Override
    protected boolean sameType(Object other){
        return other instanceof IntQueue;
    }

    @Override
    public IntQueueIterator newIterator(){
        return new IntQueueIterator(this);
    }

    @Override
    protected void freeItems(int from, int to){
    }

    @Override
    protected boolean equalItem(IntQueue other, int otherIndex, int index){
        return values[index(index)] == other.values[other.index(otherIndex)];
    }

    @Override
    protected void appendItem(StringBuilder buffer, int at){
        buffer.append(values[at]);
    }

    @Override
    protected int hashItem(int at){
        return values[at];
    }

    /**
     * Remove the first item from the queue. (dequeue from head) Always O(1).
     * @return removed object
     * @throws NoSuchElementException when queue is empty
     */
    public int removeFirst(){
        int at = removeFirstIndex();
        int result = values[at];
        values[at] = 0;
        return result;
    }

    /**
     * Remove the last item from the queue. (dequeue from tail) Always O(1).
     * @return removed object
     * @throws NoSuchElementException when queue is empty
     * @see #removeFirst()
     */
    public int removeLast(){
        int at = removeLastIndex();
        int result = values[at];
        values[at] = 0;
        return result;
    }

    /**
     * Returns the index of first occurrence of value in the queue, or -1 if no such value exists.
     * @return An index of first occurrence of value in queue or -1 if no such value exists
     */
    public int indexOf(int value){
        for(int i = 0; i < size; i++){
            if(values[index(i)] == value) return i;
        }
        return -1;
    }

    /**
     * Removes the first instance of the specified value in the queue.
     * @return true if value was found and removed, false otherwise
     */
    public boolean removeValue(int value){
        int index = indexOf(value);
        if(index == -1) return false;
        removeIndex(index);
        return true;
    }

    /** Removes and returns the item at the specified index. */
    public int removeIndex(int index){
        checkIndex(index);
        int at = index(index);
        int value = values[at];
        removeAt(at);
        return value;
    }

    /**
     * Returns the first (head) item in the queue (without removing it).
     * @throws NoSuchElementException when queue is empty
     * @see #addFirst(int)
     * @see #removeFirst()
     */
    public int first(){
        checkNotEmpty();
        return values[head];
    }

    /**
     * Returns the last (tail) item in the queue (without removing it).
     * @throws NoSuchElementException when queue is empty
     * @see #addLast(int)
     * @see #removeLast()
     */
    public int last(){
        checkNotEmpty();
        return values[lastIndex()];
    }

    /**
     * Retrieves the value in queue without removing it. Indexing is from the front to back, zero based.
     * Therefore get(0) is the same as {@link #first()}.
     * @throws IndexOutOfBoundsException when the index is negative or >= size
     */
    public int get(int index){
        checkIndex(index);
        return values[index(index)];
    }

    public void set(int index, int value){
        checkIndex(index);
        beforeModify();
        values[index(index)] = value;
    }

    public int[] toArray(){
        int[] out = new int[size];
        copyItems(out, 0);
        return out;
    }


    public static class IntQueueIterator extends QueueIterator<Integer>{
        protected final IntQueue queue;

        public IntQueueIterator(IntQueue queue){
            super(queue);
            this.queue = queue;
        }

        @Override
        public Integer next(){
            return nextInt();
        }

        public int nextInt(){
            return queue.get(advance());
        }
    }
}
