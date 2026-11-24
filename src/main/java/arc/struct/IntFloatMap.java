package arc.struct;

import arc.func.*;
import java.util.*;

/**
 * An unordered map where the keys are ints and the values are floats.
 * No allocation is done except when growing the table size.
 * <p>
 * The table uses linear probing, with a backward shift on removal, and Fibonacci hashing to spread poor hashcodes.
 * So get/put/contains/remove are typically O(1) with a worst case of O(n) when many keys collide into the same cluster.
 * Load factors greater than 0.91 greatly increase the chances of resizing to the next higher power of two.
 *
 * @author Nathan Sweet
 */
public class IntFloatMap extends HashTable<IntFloatMap.Entries, IntFloatMap> implements Iterable<IntFloatMap.Entry>{
    protected int[] keyTable;
    protected float[] valueTable;
    protected float zeroValue;

    protected transient Values values1, values2;
    protected transient Keys keys1, keys2;

    /** Creates a new map with an initial capacity of 51 and a load factor of 0.8. */
    public IntFloatMap(){
        this(51, 0.8f);
    }

    /**
     * Creates a new map with a load factor of 0.8.
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    public IntFloatMap(int initialCapacity){
        this(initialCapacity, 0.8f);
    }

    /**
     * Creates a new map with the specified initial capacity and load factor.
     * This map will hold initialCapacity items before growing the backing table.
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    public IntFloatMap(int initialCapacity, float loadFactor){
        super(initialCapacity, loadFactor);
        keyTable = new int[mask + 1];
        valueTable = new float[mask + 1];
    }

    public void each(IntFloatc cons){
        if(hasZeroValue) cons.get(0, zeroValue);
        int[] keyTable = this.keyTable;
        float[] valueTable = this.valueTable;
        for(int i = 0; i < keyTable.length; i++){
            if(keyTable[i] != 0) cons.get(keyTable[i], valueTable[i]);
        }
    }

    /** @return the sum of all values. */
    public float sum(){
        float sum = hasZeroValue ? zeroValue : 0;
        int[] keyTable = this.keyTable;
        float[] valueTable = this.valueTable;
        for(int i = 0; i < keyTable.length; i++){
            if(keyTable[i] != 0) sum += valueTable[i];
        }
        return sum;
    }

    /** Creates a new map identical to the specified map. */
    public IntFloatMap(IntFloatMap map){
        this(map.capacity(), map.loadFactor);
        copyTable(map);
        zeroValue = map.zeroValue;
    }

    @Override
    public IntFloatMap copy(){
        return new IntFloatMap(this);
    }

