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
        return events.get(type, () -> new SnapshotSeq<>(Cons.class)).remove(listener);
    }

    /** Fires an enum trigger. */
    @SuppressWarnings("rawtypes")
    public static <T extends Enum<T>> void fire(Enum<T> type){
        SnapshotSeq<Cons<?>> listeners = events.get(type);
        if(listeners == null) return;
        Cons[] items = listeners.begin();
        try{
            for (int i = 0, n = listeners.size; i < n; i++)
                items[i].get(type);
        }finally{
            listeners.end();
        }
    }

    /** Fires a non-enum event by class. */
    public static <T> void fire(T type){
        fire(type.getClass(), type);
    }

    @SuppressWarnings("rawtypes")
    public static <T> void fire(Class<?> ctype, T type){
        SnapshotSeq<Cons<?>> listeners = events.get(ctype);
        if(listeners == null) return;
        Cons[] items = listeners.begin();
        try{
            for (int i = 0, n = listeners.size; i < n; i++)
                items[i].get(type);
        }finally{
            listeners.end();
        }
    }

    /** Don't do this. */
    public static void clear(){
        events.clear();
    }
}
