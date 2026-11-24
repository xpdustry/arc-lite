package arc.struct;

import arc.func.*;
import arc.util.*;

import java.util.*;

/**
 * An unordered map where the keys are objects and the values are ints.
 * Null keys are not allowed.
 * No allocation is done except when growing the table size.
 * <p>
 * The table uses linear probing, with a backward shift on removal, and Fibonacci hashing to spread poor hashcodes.
 * So get/put/contains/remove are typically O(1) with a worst case of O(n) when many keys collide into the same cluster.
 * Load factors greater than 0.91 greatly increase the chances of resizing to the next higher power of two.
 *
 * @author Nathan Sweet
 */
public class ObjectIntMap<K> extends HashTable<ObjectIntMap.Entries<K>, ObjectIntMap<K>> implements Iterable<ObjectIntMap.Entry<K>>{
    protected K[] keyTable;
    protected int[] valueTable;

    protected transient Values values1, values2;
    protected transient Keys<K> keys1, keys2;

    /** Creates a new map with an initial capacity of 51 and a load factor of 0.8. */
    public ObjectIntMap(){
        this(51, 0.8f);
    }

    /**
     * Creates a new map with a load factor of 0.8.
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    public ObjectIntMap(int initialCapacity){
        this(initialCapacity, 0.8f);
    }

    /**
     * Creates a new map with the specified initial capacity and load factor.
     * This map will hold initialCapacity items before growing the backing table.
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    public ObjectIntMap(int initialCapacity, float loadFactor){
        super(initialCapacity, loadFactor);
        keyTable = Reflect.newArray(mask + 1);
        valueTable = new int[mask + 1];
    }

    /** Creates a new map identical to the specified map. */
    public ObjectIntMap(ObjectIntMap<? extends K> map){
        this(map.capacity(), map.loadFactor);
        copyTable(map);
    }

    public void put(K key, int value){
        int i = locateKey(key);
        if(i >= 0){ // Existing key was found.
            beforeModify();
            valueTable[i] = value;
            return;
        }
        i = -(i + 1); // Empty space was found.
        keyTable[i] = key;
        valueTable[i] = value;
        if(++size >= threshold) resize(keyTable.length << 1);
    }

    /** Returns the old value associated with the specified key, or the specified default value. */
    public int put(K key, int value, int defaultValue){
        int i = locateKey(key);
        if(i >= 0){ // Existing key was found.
            beforeModify();
            int oldValue = valueTable[i];
            valueTable[i] = value;
            return oldValue;
        }
        i = -(i + 1); // Empty space was found.
        keyTable[i] = key;
        valueTable[i] = value;
        if(++size >= threshold) resize(keyTable.length << 1);
        return defaultValue;
    }

    public void putMissing(K key, int value){
        int i = locateKey(key);
        if(i >= 0) return; // Existing key was found.
        i = -(i + 1); // Empty space was found.
        keyTable[i] = key;
        valueTable[i] = value;
        if(++size >= threshold) resize(keyTable.length << 1);
    }

    public int putMissing(K key, int value, int defaultValue){
        int i = locateKey(key);
        if(i >= 0) return valueTable[i]; // Existing key was found.
        i = -(i + 1); // Empty space was found.
        keyTable[i] = key;
        valueTable[i] = value;
        if(++size >= threshold) resize(keyTable.length << 1);
        return defaultValue;
    }

    public void putAll(ObjectIntMap<? extends K> map){
        addEntries(map);
    }

    @SuppressWarnings("unchecked")
    public void putAll(Object... values){
        for(int i = 0; i < values.length / 2; i++){
            put((K)values[i * 2], (int)values[i * 2 + 1]);
        }
    }

    @Override
    public ObjectIntMap<K> copy(){
        return new ObjectIntMap<>(this);
    }

    /** Skips checks for existing keys, doesn't increment size. */
    protected void putResize(K key, int value){
        int i = emptyIndex(place(key));
        keyTable[i] = key;
        valueTable[i] = value;
    }

