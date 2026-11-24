package arc.struct;

import arc.func.*;
import arc.math.*;
import java.util.*;

/**
 * A resizable, ordered or unordered int array. Avoids the boxing that occurs with ArrayList<Integer>.
 * If unordered, this class avoids a memory copy when removing elements
 * (the last element is moved to the removed element's position).
 * @author Nathan Sweet
 */
public class IntSeq extends BaseSeq<IntSeq.IntSeqIterator, IntSeq> implements Iterable<Integer>{
    public int[] items;

    public static IntSeq range(int min, int max){
        IntSeq out = new IntSeq();
        for(int i = min; i < max; i++){
            out.add(i);
        }
        return out;
    }

    /** Creates an ordered array with a capacity of 16. */
    public IntSeq(){
        this(true, 16);
    }

    /** Creates an ordered array with the specified capacity. */
    public IntSeq(int capacity){
        this(true, capacity);
    }

    /**
     * @param ordered If false, methods that remove elements may change the order of other elements in the array,
     *                which avoids a memory copy.
     * @param capacity Any elements added beyond this will cause the backing array to be grown.
     */
    public IntSeq(boolean ordered, int capacity){
        this.ordered = ordered;
        items = new int[capacity];
    }

    /**
     * Creates a new array containing the elements in the specific array.
     * The new array will be ordered if the specific array is ordered.
     * The capacity is set to the number of elements, so any subsequent elements added will cause
     * the backing array to be grown.
     */
    public IntSeq(IntSeq array){
        this(array.ordered, array.size);
        addAll(array);
    }

    /**
     * Creates a new ordered array containing the elements in the specified array.
     * The capacity is set to the number of elements, so any subsequent elements added will cause
     * the backing array to be grown.
     */
    public IntSeq(int[] array){
        this(true, array, 0, array.length);
    }

    /**
     * Creates a new array containing the elements in the specified array.
     * The capacity is set to the number of elements, so any subsequent elements added will cause
     * the backing array to be grown.
     * @param ordered If false, methods that remove elements may change the order of other elements in the array,
     * which avoids a memory copy.
     */
    public IntSeq(boolean ordered, int[] array, int startIndex, int count){
        this(ordered, count);
        addItems(array, startIndex, count);
    }

    /** @see #IntSeq(int[]) */
    public static IntSeq with(int... array){
        return new IntSeq(array);
    }

    /** @return the most frequently occuring element. */
    public int mode(){
        int count = 1, tempCount;
        int popular = size == 0 ? 0 : items[0];
        int temp;
        for(int i = 0; i < size - 1; i++){
            temp = items[i];
            tempCount = 0;
            for(int j = 1; j < size; j++){
                if(temp == items[j]) tempCount++;
            }
            if(tempCount > count){
                popular = temp;
                count = tempCount;
            }
        }
        return popular;
    }

    public void each(Intc iterator){
        int size = this.size;
        int[] items = this.items;
        for(int i = 0; i < size; i++){
            iterator.get(items[i]);
        }
    }

    public int count(int value){
        int out = 0;
        for(int i = 0; i < size; i++){
            if(items[i] == value){
                out ++;
            }
        }
        return out;
    }

    public int sum(){
        int sum = 0;
        for(int i = 0; i < size; i++){
            sum += items[i];
        }
        return sum;
    }

    public int count(IntBoolf pred){
        int count = 0;
        for(int i = 0; i < size; i++){
            if(pred.get(items[i])) count++;
        }
        return count;
    }

    public boolean allMatch(IntBoolf pred){
        for(int i = 0; i < size; i++){
            if(!pred.get(items[i])) return false;
        }
        return true;
    }

    /** Removes the items matching the predicate, keeping the order. */
    public IntSeq removeAll(IntBoolf pred){
        beforeModify();
        int[] items = this.items;
        int kept = 0;
        for(int i = 0; i < size; i++){
            if(!pred.get(items[i])) items[kept++] = items[i];
        }
        size = kept;
        return this;
    }

    public void chunked(int chunkSize, Cons<int[]> iterator){
        if(chunkSize < 1) throw new IllegalArgumentException("chunkSize must be at least 1: " + chunkSize);
        for(int i = 0; i < size; i += chunkSize){
            int[] slice = Arrays.copyOfRange(items, i, Math.min(i + chunkSize, size));
            iterator.get(slice);
        }
    }

    /**
     * Adds a value if it was already not in this sequence.
     * @return whether this value was not present in this sequence.
     * */
    public boolean addUnique(int value){
        if(!contains(value)){
            add(value);
            return true;
        }
        return false;
    }

