package arc.struct;

import arc.func.*;
import arc.math.*;
import arc.util.*;

import java.util.*;

/**
 * A resizable, ordered or unordered array of objects.
 * If unordered, this class avoids a memory copy when removing elements
 * (the last element is moved to the removed element's position).
 * @author Nathan Sweet
 */
@SuppressWarnings("unchecked")
public class Seq<T> extends BaseSeq<Seq.SeqIterator<T>, Seq<T>> implements Iterable<T>, Eachable<T>{
    /** Debugging variable to count total number of iterators allocated. */
    public static int iteratorsAllocated = 0;
    /**
     * Provides direct access to the underlying array.
     * If the Array's generic type is not Object, this field may only be accessed
     * if the {@link Seq#Seq(boolean, int, Class)} constructor was used.
     */
    public T[] items;

    /** Creates an ordered array with a capacity of 16. */
    public Seq(){
        this(true, 16);
    }

    /** Creates an ordered array with the specified capacity. */
    public Seq(int capacity){
        this(true, capacity);
    }

    /** Creates an ordered/unordered array with the specified capacity. */
    public Seq(boolean ordered){
        this(ordered, 16);
    }

    /**
     * @param ordered If false, methods that remove elements may change the order of other elements in the array,
     *                which avoids a memory copy.
     * @param capacity Any elements added beyond this will cause the backing array to be grown.
     */
    public Seq(boolean ordered, int capacity){
        this.ordered = ordered;
        items = Reflect.newArray(capacity);
    }

    /**
     * Creates a new array with {@link #items} of the specified type.
     * @param ordered If false, methods that remove elements may change the order of other elements in the array,
     *                which avoids a memory copy.
     * @param capacity Any elements added beyond this will cause the backing array to be grown.
     */
    public Seq(boolean ordered, int capacity, Class<?> arrayType){
        this.ordered = ordered;
        items = Reflect.newArray((Class<T>)arrayType, capacity);
    }

    /** Creates an ordered array with {@link #items} of the specified type and a capacity of 16. */
    public Seq(Class<?> arrayType){
        this(true, 16, arrayType);
    }

    /**
     * Creates a new array containing the elements in the specified array.
     * The new array will have the same type of backing array and will be ordered if the specified array is ordered.
     * The capacity is set to the number of elements, so any subsequent elements added will cause the backing array to be grown.
     */
    public Seq(Seq<? extends T> array){
        this(array.ordered, array.size, array.items.getClass().getComponentType());
        addItems(array.items, 0, array.size);
    }

    /**
     * Creates a new ordered array containing the elements in the specified array.
     * The new array will have the same type of backing array.
     * The capacity is set to the number of elements, so any subsequent elements added will cause the backing array to be grown.
     */
    public Seq(T[] array){
        this(true, array, 0, array.length);
    }

    /**
     * Creates a new array containing the elements in the specified array.
     * The new array will have the same type of backing array.
     * The capacity is set to the number of elements, so any subsequent elements added will cause the backing array to be grown.
     * @param ordered If false, methods that remove elements may change the order of other elements in the array,
     *                which avoids a memory copy.
     */
    public Seq(boolean ordered, T[] array, int start, int count){
        this(ordered, count, array.getClass().getComponentType());
        addItems(array, start, count);
    }

    public static <T> Seq<T> withArrays(Object... arrays){
        Seq<T> result = new Seq<>();
        for(Object a : arrays){
            if(a instanceof Seq){
                result.addAll((Seq<? extends T>)a);
            }else{
                result.add((T)a);
            }
        }
        return result;
    }

    /** @see #Seq(Object[]) */
    @SafeVarargs
    public static <T> Seq<T> with(T... array){
        return new Seq<>(array);
    }

    public static <T> Seq<T> with(Iterable<T> array){
        return new Seq<T>().addAll(array);
    }

    public static <T, V> Seq<V> map(T[] array, Func<T, V> mapper){
        Seq<V> result = new Seq<>(array.length);
        for(T element : array){
            result.add(mapper.get(element));
        }
        return result;
    }

    /** @see #Seq(Object[]) */
    public static <T> Seq<T> select(T[] array, Boolf<T> test){
        Seq<T> out = new Seq<>(array.length);
        for(T t : array){
            if(test.get(t)){
                out.add(t);
            }
        }
        return out;
    }