    public int get(K key){
        return get(key, 0);
    }

    /** Returns the value for the specified key, or the default value if the key is not in the map. */
    public int get(K key, int defaultValue){
        if(key == null) return defaultValue;
        int i = locateKey(key);
        return i < 0 ? defaultValue : valueTable[i];
    }

    public void each(Cons2<K, Integer> cons){
        K[] keyTable = this.keyTable;
        int[] valueTable = this.valueTable;
        for(int i = 0; i < keyTable.length; i++){
            if(keyTable[i] != null) cons.get(keyTable[i], valueTable[i]);
        }
    }

    /** @return the sum of all values. */
    public int sum(){
        int sum = 0;
        K[] keyTable = this.keyTable;
        int[] valueTable = this.valueTable;
        for(int i = 0; i < keyTable.length; i++){
            if(keyTable[i] != null) sum += valueTable[i];
        }
        return sum;
    }

    public int increment(K key){
        return increment(key, 0, 1);
    }

    public int increment(K key, int increment){
        return increment(key, 0, increment);
    }

    /**
     * Returns the key's current value and increments the stored value.
     * If the key is not in the map, defaultValue + increment is put into the map and defaultValue is returned.
     */
    public int increment(K key, int defaultValue, int increment){
        int i = locateKey(key);
        if(i >= 0){ // Existing key was found.
            beforeModify();
            int oldValue = valueTable[i];
            valueTable[i] += increment;
            return oldValue;
        }
        i = -(i + 1); // Empty space was found.
        keyTable[i] = key;
        valueTable[i] = defaultValue + increment;
        if(++size >= threshold) resize(keyTable.length << 1);
        return defaultValue;
    }

    public int remove(K key){
        return remove(key, 0);
    }

    /** Returns the value for the removed key, or the default value if the key is not in the map. */
    public int remove(K key, int defaultValue){
        if(key == null) return defaultValue;
        int i = locateKey(key);
        if(i < 0) return defaultValue;
        int oldValue = valueTable[i];
        removeAt(i);
        return oldValue;
    }

    /**
     * Returns true if the specified value is in the map.
     * Note this traverses the entire map and compares every value, which may be an expensive operation.
     */
    public boolean containsValue(int value){
        K[] keyTable = this.keyTable;
        int[] valueTable = this.valueTable;
        for(int i = valueTable.length - 1; i >= 0; i--)
            if(keyTable[i] != null && valueTable[i] == value) return true;
        return false;
    }

    public boolean containsKey(K key){
        return key != null && locateKey(key) >= 0;
    }

    /**
     * Returns the key for the specified value, or null if it is not in the map.
     * Note this traverses the entire map and compares every value, which may be an expensive operation.
     */
    public @Nullable K findKey(int value){
        K[] keyTable = this.keyTable;
        int[] valueTable = this.valueTable;
        for(int i = valueTable.length - 1; i >= 0; i--){
            K key = keyTable[i];
            if(key != null && valueTable[i] == value) return key;
        }
        return null;
    }

    @Override
    protected Object keyTable(){
        return keyTable;
    }

    @Override
    protected Object valueTable(){
        return valueTable;
    }

    @Override
    protected void resize(int newSize){
        int oldCapacity = keyTable.length;
        setTableSize(newSize);

        K[] oldKeyTable = keyTable;
        int[] oldValueTable = valueTable;

        keyTable = Reflect.newArray(newSize);
        valueTable = new int[newSize];

        if(size > 0){
            for(int i = 0; i < oldCapacity; i++){
                K key = oldKeyTable[i];
                if(key != null) putResize(key, oldValueTable[i]);
            }
        }
    }

    @Override
    protected boolean sameType(Object other){
        return other instanceof ObjectIntMap;
    }

