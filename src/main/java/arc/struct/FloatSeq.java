package arc.struct;

import arc.func.Floatc;
import arc.math.Mathf;
import arc.math.geom.Vec2;

import java.util.*;

/**
 * A resizable, ordered or unordered float array. Avoids the boxing that occurs with ArrayList<Float>.
 * If unordered, this class avoids a memory copy when removing elements
 * (the last element is moved to the removed element's position).
 * @author Nathan Sweet
 */
public class FloatSeq extends BaseSeq<FloatSeq.FloatSeqIterator, FloatSeq> implements Iterable<Float>{
    public float[] items;

    /** Creates an ordered array with a capacity of 16. */
    public FloatSeq(){
        this(true, 16);
    }

    /** Creates an ordered array with the specified capacity. */
    public FloatSeq(int capacity){
        this(true, capacity);
    }

    /**
     * @param ordered If false, methods that remove elements may change the order of other elements in the array,
     *                which avoids a memory copy.
     * @param capacity Any elements added beyond this will cause the backing array to be grown.
     */
    public FloatSeq(boolean ordered, int capacity){
        this.ordered = ordered;
        items = new float[capacity];
    }

    /**
     * Creates a new array containing the elements in the specific array.
     * The new array will be ordered if the specific array is ordered.
     * The capacity is set to the number of elements, so any subsequent elements added will cause
     * the backing array to be grown.
     */
    public FloatSeq(FloatSeq array){
        this(array.ordered, array.size);
        addAll(array);
    }

    /**
     * Creates a new ordered array containing the elements in the specified array.
     * The capacity is set to the number of elements, so any subsequent elements added will cause
     * the backing array to be grown.
     */
    public FloatSeq(float[] array){
        this(true, array, 0, array.length);
    }

    /**
     * Creates a new array containing the elements in the specified array.
     * The capacity is set to the number of elements, so any subsequent elements added will cause
     * the backing array to be grown.
     * @param ordered If false, methods that remove elements may change the order of other elements in the array,
     *                which avoids a memory copy.
     */
    public FloatSeq(boolean ordered, float[] array, int startIndex, int count){
        this(ordered, count);
        addItems(array, startIndex, count);
    }

    /** @see #FloatSeq(float[]) */
    public static FloatSeq with(float... array){
        return new FloatSeq(array);
    }

    public void each(Floatc consumer){
        for(int i = 0, n = size; i < n; i++){
            consumer.get(items[i]);
        }
    }

    /** Converts this float array to a Vec2 array, with pairs used for coordinates.*/
    public Seq<Vec2> toVec2Array(){
        Seq<Vec2> out = new Seq<>(size/2);
        for(int i = 0; i < size; i+= 2){
            out.add(new Vec2(items[i], items[i+1]));
        }
        return out;
    }

    public void add(float value){
        grow(1);
        items[size++] = value;
    }

    public void add(float value1, float value2){
        grow(2);
        items[size] = value1;
        items[size + 1] = value2;
        size += 2;
    }

    public void add(float value1, float value2, float value3){
        grow(3);
        items[size] = value1;
        items[size + 1] = value2;
        items[size + 2] = value3;
        size += 3;
    }

    public void add(float value1, float value2, float value3, float value4){
        grow(4);
        items[size] = value1;
        items[size + 1] = value2;
        items[size + 2] = value3;
        items[size + 3] = value4;
        size += 4;
    }

    public void addAll(float... array){
        addAll(array, 0, array.length);
    }

    public void addAll(float[] array, int offset, int length){
        addItems(array, offset, length);
    }

    public float get(int index){
        checkIndex(index);
        return items[index];
    }

    public void set(int index, float value){
        checkIndex(index);
        beforeModify();
        items[index] = value;
    }

    public void incr(int index, float value){
        checkIndex(index);
        beforeModify();
        items[index] += value;
    }

    public void mul(int index, float value){
        checkIndex(index);
        beforeModify();
        items[index] *= value;
    }

    public void insert(int index, float value){
        checkInsert(index);
        insertAt(index);
        items[index] = value;
    }

    public boolean contains(float value){
        return indexOf(value) != -1;
    }

    public int indexOf(float value){
        float[] items = this.items;
        for(int i = 0, n = size; i < n; i++)
            if(items[i] == value) return i;
        return -1;
    }

