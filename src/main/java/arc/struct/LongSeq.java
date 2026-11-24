package arc.struct;

import arc.func.LongBoolf;
import arc.func.Longc;
import arc.math.Mathf;

import java.util.Arrays;

/**
 * A resizable, ordered or unordered long array. Avoids the boxing that occurs with ArrayList<Long>.
 * If unordered, this class avoids a memory copy when removing elements
 * (the last element is moved to the removed element's position).
 * @author Nathan Sweet
 */
public class LongSeq extends BaseSeq<LongSeq.LongSeqIterator, LongSeq> implements Iterable<Long>{
    public long[] items;

    /** Creates an ordered array with a capacity of 16. */
    public LongSeq(){
        this(true, 16);
    }

    /** Creates an ordered array with the specified capacity. */
    public LongSeq(int capacity){
        this(true, capacity);
    }

    /**
     * @param ordered If false, methods that remove elements may change the order of other elements in the array,
     *                which avoids a memory copy.
     * @param capacity Any elements added beyond this will cause the backing array to be grown.
     */
    public LongSeq(boolean ordered, int capacity){
        this.ordered = ordered;
        items = new long[capacity];
    }

    /**
     * Creates a new array containing the elements in the specific array.
     * The new array will be ordered if the specific array is ordered.
     * The capacity is set to the number of elements, so any subsequent elements added will cause
     * the backing array to be grown.
     */
    public LongSeq(LongSeq array){
        this(array.ordered, array.size);
        addAll(array);
    }

    /**
     * Creates a new ordered array containing the elements in the specified array.
     * The capacity is set to the number of elements, so any subsequent elements added will cause
     * the backing array to be grown.
     */
    public LongSeq(long[] array){
        this(true, array, 0, array.length);
    }

    /**
     * Creates a new array containing the elements in the specified array.
     * The capacity is set to the number of elements, so any subsequent elements added will cause
     * the backing array to be grown.
     * @param ordered If false, methods that remove elements may change the order of other elements in the array,
     *                which avoids a memory copy.
     */
    public LongSeq(boolean ordered, long[] array, int startIndex, int count){
        this(ordered, count);
        addItems(array, startIndex, count);
    }

    /** @see #LongSeq(long[]) */
    public static LongSeq with(long... array){
        return new LongSeq(array);
    }

    public void each(Longc consumer){
        int size = this.size;
        long[] items = this.items;
        for(int i = 0; i < size; i++){
            consumer.get(items[i]);
        }
    }

    public long sum(){
        long sum = 0;
        for(int i = 0; i < size; i++){
            sum += items[i];
        }
        return sum;
    }

    public int count(LongBoolf pred){
        int count = 0;
        for(int i = 0; i < size; i++){
            if(pred.get(items[i])) count++;
        }
        return count;
    }

    public boolean allMatch(LongBoolf pred){
        for(int i = 0; i < size; i++){
            if(!pred.get(items[i])) return false;
        }
        return true;
    }

    /** Removes the items matching the predicate, keeping the order. */
    public LongSeq removeAll(LongBoolf pred){
        beforeModify();
        long[] items = this.items;
        int kept = 0;
        for(int i = 0; i < size; i++){
            if(!pred.get(items[i])) items[kept++] = items[i];
        }
        size = kept;
        return this;
    }

    public void add(long value){
        grow(1);
        items[size++] = value;
    }

    public void add(long value1, long value2){
        grow(2);
        items[size] = value1;
        items[size + 1] = value2;
        size += 2;
    }

    public void add(long value1, long value2, long value3){
        grow(3);
        items[size] = value1;
        items[size + 1] = value2;
        items[size + 2] = value3;
        size += 3;
    }

    public void add(long value1, long value2, long value3, long value4){
        grow(4);
        items[size] = value1;
        items[size + 1] = value2;
        items[size + 2] = value3;
        items[size + 3] = value4;
        size += 4;
    }

    public void addAll(long... array){
        addAll(array, 0, array.length);
    }

    public void addAll(long[] array, int offset, int length){
        addItems(array, offset, length);
    }

    public long get(int index){
        checkIndex(index);
        return items[index];
    }

    public void set(int index, long value){
        checkIndex(index);
        beforeModify();
        items[index] = value;
    }

    public void incr(int index, long value){
        checkIndex(index);
        beforeModify();
        items[index] += value;
    }

