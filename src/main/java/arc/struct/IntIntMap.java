package arc.struct;

import arc.func.*;

import java.util.*;

/**
 * An unordered map where the keys and values are ints.
 * No allocation is done except when growing the table size.
 * <p>
 * The table uses linear probing, with a backward shift on removal, and Fibonacci hashing to spread poor hashcodes.
 * So get/put/contains/remove are typically O(1) with a worst case of O(n) when many keys collide into the same cluster.
 * Load factors greater than 0.91 greatly increase the chances of resizing to the next higher power of two.
 *
 * @author Nathan Sweet
 */
public class IntIntMap extends HashTable<IntIntMap.Entries, IntIntMap> implements Iterable<IntIntMap.Entry>{
    protected int[] keyTable;
    protected int[] valueTable;
    protected int zeroValue;

    protected transient Values values1, values2;
    protected transient Keys keys1, keys2;

    /** Creates a new map with an initial capacity of 51 and a load factor of 0.8. */
    public IntIntMap(){
        this(51, 0.8f);
    }

    /**
     * Creates a new map with a load factor of 0.8.
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    public IntIntMap(int initialCapacity){
        this(initialCapacity, 0.8f);
    }

    /**
     * Creates a new map with the specified initial capacity and load factor.
     * This map will hold initialCapacity items before growing the backing table.
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    public IntIntMap(int initialCapacity, float loadFactor){
        super(initialCapacity, loadFactor);
        keyTable = new int[mask + 1];
        valueTable = new int[mask + 1];
    }

    /** Creates a new map identical to the specified map. */
    public IntIntMap(IntIntMap map){
        this(map.capacity(), map.loadFactor);
        copyTable(map);
        zeroValue = map.zeroValue;
    }

    public static IntIntMap of(int... values){
        IntIntMap map = new IntIntMap();
        for(int i = 0; i < values.length; i += 2){
            map.put(values[i], values[i + 1]);
        }
        return map;
    }

    public void each(Intc2 cons){
        if(hasZeroValue) cons.get(0, zeroValue);
        int[] keyTable = this.keyTable;
        int[] valueTable = this.valueTable;
        for(int i = 0; i < keyTable.length; i++){
            if(keyTable[i] != 0) cons.get(keyTable[i], valueTable[i]);
        }
    }

    /** @return the sum of all values. */
    public int sum(){
        int sum = hasZeroValue ? zeroValue : 0;
        int[] keyTable = this.keyTable;
        int[] valueTable = this.valueTable;
        for(int i = 0; i < keyTable.length; i++){
            if(keyTable[i] != 0) sum += valueTable[i];
        }
        return sum;
    }

    @Override
    public IntIntMap copy(){
        return new IntIntMap(this);
    }

