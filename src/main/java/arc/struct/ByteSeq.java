package arc.struct;

import arc.func.*;
import arc.math.Mathf;

import java.util.*;

/**
 * A resizable, ordered or unordered byte array. Avoids the boxing that occurs with ArrayList<Byte>.
 * If unordered, this class avoids a memory copy when removing elements
 * (the last element is moved to the removed element's position).
 * @author Nathan Sweet
 */
public class ByteSeq extends BaseSeq<ByteSeq.ByteSeqIterator, ByteSeq> implements Iterable<Byte>{
    public byte[] items;

    /** Creates an ordered array with a capacity of 16. */
    public ByteSeq(){
        this(true, 16);
    }

    /** Creates an ordered array with the specified capacity. */
    public ByteSeq(int capacity){
        this(true, capacity);
    }

    /**
     * @param ordered If false, methods that remove elements may change the order of other elements in the array,
     *                which avoids a memory copy.
     * @param capacity Any elements added beyond this will cause the backing array to be grown.
     */
    public ByteSeq(boolean ordered, int capacity){
        this.ordered = ordered;
        items = new byte[capacity];
    }

    /**
     * Creates a new array containing the elements in the specific array.
     * The new array will be ordered if the specific array is ordered.
     * The capacity is set to the number of elements, so any subsequent elements added will cause
     * the backing array to be grown.
     */
    public ByteSeq(ByteSeq array){
        this(array.ordered, array.size);
        addAll(array);
    }

    /**
     * Creates a new ordered array containing the elements in the specified array.
     * The capacity is set to the number of elements, so any subsequent elements added will cause
     * the backing array to be grown.
     */
    public ByteSeq(byte[] array){
        this(true, array, 0, array.length);
    }

    /**
     * Creates a new array containing the elements in the specified array.
     * The capacity is set to the number of elements, so any subsequent elements added will cause
     * the backing array to be grown.
     * @param ordered If false, methods that remove elements may change the order of other elements in the array,
     *                which avoids a memory copy.
     */
    public ByteSeq(boolean ordered, byte[] array, int startIndex, int count){
        this(ordered, count);
        addItems(array, startIndex, count);
    }

    /** @see #ByteSeq(byte[]) */
    public static ByteSeq with(byte... array){
        return new ByteSeq(array);
    }

    public void each(Bytec consumer){
        int size = this.size;
        byte[] items = this.items;
        for(int i = 0; i < size; i++){
            consumer.get(items[i]);
        }
    }

    /** @return the sum of all items. */
    public int sum(){
        int sum = 0;
        for(int i = 0; i < size; i++){
            sum += items[i];
        }
        return sum;
    }

    public void add(byte value){
        grow(1);
        items[size++] = value;
    }

    public void add(byte value1, byte value2){
        grow(2);
        items[size] = value1;
        items[size + 1] = value2;
        size += 2;
    }

    public void add(byte value1, byte value2, byte value3){
        grow(3);
        items[size] = value1;
        items[size + 1] = value2;
        items[size + 2] = value3;
        size += 3;
    }

    public void add(byte value1, byte value2, byte value3, byte value4){
        grow(4);
        items[size] = value1;
        items[size + 1] = value2;
        items[size + 2] = value3;
        items[size + 3] = value4;
        size += 4;
    }

    public void addAll(byte... array){
        addAll(array, 0, array.length);
    }

    public void addAll(byte[] array, int offset, int length){
        addItems(array, offset, length);
    }

    public byte get(int index){
        checkIndex(index);
        return items[index];
    }

    public void set(int index, byte value){
        checkIndex(index);
        beforeModify();
        items[index] = value;
    }

    public void incr(int index, byte value){
        checkIndex(index);
        beforeModify();
        items[index] += value;
    }

    public void mul(int index, byte value){
        checkIndex(index);
        beforeModify();
        items[index] *= value;
    }

    public void insert(int index, byte value){
        checkInsert(index);
        insertAt(index);
        items[index] = value;
    }