    public void mul(int index, long value){
        checkIndex(index);
        beforeModify();
        items[index] *= value;
    }

    public void insert(int index, long value){
        checkInsert(index);
        insertAt(index);
        items[index] = value;
    }

    public boolean contains(long value){
        return indexOf(value) != -1;
    }

    public int indexOf(long value){
        long[] items = this.items;
        for(int i = 0, n = size; i < n; i++)
            if(items[i] == value) return i;
        return -1;
    }

    public int lastIndexOf(long value){
        long[] items = this.items;
        for(int i = size - 1; i >= 0; i--)
            if(items[i] == value) return i;
        return -1;
    }

    /** @deprecated Use {@link #lastIndexOf(long)}. */
    @Deprecated
    public int lastIndexOf(char value){
        return lastIndexOf((long)value);
    }

    public boolean removeValue(long value){
        int index = indexOf(value);
        if(index == -1) return false;
        removeIndex(index);
        return true;
    }

    /** Removes and returns the item at the specified index. */
    public long removeIndex(int index){
        checkIndex(index);
        long value = items[index];
        removeAt(index);
        return value;
    }

    /** Removes and returns the last item. */
    public long pop(){
        beforeModify();
        return items[--size];
    }

    /** Returns the last item. */
    public long peek(){
        return items[size - 1];
    }

    /** Returns the first item. */
    public long first(){
        checkNotEmpty();
        return items[0];
    }

    /** Returns the smallest item. */
    public long min(){
        checkNotEmpty();
        return items[minIndex()];
    }

    /** Returns the largest item. */
    public long max(){
        checkNotEmpty();
        return items[maxIndex()];
    }

    /**
     * Reduces the size of the backing array to the size of the actual items.
     * This is useful to release memory when many items have been removed,
     * or if it is known that more items will not be added.
     * @return {@link #items}
     */
    public long[] shrink(){
        if(items.length != size) resize(size);
        return items;
    }

    /**
     * Increases the size of the backing array to accommodate the specified number of additional items.
     * Useful before adding many items to avoid multiple backing array resizes.
     * @return {@link #items}
     */
    public long[] ensureCapacity(int additionalCapacity){
        reserve(additionalCapacity);
        return items;
    }

    /**
     * Sets the array size, leaving any values beyond the current size undefined.
     * @return {@link #items}
     */
    public long[] setSize(int newSize){
        applySize(newSize);
        return items;
    }

    @Override
    protected Object items(){
        return items;
    }

    @Override
    public int capacity(){
        return items.length;
    }

    @Override
    protected void resize(int newSize){
        long[] newItems = new long[newSize];
        long[] items = this.items;
        System.arraycopy(items, 0, newItems, 0, Math.min(size, newItems.length));
        this.items = newItems;
    }

    @Override
    protected LongSeq create(int capacity){
        return new LongSeq(ordered, capacity);
    }

    @Override
    protected boolean sameType(Object other){
        return other instanceof LongSeq;
    }

    @Override
    public LongSeqIterator newIterator(){
        return new LongSeqIterator(this);
    }

    @Override
    protected void swapItems(int first, int second){
        long[] items = this.items;
        long value = items[first];
        items[first] = items[second];
        items[second] = value;
    }

    @Override
    protected int compareItems(int first, int second){
        return Long.compare(items[first], items[second]);
    }

    @Override
    protected void sortItems(int from, int to){
        Arrays.sort(items, from, to);
    }

    @Override
    protected void freeItems(int from, int to){
    }

    @Override
    protected boolean equalItem(LongSeq other, int otherIndex, int index){
        return items[index] == other.items[otherIndex];
    }

    @Override
    protected void appendItem(StringBuilder buffer, int index){
        buffer.append(items[index]);
    }

    @Override
    protected int hashItem(int index){
        long item = items[index];
        return (int)(item ^ item >>> 32);
    }

    /** Returns a random item from the array, or zero if the array is empty. */
    public long random(){
        if(size == 0) return 0;
        return items[Mathf.random(0, size - 1)];
    }

    public long[] toArray(){
        return Arrays.copyOf(items, size);
    }


    public static class LongSeqIterator extends SeqIterator<Long>{
        protected final LongSeq seq;

        public LongSeqIterator(LongSeq seq){
            super(seq);
            this.seq = seq;
        }

        @Override
        public Long next(){
            return nextLong();
        }

        public long nextLong(){
            return seq.items[advance()];
        }
    }
}
