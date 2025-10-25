/**
 * This file is part of MoreCommands. The plugin that adds a bunch of commands to your server.
 * Copyright (c) 2025  ZetaMap
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package arc.util;

import java.io.*;
import java.util.zip.*;

import arc.files.*;
import arc.func.*;
import arc.struct.*;
import arc.util.io.*;
import arc.util.serialization.*;


/** Simple re-implementation of {@link arc.Settings} adding choice to store as plain or binary json. */
public class JsonSettings implements Autosaver.Saveable{
    protected final ObjectMap<String, Object> decoded = new ObjectMap<>(), defaults = new ObjectMap<>();
    protected final OrderedMap<String, JsonValue> values = new OrderedMap<>();
    protected final BaseJsonReader reader;
    protected final JsonWriterBuilder builder = new JsonWriterBuilder();
    protected final Fi file, backupFile;
    protected final boolean plainJson;
    
    protected Cons<Throwable> errorHandler;
    protected Json json;
    protected boolean loaded, modified, autosaved, backuped, compressed;

    public JsonSettings(Fi file){ this(file, true, true, false, false); }
    public JsonSettings(Fi file, boolean autosave){ this(file, true, autosave, false, false); }
    // Prefer compression when using binary json
    public JsonSettings(Fi file, boolean plainJson, boolean autosave){ this(file, plainJson, autosave, false, !plainJson); }
    public JsonSettings(Fi file, boolean plainJson, boolean autosave, boolean backuped){
        this(file, plainJson, autosave, backuped, !plainJson);
    }

    /**
     * Container to store settings as json
     * 
     * @param file      the to write/read
     * @param plainJson whether settings are stored in plain or binary json.
     * @param autosave  to take advantage of regularly save.
     */
    public JsonSettings(Fi file, boolean plainJson, boolean autosave, boolean backuped, boolean compressed){
        this.file = file;
        String extension = file.extension();
        this.backupFile = file.parent().child(file.nameWithoutExtension() + "_backup" + 
                                              (extension.isEmpty() ? "" : '.' + extension));
        this.plainJson = plainJson;
        this.reader = plainJson ? new JsonReader() : new UBJsonReader();
        this.backuped = backuped;
        this.compressed = compressed;
        setJson(new Json());
        setAutosaved(autosave);
    }

    public void setJson(Json json){
        this.json = json;
        if(plainJson) this.json.setOutputType(JsonWriter.OutputType.json);
    }

    public Json getJson(){ 
        return json; 
    }

    public boolean isPlainJson(){ 
        return plainJson; 
    }

    public void setAutosaved(boolean autosave){
        if(autosave == autosaved) return;
        autosaved = autosave;
        if(autosaved) Autosaver.add(this);
        else Autosaver.remove(this);
    }

    public boolean isAutosaved(){ 
        return autosaved;
    }

    public void setBackuped(boolean backuped){ 
        this.backuped = backuped; 
    }

    public boolean isBackuped(){ 
        return backuped; 
    }

    public void setCompressed(boolean compressed){ 
        this.compressed = compressed; 
    }

    public boolean isCompressed(){ 
        return compressed; 
    }

    /**
     * Sets the error handler function. <br>
     * This function gets called when {@link #save} or {@link #load} fails. <br>
     * This can occur most often on browsers, where extensions can block writing to local storage.
     */
    public void setErrorHandler(Cons<Throwable> handler){
        errorHandler = handler;
    }

    @Override
    public boolean modified(){ 
        return modified; 
    }

    /** Loads all values. */
    public synchronized void load(){
        Throwable error = null;
        if(exists()){
            try{
                loadValues(file());
                // Backup the save file, as the values have now been loaded successfully
                if(backuped) file().copyTo(backupFile());
                modified = false;
                loaded = true;
                return;
            }catch(Throwable e){
                error = e;
            }
        }
        if(backuped && backupExists()){
            if(error != null) 
                Log.err("Failed to load settings file, attempting to load backup: @", error.toString());
            try{
                loadValues(backupFile());
                // Copy back the file
                backupFile().copyTo(file());
                Log.info("Loaded backup settings file successfully!");
            }catch(Throwable e){
                Log.err("Failed to load backup settings file", e);
                if(errorHandler != null) errorHandler.get(e);
                else throw e;
            }
            modified = false;
        }
        // if loading failed, it still counts
        loaded = true;
    }

    /** Saves all values. */
    @Override
    public synchronized void save(){
        if(!loaded || !modified) return;
        try{
            saveValues(file());
        }catch(Throwable e){
            Log.err("Error writing settings", e);
            if(errorHandler != null) errorHandler.get(e);
            else throw e;
        }
        modified = false;
    }