    public <K, V> ObjectMap<K, V> asMap(Func<T, K> keygen, Func<T, V> valgen){
        ObjectMap<K, V> map = new ObjectMap<>();
        for(int i = 0; i < size; i++){
            map.put(keygen.get(items[i]), valgen.get(items[i]));
        }
        return map;
    }

    public <K> ObjectMap<K, Seq<T>> groupBy(Func<T, K> keygen){
        ObjectMap<K, Seq<T>> map = new ObjectMap<>();
        for(int i = 0; i < size; i++){
            T item = items[i];
            map.get(keygen.get(item), Seq::new).add(item);
        }
        return map;
    }

    public <K> ObjectIntMap<K> groupByCount(Func<T, K> keygen){
        ObjectIntMap<K> map = new ObjectIntMap<>();
        for(int i = 0; i < size; i++){
            map.increment(keygen.get(items[i]));
        }
        return map;
    }

    public <K> ObjectMap<K, T> asMap(Func<T, K> keygen){
        return asMap(keygen, Structs::self);
    }

    public ObjectSet<T> asSet(){
        return ObjectSet.with(this);
    }

    public ArrayList<T> list(){
        ArrayList<T> list = new ArrayList<>(size);
        each(list::add);
        return list;
    }

    public float sumf(Floatf<T> summer){
        float sum = 0;
        for(int i = 0; i < size; i++){
            sum += summer.get(items[i]);
        }
        return sum;
    }

    public int sum(Intf<T> summer){
        int sum = 0;
        for(int i = 0; i < size; i++){
            sum += summer.get(items[i]);
        }
        return sum;
    }

    /** Gives the items by chunks of at most {@code chunkSize}. */
    public void chunked(int chunkSize, Cons<Seq<T>> cons){
        if(chunkSize < 1) throw new IllegalArgumentException("chunkSize must be at least 1: " + chunkSize);
        for(int i = 0; i < size; i += chunkSize){
            int count = Math.min(chunkSize, size - i);
            cons.get(new Seq<T>(ordered, count, items.getClass().getComponentType()).addAll(items, i, count));
        }
    }

    public <E extends T> void each(Boolf<? super T> pred, Cons<E> consumer){
        int size = this.size;
        T[] items = this.items;
        for(int i = 0; i < size; i++){
            if(pred.get(items[i])) consumer.get((E)items[i]);
        }
    }

    @Override
    public void each(Cons<? super T> consumer){
        int size = this.size;
        T[] items = this.items;
        for(int i = 0; i < size; i++){
            consumer.get(items[i]);
        }
    }

    /** Replaces values without creating a new array. */
    public void replace(Func<T, T> mapper){
        beforeModify();
        for(int i = 0; i < size; i++){
            items[i] = mapper.get(items[i]);
        }
    }

    /** Flattens this array of arrays into one array. Allocates a new instance. */
    public <R> Seq<R> flatten(){
        Seq<R> arr = new Seq<>();
        for(int i = 0; i < size; i++){
            arr.addAll((Seq<R>)items[i]);
        }
        return arr;
    }

    /** Returns a new array with the mapped values. */
    public <R> Seq<R> flatMap(Func<T, Iterable<R>> mapper){
        Seq<R> arr = new Seq<>(size);
        for(int i = 0; i < size; i++){
            arr.addAll(mapper.get(items[i]));
        }
        return arr;
    }

    /** Returns a new array with the mapped values. */
    public <R> Seq<R> map(Func<T, R> mapper){
        Seq<R> arr = new Seq<>(size);
        for(int i = 0; i < size; i++){
            arr.add(mapper.get(items[i]));
        }
        return arr;
    }

    /** @return a new int array with the mapped values. */
    public IntSeq mapInt(Intf<T> mapper){
        IntSeq arr = new IntSeq(size);
        for(int i = 0; i < size; i++){
            arr.add(mapper.get(items[i]));
        }
        return arr;
    }

    /** @return a new int array with the mapped values. */
    public IntSeq mapInt(Intf<T> mapper, Boolf<T> retain){
        IntSeq arr = new IntSeq(size);
        for(int i = 0; i < size; i++){
            T item = items[i];
            if(retain.get(item)){
                arr.add(mapper.get(item));
            }
        }
        return arr;
    }

    /** @return a new float array with the mapped values. */
    public FloatSeq mapFloat(Floatf<T> mapper){
        FloatSeq arr = new FloatSeq(size);
        for(int i = 0; i < size; i++){
            arr.add(mapper.get(items[i]));
        }
        return arr;
    }

