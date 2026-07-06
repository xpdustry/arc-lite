package arc.util;


import arc.func.*;
import arc.math.*;
import arc.struct.*;

import java.util.*;

public class Structs{

    public static boolean eq(Object a, Object b){
        return (a == b) || (a != null && a.equals(b));
    }

    @SafeVarargs
    public static <T> T[] arr(T... array){
        return array;
    }

    @SafeVarargs
    public static <T> Iterable<T> iterable(T... array){
        return () -> new Iterator<T>(){
            int index = 0;

            public boolean hasNext(){
                return index < array.length;
            }

            public T next(){
                if(index >= array.length) throw new NoSuchElementException();
                return array[index++];
            }
        };
    }

    public static <T, R> R[] map(T[] array, Class<R> type, Func<T, R> mapper){
        R[] next = Reflect.newArray(type, array.length);
        for(int i = 0; i < array.length; i++) next[i] = mapper.get(array[i]);
        return next;
    }

    /** Remove all values that match this predicate. */
    public static <T> void filter(Iterable<T> iterable, Boolf<T> removal){
        filter(iterable.iterator(), removal);
    }

    /** Remove all values that match this predicate. */
    public static <T> void filter(Iterator<T> it, Boolf<T> removal){
        while(it.hasNext()){
            if(removal.get(it.next())) it.remove();
        }
    }

    public static <T> T[] filter(T[] array, Boolf<T> removal){
        T[] temp = Reflect.newArray(array, array.length);
        int i = 0;
        for(T t : array){
            if(!removal.get(t)) temp[i++] = t;
        }
        if(i == array.length) return temp;
        T[] next = Reflect.newArray(array, i);
        System.arraycopy(temp, 0, next, 0, i);
        return next;
    }

    /** @deprecated This does the invert of others. */
    @Deprecated
    public static <T> T[] filter(Class<T> type, T[] array, Boolf<T> value){
        Seq<T> out = new Seq<>(true, array.length, type);
        for(T t : array){
            if(value.get(t)) out.add(t);
        }
        return out.toArray();
    }

    public static <T> Iterable<T> selectView(T[] array, Boolf<T> predicate){
        return () -> new Iterator<T>(){
            int index, nextIndex = -1;

            @Override
            public boolean hasNext(){
                if(nextIndex >= 0) return true;
                while(index < array.length){
                    if(predicate.get(array[index])){
                        nextIndex = index++;
                        return true;
                    }
                    index++;
                }
                return false;
            }

            @Override
            public T next(){
                if(!hasNext()) throw new NoSuchElementException();
                T value = array[nextIndex];
                nextIndex = -1;
                return value;
            }
        };
    }

    public static <T> Iterable<T> selectView(Iterable<T> array, Boolf<T> predicate){
        return () -> new Iterator<T>(){
            final Iterator<T> it = array.iterator();
            boolean ready;
            T next;

            @Override
            public boolean hasNext(){
                if(ready) return true;
                while(it.hasNext()){
                    T value = it.next();
                    if(predicate.get(value)){
                        next = value;
                        return ready = true;
                    }
                }
                return false;
            }

            @Override
            public T next(){
                if(!hasNext()) throw new NoSuchElementException();
                ready = false;
                T result = next;
                next = null;
                return result;
            }
        };
    }

    @SafeVarargs
    public static <T> T random(T... array){
        if(array.length == 0) return null;
        return array[Mathf.random(array.length - 1)];
    }

    @SafeVarargs
    public static <T> T random(Rand rand, T... array){
        if(array.length == 0) return null;
        return array[rand.random(array.length - 1)];
    }

    public static <T> int count(Iterable<T> array, Boolf<T> value){
        int total = 0;
        for(T t : array){
            if(value.get(t)) total ++;
        }
        return total;
    }

    public static <T> int count(T[] array, Boolf<T> value){
        int total = 0;
        for(T t : array){
            if(value.get(t)) total ++;
        }
        return total;
    }

