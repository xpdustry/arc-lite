package arc.struct;

import arc.util.*;

/**
 * An {@link ObjectMap} that also stores keys in an {@link Seq} using the insertion order.
 * Iteration over the {@link #entries()}, {@link #keys()}, and {@link #values()} is ordered
 * and faster than an unordered map.
 * Keys can also be accessed and the order changed using {@link #orderedKeys()}.
 * <p>
 * There is some additional overhead for put and remove O(n), because of the ordered keys.
 * When used for faster iteration versus ObjectMap and the order does not actually matter,
 * copying during remove can be greatly reduced by setting {@link Seq#ordered} to false for {@link #orderedKeys()}.
 *
 * @author Nathan Sweet
 */
public class OrderedMap<K, V> extends ObjectMap<K, V>{
    protected final Seq<K> keys;

    /** Creates a new map with an initial capacity of 51 and a load factor of 0.8. */
    public OrderedMap(){
        keys = new Seq<>();
    }

    /**
     * Creates a new map with a load factor of 0.8.
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    public OrderedMap(int initialCapacity){
        super(initialCapacity);
        keys = new Seq<>(initialCapacity);
    }

    /**
     * Creates a new map with the specified initial capacity and load factor.
     * This map will hold initialCapacity items before growing the backing table.
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    public OrderedMap(int initialCapacity, float loadFactor){
        super(initialCapacity, loadFactor);
        keys = new Seq<>(initialCapacity);
    }

    /** Creates a new map identical to the specified map. */
    public OrderedMap(OrderedMap<? extends K, ? extends V> map){
        super(map);
        keys = new Seq<>(map.keys);
    }

    @SuppressWarnings("unchecked")
    public static <K, V> OrderedMap<K, V> of(Object... values){
        OrderedMap<K, V> map = new OrderedMap<>();
        for(int i = 0; i < values.length / 2; i++){
            map.put((K)values[i * 2], (V)values[i * 2 + 1]);
        }
        return map;
    }

    @Override
    public OrderedMap<K, V> copy(){
        return new OrderedMap<>(this);
    }

    @Override
    public V put(K key, V value){
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
        keys.add(key);
        if(++size >= threshold) resize(keyTable.length << 1);
        return null;
    }

    @Override
    public @Nullable V putMissing(K key, @Nullable V value){
        int i = locateKey(key);
        if(i >= 0) return valueTable[i]; // Existing key was found.
        i = -(i + 1); // Empty space was found.
        keyTable[i] = key;
        valueTable[i] = value;
        keys.add(key);
        if(++size >= threshold) resize(keyTable.length << 1);
        return null;
    }

    public <T extends K> void putAll(OrderedMap<T, ? extends V> map){
        ensureCapacity(map.size);
        T[] keys = map.keys.items;
        for(int i = 0, n = map.keys.size; i < n; i++){
            T key = keys[i];
            put(key, map.get(key));
        }
    }

    public V removeIndex(int index){
        K key = keys.remove(index);
        int i = locateKey(key);
        V value = valueTable[i];
        super.removeAt(i);
        return value;
    }

    /** @return the value of the key at the index of {@link #orderedKeys()} */
    public V valueAt(int index){
        return get(keys.get(index));
    }

    /**
     * Changes the key {@code before} to {@code after} without changing its position in the order or its value.
     * Returns true if {@code after} has been added to the OrderedMap and {@code before} has been removed;
     * returns false if {@code after} is already present or {@code before} is not present.
     * If you are iterating over an OrderedMap and have an index, you should prefer {@link #alterIndex(int, Object)},
     * which doesn't need to search for an index like this does and so can be faster.
     * @param before a key that must be present for this to succeed
     * @param after a key that must not be in this map for this to succeed
     * @return true if {@code before} was removed and {@code after} was added, false otherwise
     */
    public boolean alter(K before, K after){
        if(containsKey(after)) return false;
        int index = keys.indexOf(before, false);
        if(index == -1) return false;
        int i = locateKey(before);
        V value = valueTable[i];
        super.removeAt(i);
        super.put(after, value);
        keys.set(index, after);
        return true;
    }

    /**
     * Changes the key at the given {@code index} in the order to {@code after},
     * without changing the ordering of other entries or any values.
     * If {@code after} is already present, this returns false;
     * it will also return false if {@code index} is invalid for the size of this map.
     * Otherwise, it returns true. Unlike {@link #alter(Object, Object)}, this operates in constant time.
     * @param index the index in the order of the key to change; must be non-negative and less than {@link #size}
     * @param after the key that will replace the contents at {@code index}; this key must not be present for this to succeed
     * @return true if {@code after} successfully replaced the key at {@code index}, false otherwise
     */
    public boolean alterIndex(int index, K after){
        if(index < 0 || index >= size || containsKey(after)) return false;
        int i = locateKey(keys.get(index));
        V value = valueTable[i];
        super.removeAt(i);
        super.put(after, value);
        keys.set(index, after);
        return true;
    }

    @Override
    public void clear(int maximumCapacity){
        keys.clear();
        super.clear(maximumCapacity);
    }

    @Override
    public void clear(){
        keys.clear();
        super.clear();
    }