    public <R> R reduce(R initial, Func2<T, R, R> reducer){
        R result = initial;
        for(int i = 0; i < size; i++){
            result = reducer.get(items[i], result);
        }
        return result;
    }

    public boolean allMatch(Boolf<T> predicate){
        for(int i = 0; i < size; i++){
            if(!predicate.get(items[i])){
                return false;
            }
        }
        return true;
    }

    public boolean contains(Boolf<T> predicate){
        return indexOf(predicate) != -1;
    }

    public T min(){
        return size == 0 ? null : items[minIndex()];
    }

    public T max(){
        return size == 0 ? null : items[maxIndex()];
    }

    public T min(Comparator<T> func){
        return min(Structs.always(), func);
    }

    public T max(Comparator<T> func){
        T result = null;
        for(int i = 0; i < size; i++){
            T t = items[i];
            if(result == null || func.compare(result, t) < 0){
                result = t;
            }
        }
        return result;
    }

    public T min(Boolf<T> filter, Floatf<T> func){
        T result = null;
        float min = Float.MAX_VALUE;
        for(int i = 0; i < size; i++){
            T t = items[i];
            if(!filter.get(t)) continue;
            float val = func.get(t);
            if(val <= min){
                result = t;
                min = val;
            }
        }
        return result;
    }

    public T min(Boolf<T> filter, Comparator<T> func){
        T result = null;
        for(int i = 0; i < size; i++){
            T t = items[i];
            if(filter.get(t) && (result == null || func.compare(result, t) > 0)){
                result = t;
            }
        }
        return result;
    }

    public T min(Floatf<T> func){
        return min(Structs.always(), func);
    }

    public T max(Boolf<T> filter, Floatf<T> func){
        T result = null;
        float max = Float.NEGATIVE_INFINITY;
        for(int i = 0; i < size; i++){
            T t = items[i];
            if(!filter.get(t)) continue;
            float val = func.get(t);
            if(val >= max){
                result = t;
                max = val;
            }
        }
        return result;
    }

    public T max(Floatf<T> func){
        return max(Structs.always(), func);
    }

    public @Nullable T find(Boolf<T> predicate){
        int index = indexOf(predicate);
        return index == -1 ? null : items[index];
    }

    /**
     * Adds a value if it was not already in this sequence.
     * @return whether this value was added successfully.
     */
    public boolean addUnique(T value){
        if(!contains(value)){
            add(value);
            return true;
        }
        return false;
    }

    public Seq<T> add(T value){
        grow(1);
        items[size++] = value;
        return this;
    }

    public Seq<T> add(T value1, T value2){
        grow(2);
        items[size] = value1;
        items[size + 1] = value2;
        size += 2;
        return this;
    }

    public Seq<T> add(T value1, T value2, T value3){
        grow(3);
        items[size] = value1;
        items[size + 1] = value2;
        items[size + 2] = value3;
        size += 3;
        return this;
    }

    public Seq<T> add(T value1, T value2, T value3, T value4){
        grow(4);
        items[size] = value1;
        items[size + 1] = value2;
        items[size + 2] = value3;
        items[size + 3] = value4;
        size += 4;
        return this;
    }

    public Seq<T> add(Seq<? extends T> array){
        addAll(array.items, 0, array.size);
        return this;
    }

    public Seq<T> add(T[] array){
        addAll(array, 0, array.length);
        return this;
    }

    public Seq<T> addAll(Seq<? extends T> array){
        addAll(array.items, 0, array.size);
        return this;
    }

    public Seq<T> addAll(Seq<? extends T> array, int start, int count){
        checkRange(array, start, count);
        addAll(array.items, start, count);
        return this;
    }

    public Seq<T> addAll(T... array){
        addAll(array, 0, array.length);
        return this;
    }

    public Seq<T> addAll(T[] array, int start, int count){
        addItems(array, start, count);
        return this;
    }

    public Seq<T> addAll(Iterable<? extends T> items){
        if(items instanceof Seq){
            addAll((Seq<? extends T>)items);
        }else{
            for(T t : items){
                add(t);
            }
        }
        return this;
    }

    /** Sets this array's contents to the specified array. */
    public void set(Seq<? extends T> array){
        if(array == this) return;
        clear();
        addAll(array);
    }

    /** Sets this array's contents to the specified array. */
    public void set(T[] array){
        clear();
        addAll(array);
    }

