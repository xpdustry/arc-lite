package arc.struct;

import arc.func.*;

import java.util.*;
import arc.util.*;

/**
 * An unordered set where the keys are objects.
 * Null keys are not allowed.
 * No allocation is done except when growing the table size.
 * <p>
 * The table uses linear probing, with a backward shift on removal, and Fibonacci hashing to spread poor hashcodes.
 * So add/contains/remove are typically O(1) with a worst case of O(n) when many keys collide into the same cluster.
 * Load factors greater than 0.91 greatly increase the chances of resizing to the next higher power of two.
 *
 * @author Nathan Sweet
 */
public class ObjectSet<T> extends HashTable<ObjectSet.ObjectSetIterator<T>, ObjectSet<T>> implements Iterable<T>, Eachable<T>{
    protected T[] keyTable;

    /** Creates a new set with an initial capacity of 51 and a load factor of 0.8. */
    public ObjectSet(){
        this(51, 0.8f);
    }

    /**
     * Creates a new set with a load factor of 0.8.
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    public ObjectSet(int initialCapacity){
        this(initialCapacity, 0.8f);
    }

    /**
     * Creates a new set with the specified initial capacity and load factor.
     * This set will hold initialCapacity items before growing the backing table.
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    public ObjectSet(int initialCapacity, float loadFactor){
        super(initialCapacity, loadFactor);
        keyTable = Reflect.newArray(mask + 1);
    }

    /** Creates a new set identical to the specified set. */
    public ObjectSet(ObjectSet<? extends T> set){
        this(set.capacity(), set.loadFactor);
        copyTable(set);
    }

    @SafeVarargs
    public static <T> ObjectSet<T> with(T... array){
        ObjectSet<T> set = new ObjectSet<>();
        set.addAll(array);
        return set;
    }

    public static <T> ObjectSet<T> with(Seq<T> array){
        ObjectSet<T> set = new ObjectSet<>();
        set.addAll(array);
        return set;
    }

    @Override
    public ObjectSet<T> copy(){
        ObjectSet<T> result = new ObjectSet<>();
        result.addAll(this);
        return result;
    }

    /** Allocates a new set with all elements that match the predicate. */
    public ObjectSet<T> select(Boolf<T> predicate){
        ObjectSet<T> arr = new ObjectSet<>();
        for(T t : this){
            if(predicate.get(t)) arr.add(t);
        }
        return arr;
    }

    public Seq<T> toSeq(){
        return iterator().toSeq();
    }

    @Override
    public void each(Cons<? super T> cons){
        for(T t : this){
            cons.get(t);
        }
    }

    /** Removes the items matching the predicate. */
    public ObjectSet<T> removeAll(Boolf<T> pred){
        for(ObjectSetIterator<T> iterator = iterator(); iterator.hasNext();){
            if(pred.get(iterator.next())) iterator.remove();
        }
        return this;
    }

    /** @return the number of items matching the predicate. */
    public int count(Boolf<T> pred){
        int count = 0;
        for(T key : keyTable){
            if(key != null && pred.get(key)) count++;
        }
        return count;
    }

    /** @return whether all the items match the predicate. */
    public boolean allMatch(Boolf<T> pred){
        for(T key : keyTable){
            if(key != null && !pred.get(key)) return false;
        }
        return true;
    }

    public T find(Boolf<T> predicate){
        return Structs.find(this, predicate);
    }

    @Override
    public boolean any(){
        return size > 0;
    }

    /**
     * Returns true if the key was added to the set or false if it was already in the set.
     * If this set already contains the key, the call leaves the set unchanged and returns false.
     */
    public boolean add(T key){
        if(key == null) return false;
        int i = locateKey(key);
        if(i >= 0) return false; // Existing key was found.
        i = -(i + 1); // Empty space was found.
        keyTable[i] = key;
        if(++size >= threshold) resize(keyTable.length << 1);
        return true;
    }

    public void addAll(Seq<? extends T> array){
        addAll(array.items, 0, array.size);
    }