    public static <T> int max(Iterable<T> array, Intf<T> intifier){
        boolean first = true;
        int index = 0;
        for(T i : array){
            int s = intifier.get(i);
            if(first) index = s;
            else if(s > index) index = s;
            first = false;
        }
        return index;
    }

    public static <T> int max(T[] array, Intf<T> intifier){
        boolean first = true;
        int index = 0;
        for(T i : array){
            int s = intifier.get(i);
            if(first) index = s;
            else if(s > index) index = s;
            first = false;
        }
        return index;
    }

    public static <T> int min(Iterable<T> array, Intf<T> intifier){
        boolean first = true;
        int index = 0;
        for(T i : array){
            int s = intifier.get(i);
            if(first) index = s;
            else if(s < index) index = s;
            first = false;
        }
        return index;
    }

    public static <T> int min(T[] array, Intf<T> intifier){
        boolean first = true;
        int index = 0;
        for(T i : array){
            int s = intifier.get(i);
            if(first) index = s;
            else if(s < index) index = s;
            first = false;
        }
        return index;
    }

    /**Uses identity comparisons.*/
    public static <T> boolean contains(T[] array, T value){
        for(T t : array){
            if(t == value || (value != null && value.equals(t))) return true;
        }
        return false;
    }

    public static <T> boolean contains(T[] array, Boolf<T> value){
        return find(array, value) != null;
    }

    public static <T> boolean contains(Iterable<T> array, Boolf<T> value){
        return find(array, value) != null;
    }

    public static <T> T find(T[] array, Boolf<T> value){
        for(T t : array){
            if(value.get(t)) return t;
        }
        return null;
    }

    public static <T> T find(Iterable<T> array, Boolf<T> value){
        for(T t : array){
            if(value.get(t)) return t;
        }
        return null;
    }

    public static <T> int indexOf(T[] array, T value){
        for(int i = 0; i < array.length; i++){
            if(array[i] == value) return i;
        }
        return -1;
    }

    public static <T> int indexOf(T[] array, Boolf<T> value){
        for(int i = 0; i < array.length; i++){
            if(value.get(array[i])) return i;
        }
        return -1;
    }

    public static <T> int indexOf(Iterable<T> array, Boolf<T> value){
        int i = 0;
        for(T t : array){
            if(value.get(t)) return i;
            i++;
        }
        return -1;
    }

    public static <T> T[] remove(T[] array, T value){
        return remove(array, indexOf(array, value));
    }

    public static <T> T[] remove(T[] array, int index){
        if(index < 0 || index >= array.length){
            return array;
        }

        T[] next = Reflect.newArray(array, array.length - 1);
        System.arraycopy(array, 0, next, 0, index);
        if(index < next.length){
            System.arraycopy(array, index + 1, next, index, next.length - index);
        }

        return next;
    }

    public static <T> T[] add(T[] array, T item){
        T[] next = Reflect.newArray(array, array.length + 1);
        next[array.length] = item;
        System.arraycopy(array, 0, next, 0, array.length);
        return next;
    }

    public static <T> T[] insert(T[] array, int index, T item){
        T[] next = Reflect.newArray(array, array.length + 1);
        if(index > 0) System.arraycopy(array, 0, next, 0, index);
        int tail = array.length - index;
        if(tail > 0) System.arraycopy(array, index, next, index + 1, tail);
        next[index] = item;
        return next;
   }

    public static <T> void swap(T[] array, int a, int b){
        T temp = array[a];
        array[a] = array[b];
        array[b] = temp;
    }

    /** Equivalent to Comparator#thenComparsing, but more compatible. */
    public static <T> Comparator<T> comps(Comparator<T> first, Comparator<T> second){
        return (a, b) -> {
            int value = first.compare(a, b);
            return value != 0 ? value : second.compare(a, b);
        };
    }

    public static <T, U> Comparator<T> comparing(Func<? super T, ? extends U> keyExtractor, Comparator<? super U> keyComparator){
        return (c1, c2) -> keyComparator.compare(keyExtractor.get(c1), keyExtractor.get(c2));
    }