    @Override
    public Entries<K> newIterator(){
        return new Entries<>(this);
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
    protected boolean hasEntry(ObjectIntMap<K> other, int index, boolean onlyKeys){
        int i = other.locateKey(keyTable[index]);
        return i >= 0 && (onlyKeys || other.valueTable[i] == valueTable[index]);
    }

    @Override
    protected void appendEntry(StringBuilder buffer, int index){
        appendObject(buffer, keyTable[index]);
        buffer.append('=').append(valueTable[index]);
    }

    @Override
    protected int hashEntry(int index){
        return keyTable[index].hashCode() + valueTable[index];
    }

    @Override
    protected void addEntry(ObjectIntMap<K> other, int index){
        put(other.keyTable[index], other.valueTable[index]);
    }

    /**
     * Returns an iterator for the values in the map. Remove is supported.
     * <p>
     * An iterator whose loop ended is reused, like the ones of {@link #iterator()}.
     * Use the {@link Values} constructor for multithreaded iteration.
     */
    public Values values(){
        Values it = reuse(values1, values2);
        if(it != null) return it;
        values2 = values1;
        return values1 = new Values(this);
    }

    /**
     * Returns an iterator for the keys in the map. Remove is supported.
     * <p>
     * An iterator whose loop ended is reused, like the ones of {@link #iterator()}.
     * Use the {@link Keys} constructor for multithreaded iteration.
     */
    public Keys<K> keys(){
        Keys<K> it = reuse(keys1, keys2);
        if(it != null) return it;
        keys2 = keys1;
        return keys1 = new Keys<>(this);
    }


    public static class Entry<K>{
        public K key;
        public int value;

        @Override
        public String toString(){
            return key + "=" + value;
        }
    }

    protected static abstract class MapIterator<M extends ObjectIntMap<?>, I, S extends Iterator<I>> extends TableIterator<I, S>{
        protected final M map;

        public MapIterator(M map){
            super(map);
            this.map = map;
            reset();
        }

        @Override
        protected void findNextIndex(){
            findNextIndex(map);
        }
    }

    public static class Entries<K> extends MapIterator<ObjectIntMap<K>, Entry<K>, Entries<K>>{
        protected Entry<K> entry = new Entry<>();

        public Entries(ObjectIntMap<K> map){
            super(map);
        }

        /** Note the same entry instance is returned each time this method is called. */
        @Override
        public Entry<K> next(){
            int i = advance();
            K[] keyTable = map.keyTable;
            entry.key = keyTable[i];
            entry.value = map.valueTable[i];
            return entry;
        }

        /** Returns a new sequence with copies of the remaining entries. */
        public Seq<Entry<K>> toSeq(){
            Seq<Entry<K>> out = new Seq<>(map.size);
            while(hasNext()){
                Entry<K> entry = next();
                Entry<K> e = new Entry<>();
                e.key = entry.key;
                e.value = entry.value;
                out.add(e);
            }
            return out;
        }

        /** @deprecated use {@link #toSeq()} */
        @Deprecated
        public Seq<Entry<K>> toArray(){
            return toSeq();
        }
    }

    public static class Values extends MapIterator<ObjectIntMap<?>, Integer, Values>{
        public Values(ObjectIntMap<?> map){
            super(map);
        }

        @Override
        public Integer next(){
            return nextValue();
        }

        public int nextValue(){
            int i = advance();
            return map.valueTable[i];
        }

        /** Returns a new sequence with the remaining values. */
        public IntSeq toSeq(){
            return toSeq(new IntSeq(true, map.size));
        }

        /** Adds the remaining values to the specified sequence. */
        public IntSeq toSeq(IntSeq array){
            while(hasNext()) array.add(nextValue());
            return array;
        }
    }

    public static class Keys<K> extends MapIterator<ObjectIntMap<K>, K, Keys<K>>{
        public Keys(ObjectIntMap<K> map){
            super(map);
        }

        @Override
        public K next(){
            int i = advance();
            return map.keyTable[i];
        }

        /** Returns a new sequence with the remaining keys. */
        public Seq<K> toSeq(){
            return toSeq(new Seq<>(true, map.size));
        }
    }
}
