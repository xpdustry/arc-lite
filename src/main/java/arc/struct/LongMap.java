package arc.struct;

import arc.func.*;
import arc.util.*;

import java.util.*;

/**
 * An unordered map where the keys are longs and the values are objects.
 * Null values are allowed.
 * No allocation is done except when growing the table size.
 * <p>
 * The table uses linear probing, with a backward shift on removal, and Fibonacci hashing to spread poor hashcodes.
 * So get/put/contains/remove are typically O(1) with a worst case of O(n) when many keys collide into the same cluster.
 * Load factors greater than 0.91 greatly increase the chances of resizing to the next higher power of two.
 *
 * @author Nathan Sweet
 */
public class LongMap<V> extends HashTable<LongMap.Entries<V>, LongMap<V>> implements Iterable<LongMap.Entry<V>>{
    protected long[] keyTable;
    protected V[] valueTable;
    protected V zeroValue;

    protected transient Values<V> values1, values2;
    protected transient Keys keys1, keys2;

    /** Creates a new map with an initial capacity of 51 and a load factor of 0.8. */
    public LongMap(){
        this(51, 0.8f);
    }

    /**
     * Creates a new map with a load factor of 0.8.
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    public LongMap(int initialCapacity){
        this(initialCapacity, 0.8f);
    }

    /**
     * Creates a new map with the specified initial capacity and load factor.
     * This map will hold initialCapacity items before growing the backing table.
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    public LongMap(int initialCapacity, float loadFactor){
        super(initialCapacity, loadFactor);
        keyTable = new long[mask + 1];
        valueTable = Reflect.newArray(mask + 1);
    }

    /** Creates a new map identical to the specified map. */
    public LongMap(LongMap<? extends V> map){
        this(map.capacity(), map.loadFactor);
        copyTable(map);
        zeroValue = map.zeroValue;
    }

    public void each(Cons2<Long, V> cons){
        if(hasZeroValue) cons.get(0L, zeroValue);
        long[] keyTable = this.keyTable;
        V[] valueTable = this.valueTable;
        for(int i = 0; i < keyTable.length; i++){
            if(keyTable[i] != 0) cons.get(keyTable[i], valueTable[i]);
        }
    }

    @Override
    public LongMap<V> copy(){
        return new LongMap<>(this);
    }

    @Override
    protected int place(long item){
        // Need to hash it first
        return super.place(item ^ item >>> 32);
    }

    /** Returns the index of the key in the table if present, else -(index + 1) for the next empty index. */
    protected int locateKey(long key){
        for(int i = place(key); ; i = i + 1 & mask){
            long other = keyTable[i];
            if(other == 0) return -(i + 1); // Empty space is available.
            if(other == key) return i; // Same key was found.
        }
    }

