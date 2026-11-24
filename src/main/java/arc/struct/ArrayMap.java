package arc.struct;

import arc.struct.ObjectMap.*;
import arc.func.*;
import arc.util.*;

import java.util.*;

/**
 * An ordered or unordered map of objects. This implementation uses arrays to store the keys and values, which means
 * {@link #getKey(Object, boolean) gets} do a comparison for each key in the map. This is slower than a typical hash map
 * implementation, but may be acceptable for small maps and has the benefits that keys and values can be accessed by index, which
 * makes iteration fast. Like {@link Seq}, if ordered is false, this class avoids a memory copy when removing elements (the last
 * element is moved to the removed element's position).
 * @author Nathan Sweet
 */
public class ArrayMap<K, V> extends BaseSeq<ArrayMap.Entries<K, V>, ArrayMap<K, V>> implements Iterable<ObjectMap.Entry<K, V>>{
    public K[] keys;
    public V[] values;

    protected transient Values<V> values1, values2;
    protected transient Keys<K> keys1, keys2;

    /** Creates an ordered map with a capacity of 16. */
    public ArrayMap(){
        this(true, 16);
    }

    /** Creates an ordered map with the specified capacity. */
    public ArrayMap(int capacity){
        this(true, capacity);
    }

    /**
     * @param ordered If false, methods that remove elements may change the order of other elements in the arrays, which avoids a
     * memory copy.
     * @param capacity Any elements added beyond this will cause the backing arrays to be grown.
     */
    public ArrayMap(boolean ordered, int capacity){
        this.ordered = ordered;
        keys = Reflect.newArray(capacity);
        values = Reflect.newArray(capacity);
    }

    /**
     * Creates a new map with {@link #keys} and {@link #values} of the specified type.
     * @param ordered If false, methods that remove elements may change the order of other elements in the arrays, which avoids a
     * memory copy.
     * @param capacity Any elements added beyond this will cause the backing arrays to be grown.
     */
    @SuppressWarnings("unchecked")
    public ArrayMap(boolean ordered, int capacity, Class<?> keyArrayType, Class<?> valueArrayType){
        this.ordered = ordered;
        keys = Reflect.newArray((Class<K>)keyArrayType, capacity);
        values = Reflect.newArray((Class<V>)valueArrayType, capacity);
    }

    /** Creates an ordered map with {@link #keys} and {@link #values} of the specified type and a capacity of 16. */
    public ArrayMap(Class<?> keyArrayType, Class<?> valueArrayType){
        this(true, 16, keyArrayType, valueArrayType);
    }

    /**
     * Creates a new map containing the elements in the specified map. The new map will have the same type of backing arrays and
     * will be ordered if the specified map is ordered. The capacity is set to the number of elements, so any subsequent elements
     * added will cause the backing arrays to be grown.
     */
    public ArrayMap(ArrayMap<?, ?> array){
        this(array.ordered, array.size,array.keys.getClass().getComponentType(), array.values.getClass().getComponentType());
        size = array.size;
        System.arraycopy(array.keys, 0, keys, 0, size);
        System.arraycopy(array.values, 0, values, 0, size);
    }

    /**Iterates through key/value pairs.*/
    public void each(Cons2<K, V> cons){
        int size = this.size;
        K[] keys = this.keys;
        V[] values = this.values;
        for(int i = 0; i < size; i++){
            cons.get(keys[i], values[i]);
        }
    }

    public int put(K key, V value){
        int index = indexOfKey(key);
        if(index == -1){
            grow(1);
            index = size++;
        }else{
            beforeModify();
        }
        keys[index] = key;
        values[index] = value;
        return index;
    }

    public int put(K key, V value, int index){
        beforeModify();
        int existingIndex = indexOfKey(key);
        if(existingIndex != -1) removeIndex(existingIndex);
        else grow(1);
        copyItems(index, index + 1, size - index);
        keys[index] = key;
        values[index] = value;
        size++;
        return index;
    }

    public void putAll(ArrayMap<? extends K, ? extends V> map){
        putAll(map, 0, map.size);
    }

    public void putAll(ArrayMap<? extends K, ? extends V> map, int offset, int length){
        checkRange(map, offset, length);
        grow(length);
        System.arraycopy(map.keys, offset, keys, size, length);
        System.arraycopy(map.values, offset, values, size, length);
        size += length;
    }

    /**
     * Returns the value for the specified key.
     * Note this does a .equals() comparison of each key in reverse order until the specified key is found.
     */
    public V get(K key){
        Object[] keys = this.keys;
        int i = size - 1;
        if(key == null){
            for(; i >= 0; i--)
                if(keys[i] == key) return values[i];
        }else{
            for(; i >= 0; i--)
                if(key.equals(keys[i])) return values[i];
        }
        return null;
    }