    public void put(int key, int value){
        if(key == 0){
            if(hasZeroValue) beforeModify();
            zeroValue = value;
            if(!hasZeroValue){
                hasZeroValue = true;
                size++;
            }
            return;
        }
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
    public int put(int key, int value, int defaultValue){
        if(key == 0){
            if(hasZeroValue) beforeModify();
            int oldValue = zeroValue;
            zeroValue = value;
            if(!hasZeroValue){
                hasZeroValue = true;
                size++;
                return defaultValue;
            }
            return oldValue;
        }
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

    public int putMissing(int key, int value){
        return putMissing(key, value, value);
    }

    /**
     * Put {@code value} only if the {@code key} is not present.
     * @return The associated value if key is present in the map, else {@code value}.
     * @see #putMissing(int, int)
     */
    public int getOrPut(int key, int value){
        return putMissing(key, value, value);
    }

    public int putMissing(int key, int value, int defaultValue){
        if(key == 0){
            if(!hasZeroValue){
                zeroValue = value;
                hasZeroValue = true;
                size++;
                return defaultValue;
            }
            return zeroValue;
        }
        int i = locateKey(key);
        if(i >= 0) return valueTable[i]; // Existing key was found.
        i = -(i + 1); // Empty space was found.
        keyTable[i] = key;
        valueTable[i] = value;
        if(++size >= threshold) resize(keyTable.length << 1);
        return defaultValue;
    }

    public void putAll(IntIntMap map){
        addEntries(map);
    }

    /** Skips checks for existing keys, doesn't increment size, doesn't need to handle key 0. */
    protected void putResize(int key, int value){
        int i = emptyIndex(place(key));
        keyTable[i] = key;
        valueTable[i] = value;
    }

    public int get(int key){
        return get(key, 0);
    }

    public int get(int key, int defaultValue){
        if(key == 0) return hasZeroValue ? zeroValue : defaultValue;
        int i = locateKey(key);
        return i >= 0 ? valueTable[i] : defaultValue;
    }

    public int increment(int key){
        return increment(key, 0, 1);
    }

    public int increment(int key, int increment){
        return increment(key, 0, increment);
    }

    /**
     * Returns the key's current value and increments the stored value.
     * If the key is not in the map, defaultValue + increment is put into the map and defaultValue is returned.
     */
    public int increment(int key, int defaultValue, int increment){
        if(key == 0){
            if(!hasZeroValue){
                hasZeroValue = true;
                zeroValue = defaultValue + increment;
                size++;
                return defaultValue;
            }
            beforeModify();
            int oldValue = zeroValue;
            zeroValue += increment;
            return oldValue;
        }
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

    /** Returns the value for the removed key, or 0 if the key is not in the map. */
    public int remove(int key){
        return remove(key, 0);
    }

    /** Returns the value for the removed key, or the default value if the key is not in the map. */
    public int remove(int key, int defaultValue){
        if(key == 0) return removeZero() ? zeroValue : defaultValue;

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
        if(hasZeroValue && zeroValue == value) return true;
        int[] keyTable = this.keyTable;
        int[] valueTable = this.valueTable;
        for(int i = valueTable.length - 1; i >= 0; i--)
            if(keyTable[i] != 0 && valueTable[i] == value) return true;
        return false;
    }

    public boolean containsKey(int key){
        if(key == 0) return hasZeroValue;
        return locateKey(key) >= 0;
    }

    /**
     * Returns the key for the specified value, or notFound if it is not in the map.
     * Note this traverses the entire map and compares every value, which may be an expensive operation.
     */
    public int findKey(int value, int notFound){
        if(hasZeroValue && zeroValue == value) return 0;
        int[] keyTable = this.keyTable;
        int[] valueTable = this.valueTable;
        for(int i = valueTable.length - 1; i >= 0; i--){
            int key = keyTable[i];
            if(key != 0 && valueTable[i] == value) return key;
        }
        return notFound;
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

        int[] oldKeyTable = keyTable;
        int[] oldValueTable = valueTable;

        keyTable = new int[newSize];
        valueTable = new int[newSize];

        if(size > 0){
            for(int i = 0; i < oldCapacity; i++){
                int key = oldKeyTable[i];
                if(key != 0) putResize(key, oldValueTable[i]);
            }
        }
    }

    @Override
    protected boolean sameType(Object other){
        return other instanceof IntIntMap;
    }

    @Override
    public Entries newIterator(){
        return new Entries(this);
    }

    @Override
    protected boolean occupied(int index){
        return keyTable[index] != 0;
    }

    @Override
    protected void clearEntries(int from, int to){
        Arrays.fill(keyTable, from, to, 0);
    }

    @Override
    protected int placeIndex(int index){
        return place(keyTable[index]);
    }

    /** Returns the index of the key in the table if present, else -(index + 1) for the next empty index. */
    protected int locateKey(int key){
        for(int i = place(key); ; i = i + 1 & mask){
            int other = keyTable[i];
            if(other == 0) return -(i + 1); // Empty space is available.
            if(other == key) return i; // Same key was found.
        }
    }

    @Override
    protected boolean hasEntry(IntIntMap other, int index, boolean onlyKeys){
        int i = other.locateKey(keyTable[index]);
        return i >= 0 && (onlyKeys || other.valueTable[i] == valueTable[index]);
    }

    @Override
    protected void appendEntry(StringBuilder buffer, int index){
        buffer.append(keyTable[index]).append('=').append(valueTable[index]);
    }

    @Override
    protected int hashEntry(int index){
        return keyTable[index] * 31 + valueTable[index];
    }

    @Override
    protected void addEntry(IntIntMap other, int index){
        put(other.keyTable[index], other.valueTable[index]);
    }

    @Override
    protected void addZero(IntIntMap other){
        put(0, other.zeroValue);
    }

    @Override
    protected boolean appendZero(StringBuilder buffer){
        if(hasZeroValue) buffer.append("0=").append(zeroValue);
        return hasZeroValue;
    }

    @Override
    protected int hashZero(){
        return hasZeroValue ? zeroValue : 0;
    }

    @Override
    protected boolean sameZero(IntIntMap other){
        return other.hasZeroValue == hasZeroValue && (!hasZeroValue || other.zeroValue == zeroValue);
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
    public Keys keys(){
        Keys it = reuse(keys1, keys2);
        if(it != null) return it;
        keys2 = keys1;
        return keys1 = new Keys(this);
    }


    public static class Entry{
        public int key;
        public int value;

        @Override
        public String toString(){
            return key + "=" + value;
        }
    }

    protected static abstract class MapIterator<I, S extends Iterator<I>> extends TableIterator<I, S>{
        protected final IntIntMap map;

        public MapIterator(IntIntMap map){
            super(map);
            this.map = map;
            reset();
        }

        @Override
        protected void findNextIndex(){
            findNextIndex(map);
        }
    }

    public static class Entries extends MapIterator<Entry, Entries>{
        protected final Entry entry = new Entry();

        public Entries(IntIntMap map){
            super(map);
        }

        /** Note the same entry instance is returned each time this method is called. */
        public Entry next(){
            int i = advance();
            int[] keyTable = map.keyTable;
            if(i == INDEX_ZERO){
                entry.key = 0;
                entry.value = map.zeroValue;
            }else{
                entry.key = keyTable[i];
                entry.value = map.valueTable[i];
            }
            return entry;
        }
    }

    public static class Values extends MapIterator<Integer, Values>{
        public Values(IntIntMap map){
            super(map);
        }

        @Override
        public Integer next(){
            return nextValue();
        }

        public int nextValue(){
            int i = advance();
            return i == INDEX_ZERO ? map.zeroValue : map.valueTable[i];
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

        /** @deprecated use {@link #toSeq()} */
        @Deprecated
        public IntSeq toArray(){
            return toSeq();
        }

        /** @deprecated use {@link #toSeq(IntSeq)} */
        @Deprecated
        public IntSeq toArray(IntSeq array){
            return toSeq(array);
        }
    }

    public static class Keys extends MapIterator<Integer, Keys>{
        public Keys(IntIntMap map){
            super(map);
        }

        @Override
        public Integer next(){
            return nextKey();
        }

        public int nextKey(){
            int i = advance();
            return i == INDEX_ZERO ? 0 : map.keyTable[i];
        }

        /** Returns a new sequence with the remaining keys. */
        public IntSeq toSeq(){
            return toSeq(new IntSeq(true, map.size));
        }

        /** Adds the remaining keys to the specified sequence. */
        public IntSeq toSeq(IntSeq array){
            while(hasNext()) array.add(nextKey());
            return array;
        }

        /** @deprecated use {@link #toSeq()} */
        @Deprecated
        public IntSeq toArray(){
            return toSeq();
        }

        /** @deprecated use {@link #toSeq(IntSeq)} */
        @Deprecated
        public IntSeq toArray(IntSeq array){
            return toSeq(array);
        }
    }
}
