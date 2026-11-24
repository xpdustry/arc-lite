package arc.struct;

import arc.func.*;
import arc.util.*;

import java.util.*;

/**
 * An unordered map where the keys and values are objects.
 * Null keys are not allowed, null values are allowed.
 * No allocation is done except when growing the table size.
 * <p>
 * The table uses linear probing, with a backward shift on removal, and Fibonacci hashing to spread poor hashcodes.
 * So get/put/contains/remove are typically O(1) with a worst case of O(n) when many keys collide into the same cluster.
 * Load factors greater than 0.91 greatly increase the chances of resizing to the next higher power of two.
 *
 * @author Nathan Sweet
 */
public class ObjectMap<K, V> extends HashTable<ObjectMap.Entries<K, V>, ObjectMap<K, V>> implements Iterable<ObjectMap.Entry<K, V>>{
    protected K[] keyTable;
    protected V[] valueTable;

    protected transient Values<V> values1, values2;
    protected transient Keys<K> keys1, keys2;

    @SuppressWarnings("unchecked")
    public static <K, V> ObjectMap<K, V> of(Object... values){
        ObjectMap<K, V> map = new ObjectMap<>();

        for(int i = 0; i < values.length / 2; i++){
            map.put((K)values[i * 2], (V)values[i * 2 + 1]);
        }

        return map;
    }

    /** Creates a new map with an initial capacity of 51 and a load factor of 0.8. */
    public ObjectMap(){
        this(51, 0.8f);
    }

    /**
     * Creates a new map with a load factor of 0.8.
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    public ObjectMap(int initialCapacity){
        this(initialCapacity, 0.8f);
    }

    /**
     * Creates a new map with the specified initial capacity and load factor.
     * This map will hold initialCapacity items before growing the backing table.
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    public ObjectMap(int initialCapacity, float loadFactor){
        super(initialCapacity, loadFactor);
        keyTable = Reflect.newArray(mask + 1);
        valueTable = Reflect.newArray(mask + 1);
    }

    /** Creates a new map identical to the specified map. */
    public ObjectMap(ObjectMap<? extends K, ? extends V> map){
        this(map.capacity(), map.loadFactor);
        copyTable(map);
    }

    /** Returns the old value associated with the specified key, or null. */
    public @Nullable V put(K key, @Nullable V value){
        int i = locateKey(key);
        if(i >= 0){ // Existing key was found.
            beforeModify();
            V oldValue = valueTable[i];
            valueTable[i] = value;
            return oldValue;
        }
        i = -(i + 1); // Empty space was found.
        keyTable[i] = key;
        valueTable[i] = value;
        if(++size >= threshold) resize(keyTable.length << 1);
        return null;
    }

    public @Nullable V putMissing(K key, @Nullable V value){
        int i = locateKey(key);
        if(i >= 0) return valueTable[i]; // Existing key was found.
        i = -(i + 1); // Empty space was found.
        keyTable[i] = key;
        valueTable[i] = value;
        if(++size >= threshold) resize(keyTable.length << 1);
        return null;
    }

    @SuppressWarnings("unchecked")
    public void putAll(Object... values){
        for(int i = 0; i < values.length / 2; i++){
            put((K)values[i * 2], (V)values[i * 2 + 1]);
        }
    }

    public void putAll(ObjectMap<? extends K, ? extends V> map){
        addEntries(map);
    }

    /** Puts the entries of the other map into this map. @return this map */
    public ObjectMap<K, V> merge(ObjectMap<? extends K, ? extends V> map){
        putAll(map);
        return this;
    }

    /** Iterates through key/value pairs. */
    public void each(Cons2<K, V> cons){
        for(Entry<K, V> entry : entries()){
            cons.get(entry.key, entry.value);
        }
    }

    /** Removes the entries matching the predicate. */
    public ObjectMap<K, V> removeAll(Boolf2<K, V> pred){
        for(Entries<K, V> entries = entries(); entries.hasNext();){
            Entry<K, V> entry = entries.next();
            if(pred.get(entry.key, entry.value)) entries.remove();
        }
        return this;
    }