    @Nullable
    public T getFrac(float index){
        if(isEmpty()) return null;
        return get(Mathf.clamp((int)(index * size), 0, size - 1));
    }

    public T get(int index){
        checkIndex(index);
        return items[index];
    }

    public void set(int index, T value){
        checkIndex(index);
        beforeModify();
        items[index] = value;
    }

    public void insert(int index, T value){
        checkInsert(index);
        insertAt(index);
        items[index] = value;
    }

    /**
     * Replaces the first occurrence of 'from' with 'to'.
     * @return whether anything was replaced.
     * */
    public boolean replace(T from, T to){
        int idx = indexOf(from);
        if(idx != -1){
            beforeModify();
            items[idx] = to;
            return true;
        }
        return false;
    }

    /** @return whether this sequence contains every other element in the other sequence. */
    public boolean containsAll(Seq<T> seq, boolean identity){
        T[] others = seq.items;

        for(int i = 0; i < seq.size; i++){
            if(!contains(others[i], identity)){
                return false;
            }
        }
        return true;
    }

    public boolean contains(T value){
        return contains(value, false);
    }

    /**
     * Returns if this array contains value.
     * @param value May be null.
     * @param identity If true, == comparison will be used. If false, .equals() comparison will be used.
     * @return true if array contains value, false if it doesn't
     */
    public boolean contains(T value, boolean identity){
        return indexOf(value, identity) != -1;
    }

    public int indexOf(T value){
        return indexOf(value, false);
    }

    /**
     * Returns the index of first occurrence of value in the array, or -1 if no such value exists.
     * @param value May be null.
     * @param identity If true, == comparison will be used. If false, .equals() comparison will be used.
     * @return An index of first occurrence of value in array or -1 if no such value exists
     */
    public int indexOf(T value, boolean identity){
        T[] items = this.items;
        if(identity || value == null){
            for(int i = 0, n = size; i < n; i++)
                if(items[i] == value) return i;
        }else{
            for(int i = 0, n = size; i < n; i++)
                if(value.equals(items[i])) return i;
        }
        return -1;
    }

    public int indexOf(Boolf<T> value){
        T[] items = this.items;
        for(int i = 0, n = size; i < n; i++)
            if(value.get(items[i])) return i;
        return -1;
    }

    /**
     * Returns an index of last occurrence of value in array or -1 if no such value exists.
     * Search is started from the end of an array.
     * @param value May be null.
     * @param identity If true, == comparison will be used. If false, .equals() comparison will be used.
     * @return An index of last occurrence of value in array or -1 if no such value exists
     */
    public int lastIndexOf(T value, boolean identity){
        T[] items = this.items;
        if(identity || value == null){
            for(int i = size - 1; i >= 0; i--)
                if(items[i] == value) return i;
        }else{
            for(int i = size - 1; i >= 0; i--)
                if(value.equals(items[i])) return i;
        }
        return -1;
    }

    /** Removes a value, without using identity. */
    public boolean remove(T value){
        return remove(value, false);
    }

    /** Removes a single value by predicate.
     * @return whether the item was found and removed. */
    public boolean remove(Boolf<T> value){
        int index = indexOf(value);
        if(index == -1) return false;
        remove(index);
        return true;
    }

    /**
     * Removes the first instance of the specified value in the array.
     * @param value May be null.
     * @param identity If true, == comparison will be used. If false, .equals() comparison will be used.
     * @return true if value was found and removed, false otherwise
     */
    public boolean remove(T value, boolean identity){
        int index = indexOf(value, identity);
        if(index == -1) return false;
        remove(index);
        return true;
    }

    /** Removes and returns the item at the specified index. */
    public T remove(int index){
        checkIndex(index);
        T value = items[index];
        removeAt(index);
        return value;
    }

    /** @return this object */
    public Seq<T> removeAll(Boolf<T> pred){
        beforeModify();
        T[] items = this.items;
        if(!ordered){
            for(int i = 0; i < size; ++i){
                if(pred.get(items[i])) remove(i--);
            }
            return this;
        }

        int newIdx = -1;
        int size = this.size;
        for(int i = 0; i < size; ++i){
            if(!pred.get(items[i]) && ++newIdx != i){
                items[newIdx] = items[i];
            }
        }
        for(int i = ++newIdx; i < size; ++i){
            items[i] = null;
        }
        this.size = newIdx;
        return this;
    }

    public boolean removeAll(Seq<? extends T> array){
        return removeAll(array, false);
    }