    public boolean contains(byte value){
        return indexOf(value) != -1;
    }

    public int indexOf(byte value){
        byte[] items = this.items;
        for(int i = 0, n = size; i < n; i++)
            if(items[i] == value) return i;
        return -1;
    }

    public int lastIndexOf(byte value){
        byte[] items = this.items;
        for(int i = size - 1; i >= 0; i--)
            if(items[i] == value) return i;
        return -1;
    }

    public boolean removeValue(byte value){
        int index = indexOf(value);
        if(index == -1) return false;
        removeIndex(index);
        return true;
    }

    /** Removes and returns the item at the specified index. */
    public int removeIndex(int index){
        checkIndex(index);
        int value = items[index];
        removeAt(index);
        return value;
    }

    /** Removes and returns the last item. */
    public byte pop(){
        beforeModify();
        return items[--size];
    }

    /** Returns the last item. */
    public byte peek(){
        return items[size - 1];
    }

    /** Returns the first item. */
    public byte first(){
        checkNotEmpty();
        return items[0];
    }

    /** Returns the smallest item. */
    public byte min(){
        checkNotEmpty();
        return items[minIndex()];
    }

    /** Returns the largest item. */
    public byte max(){
        checkNotEmpty();
        return items[maxIndex()];
    }

    /**
     * Reduces the size of the backing array to the size of the actual items.
     * This is useful to release memory when many items have been removed,
     * or if it is known that more items will not be added.
     * @return {@link #items}
     */
    public byte[] shrink(){
        if(items.length != size) resize(size);
        return items;
    }

    /**
     * Increases the size of the backing array to accommodate the specified number of additional items.
     * Useful before adding many items to avoid multiple backing array resizes.
     * @return {@link #items}
     */
    public byte[] ensureCapacity(int additionalCapacity){
        reserve(additionalCapacity);
        return items;
    }

    /**
     * Sets the array size, leaving any values beyond the current size undefined.
     * @return {@link #items}
     */
    public byte[] setSize(int newSize){
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
        byte[] newItems = new byte[newSize];
        byte[] items = this.items;
        System.arraycopy(items, 0, newItems, 0, Math.min(size, newItems.length));
        this.items = newItems;
    }

    @Override
    protected ByteSeq create(int capacity){
        return new ByteSeq(ordered, capacity);
    }

    @Override
    protected boolean sameType(Object other){
        return other instanceof ByteSeq;
    }

    @Override
    public ByteSeqIterator newIterator(){
        return new ByteSeqIterator(this);
    }

    @Override
    protected void swapItems(int first, int second){
        byte[] items = this.items;
        byte value = items[first];
        items[first] = items[second];
        items[second] = value;
    }

    @Override
    protected int compareItems(int first, int second){
        return Byte.compare(items[first], items[second]);
    }

    @Override
    protected void sortItems(int from, int to){
        Arrays.sort(items, from, to);
    }

    @Override
    protected void freeItems(int from, int to){
    }

    @Override
    protected boolean equalItem(ByteSeq other, int otherIndex, int index){
        return items[index] == other.items[otherIndex];
    }

    @Override
    protected void appendItem(StringBuilder buffer, int index){
        buffer.append(items[index]);
    }

    @Override
    protected int hashItem(int index){
        return items[index];
    }

    /** Returns a random item from the array, or zero if the array is empty. */
    public byte random(){
        if(size == 0) return 0;
        return items[Mathf.random(0, size - 1)];
    }

    public byte[] toArray(){
        return Arrays.copyOf(items, size);
    }


    public static class ByteSeqIterator extends SeqIterator<Byte>{
        protected final ByteSeq seq;

        public ByteSeqIterator(ByteSeq seq){
            super(seq);
            this.seq = seq;
        }

        @Override
        public Byte next(){
            return nextByte();
        }

        public byte nextByte(){
            return seq.items[advance()];
        }
    }
}