    public void add(int value){
        grow(1);
        items[size++] = value;
    }

    public void add(int value1, int value2){
        grow(2);
        items[size] = value1;
        items[size + 1] = value2;
        size += 2;
    }

    public void add(int value1, int value2, int value3){
        grow(3);
        items[size] = value1;
        items[size + 1] = value2;
        items[size + 2] = value3;
        size += 3;
    }

    public void add(int value1, int value2, int value3, int value4){
        grow(4);
        items[size] = value1;
        items[size + 1] = value2;
        items[size + 2] = value3;
        items[size + 3] = value4;
        size += 4;
    }

    public void addAll(int... array){
        addAll(array, 0, array.length);
    }

    public void addAll(int[] array, int offset, int length){
        addItems(array, offset, length);
    }

    public int get(int index){
        checkIndex(index);
        return items[index];
    }

    public void set(int index, int value){
        checkIndex(index);
        beforeModify();
        items[index] = value;
    }

    public void incr(int index, int value){
        checkIndex(index);
        beforeModify();
        items[index] += value;
    }

    public void mul(int index, int value){
        checkIndex(index);
        beforeModify();
        items[index] *= value;
    }

    public void insert(int index, int value){
        checkInsert(index);
        insertAt(index);
        items[index] = value;
    }

    public boolean contains(int value){
        return indexOf(value) != -1;
    }

    public int indexOf(int value){
        int[] items = this.items;
        for(int i = 0, n = size; i < n; i++)
            if(items[i] == value) return i;
        return -1;
    }

    public int lastIndexOf(int value){
        int[] items = this.items;
        for(int i = size - 1; i >= 0; i--)
            if(items[i] == value) return i;
        return -1;
    }

    public boolean removeValue(int value){
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
    public int pop(){
        beforeModify();
        return items[--size];
    }

    /** Returns the last item. */
    public int peek(){
        return items[size - 1];
    }

    /** Returns the first item. */
    public int first(){
        checkNotEmpty();
        return items[0];
    }

    /** Returns the smallest item. */
    public int min(){
        checkNotEmpty();
        return items[minIndex()];
    }

    /** Returns the largest item. */
    public int max(){
        checkNotEmpty();
        return items[maxIndex()];
    }

    /**
     * Reduces the size of the backing array to the size of the actual items.
     * This is useful to release memory when many items have been removed,
     * or if it is known that more items will not be added.
     * @return {@link #items}
     */
    public int[] shrink(){
        if(items.length != size) resize(size);
        return items;
    }

    /**
     * Increases the size of the backing array to accommodate the specified number of additional items.
     * Useful before adding many items to avoid multiple backing array resizes.
     * @return {@link #items}
     */
    public int[] ensureCapacity(int additionalCapacity){
        reserve(additionalCapacity);
        return items;
    }

    /**
     * Sets the array size, leaving any values beyond the current size undefined.
     * @return {@link #items}
     */
    public int[] setSize(int newSize){
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
        int[] newItems = new int[newSize];
        int[] items = this.items;
        System.arraycopy(items, 0, newItems, 0, Math.min(size, newItems.length));
        this.items = newItems;
    }

    @Override
    protected IntSeq create(int capacity){
        return new IntSeq(ordered, capacity);
    }

    @Override
    protected boolean sameType(Object other){
        return other instanceof IntSeq;
    }

    @Override
    public IntSeqIterator newIterator(){
        return new IntSeqIterator(this);
    }

    @Override
    protected void swapItems(int first, int second){
        int[] items = this.items;
        int value = items[first];
        items[first] = items[second];
        items[second] = value;
    }

    @Override
    protected int compareItems(int first, int second){
        return Integer.compare(items[first], items[second]);
    }

    @Override
    protected void sortItems(int from, int to){
        Arrays.sort(items, from, to);
    }

    @Override
    protected void freeItems(int from, int to){
    }

    @Override
    protected boolean equalItem(IntSeq other, int otherIndex, int index){
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
    public int random(){
        if(size == 0) return 0;
        return items[Mathf.random(0, size - 1)];
    }

    public int[] toArray(){
        return Arrays.copyOf(items, size);
    }


    public static class IntSeqIterator extends SeqIterator<Integer>{
        protected final IntSeq seq;

        public IntSeqIterator(IntSeq seq){
            super(seq);
            this.seq = seq;
        }

        @Override
        public Integer next(){
            return nextInt();
        }

        public int nextInt(){
            return seq.items[advance()];
        }
    }
}