    /** @return the number of entries matching the predicate. */
    public int count(Boolf2<K, V> pred){
        int count = 0;
        K[] keyTable = this.keyTable;
        V[] valueTable = this.valueTable;
        for(int i = 0; i < keyTable.length; i++){
            if(keyTable[i] != null && pred.get(keyTable[i], valueTable[i])) count++;
        }
        return count;
    }

    /** @return whether all the entries match the predicate. */
    public boolean allMatch(Boolf2<K, V> pred){
        K[] keyTable = this.keyTable;
        V[] valueTable = this.valueTable;
        for(int i = 0; i < keyTable.length; i++){
            if(keyTable[i] != null && !pred.get(keyTable[i], valueTable[i])) return false;
        }
        return true;
    }

    @Override
    public ObjectMap<K, V> copy(){
        return new ObjectMap<>(this);
    }

    /** Skips checks for existing keys, doesn't increment size. */
    protected void putResize(K key, @Nullable V value){
        int i = emptyIndex(place(key));
        keyTable[i] = key;
        valueTable[i] = value;
    }

    public V getThrow(K key, Prov<? extends RuntimeException> error){
        if(!containsKey(key)){
            throw error.get();
        }
        return get(key);
    }

    /** Returns the value for the key, or puts and returns a new one from the supplier if the key is not in the map. */
    public V get(K key, Prov<V> supplier){
        V val = get(key);
        if(val == null) put(key, val = supplier.get());
        return val;
    }

    /** Same as {@link #get(Object)}, which now also returns {@code null} for a null key. */
    public @Nullable V getNull(K key){
        return get(key);
    }

    /** Returns the value for the specified key, or null if the key is not in the map or is null. */
    public @Nullable <T extends K> V get(T key){
        return get(key, (V)null);
    }

    /** Returns the value for the specified key, or the default value if the key is not in the map. */
    public V get(K key, @Nullable V defaultValue){
        if(key == null) return defaultValue;
        int i = locateKey(key);
        return i < 0 ? defaultValue : valueTable[i];
    }

    /** Returns the value for the removed key, or null if the key is not in the map. */
    public @Nullable V remove(K key){
        if(key == null) return null;
        int i = locateKey(key);
        if(i < 0) return null;
        V oldValue = valueTable[i];
        removeAt(i);
        return oldValue;
    }

    /**
     * Returns true if the specified value is in the map.
     * Note this traverses the entire map and compares every value, which may be an expensive operation.
     * @param identity If true, uses == to compare the specified value with values in the map.
     * If false, uses {@link #equals(Object)}.
     */
    public boolean containsValue(@Nullable Object value, boolean identity){
        V[] valueTable = this.valueTable;
        if(value == null){
            K[] keyTable = this.keyTable;
            for(int i = valueTable.length - 1; i >= 0; i--)
                if(keyTable[i] != null && valueTable[i] == null) return true;
        }else if(identity){
            for(int i = valueTable.length - 1; i >= 0; i--)
                if(valueTable[i] == value) return true;
        }else{
            for(int i = valueTable.length - 1; i >= 0; i--)
                if(value.equals(valueTable[i])) return true;
        }
        return false;
    }

    public boolean containsKey(K key){
        return key != null && locateKey(key) >= 0;
    }

