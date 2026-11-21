package arc;


import arc.func.Cons;
import arc.struct.*;

/** Simple global event listener system. */
@SuppressWarnings("unchecked")
public class Events{
    protected static final ObjectMap<Object, SnapshotSeq<Cons<?>>> events = new ObjectMap<>();

    /** Handle an event by class. */
    public static <T> void on(Class<T> type, Cons<T> listener){
        events.get(type, () -> new SnapshotSeq<>(Cons.class)).add(listener);
    }

    /** Handle an event by enum trigger. */
    public static void run(Object type, Runnable listener){
        events.get(type, () -> new SnapshotSeq<>(Cons.class)).add(e -> listener.run());
    }

    /** Only use this method if you have the reference to the exact listener object that was used. */
    public static <T> boolean remove(Class<T> type, Cons<T> listener){
        SnapshotSeq<Cons<?>> listeners = events.get(type);
        return listeners == null ? false : listeners.remove(listener);
    }

    /** Fires an enum trigger. */
    public static <T extends Enum<T>> void fire(Enum<T> type){
        dispatch(type, type);
    }

    /** Fires a non-enum event by class. */
    public static <T> void fire(T type){
        fire(type.getClass(), type);
    }

    public static <T> void fire(Class<?> ctype, T type){
        dispatch(ctype, type);
    }

    @SuppressWarnings("rawtypes")
    private static void dispatch(Object key, Object event){
        if(events.isEmpty()) return; // fast path when events are not used
        SnapshotSeq<Cons<?>> listeners = events.get(key);
        if(listeners == null) return;
        Cons[] items = listeners.begin();
        try{
            for(int i = 0, n = listeners.size; i < n; i++)
                items[i].get(event);
        }finally{
            listeners.end();
        }
    }

    /** Don't do this. */
    public static void clear(){
        events.clear();
    }
}