    /** Removes the key from the order too, which every removal from the table goes through. */
    @Override
    protected int removeAt(int index){
        keys.remove(keyTable[index], false);
        return super.removeAt(index);
    }

    public Seq<K> orderedKeys(){
        return keys;
    }

    @Override
    public Entries<K, V> newIterator(){
        return new OrderedMapEntries<>(this);
    }

    /**
     * Returns an iterator for the values in the map, in their order. Remove is supported.
     * <p>
     * An iterator whose loop ended is reused, like the ones of {@link #iterator()}.
     * Use the {@link OrderedMapValues} constructor for multithreaded iteration.
     */
    @Override
    public Values<V> values(){
        Values<V> it = reuse(values1, values2);
        if(it != null) return it;
        values2 = values1;
        return values1 = new OrderedMapValues<>(this);
    }

    /**
     * Returns an iterator for the keys in the map, in their order. Remove is supported.
     * <p>
     * An iterator whose loop ended is reused, like the ones of {@link #iterator()}.
     * Use the {@link OrderedMapKeys} constructor for multithreaded iteration.
     */
    @Override
    public Keys<K> keys(){
        Keys<K> it = reuse(keys1, keys2);
        if(it != null) return it;
        keys2 = keys1;
        return keys1 = new OrderedMapKeys<>(this);
    }

    @Override
    public String toString(String separator, boolean braces){
        if(size == 0) return braces ? "{}" : "";
        StringBuilder buffer = new StringBuilder(32);
        if(braces) buffer.append('{');
        Seq<K> keys = this.keys;
        for(int i = 0, n = keys.size; i < n; i++){
            K key = keys.get(i);
            if(i > 0) buffer.append(separator);
            buffer.append(key == this ? "(this)" : key);
            buffer.append('=');
            V value = get(key);
            buffer.append(value == this ? "(this)" : value);
        }
        if(braces) buffer.append('}');
        return buffer.toString();
    }


    public static class OrderedMapEntries<K, V> extends Entries<K, V>{
        protected Seq<K> keys;

        public OrderedMapEntries(OrderedMap<K, V> map){
            super(map);
            keys = map.keys;
        }

        @Override
        public void reset(){
            currentIndex = -1;
            nextIndex = 0;
            hasNext = map.size > 0;
        }

        @Override
        public Entry<K, V> next(){
            checkNext();
            currentIndex = nextIndex;
            entry.key = keys.get(nextIndex);
            entry.value = map.get(entry.key);
            nextIndex++;
            hasNext = nextIndex < map.size;
            return entry;
        }

        @Override
        public void remove(){
            if(currentIndex < 0) throw new IllegalStateException("next must be called before remove.");
            map.remove(entry.key);
            nextIndex--;
            currentIndex = -1;
        }
    }

    public static class OrderedMapKeys<K> extends Keys<K>{
        protected final OrderedMap<K, ?> ordered;
        protected final Seq<K> keys;

        public OrderedMapKeys(OrderedMap<K, ?> map){
            super(map);
            ordered = map;
            keys = map.keys;
        }

        @Override
        public void reset(){
            currentIndex = -1;
            nextIndex = 0;
            hasNext = map.size > 0;
        }

        @Override
        public K next(){
            checkNext();
            K key = keys.get(nextIndex);
            currentIndex = nextIndex;
            nextIndex++;
            hasNext = nextIndex < map.size;
            return key;
        }

        @Override
        public void remove(){
            if(currentIndex < 0) throw new IllegalStateException("next must be called before remove.");
            ordered.removeIndex(currentIndex);
            nextIndex = currentIndex;
            currentIndex = -1;
        }

        @Override
        public Seq<K> toSeq(Seq<K> array){
            array.addAll(keys, nextIndex, keys.size - nextIndex);
            nextIndex = keys.size;
            hasNext = false;
            done = true;
            return array;
        }

        @Override
        public Seq<K> toSeq(){
            return toSeq(new Seq<>(true, keys.size - nextIndex));
        }
    }

    public static class OrderedMapValues<V> extends Values<V>{
        protected final OrderedMap<?, V> ordered;
        protected final Seq<?> keys;

        public OrderedMapValues(OrderedMap<?, V> map){
            super(map);
            ordered = map;
            keys = map.keys;
        }

        @Override
        public void reset(){
            currentIndex = -1;
            nextIndex = 0;
            hasNext = map.size > 0;
        }

        @Override
        public V next(){
            checkNext();
            V value = ordered.valueAt(nextIndex);
            currentIndex = nextIndex;
            nextIndex++;
            hasNext = nextIndex < map.size;
            return value;
        }

        @Override
        public void remove(){
            if(currentIndex < 0) throw new IllegalStateException("next must be called before remove.");
            ordered.removeIndex(currentIndex);
            nextIndex = currentIndex;
            currentIndex = -1;
        }

        @Override
        public Seq<V> toSeq(Seq<V> array){
            int n = keys.size;
            array.ensureCapacity(n - nextIndex);
            for(int i = nextIndex; i < n; i++)
                array.add(ordered.valueAt(i));
            currentIndex = n - 1;
            nextIndex = n;
            hasNext = false;
            done = true;
            return array;
        }

        @Override
        public Seq<V> toSeq(){
            return toSeq(new Seq<>(true, keys.size - nextIndex));
        }
    }
}
