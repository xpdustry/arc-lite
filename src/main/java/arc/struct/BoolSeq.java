package arc.struct;

import arc.func.Boolc;
import arc.math.Mathf;

import java.util.*;

/**
 * A resizable, ordered or unordered boolean array. Avoids the boxing that occurs with ArrayList<Boolean>.
 * It is less memory efficient than {@link BitSet}, except for very small sizes.
 * It more CPU efficient than {@link BitSet}, except for very large sizes or if BitSet functionality such as and,
 * or, xor, etc are needed.
 * If unordered, this class avoids a memory copy when removing elements
 * (the last element is moved to the removed element's position).
 * @author Nathan Sweet
 */
public class BoolSeq extends BaseSeq<BoolSeq.BoolSeqIterator, BoolSeq> implements Iterable<Boolean>{
    public boolean[] items;

    /** Creates an ordered array with a capacity of 16. */
    public BoolSeq(){
        this(true, 16);
    }

    /** Creates an ordered array with the specified capacity. */
    public BoolSeq(int capacity){
        this(true, capacity);
    }

    /**
     * @param ordered If false, methods that remove elements may change the order of other elements in the array,
     *                which avoids a memory copy.
     * @param capacity Any elements added beyond this will cause the backing array to be grown.
     */
    public BoolSeq(boolean ordered, int capacity){
        this.ordered = ordered;
        items = new boolean[capacity];
    }

    /**
     * Creates a new array containing the elements in the specific array.
     * The new array will be ordered if the specific array is ordered.
     * The capacity is set to the number of elements, so any subsequent elements added will cause
     * the backing array to be grown.
     */
    public BoolSeq(BoolSeq array){
        this(array.ordered, array.size);
        addAll(array);
    }

    /**
     * Creates a new ordered array containing the elements in the specified array.
     * The capacity is set to the number of elements, so any subsequent elements added will cause
     * the backing array to be grown.
     */
    public BoolSeq(boolean[] array){
        this(true, array, 0, array.length);
    }

    /**
     * Creates a new array containing the elements in the specified array.
     * The capacity is set to the number of elements, so any subsequent elements added will cause
     * the backing array to be grown.
     * @param ordered If false, methods that remove elements may change the order of other elements in the array,
     *                which avoids a memory copy.
     */
    public BoolSeq(boolean ordered, boolean[] array, int startIndex, int count){
        this(ordered, count);
        addItems(array, startIndex, count);
    }

    /** @see #BoolSeq(boolean[]) */
    public static BoolSeq with(boolean... array){
        return new BoolSeq(array);
    }

    public void each(Boolc consumer){
        int size = this.size;
        boolean[] items = this.items;
        for(int i = 0; i < size; i++){
            consumer.get(items[i]);
        }
    }

    public void add(boolean value){
        grow(1);
        items[size++] = value;
    }

    public void add(boolean value1, boolean value2){
        grow(2);
        items[size] = value1;
        items[size + 1] = value2;
        size += 2;
    }

    public void add(boolean value1, boolean value2, boolean value3){
        grow(3);
        items[size] = value1;
        items[size + 1] = value2;
        items[size + 2] = value3;
        size += 3;
    }

    public void add(boolean value1, boolean value2, boolean value3, boolean value4){
        grow(4);
        items[size] = value1;
        items[size + 1] = value2;
        items[size + 2] = value3;
        items[size + 3] = value4;
        size += 4;
    }

    public void addAll(boolean... array){
        addAll(array, 0, array.length);
    }

    public void addAll(boolean[] array, int offset, int length){
        addItems(array, offset, length);
    }

    public boolean get(int index){
        checkIndex(index);
        return items[index];
    }

    public void set(int index, boolean value){
        checkIndex(index);
        beforeModify();
        items[index] = value;
    }

    public void insert(int index, boolean value){
        checkInsert(index);
        insertAt(index);
        items[index] = value;
    }

    /** Removes and returns the item at the specified index. */
    public boolean removeIndex(int index){
        checkIndex(index);
        boolean value = items[index];
        removeAt(index);
        return value;
    }

    /** Removes and returns the last item. */
    public boolean pop(){
        beforeModify();
        return items[--size];
    }

    /** Returns the last item. */
    public boolean peek(){
        return items[size - 1];
    }

    /** Returns the first item. */
    public boolean first(){
        checkNotEmpty();
        return items[0];
    }

    /** Returns the smallest item. */
    public boolean min(){
        checkNotEmpty();
        return items[minIndex()];
    }

    /** Returns the largest item. */
    public boolean max(){
        checkNotEmpty();
        return items[maxIndex()];
    }

    /**
     * Reduces the size of the backing array to the size of the actual items.
     * This is useful to release memory when many items have been removed,
     * or if it is known that more items will not be added.
     * @return {@link #items}
     */
    public boolean[] shrink(){
        if(items.length != size) resize(size);
        return items;
    }

    /**
     * Increases the size of the backing array to accommodate the specified number of additional items.
     * Useful before adding many items to avoid multiple backing array resizes.
     * @return {@link #items}
     */
    public boolean[] ensureCapacity(int additionalCapacity){
        reserve(additionalCapacity);
        return items;
    }

    /**
     * Sets the array size, leaving any values beyond the current size undefined.
     * @return {@link #items}
     */
    public boolean[] setSize(int newSize){
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
        boolean[] newItems = new boolean[newSize];
        boolean[] items = this.items;
        System.arraycopy(items, 0, newItems, 0, Math.min(size, newItems.length));
        this.items = newItems;
    }

    @Override
    protected BoolSeq create(int capacity){
        return new BoolSeq(ordered, capacity);
    }

    @Override
    protected boolean sameType(Object other){
        return other instanceof BoolSeq;
    }

    @Override
    public BoolSeqIterator newIterator(){
        return new BoolSeqIterator(this);
    }

    @Override
    protected void swapItems(int first, int second){
        boolean[] items = this.items;
        boolean value = items[first];
        items[first] = items[second];
        items[second] = value;
    }

    @Override
    protected int compareItems(int first, int second){
        return Boolean.compare(items[first], items[second]);
    }

    @Override
    protected void sortItems(int from, int to){
        int falses = from;
        for(int i = from; i < to; i++){
            if(!items[i]) falses++;
        }
        Arrays.fill(items, from, falses, false);
        Arrays.fill(items, falses, to, true);
    }

    /** Puts false before true. */
    @Override
    protected void freeItems(int from, int to){
    }

    @Override
    protected boolean equalItem(BoolSeq other, int otherIndex, int index){
        return items[index] == other.items[otherIndex];
    }

    @Override
    protected void appendItem(StringBuilder buffer, int index){
        buffer.append(items[index]);
    }

    @Override
    protected int hashItem(int index){
        return items[index] ? 1231 : 1237;
    }

    /** Returns a random item from the array, or false if the array is empty. */
    public boolean random(){
        if(size == 0) return false;
        return items[Mathf.random(0, size - 1)];
    }

    public boolean[] toArray(){
        return Arrays.copyOf(items, size);
    }


    public static class BoolSeqIterator extends SeqIterator<Boolean>{
        protected final BoolSeq seq;

        public BoolSeqIterator(BoolSeq seq){
            super(seq);
            this.seq = seq;
        }

        @Override
        public Boolean next(){
            return nextBool();
        }

        public boolean nextBool(){
            return seq.items[advance()];
        }
    }
}
