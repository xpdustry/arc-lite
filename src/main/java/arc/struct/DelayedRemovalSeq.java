package arc.struct;

import arc.func.Boolf;

/**
 * Queues any removals done after {@link #begin()} is called to occur once {@link #end()} is called.
 * This can allow code out of your control to remove items without affecting iteration.
 * Between begin and end, most mutator methods will throw IllegalStateException. Only {@link #remove(int)},
 * {@link #remove(Object, boolean)}, {@link #removeRange(int, int)}, {@link #clear()}, and add methods are allowed.
 * <p>
 * Code using this class must not rely on items being removed immediately.
 * Consider using {@link SnapshotSeq} if this is a problem.
 * @author Nathan Sweet
 */
public class DelayedRemovalSeq<T> extends Seq<T>{
    private int iterating;
    private final IntSeq remove = new IntSeq(0);
    private int clear;

    public DelayedRemovalSeq(){
    }

    public DelayedRemovalSeq(Seq<? extends T> array){
        super(array);
    }

    public DelayedRemovalSeq(boolean ordered, int capacity, Class<?> arrayType){
        super(ordered, capacity, arrayType);
    }

    public DelayedRemovalSeq(boolean ordered, int capacity){
        super(ordered, capacity);
    }

    public DelayedRemovalSeq(boolean ordered, T[] array, int startIndex, int count){
        super(ordered, array, startIndex, count);
    }

    public DelayedRemovalSeq(Class<?> arrayType){
        super(arrayType);
    }

    public DelayedRemovalSeq(int capacity){
        super(capacity);
    }

    public DelayedRemovalSeq(T[] array){
        super(array);
    }

    /** @see #DelayedRemovalSeq(Object[]) */
    @SafeVarargs
    public static <T> DelayedRemovalSeq<T> with(T... array){
        return new DelayedRemovalSeq<>(array);
    }

    public void begin(){
        iterating++;
    }

    public void end(){
        if(iterating == 0) throw new IllegalStateException("begin must be called before end.");
        iterating--;
        if(iterating == 0){
            if(clear > 0 && clear == size){
                remove.clear();
                clear();
            }else{
                for(int i = 0, n = remove.size; i < n; i++){
                    int index = remove.pop();
                    if(index >= clear) remove(index);
                }
                for(int i = clear - 1; i >= 0; i--)
                    remove(i);
            }
            clear = 0;
        }
    }

    private void removeIntern(int index){
        if(index < clear) return;
        for(int i = 0, n = remove.size; i < n; i++){
            int removeIndex = remove.get(i);
            if(index == removeIndex) return;
            if(index < removeIndex){
                remove.insert(i, index);
                return;
            }
        }
        remove.add(index);
    }

    @Override
    public T remove(int index){
        if(iterating > 0){
            removeIntern(index);
            return get(index);
        }
        return super.remove(index);
    }

    @Override
    protected void removeItem(int index){
        if(iterating > 0){
            checkIndex(index);
            removeIntern(index);
        }else{
            super.removeItem(index);
        }
    }

    @Override
    public Seq<T> removeAll(Boolf<T> pred){
        if(iterating > 0){
            for(int i = 0; i < size; i++){
                if(pred.get(items[i])) removeIntern(i);
            }
            return this;
        }
        return super.removeAll(pred);
    }

    @Override
    public void removeRange(int start, int end){
        if(iterating > 0){
            for(int i = end; i >= start; i--)
                removeIntern(i);
        }else
            super.removeRange(start, end);
    }

    @Override
    public Seq<T> clear(){
        if(iterating > 0){
            clear = size;
            return this;
        }
        return super.clear();
    }

    public void check(){
        if(iterating > 0) throw new IllegalStateException("Invalid between begin/end.");
    }

    /** Refuses the changes between begin and end. */
    @Override
    protected void beforeModify(){
        check();
    }
}