    /**
     * Returns the key for the specified value.
     * Note this does a comparison of each value in reverse order until the specified value is found.
     * @param identity If true, == comparison will be used. If false, .equals() comparison will be used.
     */
    public K getKey(V value, boolean identity){
        Object[] values = this.values;
        int i = size - 1;
        if(identity || value == null){
            for(; i >= 0; i--)
                if(values[i] == value) return keys[i];
        }else{
            for(; i >= 0; i--)
                if(value.equals(values[i])) return keys[i];
        }
        return null;
    }

    public K getKeyAt(int index){
        checkIndex(index);
        return keys[index];
    }

    public V getValueAt(int index){
        checkIndex(index);
        return values[index];
    }

    public K firstKey(){
        if(size == 0) throw new IllegalStateException("Map is empty.");
        return keys[0];
    }

    public V firstValue(){
        if(size == 0) throw new IllegalStateException("Map is empty.");
        return values[0];
    }

    public void setKey(int index, K key){
        checkIndex(index);
        beforeModify();
        keys[index] = key;
    }

    public void setValue(int index, V value){
        checkIndex(index);
        beforeModify();
        values[index] = value;
    }

    public void insert(int index, K key, V value){
        checkInsert(index);
        insertAt(index);
        keys[index] = key;
        values[index] = value;
    }

    public boolean containsKey(K key){
        return indexOfKey(key) != -1;
    }

    /** @param identity If true, == comparison will be used. If false, .equals() comparison will be used. */
    public boolean containsValue(V value, boolean identity){
        return indexOfValue(value, identity) != -1;
    }

    public int indexOfKey(K key){
        Object[] keys = this.keys;
        if(key == null){
            for(int i = 0, n = size; i < n; i++)
                if(keys[i] == key) return i;
        }else{
            for(int i = 0, n = size; i < n; i++)
                if(key.equals(keys[i])) return i;
        }
        return -1;
    }

    public int indexOfValue(V value, boolean identity){
        Object[] values = this.values;
        if(identity || value == null){
            for(int i = 0, n = size; i < n; i++)
                if(values[i] == value) return i;
        }else{
            for(int i = 0, n = size; i < n; i++)
                if(value.equals(values[i])) return i;
        }
        return -1;
    }

    public V removeKey(K key){
        int index = indexOfKey(key);
        if(index == -1) return null;
        V value = values[index];
        removeIndex(index);
        return value;
    }

    public boolean removeValue(V value, boolean identity){
        int index = indexOfValue(value, identity);
        if(index == -1) return false;
        removeIndex(index);
        return true;
    }

    /** Removes the key/value pair at the specified index. */
    public void removeIndex(int index){
        checkIndex(index);
        removeAt(index);
    }

    /** Returns the last key. */
    public K peekKey(){
        return keys[size - 1];
    }

    /** Returns the last value. */
    public V peekValue(){
        return values[size - 1];
    }

    /**
     * Reduces the size of the backing arrays to the size of the actual number of entries.
     * This is useful to release memory when many items have been removed,
     * or if it is known that more entries will not be added.
     */
    public void shrink(){
        if(keys.length == size) return;
        resize(size);
    }

    /**
     * Increases the size of the backing arrays to accommodate the specified number of additional entries.
     * Useful before adding many entries to avoid multiple backing array resizes.
     */
    public void ensureCapacity(int additionalCapacity){
        reserve(additionalCapacity);
    }

    /** @return the keys, as the values are copied and freed with them by the overrides. */
    @Override
    protected Object items(){
        return keys;
    }

    @Override
    public int capacity(){
        return keys.length;
    }

    @Override
    protected void resize(int newSize){
        K[] newKeys = Reflect.newArray(keys, newSize);
        System.arraycopy(keys, 0, newKeys, 0, Math.min(size, newKeys.length));
        this.keys = newKeys;

        V[] newValues = Reflect.newArray(values, newSize);
        System.arraycopy(values, 0, newValues, 0, Math.min(size, newValues.length));
        this.values = newValues;
    }

    @Override
    protected ArrayMap<K, V> create(int capacity){
        return new ArrayMap<>(ordered, capacity, keys.getClass().getComponentType(), values.getClass().getComponentType());
    }

    @Override
    protected boolean sameType(Object other){
        return other instanceof ArrayMap;
    }

    @Override
    public Entries<K, V> newIterator(){
        return new Entries<>(this);
    }

    @Override
    protected void swapItems(int first, int second){
        K key = keys[first];
        keys[first] = keys[second];
        keys[second] = key;
        V value = values[first];
        values[first] = values[second];
        values[second] = value;
    }

    /** Compares the keys, which must implement {@link Comparable}. */
    @Override
    @SuppressWarnings("unchecked")
    protected int compareItems(int first, int second){
        return ((Comparable<Object>)keys[first]).compareTo(keys[second]);
    }

