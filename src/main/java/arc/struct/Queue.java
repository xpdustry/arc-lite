package arc.struct;

import arc.func.*;
import arc.util.*;

import java.util.*;

/**
 * A resizable, ordered array of objects with efficient add and remove at the beginning and end.
 * Values in the backing array may wrap back to the beginning, making add and remove at the beginning
 * and end O(1) (unless the backing array needs to resize when adding).
 * Deque functionality is provided via {@link #removeLast()} and {@link #addFirst(Object)}.
 */
public class Queue<T> extends BaseQueue<Queue.QueueIterator<T>, Queue<T>> implements Iterable<T>, Eachable<T>{
    /** Contains the values in the queue. Head and tail indices go in a circle around this array, wrapping at the end. */
    public T[] values;

    /** Creates a new Queue which can hold 16 values without needing to resize backing array. */
    public Queue(){
        this(16);
    }

    /** Creates a new Queue which can hold the specified number of values without needing to resize backing array. */
    public Queue(int initialSize){
        this.values = Reflect.newArray(initialSize);
    }

    /**
     * Creates a new Queue which can hold the specified number of values without needing to resize backing array.
     * This creates backing array of the specified type via reflection,
     * which is necessary only when accessing the backing array directly.
     */
    @SuppressWarnings("unchecked")
    public Queue(int initialSize, Class<?> type){
        this.values = Reflect.newArray((Class<T>)type, initialSize);
    }

    public T[] toArray(Class<T> type){
        T[] out = Reflect.newArray(type, size);
        copyItems(out, 0);
        return out;
    }

    /**
     * Append given object to the tail. (enqueue to tail) Unless backing array needs resizing, operates in O(1) time.
     * @param object can be null
     */
    public void addLast(T object){
        int at = addLastIndex();
        values[at] = object;
    }

    /** Adds an object to the tail. */
    public void add(T object){
        addLast(object);
    }

    /**
     * Prepend given object to the head. (enqueue to head) Unless backing array needs resizing, operates in O(1) time.
     * @param object can be null
     * @see #addLast(Object)
     */
    public void addFirst(T object){
        int at = addFirstIndex();
        values[at] = object;
    }

    /**
     * Reduces the size of the backing array to the size of the actual items.
     * This is useful to release memory when many items have been removed,
     * or if it is known that more items will not be added.
     * @return {@link #values}
     */
    public T[] shrink(){
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
        T[] newArray = Reflect.newArray(values, newSize);
        copyTo(newArray, newSize);
        values = newArray;
    }

    @Override
    protected Queue<T> create(int capacity){
        return values.getClass() == Object[].class ? new Queue<>(capacity) :
               new Queue<>(capacity, values.getClass().getComponentType());
    }

    @Override
    protected boolean sameType(Object other){
        return other instanceof Queue;
    }

    @Override
    public QueueIterator<T> newIterator(){
        return new QueueIterator<>(this);
    }

    @Override
    protected void freeItems(int from, int to){
        Arrays.fill(values, from, to, null);
    }

    @Override
    protected boolean equalItem(Queue<T> other, int otherIndex, int index){
        return Structs.eq(values[index(index)], other.values[other.index(otherIndex)]);
    }

    @Override
    protected void appendItem(StringBuilder buffer, int at){
        buffer.append(values[at]);
    }

    @Override
    protected int hashItem(int at){
        T value = values[at];
        return value == null ? 0 : value.hashCode();
    }

    /**
     * Remove the first item from the queue. (dequeue from head) Always O(1).
     * @return removed object
     * @throws NoSuchElementException when queue is empty
     */
    public T removeFirst(){
        int at = removeFirstIndex();
        T result = values[at];
        values[at] = null;
        return result;
    }

    /**
     * Remove the last item from the queue. (dequeue from tail) Always O(1).
     * @return removed object
     * @throws NoSuchElementException when queue is empty
     * @see #removeFirst()
     */
    public T removeLast(){
        int at = removeLastIndex();
        T result = values[at];
        values[at] = null;
        return result;
    }

    public boolean contains(T value){
        return contains(value, true);
    }

    public boolean contains(T value, boolean identity){
        return indexOf(value, identity) != -1;
    }

    /**
     * Returns the index of first occurrence of value in the queue, or -1 if no such value exists.
     * @param identity If true, == comparison will be used. If false, .equals() comparison will be used.
     * @return An index of first occurrence of value in queue or -1 if no such value exists
     */
    public int indexOf(T value, boolean identity){
        for(int i = 0; i < size; i++){
            T other = values[index(i)];
            if(identity || value == null ? other == value : value.equals(other)) return i;
        }
        return -1;
    }

    public int indexOf(Boolf<T> value){
        for(int i = 0; i < size; i++){
            if(value.get(values[index(i)])) return i;
        }
        return -1;
    }

    public boolean remove(Boolf<T> value){
        int index = indexOf(value);
        if(index == -1) return false;
        removeIndex(index);
        return true;
    }

    public boolean remove(T value){
        return remove(value, false);
    }

    /**
     * Removes the first instance of the specified value in the queue.
     * @param identity If true, == comparison will be used. If false, .equals() comparison will be used.
     * @return true if value was found and removed, false otherwise
     */
    public boolean remove(T value, boolean identity){
        int index = indexOf(value, identity);
        if(index == -1) return false;
        removeIndex(index);
        return true;
    }

    /** Removes and returns the item at the specified index. */
    public T removeIndex(int index){
        checkIndex(index);
        int at = index(index);
        T value = values[at];
        removeAt(at);
        return value;
    }

    /**
     * Returns the first (head) item in the queue (without removing it).
     * @throws NoSuchElementException when queue is empty
     * @see #addFirst(Object)
     * @see #removeFirst()
     */
    public T first(){
        checkNotEmpty();
        return values[head];
    }

    /**
     * Returns the last (tail) item in the queue (without removing it).
     * @throws NoSuchElementException when queue is empty
     * @see #addLast(Object)
     * @see #removeLast()
     */
    public T last(){
        checkNotEmpty();
        return values[lastIndex()];
    }

    /**
     * Retrieves the value in queue without removing it. Indexing is from the front to back, zero based.
     * Therefore get(0) is the same as {@link #first()}.
     * @throws IndexOutOfBoundsException when the index is negative or >= size
     */
    public T get(int index){
        checkIndex(index);
        return values[index(index)];
    }

    @Override
    public void each(Cons<? super T> c){
        for(int i = 0; i < size; i++){
            c.get(values[index(i)]);
        }
    }

    public T find(Boolf<T> func){
        for(int i = 0; i < size; i++){
            T value = values[index(i)];
            if(func.get(value)) return value;
        }
        return null;
    }


    public static class QueueIterator<T> extends BaseQueue.QueueIterator<T>{
        protected final Queue<T> queue;

        public QueueIterator(Queue<T> queue){
            super(queue);
            this.queue = queue;
        }

        @Override
        public T next(){
            return queue.values[queue.index(advance())];
        }
    }
}