    public void addAll(Seq<? extends T> array, int offset, int length){
        if(offset + length > array.size)
            throw new IllegalArgumentException("offset + length must be <= size: " + offset + " + " + length + " <= " + array.size);
        addAll(array.items, offset, length);
    }

    @SuppressWarnings("unchecked")
    public boolean addAll(T... array){
        return addAll(array, 0, array.length);
    }

    public boolean addAll(T[] array, int offset, int length){
        ensureCapacity(length);
        int oldSize = size;
        for(int i = offset, n = i + length; i < n; i++){
            T value = array[i];
            if(value != null) add(value);
        }
        return oldSize != size;
    }

    public void addAll(ObjectSet<? extends T> set){
        addEntries(set);
    }

    /** Skips checks for existing keys, doesn't increment size. */
    protected void addResize(T key){
        keyTable[emptyIndex(place(key))] = key;
    }

    public void removeAll(T[] array, int offset, int length){
        for(int i = offset, n = i + length; i < n; i++)
            remove(array[i]);
    }

    public void removeAll(T[] array){
        Structs.each(this::remove, array);
    }

    public void removeAll(Seq<? extends T> array){
        removeAll(array.items, 0, array.size);
    }

    /** Returns true if the key was removed. */
    public boolean remove(T key){
        if(key == null) return false;
        int i = locateKey(key);
        if(i < 0) return false;
        removeAt(i);
        return true;
    }

    /** Returns whether the key is in the set. */
    public boolean contains(T key){
        return key != null && locateKey(key) >= 0;
    }

    public @Nullable T get(T key){
        if(key == null) return null;
        int i = locateKey(key);
        return i < 0 ? null : keyTable[i];
    }

    public T first(){
        T[] keyTable = this.keyTable;
        for(T element : keyTable) if(element != null) return element;
        throw new IllegalStateException("ObjectSet is empty.");
    }

    @Override
    protected Object keyTable(){
        return keyTable;
    }

    @Override
    protected void resize(int newSize){
        int oldCapacity = keyTable.length;
        setTableSize(newSize);
        T[] oldKeyTable = keyTable;

        keyTable = Reflect.newArray(newSize);

        if(size > 0){
            for(int i = 0; i < oldCapacity; i++){
                T key = oldKeyTable[i];
                if(key != null) addResize(key);
            }
        }
    }

    @Override
    protected boolean sameType(Object other){
        return other instanceof ObjectSet;
    }

    @Override
    public ObjectSetIterator<T> newIterator(){
        return new ObjectSetIterator<>(this);
    }

    @Override
    protected boolean occupied(int index){
        return keyTable[index] != null;
    }

    @Override
    protected void clearEntries(int from, int to){
        Arrays.fill(keyTable, from, to, null);
    }

    @Override
    protected int placeIndex(int index){
        return place(keyTable[index]);
    }

    @Override
    protected boolean hasEntry(ObjectSet<T> other, int index, boolean onlyKeys){
        return other.contains(keyTable[index]);
    }

    @Override
    protected void appendEntry(StringBuilder buffer, int index){
        appendObject(buffer, keyTable[index]);
    }

    @Override
    protected int hashEntry(int index){
        return keyTable[index].hashCode();
    }

    @Override
    protected void addEntry(ObjectSet<T> other, int index){
        add(other.keyTable[index]);
    }


    public static class ObjectSetIterator<K> extends TableIterator<K, ObjectSetIterator<K>>{
        protected final ObjectSet<K> set;

        public ObjectSetIterator(ObjectSet<K> set){
            super(set);
            this.set = set;
            reset();
        }

        @Override
        protected void findNextIndex(){
            findNextIndex(set);
        }

        public K next(){
            int i = advance();
            return set.keyTable[i];
        }

        /** Returns a new sequence with the remaining values. */
        public Seq<K> toSeq(){
            return toSeq(new Seq<>(true, set.size));
        }
    }
}