    /** Does an insertion sort. */
    @Override
    protected void sortItems(int from, int to){
        for(int i = from + 1; i < to; i++){
            for(int j = i; j > from && compareItems(j - 1, j) > 0; j--){
                swapItems(j, j - 1);
            }
        }
    }

    @Override
    protected void freeItems(int from, int to){
        Arrays.fill(keys, from, to, null);
        Arrays.fill(values, from, to, null);
    }

    @Override
    protected boolean equalItem(ArrayMap<K, V> other, int otherIndex, int index){
        return Structs.eq(keys[index], other.keys[otherIndex]) && Structs.eq(values[index], other.values[otherIndex]);
    }

    @Override
    protected void appendItem(StringBuilder buffer, int index){
        buffer.append(keys[index]).append('=').append(values[index]);
    }

    @Override
    protected int hashItem(int index){
        K key = keys[index];
        V value = values[index];
        return Structs.hash(key) * 31 + Structs.hash(value);
    }

    @Override
    protected void copyItems(ArrayMap<K, V> from, int fromIndex, ArrayMap<K, V> to, int toIndex, int count){
        System.arraycopy(from.keys, fromIndex, to.keys, toIndex, count);
        System.arraycopy(from.values, fromIndex, to.values, toIndex, count);
    }

    /** Removes through {@link #removeIndex(int)}, which the subclasses may change. */
    @Override
    protected void removeItem(int index){
        removeIndex(index);
    }

    /** Compares the entries like a map, whatever their order. */
    @Override
    protected boolean equalItems(ArrayMap<K, V> other){
        if(other.size != size) return false;
        for(int i = 0, n = size; i < n; i++){
            K key = keys[i];
            V value = values[i];
            if(!Structs.eq(value, other.get(key)) || value == null && !other.containsKey(key)) return false;
        }
        return true;
    }

    /** The hash of the entries, whatever their order. */
    @Override
    public int hashCode(){
        int h = 0;
        for(int i = 0, n = size; i < n; i++){
            h += hashItem(i);
        }
        return h;
    }

    @Override
    public String toString(){
        return size == 0 ? "{}" : join(new StringBuilder(32).append('{'), ", ").append('}').toString();
    }

    /** @see #iterator() */
    public Entries<K, V> entries(){
        return iterator();
    }

    /**
     * Returns an iterator for the values in the map. Remove is supported.
     * Same iterator instance is returned each time.
     * Use the {@link Values} constructor for nested or multithreaded iteration.
     */
    public Values<V> values(){
        Values<V> it = reuse(values1, values2);
        if(it != null) return it;
        values2 = values1;
        return values1 = new Values<>(this);
    }

    /**
     * Returns an iterator for the keyx in the map. Remove is supported.
     * Same iterator instance is returned each time.
     * Use the {@link Keys} constructor for nested or multithreaded iteration.
     */
    public Keys<K> keys(){
        Keys<K> it = reuse(keys1, keys2);
        if(it != null) return it;
        keys2 = keys1;
        return keys1 = new Keys<>(this);
    }


    protected static abstract class MapIterator<M extends ArrayMap<?, ?>, T> extends SeqIterator<T>{
        protected final M map;

        protected MapIterator(M map){
            super(map);
            this.map = map;
        }

        protected void end(){
            index = map.size;
            done = true;
        }
    }

    public static class Entries<K, V> extends MapIterator<ArrayMap<K, V>, Entry<K, V>>{
        protected final Entry<K, V> entry = new Entry<>();

        public Entries(ArrayMap<K, V> map){
            super(map);
        }

        /** Note the same entry instance is returned each time this method is called. */
        @Override
        public Entry<K, V> next(){
            int i = advance();
            entry.key = map.keys[i];
            entry.value = map.values[i];
            return entry;
        }
    }

    public static class Values<V> extends MapIterator<ArrayMap<?, V>, V>{
        public Values(ArrayMap<?, V> map){
            super(map);
        }

        @Override
        public V next(){
            return map.values[advance()];
        }

        /** Returns a new sequence with the remaining values. */
        public Seq<V> toArray(){
            Seq<V> array = new Seq<>(true, map.values, index, map.size - index);
            end();
            return array;
        }

        /** Adds the remaining values to the specified sequence. */
        public Seq<V> toArray(Seq<V> array){
            array.addAll(map.values, index, map.size - index);
            end();
            return array;
        }
    }

    public static class Keys<K> extends MapIterator<ArrayMap<K, ?>, K>{
        public Keys(ArrayMap<K, ?> map){
            super(map);
        }

        @Override
        public K next(){
            return map.keys[advance()];
        }

        /** Returns a new sequence with the remaining keys. */
        public Seq<K> toArray(){
            Seq<K> array = new Seq<>(true, map.keys, index, map.size - index);
            end();
            return array;
        }

        /** Adds the remaining keys to the specified sequence. */
        public Seq<K> toArray(Seq<K> array){
            array.addAll(map.keys, index, map.size - index);
            end();
            return array;
        }
    }
}
