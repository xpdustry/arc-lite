package arc.util.pooling;

import arc.struct.Seq;
import arc.struct.ObjectMap;
import arc.func.Prov;

/**
 * Stores a map of {@link Pool}s by type for convenient static access.
 * @author Nathan Sweet
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class Pools{
    private static final ObjectMap<Class, Pool> typePools = new ObjectMap<>();
    private static volatile boolean updating;

    private Pools(){
    }

    /** Will block if a pool is updating, as map is not thread-safe. */
    private static <T> Pool<T> getPool(Class<T> type){
        if(!updating) return typePools.get(type);
        synchronized(typePools){
            return typePools.get(type);
        }
    }

    private static <T> void putPool(Class<T> type, Pool<T> pool){
        updating = true;
        synchronized(typePools){
            typePools.put(type, pool);
            updating = false;
        }
    }

    /**
     * Returns a new or existing pool for the specified type, stored in a Class to {@link Pool} map.
     * Note that the capacity will be ignored if pool is already created.
     * if this is not the first time this pool has been requested.
     */
    public static <T> Pool<T> get(Class<T> type, Prov<T> supplier, int capacity){
        Pool<T> pool = getPool(type);
        if(pool == null){
            pool = new Pool<T>(capacity){
                @Override
                protected T newObject(){
                    return supplier.get();
                }
            };
            putPool(type, pool);
        }
        return pool;
    }

    /**
     * Returns a new or existing pool for the specified type, stored in a Class to {@link Pool} map.
     * The pool capacity will be 1024.
     */
    public static <T> Pool<T> get(Class<T> type, Prov<T> supplier){
        return get(type, supplier, 1024);
    }

    /** Sets an existing pool for the specified type, stored in a Class to {@link Pool} map. */
    public static <T> void set(Class<T> type, Pool<T> pool){
        putPool(type, pool);
    }

    /** Obtains an object from the {@link #get(Class, Prov) pool}. */
    public static <T> T obtain(Class<T> type, Prov<T> supplier){
        return get(type, supplier).obtain();
    }

    /**
     * Frees an object from the {@link #get(Class, Prov) pool}.
     * @return {@code false} if no pool has been created for this object, or if its pool is full
     */
    public static boolean free(Object object){
        if(object == null) throw new IllegalArgumentException("Object cannot be null.");
        Pool pool = getPool(object.getClass());
        if(pool == null) return false; // Ignore freeing an object that was never retained.
        return pool.free(object);
    }

    /**
     * Frees the specified objects from the {@link #get(Class, Prov) pool}.
     * Null objects within the array are silently ignored.
     * Objects don't need to be from the same pool.
     */
    public static void freeAll(Seq objects){
        freeAll(objects, false);
    }

    /**
     * Frees the specified objects from the {@link #get(Class, Prov) pool}.
     * Null objects within the array are silently ignored.
     * @param samePool If false, objects don't need to be from the same pool but the pool must be looked up for each object.
     */
    public static void freeAll(Seq objects, boolean samePool){
        if(objects == null) throw new IllegalArgumentException("Objects cannot be null.");
        Pool pool;
        for(int i = 0, n = objects.size; i < n; i++){
            Object object = objects.get(i);
            if(object == null) continue;
            pool = getPool(object.getClass());
            if(pool == null) continue; // Ignore freeing an object that was never retained.
            if (samePool) {
                pool.freeAll(objects);
                return;
            }
            pool.free(object);
        }
    }
}
