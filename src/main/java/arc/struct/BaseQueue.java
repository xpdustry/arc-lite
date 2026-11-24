package arc.struct;

import arc.func.*;
import arc.util.*;

import java.util.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class BaseQueue<I extends BaseQueue.QueueIterator, S extends BaseQueue>{
    public int size;
    /**
     * Index of first element. Logically smaller than tail.
     * Unless empty, it points to a valid element inside queue.
     */
    protected int head;
    /**
     * Index of last element. Logically bigger than head.
     * Usually points to an empty position, but points to the head when full.
     */
    protected int tail;

    protected transient I first, second;

    /** @return the backing array, for {@link System#arraycopy} use. */
    protected abstract Object items();

    /** @return the length of the backing array. */
    public abstract int capacity();

    protected abstract void resize(int newSize);

    protected abstract S create(int capacity);

    protected abstract boolean sameType(Object other);

    /** Allocates a new iterator that is not reused, unlike {@link #iterator()}. */
    public abstract I newIterator();

    protected abstract void freeItems(int from, int to);

    protected abstract boolean equalItem(S other, int otherIndex, int index);

    protected abstract void appendItem(StringBuilder buffer, int at);

    protected abstract int hashItem(int at);

    /** Called before items are overwritten (moved, removed, etc), not added. */
    protected void beforeModify(){}

    protected void copyItems(S from, int fromIndex, S to, int toIndex, int count){
        System.arraycopy(from.items(), fromIndex, to.items(), toIndex, count);
    }

    protected void copyItems(int from, int to, int count){
        copyItems(self(), from, self(), to, count);
    }

    protected void copyItems(Object array, int position){
        Object values = items();
        if(head < tail){
            System.arraycopy(values, head, array, position, tail - head);
        }else if(size > 0){
            int rest = capacity() - head;
            System.arraycopy(values, head, array, position, rest);
            System.arraycopy(values, 0, array, position + rest, tail);
        }
    }

    protected void addItems(Object array, int offset, int length){
        ensureCapacity(length);
        int capacity = capacity(), first = Math.min(length, capacity - tail);
        System.arraycopy(array, offset, items(), tail, first);
        System.arraycopy(array, offset + first, items(), 0, length - first);
        tail += length;
        if(tail >= capacity) tail -= capacity;
        size += length;
    }

    protected void copyTo(Object newArray, int newSize){
        copyItems(newArray, 0);
        head = 0;
        // The tail wraps when the values fill the array
        tail = size == newSize ? 0 : size;
    }

    protected S self(){
        return (S)this;
    }

    public S copy(){
        S copy = create(size);
        copyItems(copy.items(), 0);
        copy.size = size;
        copy.tail = size == copy.capacity() ? 0 : size;
        return copy;
    }

    public S with(Cons<S> cons){
        S self = self();
        cons.get(self);
        return self;
    }

    public S addAll(S other){
        int length = other.size;
        ensureCapacity(length);
        // The values of the other queue are in two parts when they wrap, which this one may also be
        int rest = Math.min(length, other.capacity() - other.head);
        addItems(other.items(), other.head, rest);
        addItems(other.items(), 0, length - rest);
        return self();
    }

    public void set(S other){
        if(other == this) return;
        clear();
        addAll(other);
    }

    public boolean containsAll(S other){
        for(int i = 0; i < other.size; i++){
            if(indexOfItem(other, i) == -1) return false;
        }
        return true;
    }

    public boolean removeAll(S other){
        boolean removed = false;
        for(int i = 0; i < other.size; i++){
            int index = indexOfItem(other, i);
            if(index == -1) continue;
            removeItem(index);
            removed = true;
        }
        return removed;
    }

    public boolean retainAll(S other){
        boolean removed = false;
        for(int i = size - 1; i >= 0; i--){
            if(other.indexOfItem(self(), i) != -1) continue;
            removeItem(i);
            removed = true;
        }
        return removed;
    }

    public int size(){
        return size;
    }

    public boolean isEmpty(){
        return size == 0;
    }

    public boolean any(){
        return size > 0;
    }

    public void clear(){
        if(size == 0) return;
        beforeModify();
        if(head < tail){
            freeItems(head, tail);
        }else{
            freeItems(head, capacity());
            freeItems(0, tail);
        }
        head = 0;
        tail = 0;
        size = 0;
    }

    /** Clears the queue and reduces the size of the backing array to the specified capacity if it is larger. */
    public void clear(int maximumCapacity){
        clear();
        if(capacity() > maximumCapacity) resize(maximumCapacity);
    }

    /** Reduces the size of the queue to the specified size. Does nothing if already smaller. */
    public void truncate(int newSize){
        if(newSize < 0) throw new IllegalArgumentException("newSize must be >= 0: " + newSize);
        if(size <= newSize) return;
        beforeModify();
        int newTail = index(newSize);
        if(newTail < tail){
            freeItems(newTail, tail);
        }else{
            freeItems(newTail, capacity());
            freeItems(0, tail);
        }
        tail = newTail;
        size = newSize;
    }

    public void ensureCapacity(int additional){
        int needed = size + additional;
        if(capacity() < needed) resize(needed);
    }

    protected void grow(){
        resize(Math.max(1, capacity() << 1));
    }

    protected void checkNotEmpty(){
        if(size == 0) throw new NoSuchElementException("Queue is empty.");
    }

    protected void removeItem(int index){
        checkIndex(index);
        removeAt(index(index));
    }

    protected int indexOfItem(S other, int otherIndex){
        for(int i = 0; i < size; i++){
            if(equalItem(other, otherIndex, i)) return i;
        }
        return -1;
    }

    protected void checkIndex(int index){
        if(index < 0) throw new IndexOutOfBoundsException("index can't be < 0: " + index);
        if(index >= size) throw new IndexOutOfBoundsException("index can't be >= size: " + index + " >= " + size);
    }

    protected int removeAt(int at){
        beforeModify();
        int free;
        if(head < tail || at < head){ // between head and tail, or between 0 and tail
            copyItems(at + 1, at, tail - at);
            free = --tail;
        }else{ // between head and the end of the array
            copyItems(head, head + 1, at - head);
            free = head;
            if(++head == capacity()) head = 0;
        }
        size--;
        freeItems(free, free + 1);
        return free;
    }

    protected int index(int index){
        int at = head + index, capacity = capacity();
        return at >= capacity ? at - capacity : at;
    }

    protected int lastIndex(){
        return tail == 0 ? capacity() - 1 : tail - 1;
    }

    protected int addLastIndex(){
        if(size == capacity()) grow();
        int at = tail++;
        if(tail == capacity()) tail = 0;
        size++;
        return at;
    }

    protected int addFirstIndex(){
        if(size == capacity()) grow();
        if(--head == -1) head = capacity() - 1;
        size++;
        return head;
    }

    protected int removeFirstIndex(){
        checkNotEmpty();
        beforeModify();
        int at = head;
        if(++head == capacity()) head = 0;
        size--;
        return at;
    }

    protected int removeLastIndex(){
        checkNotEmpty();
        beforeModify();
        if(--tail == -1) tail = capacity() - 1;
        size--;
        return tail;
    }

    @Override
    public int hashCode(){
        int hash = size + 1;
        for(int i = 0; i < size; i++){
            hash = hash * 31 + hashItem(index(i));
        }
        return hash;
    }

    protected boolean equalItems(S other){
        if(other.size != size) return false;
        for(int i = 0; i < size; i++){
            if(!equalItem(other, i, i)) return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object other){
        return other == this || sameType(other) && equalItems((S)other);
    }

    @Override
    public String toString(){
        return size == 0 ? "[]" : join(new StringBuilder(64).append('['), ", ").append(']').toString();
    }

    public String toString(String separator){
        return size == 0 ? "" : join(new StringBuilder(64), separator).toString();
    }

    protected StringBuilder join(StringBuilder buffer, String separator){
        appendItem(buffer, head);
        for(int i = 1; i < size; i++){
            appendItem(buffer.append(separator), index(i));
        }
        return buffer;
    }

    /**
     * Returns an iterator for the items in this sequence. Remove is supported.
     * <p>
     * Always returns the two same iterators. Use {@link #newIterator()} for multithreaded or nested iteration.
     */
    public I iterator(){
        I it = reuse(first, second);
        if(it != null) return it;
        second = first;
        return first = newIterator();
    }

    protected <A extends QueueIterator> A reuse(A first, A second){
        A it = first != null && first.done ? first : second != null && second.done ? second : null;
        if(it != null){
            it.reset();
            it.done = false;
        }
        return it;
    }


    public static abstract class QueueIterator<T> implements Iterable<T>, Iterator<T>{
        protected final BaseQueue source;
        protected int index;
        protected boolean done;
        /** Whether {@link #remove()} is allowed. */
        public boolean allowRemove = true;

        protected QueueIterator(BaseQueue source){
            this.source = source;
        }

        /** Whether this iterator can be reused. */
        public boolean done(){
            return done;
        }

        public void reset(){
            index = 0;
        }

        public boolean hasNext(){
            if(index < source.size) return true;
            done = true;
            return false;
        }

        protected final int advance(){
            if(index >= source.size) throw new NoSuchElementException(index + ">=" + source.size);
            return index++;
        }

        public void remove(){
            if(!allowRemove) throw new ArcRuntimeException("Remove not allowed.");
            source.removeItem(--index);
        }

        @Override
        public Iterator<T> iterator(){
            return this;
        }
    }
}
