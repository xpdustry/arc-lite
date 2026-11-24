package arc.struct;

import arc.func.*;
import arc.math.*;

import java.util.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class HashTable<I extends HashTable.TableIterator, S extends HashTable>{
    public int size;
    public final float loadFactor;
    protected int threshold;
    /**
     * Used by {@link #place(int)} to bit shift the upper bits of a {@code long} into a usable range
     * (&gt;= 0 and &lt;= {@link #mask}).
     * The shift can be negative, which is convenient to match the number of bits in mask:
     * if mask is a 7-bit  number, a shift of -7 shifts the upper 7 bits into the lowest 7 positions.
     * This class sets the shift &gt; 32 and &lt; 64, which if used with an int will still move the upper bits
     * of an int to the lower bits due to Java's implicit modulus on shifts.
     * <p>
     * {@link #mask} can also be used to mask the low bits of a number, which may be faster for some hashcodes,
     * if {@link #place(int)} is overridden.
     */
    protected int shift;
    /**
     * A bitmask used to confine hashcodes to the size of the table.
     * Must be all 1 bits in its low positions, ie a power of two minus 1.
     * If {@link #place(int)} is overridden, this can be used instead of {@link #shift} to isolate usable bits of a hash.
     */
    protected int mask;
    /** Whether the table has the zero key. */
    protected boolean hasZeroValue;

    protected transient I first, second;

    protected HashTable(int initialCapacity, float loadFactor){
        if(loadFactor <= 0f || loadFactor >= 1f)
            throw new IllegalArgumentException("loadFactor must be > 0 and < 1: " + loadFactor);
        this.loadFactor = loadFactor;
        setTableSize(tableSize(initialCapacity, loadFactor));
    }

    protected abstract Object keyTable();

    /** {@code null} for sets. */
    protected Object valueTable(){
        return null;
    }

    protected abstract void resize(int newSize);

    public abstract S copy();

    protected abstract boolean sameType(Object other);

    /** Allocates a new iterator that is not reused, unlike {@link #iterator()}. */
    public abstract I newIterator();

    protected abstract boolean occupied(int index);

    protected abstract int placeIndex(int index);

    protected abstract void clearEntries(int from, int to);

    /** @param onlyKeys whether only the key is compared, for a map. */
    protected abstract boolean hasEntry(S other, int index, boolean onlyKeys);

    protected abstract void appendEntry(StringBuilder buffer, int index);

    protected abstract int hashEntry(int index);

    protected abstract void addEntry(S other, int index);

    /** Called before entries are overwritten (moved, removed, etc), not added. */
    protected void beforeModify(){}

    protected void setTableSize(int tableSize){
        threshold = (int)(tableSize * loadFactor);
        mask = tableSize - 1;
        shift = Long.numberOfLeadingZeros(mask);
    }

    protected int tableSize(int capacity, float loadFactor){
        if(capacity < 0) throw new IllegalArgumentException("capacity must be >= 0: " + capacity);
        int tableSize = Mathf.nextPowerOfTwo(Math.max(2, (int)Math.ceil(capacity / loadFactor)));
        if(tableSize > 1 << 30) throw new IllegalArgumentException("The required capacity is too large: " + capacity);
        return tableSize;
    }

    protected void move(int from, int to){
        Object keys = keyTable(), values = valueTable();
        System.arraycopy(keys, from, keys, to, 1);
        if(values != null) System.arraycopy(values, from, values, to, 1);
    }

    protected void copyTable(HashTable other){
        if(mask != other.mask) resize(other.mask + 1); // Be sure
        int length = other.mask + 1;
        System.arraycopy(other.keyTable(), 0, keyTable(), 0, length);
        Object values = other.valueTable();
        if(values != null) System.arraycopy(values, 0, valueTable(), 0, length);
        size = other.size;
        hasZeroValue = other.hasZeroValue;
    }

    protected S self(){
        return (S)this;
    }

    public S with(Cons<S> cons){
        S self = self();
        cons.get(self);
        return self;
    }

    public void set(S other){
        if(other == this) return;
        clear();
        addEntries(other);
    }

    /** @return whether this table has every entry of the other one, with equal values for a map */
    public boolean containsAll(S other){
        return containsAll(other, false);
    }

    /** @param onlyKeys whether only the keys are compared, for a map. */
    public boolean containsAll(S other, boolean onlyKeys){
        return hasEntries(other, onlyKeys);
    }

    /** Removes the entries that the other table has. @return whether this table changed */
    public boolean removeAll(S other){
        return removeAll(other, false);
    }

    /** @param onlyKeys whether only the keys are compared, for a map. */
    public boolean removeAll(S other, boolean onlyKeys){
        return removeEntries(other, true, onlyKeys);
    }

    /** Removes the entries that the other table does not have. @return whether this table changed */
    public boolean retainAll(S other){
        return retainAll(other, false);
    }

    /** @param onlyKeys whether only the keys are compared, for a map. */
    public boolean retainAll(S other, boolean onlyKeys){
        return removeEntries(other, false, onlyKeys);
    }

    public int size(){
        return size;
    }

    public boolean isEmpty(){
        return size == 0;
    }

    public boolean any(){
        return size > 0;
    }

    /** @deprecated Use {@link #any()}. */
    @Deprecated
    public boolean notEmpty(){
        return any();
    }

    /** @return the number of entries that the table holds before growing. */
    public int capacity(){
        return threshold;
    }

    /**
     * Clears the table, leaving the backing arrays at the current capacity. When the capacity is high and the
     * population is low, iteration can be unnecessarily slow: {@link #clear(int)} can reduce the capacity.
     */
    public void clear(){
        if(size == 0) return;
        beforeModify();
        size = 0;
        clearZero();
        clearEntries(0, mask + 1);
    }

    /**
     * Reduces the size of the backing arrays to be the specified capacity / loadFactor, or less.
     * If the capacity is already less, nothing is done.
     * If the table contains more items than the specified capacity, the next highest power of two capacity is used instead.
     */
    public void shrink(int maximumCapacity){
        if(maximumCapacity < 0) throw new IllegalArgumentException("maximumCapacity must be >= 0: " + maximumCapacity);
        if(size > maximumCapacity) maximumCapacity = size;
        int tableSize = tableSize(maximumCapacity, loadFactor);
        if(mask + 1 > tableSize) resize(tableSize);
    }

    /**
     * Clears the table and reduces the size of the backing arrays to be the specified capacity / loadFactor,
     * if they are larger.
     */
    public void clear(int maximumCapacity){
        int tableSize = tableSize(maximumCapacity, loadFactor);
        if(mask + 1 <= tableSize){
            clear();
            return;
        }
        beforeModify();
        size = 0;
        clearZero();
        resize(tableSize);
    }

    public void ensureCapacity(int additionalCapacity){
        int tableSize = tableSize(size + additionalCapacity, loadFactor);
        if(mask + 1 < tableSize) resize(tableSize);
    }

    protected void clearZero(){
        hasZeroValue = false;
    }

    protected boolean removeZero(){
        if(!hasZeroValue) return false;
        beforeModify();
        clearZero();
        size--;
        return true;
    }

    /** Returns an index >= 0 and <= {@link #mask} for the specified key. This can be overridden to customize hashing. */
    protected int place(Object item){
        return place(item.hashCode());
    }

    /**
     * Returns an index >= 0 and <= {@link #mask} for the specified {@code item}.
     * <p>
     * The default implementation uses Fibonacci hashing on the item's {@link Object#hashCode()}:
     * the hashcode is multiplied by a long constant (2 to the 64th, divided by the golden ratio)
     * then the uppermost bits are shifted into the lowest positions to obtain an index in the desired range.
     * Multiplication by a long may be slower than int (eg on GWT) but greatly improves rehashing,
     * allowing even very poor hashcodes, such as those that only differ in their upper bits,
     * to be used without high collision rates.
     * Fibonacci hashing has increased collision rates when all or most hashcodes are multiples of larger Fibonacci numbers
     * (see <a href="https://probablydance.com/2018/06/16/fibonacci-hashing-the-optimization-that-the-world-forgot-or-a-better-alternative-to-integer-modulo/">
     * Malte Skarupke's blog post</a>).
     * <p>
     * This method can be overridden to customizing hashing.
     * This may be useful eg in the unlikely event that most hashcodes are Fibonacci numbers,
     * if keys provide poor or incorrect hashcodes, or to simplify hashing if keys provide high quality hashcodes
     * and don't need Fibonacci hashing.
     */
    protected int place(long item){
        return (int)(item * 0x9E3779B97F4A7C15L >>> shift);
    }

    /** Returns the index of the key if already present, else -(index + 1) for the next empty index. */
    protected int locateKey(Object key){
        if(key == null) throw new IllegalArgumentException("key cannot be null.");
        Object[] keyTable = (Object[])keyTable();
        for(int i = place(key); ; i = i + 1 & mask){
            Object other = keyTable[i];
            if(other == null) return -(i + 1); // Empty space is available.
            if(other.equals(key)) return i; // Same key was found.
        }
    }

    /** @return the first empty index from the start, where a key that is not in the table goes. */
    protected int emptyIndex(int start){
        for(int i = start; ; i = i + 1 & mask){
            if(!occupied(i)) return i;
        }
    }

    /**
     * Removes the entry at the index of the table, and moves back the next ones, to avoid collisions.
     * @return the index left empty, which differs from the specified one if an entry was moved there
     */
    protected int removeAt(int index){
        beforeModify();
        int mask = this.mask, next = index + 1 & mask;
        while(occupied(next)){
            int placement = placeIndex(next);
            if((next - placement & mask) > (index - placement & mask)){
                move(next, index);
                index = next;
            }
            next = next + 1 & mask;
        }
        clearEntries(index, index + 1);
        size--;
        return index;
    }

    protected void addEntries(HashTable other){
        S table = (S)other;
        ensureCapacity(table.size);
        if(table.hasZeroValue) addZero(table);
        for(int i = 0, n = table.mask + 1; i < n; i++){
            if(table.occupied(i)) addEntry(table, i);
        }
    }

    protected boolean hasEntries(S other, boolean onlyKeys){
        if(other.hasZeroValue && !(hasZeroValue && (onlyKeys || sameZero(other)))) return false;
        S self = self();
        for(int i = other.mask; i >= 0; i--){
            if(other.occupied(i) && !other.hasEntry(self, i, onlyKeys)) return false;
        }
        return true;
    }

    protected boolean removeEntries(S other, boolean present, boolean onlyKeys){
        boolean removed = false;
        if(hasZeroValue && (other.hasZeroValue && (onlyKeys || sameZero(other))) == present){
            removeZero();
            removed = true;
        }
        for(int i = 0; i <= mask; i++){
            // The removal can move an entry back to the index, which is then checked again
            while(occupied(i) && hasEntry(other, i, onlyKeys) == present){
                removeAt(i);
                removed = true;
            }
        }
        return removed;
    }

    protected void appendObject(StringBuilder buffer, Object item){
        buffer.append(item == this ? "(this)" : item);
    }

    //NOTE: for a non-primitive implementation, the "zero key" can the the "null key"
    protected boolean appendZero(StringBuilder buffer){
        if(hasZeroValue) buffer.append('0');
        return hasZeroValue;
    }

    protected int hashZero(){
        return 0;
    }

    protected boolean sameZero(S other){
        return other.hasZeroValue == hasZeroValue;
    }

    protected void addZero(S other){}

    @Override
    public int hashCode(){
        int h = size + hashZero();
        for(int i = mask; i >= 0; i--){
            if(occupied(i)) h += hashEntry(i);
        }
        return h;
    }

    protected boolean equalEntries(S other){
        if(other.size != size || !sameZero(other)) return false;
        for(int i = mask; i >= 0; i--){
            if(occupied(i) && !hasEntry(other, i, false)) return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object other){
        return other == this || sameType(other) && equalEntries((S)other);
    }

    @Override
    public String toString(){
        // implementations with primitives as key usually uses brackets
        boolean objects = keyTable() instanceof Object[];
        return toString(", ", objects ? "{" : "[", objects ? "}" : "]");
    }

    public String toString(String separator){
        return toString(separator, "", "");
    }

    protected String toString(String separator, String open, String close){
        StringBuilder buffer = new StringBuilder(32).append(open);
        boolean first = !appendZero(buffer);
        for(int i = mask; i >= 0; i--){
            if(!occupied(i)) continue;
            if(!first) buffer.append(separator);
            first = false;
            appendEntry(buffer, i);
        }
        return buffer.append(close).toString();
    }

    /** @see #iterator() */
    public I entries(){
        return iterator();
    }

    /**
     * Returns an iterator for the items in this sequence. Remove is supported.
     * <p>
     * Always returns the two same iterators. Use {@link #newIterator()} for multithreaded or nested iteration.
     */
    public I iterator(){
        I it = reuse(first, second);
        if(it != null) return it;
        second = first;
        return first = newIterator();
    }

    protected <A extends TableIterator> A reuse(A first, A second){
        A it = first != null && first.done ? first : second != null && second.done ? second : null;
        if(it != null){
            it.reset();
            it.done = false;
        }
        return it;
    }


    public static abstract class TableIterator<T, I extends Iterator<T>> implements Iterable<T>, Iterator<T>{
        protected static final int INDEX_ILLEGAL = -2, INDEX_ZERO = -1;

        public boolean hasNext;
        protected final HashTable table;
        protected int nextIndex, currentIndex;
        protected boolean done;

        /** The subclass should call {@link #reset()} once it is initialized. */
        protected TableIterator(HashTable table){
            this.table = table;
        }

        /** Whether this iterator can be reused. */
        public boolean done(){
            return done;
        }

        public void reset(){
            currentIndex = INDEX_ILLEGAL;
            nextIndex = INDEX_ZERO;
            if(table.hasZeroValue) hasNext = true;
            else findNextIndex();
        }

        public boolean hasNext(){
            if(!hasNext) done = true;
            return hasNext;
        }

        protected void checkNext(){
            if(!hasNext) throw new NoSuchElementException();
        }

        /** Sets {@link #nextIndex} to the next occupied index after it, and {@link #hasNext} to whether there is one. */
        protected abstract void findNextIndex();

        /** Finds the next index with {@link HashTable#occupied(int)}, called on the subclass of the table for speed. */
        protected final void findNextIndex(HashTable table){
            while(++nextIndex <= table.mask && !table.occupied(nextIndex));
            hasNext = nextIndex <= table.mask;
        }

        protected final int advance(){
            checkNext();
            int index = nextIndex;
            currentIndex = index;
            findNextIndex();
            return index;
        }

        public void remove(){
            int i = currentIndex;
            if(i == INDEX_ZERO && table.removeZero()){
                currentIndex = INDEX_ILLEGAL;
                return;
            }
            if(i < 0) throw new IllegalStateException("next must be called before remove.");
            if(table.removeAt(i) != i) --nextIndex;
            currentIndex = INDEX_ILLEGAL;
        }

        @Override
        public I iterator(){
            return (I)this;
        }

        public Seq<T> toSeq(Seq<T> array){
            while(hasNext()) array.add(next());
            return array;
        }
    }
}
