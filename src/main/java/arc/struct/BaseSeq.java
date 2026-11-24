package arc.struct;

import arc.func.*;
import arc.math.*;
import arc.util.*;

import java.util.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class BaseSeq<I extends BaseSeq.SeqIterator, S extends BaseSeq>{
    public int size;
    public boolean ordered;

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

    protected abstract void swapItems(int first, int second);

    /** @return the comparison of two items by their indices. */
    protected abstract int compareItems(int first, int second);

    protected abstract void sortItems(int from, int to);

    protected abstract void freeItems(int from, int to);

    protected abstract boolean equalItem(S other, int otherIndex, int index);

    protected abstract void appendItem(StringBuilder buffer, int index);

    protected abstract int hashItem(int index);

    /** Called before items are overwritten (moved, removed, etc), not added. */
    protected void beforeModify(){}

    protected void copyItems(S from, int fromIndex, S to, int toIndex, int count){
        System.arraycopy(from.items(), fromIndex, to.items(), toIndex, count);
    }

    protected void copyItems(int from, int to, int count){
        copyItems(self(), from, self(), to, count);
    }

    protected void addItems(Object array, int offset, int length){
        grow(length);
        System.arraycopy(array, offset, items(), size, length);
        size += length;
    }

    protected S self(){
        return (S)this;
    }

    public S copy(){
        S copy = create(size);
        copyItems(self(), 0, copy, 0, size);
        copy.size = size;
        return copy;
    }

    public S with(Cons<S> cons){
        S self = self();
        cons.get(self);
        return self;
    }

    public S addAll(S other){
        return addAll(other, 0, other.size);
    }

    public S addAll(S other, int offset, int length){
        checkRange(other, offset, length);
        grow(length);
        copyItems(other, offset, self(), size, length);
        size += length;
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

    /**
     * Removes the items that are not in the other sequence.
     * @return whether this sequence changed
     */
    public boolean retainAll(S other){
        boolean removed = false;
        for(int i = size - 1; i >= 0; i--){
            if(other.indexOfItem(self(), i) != -1) continue;
            removeItem(i);
            removed = true;
        }
        return removed;
    }

    /** Removes the duplicated items in place, keeping the first of each, in O(n^2) time. */
    public S distinct(){
        for(int i = size - 1; i > 0; i--){
            for(int j = 0; j < i; j++){
                if(equalItem(self(), j, i)){
                    removeItem(i);
                    break;
                }
            }
        }
        return self();
    }

    public int size() {
        return size;
    }

    public boolean isEmpty(){
        return size == 0;
    }

    public boolean any(){
        return size > 0;
    }

    public S clear(){
        beforeModify();
        freeItems(0, size);
        size = 0;
        return self();
    }

    /** Clears the sequence and reduces the size of the backing array, if larger. */
    public void clear(int maximumCapacity){
        clear();
        if(capacity() > maximumCapacity) resize(Math.max(maximumCapacity, size));
    }

    /** Reduces the size of the sequence to the specified size. Does nothing if already smaller. */
    public void truncate(int newSize){
        if(newSize < 0) throw new IllegalArgumentException("newSize must be >= 0: " + newSize);
        if(size <= newSize) return;
        beforeModify();
        freeItems(newSize, size);
        size = newSize;
    }

    public void swap(int first, int second){
        if(first >= size) throw new IndexOutOfBoundsException("first can't be >= size: " + first + " >= " + size);
        if(second >= size) throw new IndexOutOfBoundsException("second can't be >= size: " + second + " >= " + size);
        beforeModify();
        swapItems(first, second);
    }

    /** Sorts the items in their natural order. */
    public S sort(){
        return sort(0, size);
    }

    /** Sorts the items between the indices (end excluded) in their natural order. */
    public S sort(int from, int to){
        if(from < 0 || to > size || from > to) throw new IndexOutOfBoundsException("range [" + from + ", " + to + ") is out of [0, " + size + "]");
        beforeModify();
        sortItems(from, to);
        return self();
    }

    public int minIndex(){
        int min = size == 0 ? -1 : 0;
        for(int i = 1; i < size; i++){
            if(compareItems(i, min) < 0) min = i;
        }
        return min;
    }

    public int maxIndex(){
        int max = size == 0 ? -1 : 0;
        for(int i = 1; i < size; i++){
            if(compareItems(i, max) > 0) max = i;
        }
        return max;
    }

    /** @return whether the items are in their natural order. */
    public boolean isSorted(){
        for(int i = 1; i < size; i++){
            if(compareItems(i - 1, i) > 0) return false;
        }
        return true;
    }

    public S reverse(){
        beforeModify();
        for(int i = 0, lastIndex = size - 1, n = size / 2; i < n; i++){
            swapItems(i, lastIndex - i);
        }
        return self();
    }

    public S shuffle(){
        return shuffle(Mathf.rand);
    }

    public S shuffle(Rand rand){
        beforeModify();
        for(int i = size - 1; i >= 0; i--){
            swapItems(i, rand.nextInt(i + 1));
        }
        return self();
    }

    /** Removes the items between the specified indices, inclusive. */
    public void removeRange(int start, int end){
        if(end >= size) throw new IndexOutOfBoundsException("end can't be >= size: " + end + " >= " + size);
        if(start > end) throw new IndexOutOfBoundsException("start can't be > end: " + start + " > " + end);
        beforeModify();
        int count = end - start + 1, newSize = size - count;
        if(ordered){
            copyItems(start + count, start, size - (start + count));
        }else{
            int lastIndex = size - 1;
            for(int i = 0; i < count; i++){
                copyItems(lastIndex - i, start + i, 1);
            }
        }
        freeItems(newSize, size);
        size = newSize;
    }

    protected void grow(int additional){
        if(size + additional > capacity()) expand(size + additional);
    }

    protected void expand(int needed){
        resize(Math.max(Math.max(8, needed), (int)(size * 1.75f)));
    }

    protected void reserve(int additional){
        if(additional < 0) throw new IllegalArgumentException("additionalCapacity must be >= 0: " + additional);
        int needed = size + additional;
        if(needed > capacity()) resize(Math.max(8, needed));
    }

    protected void applySize(int newSize){
        truncate(newSize);
        if(newSize > capacity()) resize(Math.max(8, newSize));
        size = newSize;
    }

    protected static void checkRange(BaseSeq other, int offset, int length){
        if(offset + length > other.size)
            throw new IllegalArgumentException("offset + length must be <= size: " +
                                               offset + " + " + length + " <= " + other.size);
    }

    protected void checkNotEmpty(){
        if(size == 0) throw new IllegalStateException("Array is empty.");
    }

    protected void removeItem(int index){
        checkIndex(index);
        removeAt(index);
    }

    protected int indexOfItem(S other, int otherIndex){
        for(int i = 0; i < size; i++){
            if(equalItem(other, otherIndex, i)) return i;
        }
        return -1;
    }

    protected void checkIndex(int index){
        if(index >= size) throw new IndexOutOfBoundsException("index can't be >= size: " + index + " >= " + size);
    }

    protected void checkInsert(int index){
        if(index > size) throw new IndexOutOfBoundsException("index can't be > size: " + index + " > " + size);
    }

    protected void removeAt(int index){
        beforeModify();
        size--;
        if(ordered) copyItems(index + 1, index, size - index);
        else copyItems(size, index, 1);
        freeItems(size, size + 1);
    }

    protected void insertAt(int index){
        beforeModify();
        grow(1);
        if(ordered) copyItems(index, index + 1, size - index);
        else copyItems(index, size, 1);
        size++;
    }

    @Override
    public int hashCode(){
        if(!ordered) return super.hashCode();
        int h = 1;
        for(int i = 0, n = size; i < n; i++){
            h = h * 31 + hashItem(i);
        }
        return h;
    }

    protected boolean equalItems(S other){
        if(!ordered || !other.ordered || other.size != size) return false;
        for(int i = 0, n = size; i < n; i++){
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
        return size == 0 ? "[]" : join(new StringBuilder(32).append('['), ", ").append(']').toString();
    }

    public String toString(String separator){
        return size == 0 ? "" : join(new StringBuilder(32), separator).toString();
    }

    protected StringBuilder join(StringBuilder buffer, String separator){
        appendItem(buffer, 0);
        for(int i = 1; i < size; i++){
            appendItem(buffer.append(separator), i);
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

    protected <A extends SeqIterator> A reuse(A first, A second){
        A it = first != null && first.done ? first : second != null && second.done ? second : null;
        if(it != null){
            it.reset();
            it.done = false;
        }
        return it;
    }


    public static abstract class SeqIterator<T> implements Iterable<T>, Iterator<T>{
        protected final BaseSeq sequence;
        protected int index;
        protected boolean done;
        /** Whether {@link #remove()} is allowed. */
        public boolean allowRemove = true;

        protected SeqIterator(BaseSeq sequence){
            this.sequence = sequence;
        }

        /** Whether this iterator can be reused. */
        public boolean done(){
            return done;
        }

        public void reset(){
            index = 0;
        }

        public boolean hasNext(){
            if(index < sequence.size) return true;
            done = true;
            return false;
        }

        protected final int advance(){
            if(index >= sequence.size) throw new NoSuchElementException(index + ">=" + sequence.size);
            return index++;
        }

        public void remove(){
            if(!allowRemove) throw new ArcRuntimeException("Remove not allowed.");
            sequence.removeItem(--index);
        }

        @Override
        public Iterator<T> iterator(){
            return this;
        }
    }
}
