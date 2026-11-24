package arc.struct;

import arc.func.*;
import arc.util.*;

import java.util.*;

/**
 * An unordered map where the keys are objects and the values are floats.
 * Null keys are not allowed.
 * No allocation is done except when growing the table size.
 * <p>
 * The table uses linear probing, with a backward shift on removal, and Fibonacci hashing to spread poor hashcodes.
 * So get/put/contains/remove are typically O(1) with a worst case of O(n) when many keys collide into the same cluster.
 * Load factors greater than 0.91 greatly increase the chances of resizing to the next higher power of two.
 *
 * @author Nathan Sweet
 */
public class ObjectFloatMap<K> extends HashTable<ObjectFloatMap.Entries<K>, ObjectFloatMap<K>> implements Iterable<ObjectFloatMap.Entry<K>>{
    protected K[] keyTable;
    protected float[] valueTable;

    protected transient Values values1, values2;
    protected transient Keys<K> keys1, keys2;

    /** Creates a new map with an initial capacity of 51 and a load factor of 0.8. */
    public ObjectFloatMap(){
        this(51, 0.8f);
    }

    /**
     * Creates a new map with a load factor of 0.8.
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    public ObjectFloatMap(int initialCapacity){
        this(initialCapacity, 0.8f);
    }

    /**
     * Creates a new map with the specified initial capacity and load factor.
     * This map will hold initialCapacity items before growing the backing table.
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    public ObjectFloatMap(int initialCapacity, float loadFactor){
        super(initialCapacity, loadFactor);
        keyTable = Reflect.newArray(mask + 1);
        valueTable = new float[mask + 1];
    }

    /** Creates a new map identical to the specified map. */
    public ObjectFloatMap(ObjectFloatMap<? extends K> map){
        this(map.capacity(), map.loadFactor);
        copyTable(map);
    }

    @SuppressWarnings("unchecked")
    public ObjectFloatMap<K> putAll(Object... values){
        for(int i = 0; i < values.length / 2; i++){
            put((K)values[i * 2], (float)values[i * 2 + 1]);
        }

        return this;
    }