    public int lastIndexOf(float value){
        float[] items = this.items;
        for(int i = size - 1; i >= 0; i--)
            if(items[i] == value) return i;
        return -1;
    }

    public boolean removeValue(float value){
        int index = indexOf(value);
        if(index == -1) return false;
        removeIndex(index);
        return true;
    }

    /** Removes and returns the item at the specified index. */
    public float removeIndex(int index){
        checkIndex(index);
        float value = items[index];
        removeAt(index);
        return value;
    }

    /** Removes and returns the last item. */
    public float pop(){
        beforeModify();
        return items[--size];
    }

    /** Returns the last item. */
    public float peek(){
        return items[size - 1];
    }

    /** Returns the first item. */
    public float first(){
        checkNotEmpty();
        return items[0];
    }

    /** Returns the smallest item. */
    public float min(){
        checkNotEmpty();
        return items[minIndex()];
    }

    /** Returns the largest item. */
    public float max(){
        checkNotEmpty();
        return items[maxIndex()];
    }

    public float sum(){
        float s = 0;
        for(int i = 0; i < size; i++){
            s += items[i];
        }
        return s;
    }

    /**
     * Reduces the size of the backing array to the size of the actual items.
     * This is useful to release memory when many items have been removed,
     * or if it is known that more items will not be added.
     * @return {@link #items}
     */
    public float[] shrink(){
        if(items.length != size) resize(size);
        return items;
    }

    /**
     * Increases the size of the backing array to accommodate the specified number of additional items.
     * Useful before adding many items to avoid multiple backing array resizes.
     * @return {@link #items}
     */
    public float[] ensureCapacity(int additionalCapacity){
        reserve(additionalCapacity);
        return items;
    }

    /**
     * Sets the array size, leaving any values beyond the current size undefined.
     * @return {@link #items}
     */
    public float[] setSize(int newSize){
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
        float[] newItems = new float[newSize];
        float[] items = this.items;
        System.arraycopy(items, 0, newItems, 0, Math.min(size, newItems.length));
        this.items = newItems;
    }

    @Override
    protected FloatSeq create(int capacity){
        return new FloatSeq(ordered, capacity);
    }

    @Override
    protected boolean sameType(Object other){
        return other instanceof FloatSeq;
    }

    @Override
    public FloatSeqIterator newIterator(){
        return new FloatSeqIterator(this);
    }

    @Override
    protected void swapItems(int first, int second){
        float[] items = this.items;
        float value = items[first];
        items[first] = items[second];
        items[second] = value;
    }

    @Override
    protected int compareItems(int first, int second){
        return Float.compare(items[first], items[second]);
    }

    @Override
    protected void sortItems(int from, int to){
        Arrays.sort(items, from, to);
    }

    @Override
    protected void freeItems(int from, int to){
    }

    @Override
    protected boolean equalItem(FloatSeq other, int otherIndex, int index){
        return items[index] == other.items[otherIndex];
    }

    @Override
    protected void appendItem(StringBuilder buffer, int index){
        buffer.append(items[index]);
    }

    @Override
    protected int hashItem(int index){
        return Float.floatToIntBits(items[index]);
    }

    /** Returns a random item from the array, or zero if the array is empty. */
    public float random(){
        if(size == 0) return 0;
        return items[Mathf.random(0, size - 1)];
    }

    public float[] toArray(){
        return Arrays.copyOf(items, size);
    }

    public boolean equals(Object object, float epsilon){
        if(object == this) return true;
        if(!(object instanceof FloatSeq)) return false;
        FloatSeq array = (FloatSeq)object;
        int n = size;
        if(n != array.size) return false;
        if(!ordered) return false;
        if(!array.ordered) return false;
        float[] items1 = this.items;
        float[] items2 = array.items;
        for(int i = 0; i < n; i++)
            if(Math.abs(items1[i] - items2[i]) > epsilon) return false;
        return true;
    }


    public static class FloatSeqIterator extends SeqIterator<Float>{
        protected final FloatSeq seq;

        public FloatSeqIterator(FloatSeq seq){
            super(seq);
            this.seq = seq;
        }

        @Override
        public Float next(){
            return nextFloat();
        }

        public float nextFloat(){
            return seq.items[advance()];
        }
    }
}
