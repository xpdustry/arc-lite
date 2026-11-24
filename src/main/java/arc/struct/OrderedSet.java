package arc.struct;

/**
 * An {@link ObjectSet} that also stores keys in an {@link Seq} using the insertion order.
 * {@link #iterator() Iteration} is ordered and faster than an unordered set.
 * Keys can also be accessed and the order changed using {@link #orderedItems()}.
 * <p>
 * There is some additional overhead for add and remove O(n), because of the ordered keys.
 * When used for faster iteration versus ObjectSet and the order does not actually matter,
 * copying during remove can be greatly reduced by setting {@link Seq#ordered} to false for {@link #orderedItems()}.
 *
 * @author Nathan Sweet
 */
public class OrderedSet<T> extends ObjectSet<T>{
    protected final Seq<T> items;

    /** Creates a new set with an initial capacity of 51 and a load factor of 0.8. */
    public OrderedSet(){
        items = new Seq<>();
    }

    /**
     * Creates a new set with the specified initial capacity and load factor.
     * This set will hold initialCapacity items before growing the backing table.
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    public OrderedSet(int initialCapacity, float loadFactor){
        super(initialCapacity, loadFactor);
        items = new Seq<>(initialCapacity);
    }

    /**
     * Creates a new set with a load factor of 0.8.
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    public OrderedSet(int initialCapacity){
        super(initialCapacity);
        items = new Seq<>(initialCapacity);
    }

    /** Creates a new set identical to the specified set. */
    public OrderedSet(OrderedSet<? extends T> set){
        super(set);
        items = new Seq<>(set.items);
    }

    @SafeVarargs
    public static <T> OrderedSet<T> with(T... array){
        OrderedSet<T> set = new OrderedSet<>();
        set.addAll(array);
        return set;
    }

    @Override
    public OrderedSet<T> copy(){
        return new OrderedSet<>(this);
    }

    @Override
    public boolean add(T key){
        if(!super.add(key)) return false;
        items.add(key);
        return true;
    }

    /**
     * Sets the key at the specified index.
     * Returns true if the key was added to the set or false if it was already in the set.
     * If this set already contains the key, the existing key's index is changed if needed and false is returned.
     */
    public boolean add(T key, int index){
        if(key == null) return false;
        if(index < 0 || index > items.size) throw new IndexOutOfBoundsException("index can't be > size: " + index + " > " + size);
        if(!super.add(key)){
            int oldIndex = items.indexOf(key, false);
            if(oldIndex != index){
                T old = items.remove(oldIndex);
                items.insert(Math.min(index, items.size), old);
            }
            return false;
        }
        items.insert(index, key);
        return true;
    }

    public void addAll(OrderedSet<T> set){
        addAll(set.items);
    }

    @Override
    public void ensureCapacity(int additionalCapacity){
        super.ensureCapacity(additionalCapacity);
        items.ensureCapacity(additionalCapacity);
    }

    public T removeIndex(int index){
        T key = items.remove(index);
        super.removeAt(locateKey(key));
        return key;
    }

    /**
     * Changes the item {@code before} to {@code after} without changing its position in the order.
     * Returns true if {@code after} has been added to the OrderedSet and {@code before} has been removed;
     * returns false if {@code after} is already present or {@code before} is not present.
     * If you are iterating over an OrderedSet and have an index, you should prefer {@link #alterIndex(int, Object)},
     * which doesn't need to search for an index like this does and so can be faster.
     * @param before an item that must be present for this to succeed
     * @param after an item that must not be in this set for this to succeed
     * @return true if {@code before} was removed and {@code after} was added, false otherwise
     */
    public boolean alter(T before, T after){
        if(after == null || contains(after)) return false;
        int i = before == null ? -1 : locateKey(before);
        if(i < 0) return false;
        super.removeAt(i);
        super.add(after);
        items.set(items.indexOf(before, false), after);
        return true;
    }

    /**
     * Changes the item at the given {@code index} in the order to {@code after},
     * without changing the ordering of other items.
     * If {@code after} is already present, this returns false;
     * it will also return false if {@code index} is invalid for the size of this set.
     * Otherwise, it returns true. Unlike {@link #alter(Object, Object)}, this operates in constant time.
     * @param index the index in the order of the item to change; must be non-negative and less than {@link #size}
     * @param after the item that will replace the contents at {@code index}; this item must not be present for this to succeed
     * @return true if {@code after} successfully replaced the contents at {@code index}, false otherwise
     */
    public boolean alterIndex(int index, T after){
        if(index < 0 || index >= size || after == null || contains(after)) return false;
        super.removeAt(locateKey(items.get(index)));
        super.add(after);
        items.set(index, after);
        return true;
    }

    @Override
    public void clear(int maximumCapacity){
        items.clear();
        super.clear(maximumCapacity);
    }

    @Override
    public void clear(){
        items.clear();
        super.clear();
    }

    @Override
    protected int removeAt(int index){
        items.remove(keyTable[index], false);
        return super.removeAt(index);
    }

    public Seq<T> orderedItems(){
        return items;
    }

    @Override
    public T first(){
        return items.first();
    }

    @Override
    public OrderedSetIterator<T> newIterator(){
        return new OrderedSetIterator<>(this);
    }

    @Override
    public OrderedSetIterator<T> iterator(){
        return (OrderedSetIterator<T>)super.iterator();
    }

    /** Appends the items in their order. */
    @Override
    protected String toString(String separator, String open, String close){
        StringBuilder buffer = new StringBuilder(32).append(open);
        T[] items = this.items.items;
        for(int i = 0, n = this.items.size; i < n; i++){
            if(i > 0) buffer.append(separator);
            appendObject(buffer, items[i]);
        }
        return buffer.append(close).toString();
    }


    public static class OrderedSetIterator<K> extends ObjectSetIterator<K>{
        protected final OrderedSet<K> ordered;
        protected final Seq<K> items;

        public OrderedSetIterator(OrderedSet<K> set){
            super(set);
            ordered = set;
            items = set.items;
        }

        @Override
        public void reset(){
            currentIndex = -1;
            nextIndex = 0;
            hasNext = set.size > 0;
        }

        @Override
        public K next(){
            checkNext();
            K key = items.get(nextIndex);
            currentIndex = nextIndex;
            nextIndex++;
            hasNext = nextIndex < set.size;
            return key;
        }

        @Override
        public void remove(){
            if(currentIndex < 0) throw new IllegalStateException("next must be called before remove.");
            ordered.removeIndex(currentIndex);
            nextIndex = currentIndex;
            currentIndex = -1;
            hasNext = nextIndex < set.size;
        }

        @Override
        public Seq<K> toSeq(Seq<K> array){
            array.addAll(items, nextIndex, items.size - nextIndex);
            nextIndex = items.size;
            hasNext = false;
            done = true;
            return array;
        }

        @Override
        public Seq<K> toSeq(){
            return toSeq(new Seq<>(true, set.size - nextIndex));
        }
    }
}