    public synchronized void loadValues(Fi file){
        try{
            boolean compressed = false;
            if(!plainJson){
                // read the first few bytes to check if it is compressed.
                byte[] header = new byte[2];
                file.readBytes(header, 0, 2);
                compressed = header[0] == (byte)0x78 && (header[1] == (byte)0x01 || header[1] == (byte)0x5E
                          || header[1] == (byte)0x9c || header[1] == (byte)0xda);
            }
            
            JsonValue content = reader.parse(compressed ? new InflaterInputStream(file.read(8192)) : file.read(8192));
            
            if(content != null){
                for(JsonValue child = content.child, last = null; child != null; last = child, child = child.next){
                    values.put(child.name, child);
                    // unlink values
                    child.parent = child.prev = null;
                    if(last != null) last.next = null;
                    child.name = null;
                }
            }
            
        }catch(Throwable e){
            throw new RuntimeException("Error reading file: " + file, e);
        }
    }

    public synchronized void saveValues(Fi file){
        if(plainJson){
            try(Writer writer = new BufferedWriter(file.writer(false), 8192)){
                builder.reset();
                
                builder.object();
                for(OrderedMap.Entry<String, JsonValue> e : values) builder.set(e.key, decoded.get(e.key, e.value));
                builder.close();
                
                if(compressed) builder.getJson().toJson(writer, json.getOutputType());
                else builder.getJson().prettyPrint(json.getOutputType(), writer);
                builder.reset();
                
            }catch(Throwable e){
                throw new RuntimeException("Error writing file: " + file, e);
            }
        }else{
            try(OutputStream out = compressed ? new FastDeflaterOutputStream(file.write(false, 8192)) : file.write(false, 8192)){
                UBJsonWriter writer = new UBJsonWriter(out);
                
                writer.object();
                for(OrderedMap.Entry<String, JsonValue> e : values){
                    // This is needed because #value() with a JsonValue is another method
                    if(decoded.containsKey(e.key)) writer.set(e.key, decoded.get(e.key));
                    else{
                        e.value.name = null; // in case of
                        writer.name(e.key).value(e.value);
                    }
                }
                writer.close();
                
            }catch(Throwable e){
                throw new RuntimeException("Error writing file: " + file, e);
            }
        }
    }

    /** @return whether the file exists or not. */
    public boolean exists(){ 
        return file().exists(); 
    }

    /** @return whether the backup file exists or not. */
    public boolean backupExists(){ 
        return backupFile().exists();
    }

    @Override
    public String name(){
        return "'" + file().path() + "'"; 
    }

    /** @return the file used for writing settings to. */
    public Fi file(){
        return file; 
    }

    /** @return the file used for recovering settings. */
    public Fi backupFile(){
        return backupFile;
    }

    /** Clears all preference values. */
    public synchronized void clear(){
        values.clear();
        modified = true;
    }

    public synchronized Iterable<String> keys(){
        return values.keys();
    }

    public synchronized int size(){
        return values.size; 
    }

    protected boolean isKnownType(Class<?> known){
        return known == String.class || known.isPrimitive() || Reflect.isWrapper(known);
    }

    public synchronized boolean has(String name){ 
        return values.containsKey(name);
    }

    public synchronized void remove(String name){
        values.remove(name);
        modified = true;
    }

    /**
     * Set up a list of defaults values. <br>
     * Format: name1, default1, name2, default2, etc
     */
    public synchronized void defaults(Object... objects){
        for(int i = 0; i < objects.length; i += 2){
            defaults.put((String)objects[i], objects[i + 1]);
        }
    }

    public synchronized Object getDefault(String name){ 
        return defaults.get(name); 
    }

    public synchronized void putAll(ObjectMap<String, Object> map){ 
        map.each(this::put); 
    }

    public void put(String name, Object value){ 
        put(name, null, null, value); 
    }
    
    public <E> void put(String name, Class<E> elementType, Object value){ 
        put(name, elementType, null, value); 
    }
    
