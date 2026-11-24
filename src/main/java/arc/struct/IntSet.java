package arc.struct;

import arc.func.*;

import java.util.*;

/**
 * An unordered set where the keys are ints.
 * No allocation is done except when growing the table size.
 * <p>
 * The table uses linear probing, with a backward shift on removal, and Fibonacci hashing to spread poor hashcodes.
 * So add/contains/remove are typically O(1) with a worst case of O(n) when many keys collide into the same cluster.
 * Load factors greater than 0.91 greatly increase the chances of resizing to the next higher power of two.
 *
 * @author Nathan Sweet
 */
public class IntSet extends HashTable<IntSet.IntSetIterator, IntSet> implements Iterable<Integer>{
    protected int[] keyTable;

    /** Creates a new set with an initial capacity of 51 and a load factor of 0.8. */
    public IntSet(){
        this(51, 0.8f);
    }

    /**
     * Creates a new set with a load factor of 0.8.
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    public IntSet(int initialCapacity){
        this(initialCapacity, 0.8f);
    }

    /**
     * Creates a new set with the specified initial capacity and load factor.
     * This set will hold initialCapacity items before growing the backing table.
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    public IntSet(int initialCapacity, float loadFactor){
        super(initialCapacity, loadFactor);
        keyTable = new int[mask + 1];
    }

    /** Creates a new set identical to the specified set. */
    public IntSet(IntSet set){
        this(set.capacity(), set.loadFactor);
        copyTable(set);
    }

    @Override
    public IntSet copy(){
        return new IntSet(this);
    }

    public void each(Intc cons){
        for(IntSetIterator iter = iterator(); iter.hasNext();)
            cons.get(iter.nextInt());
    }

    /** @return the sum of all keys. */
    public int sum(){
        int sum = 0;
        for(int key : keyTable){
            sum += key;
        }
        return sum;
    }

    /** @return the number of keys matching the predicate. */
    public int count(IntBoolf pred){
        int count = hasZeroValue && pred.get(0) ? 1 : 0;
        for(int key : keyTable){
            if(key != 0 && pred.get(key)) count++;
        }
        return count;
    }

    /** @return whether all the keys match the predicate. */
    public boolean allMatch(IntBoolf pred){
        if(hasZeroValue && !pred.get(0)) return false;
        for(int key : keyTable){
            if(key != 0 && !pred.get(key)) return false;
        }
        return true;
    }

    /** Removes the keys matching the predicate. */
    public IntSet removeAll(IntBoolf pred){
        for(IntSetIterator iterator = iterator(); iterator.hasNext();){
            if(pred.get(iterator.nextInt())) iterator.remove();
        }
        return this;
    }

    /** Returns true if the key was added to the set or false if it was already in the set. */
    public boolean add(int key){
        if(key == 0){
            if(hasZeroValue) return false;
            hasZeroValue = true;
            size++;
            return true;
        }
        int i = locateKey(key);
        if(i >= 0) return false; // Existing key was found.
        i = -(i + 1); // Empty space was found.
        keyTable[i] = key;
        if(++size >= threshold) resize(keyTable.length << 1);
        return true;
    }

    public void addAll(IntSeq array){
        addAll(array.items, 0, array.size);
    }

    public void addAll(IntSeq array, int offset, int length){
        if(offset + length > array.size)
            throw new IllegalArgumentException("offset + length must be <= size: " + offset + " + " + length + " <= " + array.size);
        addAll(array.items, offset, length);
    }

    public void addAll(int... array){
        addAll(array, 0, array.length);
    }

    public void addAll(int[] array, int offset, int length){
        ensureCapacity(length);
        for(int i = offset, n = i + length; i < n; i++)
            add(array[i]);
    }

    public void addAll(IntSet set){
        addEntries(set);
    }

    /** Skips checks for existing keys, doesn't increment size, doesn't need to handle key 0. */
    protected void addResize(int key){
        keyTable[emptyIndex(place(key))] = key;
    }

    /** Returns true if the key was removed. */
    public boolean remove(int key){
        if(key == 0) return removeZero();

        int i = locateKey(key);
        if(i < 0) return false;
        removeAt(i);
        return true;
    }

    /** Returns whether the key is in the set. */
    public boolean contains(int key){
        if(key == 0) return hasZeroValue;
        return locateKey(key) >= 0;
    }

    public int first(){
        if(hasZeroValue) return 0;
        int[] keyTable = this.keyTable;
        for(int element : keyTable) if(element != 0) return element;
        throw new IllegalStateException("IntSet is empty.");
    }

    @Override
    protected Object keyTable(){
        return keyTable;
    }

    @Override
    protected void resize(int newSize){
        int oldCapacity = keyTable.length;
        setTableSize(newSize);

        int[] oldKeyTable = keyTable;

        keyTable = new int[newSize];

        if(size > 0){
            for(int i = 0; i < oldCapacity; i++){
                int key = oldKeyTable[i];
                if(key != 0) addResize(key);
            }
        }
    }

    @Override
    protected boolean sameType(Object other){
        return other instanceof IntSet;
    }

    @Override
    public IntSetIterator newIterator(){
        return new IntSetIterator(this);
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
    protected boolean hasEntry(IntSet other, int index, boolean onlyKeys){
        return other.contains(keyTable[index]);
    }

    @Override
    protected void appendEntry(StringBuilder buffer, int index){
        buffer.append(keyTable[index]);
    }

    @Override
    protected int hashEntry(int index){
        return keyTable[index];
    }

    @Override
    protected void addEntry(IntSet other, int index){
        add(other.keyTable[index]);
    }

    @Override
    protected void addZero(IntSet other){
        add(0);
    }

    static public IntSet with(int... array){
        IntSet set = new IntSet(array.length);
        set.addAll(array);
        return set;
    }


    public static class IntSetIterator extends TableIterator<Integer, IntSetIterator>{
        protected final IntSet set;

        public IntSetIterator(IntSet set){
            super(set);
            this.set = set;
            reset();
        }

        @Override
        protected void findNextIndex(){
            findNextIndex(set);
        }

        @Override
        public Integer next(){
            return nextInt();
        }

        public int nextInt(){
            int i = advance();
            return i == INDEX_ZERO ? 0 : set.keyTable[i];
        }

        /** Returns a new sequence with the remaining keys. */
        public IntSeq toSeq(){
            return toSeq(new IntSeq(true, set.size));
        }

        /** Adds the remaining keys to the specified sequence. */
        public IntSeq toSeq(IntSeq array){
            while(hasNext()) array.add(nextInt());
            return array;
        }

        /** @deprecated use {@link #toSeq()} */
        @Deprecated
        public IntSeq toArray(){
            return toSeq();
        }
    }
}