    /**
     * Returns the key for the specified value, or null if it is not in the map.
     * Note this traverses the entire map and compares every value, which may be an expensive operation.
     * @param identity If true, uses == to compare the specified value with values in the map.
     * If false, uses {@link #equals(Object)}.
     */
    public @Nullable K findKey(@Nullable Object value, boolean identity){
        V[] valueTable = this.valueTable;
        if(value == null){
            K[] keyTable = this.keyTable;
            for(int i = valueTable.length - 1; i >= 0; i--)
                if(keyTable[i] != null && valueTable[i] == null) return keyTable[i];
        }else if(identity){
            for(int i = valueTable.length - 1; i >= 0; i--)
                if(valueTable[i] == value) return keyTable[i];
        }else{
            for(int i = valueTable.length - 1; i >= 0; i--)
                if(value.equals(valueTable[i])) return keyTable[i];
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
        V[] oldValueTable = valueTable;

        keyTable = Reflect.newArray(newSize);
        valueTable = Reflect.newArray(newSize);

        if(size > 0){
            for(int i = 0; i < oldCapacity; i++){
                K key = oldKeyTable[i];
                if(key != null) putResize(key, oldValueTable[i]);
            }
        }
    }

    @Override
    protected boolean sameType(Object other){
        return other instanceof ObjectMap;
    }

    @Override
    public Entries<K, V> newIterator(){
        return new Entries<>(this);
    }

    @Override
    protected boolean occupied(int index){
        return keyTable[index] != null;
    }

    @Override
    protected void clearEntries(int from, int to){
        Arrays.fill(keyTable, from, to, null);
        Arrays.fill(valueTable, from, to, null);
    }

    @Override
    protected int placeIndex(int index){
        return place(keyTable[index]);
    }

    @Override
    protected boolean hasEntry(ObjectMap<K, V> other, int index, boolean onlyKeys){
        int i = other.locateKey(keyTable[index]);
        return i >= 0 && (onlyKeys || Structs.eq(valueTable[index], other.valueTable[i]));
    }

    @Override
    protected void appendEntry(StringBuilder buffer, int index){
        appendObject(buffer, keyTable[index]);
        appendObject(buffer.append('='), valueTable[index]);
    }

    @Override
    protected int hashEntry(int index){
        V value = valueTable[index];
        return keyTable[index].hashCode() + (value == null ? 0 : value.hashCode());
    }

    @Override
    protected void addEntry(ObjectMap<K, V> other, int index){
        put(other.keyTable[index], other.valueTable[index]);
    }

    @Override
    public String toString(String separator){
        return toString(separator, false);
    }

    @Override
    public String toString(){
        return toString(", ", true);
    }

    public String toString(String separator, boolean braces){
        return braces ? toString(separator, "{", "}") : toString(separator, "", "");
    }

    /**
     * Returns an iterator for the values in the map. Remove is supported.
     * <p>
     * An iterator whose loop ended is reused, like the ones of {@link #iterator()}.
     * Use the {@link Values} constructor for multithreaded iteration.
     */
    public Values<V> values(){
        Values<V> it = reuse(values1, values2);
        if(it != null) return it;
        values2 = values1;
        return values1 = new Values<>(this);
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


    public static class Entry<K, V>{
        public K key;
        public @Nullable V value;

        @Override
        public String toString(){
            return key + "=" + value;
        }
    }

    protected static abstract class MapIterator<M extends ObjectMap<?, ?>, I, S extends Iterator<I>> extends TableIterator<I, S>{
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

    public static class Entries<K, V> extends MapIterator<ObjectMap<K, V>, Entry<K, V>, Entries<K, V>>{
        protected Entry<K, V> entry = new Entry<>();

        public Entries(ObjectMap<K, V> map){
            super(map);
        }

        /** Note the same entry instance is returned each time this method is called. */
        @Override
        public Entry<K, V> next(){
            int i = advance();
            K[] keyTable = map.keyTable;
            entry.key = keyTable[i];
            entry.value = map.valueTable[i];
            return entry;
        }
    }

    public static class Values<V> extends MapIterator<ObjectMap<?, V>, V, Values<V>>{
        public Values(ObjectMap<?, V> map){
            super(map);
        }

        @Override
        public @Nullable V next(){
            int i = advance();
            return map.valueTable[i];
        }

        /** Returns a new sequence with the remaining values. */
        public Seq<V> toSeq(){
            return toSeq(new Seq<>(true, map.size));
        }
    }

    public static class Keys<K> extends MapIterator<ObjectMap<K, ?>, K, Keys<K>>{
        public Keys(ObjectMap<K, ?> map){
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