    /**
     * Removes from this array all of elements contained in the specified array.
     * @param identity True to use ==, false to use .equals().
     * @return true if this array was modified.
     */
    public boolean removeAll(Seq<? extends T> array, boolean identity){
        boolean removed = false;
        for(int i = 0, n = array.size; i < n; i++){
            int index = indexOf(array.get(i), identity);
            if(index == -1) continue;
            remove(index);
            removed = true;
        }
        return removed;
    }

    /** If this array is empty, returns an object specified by the constructor. Otherwise, acts like pop(). */
    public T pop(Prov<T> constructor){
        if(size == 0) return constructor.get();
        return pop();
    }

    /** Removes and returns the last item. */
    public T pop(){
        checkNotEmpty();
        beforeModify();
        --size;
        T item = items[size];
        items[size] = null;
        return item;
    }

    /** Returns the last item. */
    public T peek(){
        checkNotEmpty();
        return items[size - 1];
    }

    /** Returns the first item. */
    public T first(){
        checkNotEmpty();
        return items[0];
    }

    /** Returns the first item, or null if this Seq is empty. */
    @Nullable
    public T firstOpt(){
        if(size == 0) return null;
        return items[0];
    }

    /**
     * Reduces the size of the backing array to the size of the actual items.
     * This is useful to release memory when many items have been removed,
     * or if it is known that more items will not be added.
     * @return {@link #items}
     */
    public T[] shrink(){
        if(items.length != size) resize(size);
        return items;
    }

    /**
     * Increases the size of the backing array to accommodate the specified number of additional items.
     * Useful before adding many items to avoid multiple backing array resizes.
     * @return {@link #items}
     */
    public T[] ensureCapacity(int additionalCapacity){
        reserve(additionalCapacity);
        return items;
    }