    /** @apiNote {@code keyType} is not supported and ignored. object keys are converted using {@link String#valueOf(Object)} */
    public synchronized <K, E> void put(String name, Class<E> elementType, Class<K> keyType, Object value){
        // Store primitive, null and JsonValue values directly instead of converting it to JsonValue
        if(value == null || isKnownType(value.getClass())){
            decoded.put(name, value);
            if(!has(name)) values.put(name, null); // reserve the key
            modified = true;
            return;
            
        }else if(value instanceof JsonValue){
            values.put(name, (JsonValue)value); 
            modified = true; 
            return; 
        }
        
        builder.reset();
        json.setWriter(builder);
        try{
            json.writeValue(value, value == null ? null : value.getClass(), elementType);
        }catch(Throwable e){
            throw new RuntimeException(e);
        }
        
        values.put(name, builder.getJson());
        decoded.remove(name); // in case of type change
        builder.reset();
        modified = true;
    }

    public <T> T get(String name, Class<T> type, Prov<T> def){ 
        return get(name, type, null, null, def); 
    }

    public <T, E> T get(String name, Class<T> type, Class<E> elementType, Prov<T> def){
        return get(name, type, elementType, null, def);
    }

    public <T, K, E> T get(String name, Class<T> type, Class<E> elementType, Class<K> keyType, Prov<T> def){
        return has(name) ? get(name, type, elementType, keyType) : def.get();
    }

    protected synchronized <T, K, E> T get(String name, Class<T> type){ 
        return get(name, type, null, (Class<K>)null);
    }

    @SuppressWarnings("unchecked")
    protected synchronized <T, K, E> T get(String name, Class<T> type, Class<E> elementType, Class<K> keyType){
        // Use the already decoded value when possible
        boolean contains = decoded.containsKey(name);
        if(contains){
            Object o = decoded.get(name);
            // check for inferred types?
            if(o == null || o.getClass() == type) return (T)o;
        }else if(type == JsonValue.class) return (T)values.get(name);
        
        T result;
        try{
            result = json.readValue(type, elementType, keyType, values.get(name));
        }catch(Throwable e){
            throw new RuntimeException(e);
        }
        
        // Store primitive, null and JsonValue values directly when possible
        if(!contains && (result == null || isKnownType(result.getClass()) && (type == null || isKnownType(type))))
            decoded.put(name, result);
        return result;
    }

    public <T> T getOrPut(String name, Class<T> type, Prov<T> def){
        return getOrPut(name, type, null, null, def);
    }

    public <T, E> T getOrPut(String name, Class<T> type, Class<E> elementType, Prov<T> def){
        return getOrPut(name, type, elementType, null, def);
    }

    /** Put and return {@code def} if the {@code name} key is not found */
    public synchronized <T, K, E> T getOrPut(String name, Class<T> type, Class<E> elementType, Class<K> keyType, Prov<T> def){
        if(!has(name)){
            T fall = def.get();
            put(name, elementType, keyType, fall);
            return fall;
        }
        return get(name, type, elementType, keyType);
    }

    /** Put and return {@code def} if the {@code name} key is not found */
    protected synchronized <T, K, E> T getOrPut(String name, Class<T> type, T def){
        if(!has(name)){
            put(name, def);
            return def;
        }
        return get(name, type);
    }

    public float getFloat(String name, float def){
        return getOrPut(name, Float.class, def);
    }

    public double getDouble(String name, double def){
        return getOrPut(name, Double.class, def);
    }

    public int getInt(String name, int def){
        return getOrPut(name, Integer.class, def);
    }

    public long getLong(String name, long def){
        return getOrPut(name, Long.class, def);
    }

    public boolean getBool(String name, boolean def){
        return getOrPut(name, Boolean.class, def);
    }

    public String getString(String name, String def){
        return getOrPut(name, String.class, def);
    }

    public float getFloat(String name){
        return has(name) ? get(name, Float.class) : (float)defaults.get(name, 0f);
    }

    public double getDouble(String name){
        return has(name) ? get(name, Double.class) : (double)defaults.get(name, 0f);
    }

    public int getInt(String name){
        return has(name) ? get(name, Integer.class) : (int)defaults.get(name, 0);
    }

    public long getLong(String name){
        return has(name) ? get(name, Long.class) : (long)defaults.get(name, 0f);
    }

    public boolean getBool(String name){
        return has(name) ? get(name, Boolean.class) : (boolean)defaults.get(name, false);
    }

    /** Runs the specified code once, and never again. */
    public void getBoolOnce(String name, Runnable run){
        if (getBool(name)) return;
        run.run();
        put(name, true);
    }

    /** Returns {@code false} once, and never again. */
    public boolean getBoolOnce(String name){
        boolean val = getBool(name);
        if(!val) put(name, true);
        return val;
    }

    /** @return {@code null} if not found */
    public String getString(String name){
        return has(name) ? get(name, String.class) : (String)defaults.get(name);
    }
}