    public @Nullable V put(long key, @Nullable V value){
        if(key == 0){
            if(hasZeroValue) beforeModify();
            V oldValue = zeroValue;
            zeroValue = value;
            if(!hasZeroValue){
                hasZeroValue = true;
                size++;
            }
            return oldValue;
        }
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

    public @Nullable V putMissing(long key, @Nullable V value){
        if(key == 0){
            V oldValue = zeroValue;
            if(!hasZeroValue){
                zeroValue = value;
                hasZeroValue = true;
                size++;
            }
            return oldValue;
        }
        int i = locateKey(key);
        if(i >= 0) return valueTable[i]; // Existing key was found.
        i = -(i + 1); // Empty space was found.
        keyTable[i] = key;
        valueTable[i] = value;
        if(++size >= threshold) resize(keyTable.length << 1);
        return null;
    }

    public void putAll(LongMap<? extends V> map){
        addEntries(map);
    }

    /** Skips checks for existing keys, doesn't increment size, doesn't need to handle key 0. */
    protected void putResize(long key, @Nullable V value){
        int i = emptyIndex(place(key));
        keyTable[i] = key;
        valueTable[i] = value;
    }

    public @Nullable V get(long key){
        if(key == 0) return hasZeroValue ? zeroValue : null;
        int i = locateKey(key);
        return i >= 0 ? valueTable[i] : null;
    }

    public V get(long key, @Nullable V defaultValue){
        if(key == 0) return hasZeroValue ? zeroValue : defaultValue;
        int i = locateKey(key);
        return i >= 0 ? valueTable[i] : defaultValue;
    }

    public V get(long key, Prov<V> defaultValue){
        V out = get(key);
        if(out == null){
            out = defaultValue.get();
            put(key, out);
        }
        return out;
    }

    /** Returns the value for the removed key, or null if the key is not in the map. */
    public @Nullable V remove(long key){
        if(key == 0){
            V oldValue = zeroValue;
            removeZero();
            return oldValue;
        }

        int i = locateKey(key);
        if(i < 0) return null;
        V oldValue = valueTable[i];
        removeAt(i);
        return oldValue;
    }

    public void eachKey(Longc cons){
        if(hasZeroValue) cons.get(0);
        long[] keyTable = this.keyTable;
        for(long element : keyTable){
            if(element != 0) cons.get(element);
        }
    }

    public void eachValue(Cons<V> iterator){
        if(hasZeroValue) iterator.get(zeroValue);
        long[] keyTable = this.keyTable;
        V[] valueTable = this.valueTable;
        for(int i = 0; i < keyTable.length; i++){
            if(keyTable[i] != 0) iterator.get(valueTable[i]);
        }
    }

    /** Removes the entries whose value matches the predicate. */
    public LongMap<V> removeAll(Boolf<V> pred){
        for(Entries<V> entries = entries(); entries.hasNext();){
            if(pred.get(entries.next().value)) entries.remove();
        }
        return this;
    }

    /** @return the number of values matching the predicate. */
    public int count(Boolf<V> pred){
        int count = hasZeroValue && pred.get(zeroValue) ? 1 : 0;
        long[] keyTable = this.keyTable;
        V[] valueTable = this.valueTable;
        for(int i = 0; i < keyTable.length; i++){
            if(keyTable[i] != 0 && pred.get(valueTable[i])) count++;
        }
        return count;
    }

    /** @return whether all the values match the predicate. */
    public boolean allMatch(Boolf<V> pred){
        if(hasZeroValue && !pred.get(zeroValue)) return false;
        long[] keyTable = this.keyTable;
        V[] valueTable = this.valueTable;
        for(int i = 0; i < keyTable.length; i++){
            if(keyTable[i] != 0 && !pred.get(valueTable[i])) return false;
        }
        return true;
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
            if(hasZeroValue && zeroValue == null) return true;
            long[] keyTable = this.keyTable;
            for(int i = valueTable.length - 1; i >= 0; i--)
                if(keyTable[i] != 0 && valueTable[i] == null) return true;
        }else if(identity){
            if(value == zeroValue) return true;
            for(int i = valueTable.length - 1; i >= 0; i--)
                if(valueTable[i] == value) return true;
        }else{
            if(hasZeroValue && value.equals(zeroValue)) return true;
            for(int i = valueTable.length - 1; i >= 0; i--)
                if(value.equals(valueTable[i])) return true;
        }
        return false;
    }

    public boolean containsKey(long key){
        if(key == 0) return hasZeroValue;
        return locateKey(key) >= 0;
    }