    /**
     * Sets the array size, leaving any values beyond the current size null.
     * @return {@link #items}
     */
    public T[] setSize(int newSize){
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

    /** Creates a new backing array with the specified size containing the current items. */
    @Override
    protected void resize(int newSize){
        T[] items = this.items;
        //avoid reflection when possible
        T[] newItems = items.getClass() == Object[].class ? Reflect.newArray(newSize) :
                       Reflect.newArray(items, newSize);
        System.arraycopy(items, 0, newItems, 0, Math.min(size, newItems.length));
        this.items = newItems;
    }

    @Override
    protected Seq<T> create(int capacity){
        return items.getClass() == Object[].class ? new Seq<>(ordered, capacity) :
               new Seq<>(ordered, capacity, items.getClass().getComponentType());
    }

    @Override
    protected boolean sameType(Object other){
        return other instanceof Seq;
    }

    @Override
    public SeqIterator<T> newIterator(){
        return new SeqIterator<>(this);
    }

    @Override
    protected void swapItems(int first, int second){
        T item = items[first];
        items[first] = items[second];
        items[second] = item;
    }

    @Override
    protected int compareItems(int first, int second){
        return ((Comparable<Object>)items[first]).compareTo(items[second]);
    }

    @Override
    protected void sortItems(int from, int to){
        Sort.instance().sort(items, from, to);
    }

    @Override
    protected void freeItems(int from, int to){
        Arrays.fill(items, from, to, null);
    }

    @Override
    protected boolean equalItem(Seq<T> other, int otherIndex, int index){
        return Structs.eq(items[index], other.items[otherIndex]);
    }

    @Override
    protected void appendItem(StringBuilder buffer, int index){
        buffer.append(items[index]);
    }

    @Override
    protected int hashItem(int index){
        return Structs.hash(items[index]);
    }

    /** Sorts the array. This method is not thread safe (uses {@link Sort#instance()}). */
    public Seq<T> sort(Comparator<? super T> comparator){
        beforeModify();
        Sort.instance().sort(items, comparator, 0, size);
        return this;
    }

    public Seq<T> sort(Floatf<? super T> comparator){
        return sort(Structs.comparingFloat(comparator));
    }

    public <U extends Comparable<? super U>> Seq<T> sortComparing(Func<? super T, ? extends U> keyExtractor){
        sort(Structs.comparing(keyExtractor));
        return this;
    }

    public Seq<T> selectFrom(Seq<T> base, Boolf<T> predicate){
        clear();
        base.each(predicate, this::add);
        return this;
    }

    //TODO: keep this allocating a new list
    /** Note that this allocates a new set. Mutates. */
    @Override
    public Seq<T> distinct(){
        ObjectSet<T> set = asSet();
        clear();
        addAll(set);
        return this;
    }

    public <R> Seq<R> as(){
        return (Seq<R>)this;
    }

    /** Allocates a new array with all elements that match the predicate. */
    public Seq<T> select(Boolf<T> predicate){
        Seq<T> arr = new Seq<>();
        for(int i = 0; i < size; i++){
            if(predicate.get(items[i])){
                arr.add(items[i]);
            }
        }
        return arr;
    }

    /** Removes everything that does not match this predicate. */
    public Seq<T> retainAll(Boolf<T> predicate){
        return removeAll(e -> !predicate.get(e));
    }

    public int count(Boolf<T> predicate){
        int count = 0;
        for(int i = 0; i < size; i++){
            if(predicate.get(items[i])){
                count ++;
            }
        }
        return count;
    }

    /**
     * Selects the nth-lowest element from the Seq according to Comparator ranking.
     * This might partially sort the Array.
     * The array must have a size greater than 0, or a {@link ArcRuntimeException} will be thrown.
     * @param comparator used for comparison
     * @param kthLowest rank of desired object according to comparison, n is based on ordinal numbers, not array indices.
     *                  for min value use 1, for max value use size of array, using 0 results in runtime exception.
     * @return the value of the Nth lowest ranked object.
     * @see Select
     */
    public T selectRanked(Comparator<T> comparator, int kthLowest){
        if(kthLowest < 1){
            throw new ArcRuntimeException("nth_lowest must be greater than 0, 1 = first, 2 = second...");
        }
        return Select.instance().select(items, comparator, kthLowest, size);
    }

    /**
     * @param comparator used for comparison
     * @param kthLowest rank of desired object according to comparison, n is based on ordinal numbers, not array indices.
     *                  for min value use 1, for max value use size of array, using 0 results in runtime exception.
     * @return the index of the Nth lowest ranked object.
     * @see Seq#selectRanked(java.util.Comparator, int)
     */
    public int selectRankedIndex(Comparator<T> comparator, int kthLowest){
        if(kthLowest < 1){
            throw new ArcRuntimeException("nth_lowest must be greater than 0, 1 = first, 2 = second...");
        }
        return Select.instance().selectIndex(items, comparator, kthLowest, size);
    }

    public T random(Rand rand){
        if(size == 0) return null;
        return items[rand.random(0, size - 1)];
    }

    /** Returns a random item from the array, or null if the array is empty. */
    public T random(){
        return random(Mathf.rand);
    }

    /**
     * Returns a random item from the array, excluding the specified element.
     * If the array is empty, returns null. If this array only has one element, returns that element.
     */
    public T random(T exclude){
        if(exclude == null) return random();
        if(size == 0) return null;
        if(size == 1) return first();

        int eidx = indexOf(exclude);
        //this item isn't even in the array!
        if(eidx == -1) return random();

        //shift up the index
        int index = Mathf.random(0, size - 2);
        if(index >= eidx){
            index ++;
        }
        return items[index];
    }

    /**
     * Returns the items as an array. Note the array is typed, so the {@link #Seq(Class)} constructor must have been used.
     * Otherwise use {@link #toArray(Class)} to specify the array type.
     */
    public T[] toArray(){
        return Arrays.copyOf(items, size);
    }

    public <V> V[] toArray(Class<V> type){
        V[] result = Reflect.newArray(type, size);
        System.arraycopy(items, 0, result, 0, size);
        return result;
    }

    public <V> V[] toArray(Class<V> type, Func<T, V> mapper){
        V[] result = Reflect.newArray(type, size);
        for(int i = 0; i < size; i++){
            result[i] = mapper.get(items[i]);
        }
        return result;
    }

    public String toString(String separator, Func<T, String> stringifier){
        if(size == 0) return "";
        T[] items = this.items;
        StringBuilder buffer = new StringBuilder(32);
        buffer.append(stringifier.get(items[0]));
        for(int i = 1; i < size; i++){
            buffer.append(separator);
            buffer.append(stringifier.get(items[i]));
        }
        return buffer.toString();
    }


    public static class SeqIterator<T> extends BaseSeq.SeqIterator<T>{
        protected final Seq<T> seq;

        public SeqIterator(Seq<T> seq){
            super(seq);
            this.seq = seq;
            iteratorsAllocated++;
        }

        @Override
        public T next(){
            return seq.items[advance()];
        }
    }
}