    public void put(int key, float value){
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

    /**
     * Returns the old value associated with the specified key, or the specified default value.
     * @param defaultValue {@link Float#NaN} can be used for a value unlikely to be in the map.
     */
    public float put(int key, float value, float defaultValue){
        if(key == 0){
            if(hasZeroValue) beforeModify();
            float oldValue = zeroValue;
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

    public void putMissing(int key, float value){
        if(key == 0){
            if(!hasZeroValue){
                zeroValue = value;
                hasZeroValue = true;
                size++;
            }
            return;
        }
        int i = locateKey(key);
        if(i >= 0) return; // Existing key was found.
        i = -(i + 1); // Empty space was found.
        keyTable[i] = key;
        valueTable[i] = value;
        if(++size >= threshold) resize(keyTable.length << 1);
    }

    public float putMissing(int key, float value, float defaultValue){
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

    public void putAll(IntFloatMap map){
        addEntries(map);
    }

    /** Skips checks for existing keys, doesn't increment size, doesn't need to handle key 0. */
    protected void putResize(int key, float value){
        int i = emptyIndex(place(key));
        keyTable[i] = key;
        valueTable[i] = value;
    }

    public float get(int key){
        return get(key, 0f);
    }

    /** @param defaultValue {@link Float#NaN} can be used for a value unlikely to be in the map. */
    public float get(int key, float defaultValue){
        if(key == 0) return hasZeroValue ? zeroValue : defaultValue;
        int i = locateKey(key);
        return i >= 0 ? valueTable[i] : defaultValue;
    }

    public float increment(int key){
        return increment(key, 0, 1f);
    }

    public float increment(int key, float increment){
        return increment(key, 0, increment);
    }

    /**
     * Returns the key's current value and increments the stored value.
     * If the key is not in the map, defaultValue + increment is put into the map and defaultValue is returned.
     */
    public float increment(int key, float defaultValue, float increment){
        if(key == 0){
            if(!hasZeroValue){
                hasZeroValue = true;
                zeroValue = defaultValue + increment;
                size++;
                return defaultValue;
            }
            beforeModify();
            float oldValue = zeroValue;
            zeroValue += increment;
            return oldValue;
        }
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
    public float remove(int key, float defaultValue){
        if(key == 0) return removeZero() ? zeroValue : defaultValue;

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
        if(hasZeroValue && zeroValue == value) return true;
        int[] keyTable = this.keyTable;
        float[] valueTable = this.valueTable;
        for(int i = valueTable.length - 1; i >= 0; i--)
            if(keyTable[i] != 0 && valueTable[i] == value) return true;
        return false;
    }

    /**
     * Returns true if the specified value is in the map.
     * Note this traverses the entire map and compares every value, which may be an expensive operation.
     */
    public boolean containsValue(float value, float epsilon){
        if(hasZeroValue && Math.abs(zeroValue - value) <= epsilon) return true;
        int[] keyTable = this.keyTable;
        float[] valueTable = this.valueTable;
        for(int i = valueTable.length - 1; i >= 0; i--)
            if(keyTable[i] != 0 && Math.abs(valueTable[i] - value) <= epsilon) return true;
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
    public int findKey(float value, int notFound){
        if(hasZeroValue && zeroValue == value) return 0;
        int[] keyTable = this.keyTable;
        float[] valueTable = this.valueTable;
        for(int i = valueTable.length - 1; i >= 0; i--)
            if(keyTable[i] != 0 && valueTable[i] == value) return keyTable[i];
        return notFound;
    }

    /**
     * Returns the key for the specified value, or notFound if it is not in the map.
     * Note this traverses the entire map and compares every value, which may be an expensive operation.
     */
    public int findKey(float value, float epsilon, int notFound){
        if(hasZeroValue && Math.abs(zeroValue - value) <= epsilon) return 0;
        int[] keyTable = this.keyTable;
        float[] valueTable = this.valueTable;
        for(int i = valueTable.length - 1; i >= 0; i--)
            if(keyTable[i] != 0 && Math.abs(valueTable[i] - value) <= epsilon) return keyTable[i];
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
        float[] oldValueTable = valueTable;

        keyTable = new int[newSize];
        valueTable = new float[newSize];

        if(size > 0){
            for(int i = 0; i < oldCapacity; i++){
                int key = oldKeyTable[i];
                if(key != 0) putResize(key, oldValueTable[i]);
            }
        }
    }

    @Override
    protected boolean sameType(Object other){
        return other instanceof IntFloatMap;
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
    protected boolean hasEntry(IntFloatMap other, int index, boolean onlyKeys){
        int i = other.locateKey(keyTable[index]);
        return i >= 0 && (onlyKeys || other.valueTable[i] == valueTable[index]);
    }

    @Override
    protected void appendEntry(StringBuilder buffer, int index){
        buffer.append(keyTable[index]).append('=').append(valueTable[index]);
    }

    @Override
    protected int hashEntry(int index){
        return keyTable[index] * 31 + Float.floatToRawIntBits(valueTable[index]);
    }

    @Override
    protected void addEntry(IntFloatMap other, int index){
        put(other.keyTable[index], other.valueTable[index]);
    }

    @Override
    protected void addZero(IntFloatMap other){
        put(0, other.zeroValue);
    }

    @Override
    protected boolean appendZero(StringBuilder buffer){
        if(hasZeroValue) buffer.append("0=").append(zeroValue);
        return hasZeroValue;
    }

    @Override
    protected int hashZero(){
        return hasZeroValue ? Float.floatToRawIntBits(zeroValue) : 0;
    }

    @Override
    protected boolean sameZero(IntFloatMap other){
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
        public float value;

        @Override
        public String toString(){
            return key + "=" + value;
        }
    }

    protected static abstract class MapIterator<I, S extends Iterator<I>> extends TableIterator<I, S>{
        protected final IntFloatMap map;

        public MapIterator(IntFloatMap map){
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

        public Entries(IntFloatMap map){
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

    public static class Values extends MapIterator<Float, Values>{
        public Values(IntFloatMap map){
            super(map);
        }

        @Override
        public Float next(){
            return nextValue();
        }

        public float nextValue(){
            int i = advance();
            return i == INDEX_ZERO ? map.zeroValue : map.valueTable[i];
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

        /** @deprecated use {@link #toSeq()} */
        @Deprecated
        public FloatSeq toArray(){
            return toSeq();
        }

        /** @deprecated use {@link #toSeq(FloatSeq)} */
        @Deprecated
        public FloatSeq toArray(FloatSeq array){
            return toSeq(array);
        }
    }

    public static class Keys extends MapIterator<Integer, Keys>{
        public Keys(IntFloatMap map){
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