    /**
     * Returns the key for the specified value, or notFound if it is not in the map.
     * Note this traverses the entire map and compares every value, which may be an expensive operation.
     * @param identity If true, uses == to compare the specified value with values in the map.
     * If false, uses {@link #equals(Object)}.
     */
    public long findKey(@Nullable Object value, boolean identity, long notFound){
        V[] valueTable = this.valueTable;
        if(value == null){
            if(hasZeroValue && zeroValue == null) return 0;
            long[] keyTable = this.keyTable;
            for(int i = valueTable.length - 1; i >= 0; i--)
                if(keyTable[i] != 0 && valueTable[i] == null) return keyTable[i];
        }else if(identity){
            if(value == zeroValue) return 0;
            for(int i = valueTable.length - 1; i >= 0; i--)
                if(valueTable[i] == value) return keyTable[i];
        }else{
            if(hasZeroValue && value.equals(zeroValue)) return 0;
            for(int i = valueTable.length - 1; i >= 0; i--)
                if(value.equals(valueTable[i])) return keyTable[i];
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

        long[] oldKeyTable = keyTable;
        V[] oldValueTable = valueTable;

        keyTable = new long[newSize];
        valueTable = Reflect.newArray(newSize);

        if(size > 0){
            for(int i = 0; i < oldCapacity; i++){
                long key = oldKeyTable[i];
                if(key != 0) putResize(key, oldValueTable[i]);
            }
        }
    }

    @Override
    protected boolean sameType(Object other){
        return other instanceof LongMap;
    }

    @Override
    public Entries<V> newIterator(){
        return new Entries<>(this);
    }

    @Override
    protected boolean occupied(int index){
        return keyTable[index] != 0;
    }

    @Override
    protected void clearEntries(int from, int to){
        Arrays.fill(keyTable, from, to, 0);
        Arrays.fill(valueTable, from, to, null);
    }

    @Override
    protected boolean hasEntry(LongMap<V> other, int index, boolean onlyKeys){
        int i = other.locateKey(keyTable[index]);
        return i >= 0 && (onlyKeys || Structs.eq(valueTable[index], other.valueTable[i]));
    }

    @Override
    protected void appendEntry(StringBuilder buffer, int index){
        appendObject(buffer.append(keyTable[index]).append('='), valueTable[index]);
    }

    @Override
    protected int hashEntry(int index){
        return (int)(keyTable[index] * 31) + Structs.hash(valueTable[index]);
    }

    @Override
    protected void addEntry(LongMap<V> other, int index){
        put(other.keyTable[index], other.valueTable[index]);
    }

    @Override
    protected void addZero(LongMap<V> other){
        put(0, other.zeroValue);
    }

    @Override
    protected int placeIndex(int index){
        return place(keyTable[index]);
    }

    @Override
    protected boolean appendZero(StringBuilder buffer){
        if(hasZeroValue) appendObject(buffer.append("0="), zeroValue);
        return hasZeroValue;
    }

    @Override
    protected int hashZero(){
        return hasZeroValue ? Structs.hash(zeroValue) : 0;
    }

    @Override
    protected boolean sameZero(LongMap<V> other){
        return other.hasZeroValue == hasZeroValue && (!hasZeroValue || Structs.eq(zeroValue, other.zeroValue));
    }

    @Override
    protected void clearZero(){
        hasZeroValue = false;
        zeroValue = null;
    }

    /** Uses == for comparison of each value. */
    public boolean equalsIdentity(@Nullable Object obj){
        if(obj == this) return true;
        if(!(obj instanceof LongMap)) return false;
        LongMap<?> other = (LongMap<?>)obj;
        if(other.size != size) return false;
        if(other.hasZeroValue != hasZeroValue) return false;
        if(hasZeroValue && zeroValue != other.zeroValue) return false;
        long[] keyTable = this.keyTable;
        V[] valueTable = this.valueTable;
        for(int i = 0, n = keyTable.length; i < n; i++){
            long key = keyTable[i];
            if(key == 0) continue;
            int j = other.locateKey(key);
            if(j < 0 || valueTable[i] != other.valueTable[j]) return false;
        }
        return true;
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
    public Keys keys(){
        Keys it = reuse(keys1, keys2);
        if(it != null) return it;
        keys2 = keys1;
        return keys1 = new Keys(this);
    }


    public static class Entry<V>{
        public long key;
        public @Nullable V value;

        @Override
        public String toString(){
            return key + "=" + value;
        }
    }

    protected static abstract class MapIterator<M extends LongMap<?>, I, S extends Iterator<I>> extends TableIterator<I, S>{
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

    public static class Entries<V> extends MapIterator<LongMap<V>, Entry<V>, Entries<V>>{
        protected final Entry<V> entry = new Entry<>();

        public Entries(LongMap<V> map){
            super(map);
        }

        /** Note the same entry instance is returned each time this method is called. */
        public Entry<V> next(){
            int i = advance();
            long[] keyTable = map.keyTable;
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

    public static class Values<V> extends MapIterator<LongMap<V>, V, Values<V>>{
        public Values(LongMap<V> map){
            super(map);
        }

        public @Nullable V next(){
            int i = advance();
            V value;
            if(i == INDEX_ZERO)
                value = map.zeroValue;
            else
                value = map.valueTable[i];
            return value;
        }

        /** Returns a new sequence with the remaining values. */
        public Seq<V> toSeq(){
            return toSeq(new Seq<>(true, map.size));
        }
    }

    public static class Keys extends MapIterator<LongMap<?>, Long, Keys>{
        public Keys(LongMap<?> map){
            super(map);
        }

        @Override
        public Long next(){
            return nextKey();
        }

        public long nextKey(){
            int i = advance();
            return i == INDEX_ZERO ? 0 : map.keyTable[i];
        }

        /** Returns a new sequence with the remaining keys. */
        public LongSeq toSeq(){
            return toSeq(new LongSeq(true, map.size));
        }

        /** Adds the remaining keys to the specified sequence. */
        public LongSeq toSeq(LongSeq array){
            while(hasNext()) array.add(nextKey());
            return array;
        }
    }
}