    public static <T, U extends Comparable<? super U>> Comparator<T> comparing(Func<? super T, ? extends U> keyExtractor){
        return (c1, c2) -> keyExtractor.get(c1).compareTo(keyExtractor.get(c2));
    }

    public static <T> Comparator<T> comparingFloat(Floatf<? super T> keyExtractor){
        return (c1, c2) -> Float.compare(keyExtractor.get(c1), keyExtractor.get(c2));
    }

    public static <T> Comparator<T> comparingInt(Intf<? super T> keyExtractor){
        return (c1, c2) -> Integer.compare(keyExtractor.get(c1), keyExtractor.get(c2));
    }

    public static <T> Comparator<T> comparingLong(Longf<? super T> keyExtractor){
        return (c1, c2) -> Long.compare(keyExtractor.get(c1), keyExtractor.get(c2));
    }

    public static <T> Comparator<T> comparingBool(Boolf<? super T> keyExtractor){
        return (c1, c2) -> Boolean.compare(keyExtractor.get(c1), keyExtractor.get(c2));
    }

    @SafeVarargs
    public static <T> void each(Cons<T> cons, T... objects){
        for(T t : objects){
            cons.get(t);
        }
    }

    /** Alias of {@link #forEach}. */
    public static <T> void each(Iterable<T> i, Cons<T> cons){
        forEach(i, cons);
    }

    public static <T> void forEach(Iterable<T> i, Cons<T> cons){
        for(T t : i){
            cons.get(t);
        }
    }

    public static <T> T findMin(T[] arr, Comparator<T> comp){
        T result = null;
        for(T t : arr){
            if(result == null || comp.compare(result, t) > 0){
                result = t;
            }
        }
        return result;
    }

    public static <T> T findMin(T[] arr, Floatf<T> proc){
        T result = null;
        float min = Float.MAX_VALUE;
        for(T t : arr){
            float val = proc.get(t);
            if(val <= min){
                result = t;
                min = val;
            }
        }
        return result;
    }

    public static <T> T findMin(Iterable<T> arr, Comparator<T> comp){
        T result = null;
        for(T t : arr){
            if(result == null || comp.compare(result, t) > 0){
                result = t;
            }
        }
        return result;
    }

    public static <T> T findMin(Iterable<T> arr, Boolf<T> allow, Comparator<T> comp){
        T result = null;
        for(T t : arr){
            if(allow.get(t) && (result == null || comp.compare(result, t) > 0)){
                result = t;
            }
        }
        return result;
    }

    public static <T> boolean inBounds(int x, int y, T[][] array){
        return x >= 0 && y >= 0 && x < array.length && y < array[0].length;
    }

    public static boolean inBounds(int x, int y, int[][] array){
        return x >= 0 && y >= 0 && x < array.length && y < array[0].length;
    }

    public static boolean inBounds(int x, int y, float[][] array){
        return x >= 0 && y >= 0 && x < array.length && y < array[0].length;
    }

    public static boolean inBounds(int x, int y, boolean[][] array){
        return x >= 0 && y >= 0 && x < array.length && y < array[0].length;
    }

    public static <T> boolean inBounds(int x, int y, int z, T[][][] array){
        return x >= 0 && y >= 0 && z >= 0 && x < array.length && y < array[0].length && z < array[0][0].length;
    }

    public static boolean inBounds(int x, int y, int z, int[][][] array){
        return x >= 0 && y >= 0 && z >= 0 && x < array.length && y < array[0].length && z < array[0][0].length;
    }

    public static boolean inBounds(int x, int y, int z, int size, int padding){
        return x >= padding && y >= padding && z >= padding && x < size - padding && y < size - padding
                && z < size - padding;
    }

    public static boolean inBounds(int x, int y, int width, int height){
        return x >= 0 && y >= 0 && x < width && y < height;
    }
}