    public void put(K key, float value){
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

    /**
     * Returns the old value associated with the specified key, or the specified default value.
     * @param defaultValue {@link Float#NaN} can be used for a value unlikely to be in the map.
     */
    public float put(K key, float value, float defaultValue){
        int i = locateKey(key);
        if(i >= 0){ // Existing key was found.
            beforeModify();
            float oldValue = valueTable[i];
            valueTable[i] = value;
            return oldValue;
        }
        i = -(i + 1); // Empty space was found.
        keyTable[i] = key;
        valueTable[i] = value;
        if(++size >= threshold) resize(keyTable.length << 1);
        return defaultValue;
    }

    public void putMissing(K key, float value){
        int i = locateKey(key);
        if(i >= 0) return; // Existing key was found.
        i = -(i + 1); // Empty space was found.
        keyTable[i] = key;
        valueTable[i] = value;
        if(++size >= threshold) resize(keyTable.length << 1);
    }

    public float putMissing(K key, float value, float defaultValue){
        int i = locateKey(key);
        if(i >= 0) return valueTable[i]; // Existing key was found.
        i = -(i + 1); // Empty space was found.
        keyTable[i] = key;
        valueTable[i] = value;
        if(++size >= threshold) resize(keyTable.length << 1);
        return defaultValue;
    }

    public void putAll(ObjectFloatMap<? extends K> map){
        addEntries(map);
    }

    @Override
    public ObjectFloatMap<K> copy(){
        return new ObjectFloatMap<>(this);
    }

    public void each(Cons<Entry<K>> cons){
        for(Entry<K> e : this){
            cons.get(e);
        }
    }

    public void each(Cons2<K, Float> cons){
        K[] keyTable = this.keyTable;
        float[] valueTable = this.valueTable;
        for(int i = 0; i < keyTable.length; i++){
            if(keyTable[i] != null) cons.get(keyTable[i], valueTable[i]);
        }
    }

    /** Skips checks for existing keys, doesn't increment size. */
    protected void putResize(K key, float value){
        int i = emptyIndex(place(key));
        keyTable[i] = key;
        valueTable[i] = value;
    }

    /**
     * Returns the value for the specified key, or the default value if the key is not in the map.
     * @param defaultValue {@link Float#NaN} can be used for a value unlikely to be in the map.
     */
    public float get(K key, float defaultValue){
        if(key == null) return defaultValue;
        int i = locateKey(key);
        return i < 0 ? defaultValue : valueTable[i];
    }

    /** @return the sum of all values. */
    public float sum(){
        float sum = 0;
        K[] keyTable = this.keyTable;
        float[] valueTable = this.valueTable;
        for(int i = 0; i < keyTable.length; i++){
            if(keyTable[i] != null) sum += valueTable[i];
        }
        return sum;
    }

    public float increment(K key){
        return increment(key, 0, 1);
    }

    public float increment(K key, float increment){
        return increment(key, 0, increment);
    }

    /**
     * Returns the key's current value and increments the stored value.
     * If the key is not in the map, defaultValue + increment is put into the map and defaultValue is returned.
     */
    public float increment(K key, float defaultValue, float increment){
        int i = locateKey(key);
        if(i >= 0){ // Existing key was found.
            beforeModify();
            float oldValue = valueTable[i];
            valueTable[i] += increment;
            return oldValue;
        }
        i = -(i + 1); // Empty space was found.
        keyTable[i] = key;
        valueTable[i] = defaultValue + increment;
        if(++size >= threshold) resize(keyTable.length << 1);
        return defaultValue;
    }

    /**
     * Returns the value for the removed key, or the default value if the key is not in the map.
     * @param defaultValue {@link Float#NaN} can be used for a value unlikely to be in the map.
     */
    public float remove(K key, float defaultValue){
        if(key == null) return defaultValue;
        int i = locateKey(key);
        if(i < 0) return defaultValue;
        float oldValue = valueTable[i];
        removeAt(i);
        return oldValue;
    }

    /**
     * Returns true if the specified value is in the map.
     * Note this traverses the entire map and compares every value, which may be an expensive operation.
     */
    public boolean containsValue(float value){
        K[] keyTable = this.keyTable;
        float[] valueTable = this.valueTable;
        for(int i = valueTable.length - 1; i >= 0; i--)
            if(keyTable[i] != null && valueTable[i] == value) return true;
        return false;
    }

    /**
     * Returns true if the specified value is in the map.
     * Note this traverses the entire map and compares every value, which may be an expensive operation.
     */
    public boolean containsValue(float value, float epsilon){
        K[] keyTable = this.keyTable;
        float[] valueTable = this.valueTable;
        for(int i = valueTable.length - 1; i >= 0; i--)
            if(keyTable[i] != null && Math.abs(valueTable[i] - value) <= epsilon) return true;
        return false;
    }

    public boolean containsKey(K key){
        return key != null && locateKey(key) >= 0;
    }

    /**
     * Returns the key for the specified value, or null if it is not in the map.
     * Note this traverses the entire map and compares every value, which may be an expensive operation.
     */
    public @Nullable K findKey(float value){
        K[] keyTable = this.keyTable;
        float[] valueTable = this.valueTable;
        for(int i = valueTable.length - 1; i >= 0; i--){
            K key = keyTable[i];
            if(key != null && valueTable[i] == value) return key;
        }
        return null;
    }

    /**
     * Returns the key for the specified value, or null if it is not in the map.
     * Note this traverses the entire map and compares every value, which may be an expensive operation.
     */
    public @Nullable K findKey(float value, float epsilon){
        K[] keyTable = this.keyTable;
        float[] valueTable = this.valueTable;
        for(int i = valueTable.length - 1; i >= 0; i--){
            K key = keyTable[i];
            if(key != null && Math.abs(valueTable[i] - value) <= epsilon) return key;
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
        float[] oldValueTable = valueTable;

        keyTable = Reflect.newArray(newSize);
        valueTable = new float[newSize];

        if(size > 0){
            for(int i = 0; i < oldCapacity; i++){
                K key = oldKeyTable[i];
                if(key != null) putResize(key, oldValueTable[i]);
            }
        }
    }

    @Override
    protected boolean sameType(Object other){
        return other instanceof ObjectFloatMap;
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
    protected boolean hasEntry(ObjectFloatMap<K> other, int index, boolean onlyKeys){
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
        return keyTable[index].hashCode() + Float.floatToRawIntBits(valueTable[index]);
    }

    @Override
    protected void addEntry(ObjectFloatMap<K> other, int index){
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
        public float value;

        @Override
        public String toString(){
            return key + "=" + value;
        }
    }

    protected static abstract class MapIterator<M extends ObjectFloatMap<?>, I, S extends Iterator<I>> extends TableIterator<I, S>{
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

    static public class Entries<K> extends MapIterator<ObjectFloatMap<K>, Entry<K>, Entries<K>>{
        protected Entry<K> entry = new Entry<>();

        public Entries(ObjectFloatMap<K> map){
            super(map);
        }

        /** Note the same entry instance is returned each time this method is called. */
        public Entry<K> next(){
            int i = advance();
            K[] keyTable = map.keyTable;
            entry.key = keyTable[i];
            entry.value = map.valueTable[i];
            return entry;
        }
    }

    public static class Values extends MapIterator<ObjectFloatMap<?>, Float, Values>{
        public Values(ObjectFloatMap<?> map){
            super(map);
        }

        @Override
        public Float next(){
            return nextValue();
        }

        public float nextValue(){
            int i = advance();
            return map.valueTable[i];
        }

        /** Returns a new sequence with the remaining values. */
        public FloatSeq toSeq(){
            return toSeq(new FloatSeq(true, map.size));
        }

        /** Adds the remaining values to the specified sequence. */
        public FloatSeq toSeq(FloatSeq array){
            while(hasNext()) array.add(nextValue());
            return array;
        }
    }

    public static class Keys<K> extends MapIterator<ObjectFloatMap<K>, K, Keys<K>>{
        public Keys(ObjectFloatMap<K> map){
            super(map);
        }

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
