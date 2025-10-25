package arc.util.serialization;

import arc.files.*;
import arc.struct.*;
import arc.struct.Queue;
import arc.util.*;
import arc.util.io.*;
import arc.util.serialization.JsonWriter.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Reads/writes Java objects to/from JSON, automatically.
 * TODO remove and replace with own implementation
 * @author Nathan Sweet
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class Json{
    private static final boolean debug = false;
    protected static final OrderedMap<Class, Serializer> defaultInheritSerializers = new OrderedMap();
    protected final ObjectMap<Class, OrderedMap<String, FieldMetadata>> typeToFields = new ObjectMap();
    protected final ObjectMap<String, Class> tagToClass = new ObjectMap();
    protected final ObjectMap<Class, String> classToTag = new ObjectMap();
    protected final ObjectMap<Class, Serializer> classToSerializer = new ObjectMap();
    protected final OrderedMap<Class, Serializer> inheritSerializers = new OrderedMap();
    protected final ObjectMap<Class, Object[]> classToDefaultValues = new ObjectMap();
    private final Object[] equals1 = {null}, equals2 = {null};
    protected BaseJsonWriter writer;
    public String typeName = "class";
    public boolean usePrototypes = true;
    public OutputType outputType;
    public boolean quoteLongValues;
    public boolean ignoreUnknownFields = true;
    public boolean ignoreDeprecated;
    public boolean readDeprecated;
    public boolean enumNames = true;
    protected Serializer defaultSerializer;

    public Json(){
        outputType = OutputType.minimal;
    }

    public Json(OutputType outputType){
        this.outputType = outputType;
    }

    public boolean getIgnoreUnknownFields(){
        return ignoreUnknownFields;
    }

    /**
     * When true, fields in the JSON that are not found on the class will not throw a {@link SerializationException}. Default is true.
     */
    public void setIgnoreUnknownFields(boolean ignoreUnknownFields){
        this.ignoreUnknownFields = ignoreUnknownFields;
    }

    /** When true, fields with the {@link Deprecated} annotation will not be serialized. */
    public void setIgnoreDeprecated(boolean ignoreDeprecated){
        this.ignoreDeprecated = ignoreDeprecated;
    }

    /**
     * When true, fields with the {@link Deprecated} annotation will be read (but not written) when
     * {@link #setIgnoreDeprecated(boolean)} is true.
     */
    public void setReadDeprecated(boolean readDeprecated){
        this.readDeprecated = readDeprecated;
    }

    /** @see JsonWriter#setOutputType(OutputType) */
    public void setOutputType(OutputType outputType){
        this.outputType = outputType;
    }
    
    public OutputType getOutputType(){
        return outputType;
    }

    /** @see JsonWriter#setQuoteLongValues(boolean) */
    public void setQuoteLongValues(boolean quoteLongValues){
        this.quoteLongValues = quoteLongValues;
    }

    /**
     * When true, {@link Enum#name()} is used to write enum values. When false, {@link Enum#toString()} is used which may not be
     * unique. Default is true.
     */
    public void setEnumNames(boolean enumNames){
        this.enumNames = enumNames;
    }

    /** Sets a tag to use instead of the fully qualifier class name. This can make the JSON easier to read. */
    public void addClassTag(String tag, Class type){
        tagToClass.put(tag, type);
        classToTag.put(type, tag);
    }

    /** Returns the class for the specified tag, or null. */
    public Class getClass(String tag){
        return tagToClass.get(tag);
    }

    /** Returns the tag for the specified class, or null. */
    public String getTag(Class type){
        return classToTag.get(type);
    }

    /**
     * Sets the name of the JSON field to store the Java class name or class tag when required to avoid ambiguity during
     * deserialization. Set to null to never output this information, but be warned that deserialization may fail. Default is
     * "class".
     */
    public void setTypeName(String typeName){
        this.typeName = typeName;
    }
    
    public String getTypeName(){
        return typeName;
    }
    
    public boolean isTypeNameSet(){
        return typeName != null;
    }

    /**
     * Sets the serializer to use when the type being deserialized is not known (null).
     * @param defaultSerializer May be null.
     */
    public void setDefaultSerializer(Serializer defaultSerializer){
        this.defaultSerializer = defaultSerializer;
    }

    /**
     * Registers a serializer to use for the specified type instead of the default behavior of serializing all of an objects
     * fields.
     */
    public <T> void setSerializer(Class<T> type, Serializer<T> serializer){
        classToSerializer.put(type, serializer);
    }

    public <T> Serializer<T> getSerializer(Class<T> type){
        return classToSerializer.get(type);
    }

    /** Try to find a serializer using {@link #getSerializer(Class)} and {@link #getInheritSerializer(Class)} */
    public <T> Serializer<T> findSerializer(Class<T> type){
        Serializer<T> serializer = getSerializer(type);
        return serializer != null ? serializer : getInheritSerializer(type);
    }

    /* Registers a serializer for the parent's class that will be inherited. */
    public <T> void setInheritSerializer(Class<T> type, Serializer<T> serializer){
        if (inheritSerializers.containsKey(type)) inheritSerializers.remove(type);
        inheritSerializers.put(type, serializer); // now at end
        Seq<Class> keys = inheritSerializers.orderedKeys();
        int index = Math.max(keys.indexOf(c -> c.isAssignableFrom(type)), 0);
        keys.remove(keys.size - 1); // remove the new one from end
        keys.insert(index, type); // insert at correct position
    }

    public <T> Serializer<T> getInheritSerializer(Class<T> type){
        Class found = inheritSerializers.orderedKeys().find(c -> c.isAssignableFrom(type));
        if (found != null) return inheritSerializers.get(found);
        found = defaultInheritSerializers.orderedKeys().find(c -> c.isAssignableFrom(type));
        if (found != null) return defaultInheritSerializers.get(found);
        return null;
    }

    /** When true, field values that are identical to a newly constructed instance are not written. Default is true. */
    public void setUsePrototypes(boolean usePrototypes){
        this.usePrototypes = usePrototypes;
    }

    /**
     * Sets the type of elements in a struct. When the element type is known, the class for each element in the struct
     * does not need to be written unless different from the element type.
     */
    public void setElementType(Class type, String fieldName, Class elementType){
        ObjectMap<String, FieldMetadata> fields = getFields(type);
        FieldMetadata metadata = fields.get(fieldName);
        if(metadata == null)
            throw new SerializationException("Field not found: " + fieldName + " (" + type.getName() + ")");
        metadata.elementType = elementType;
    }

    public OrderedMap<String, FieldMetadata> getFields(Class type){
        OrderedMap<String, FieldMetadata> fields = typeToFields.get(type);
        if(fields != null) return fields;

        Seq<Class> classHierarchy = new Seq();
        Class nextClass = type;
        while(nextClass != Object.class){
            classHierarchy.add(nextClass);
            nextClass = nextClass.getSuperclass();
        }
        Seq<Field> allFields = new Seq<>();
        for(int i = classHierarchy.size - 1; i >= 0; i--)
            allFields.addAll(classHierarchy.get(i).getDeclaredFields());

        OrderedMap<String, FieldMetadata> nameToField = new OrderedMap(allFields.size);

        for(Field field: allFields){
            if(Modifier.isTransient(field.getModifiers())) continue;
            if(Modifier.isStatic(field.getModifiers())) continue;
            if(field.isSynthetic() || type.isEnum() || Reflect.isWrapper(type)) continue;

            //this is deprecated, but I know what I'm doing
            if(!field.isAccessible()){
                try{
                    field.setAccessible(true);
                }catch(Exception ex){
                    continue;
                }
            }

            if(ignoreDeprecated && !readDeprecated && field.isAnnotationPresent(Deprecated.class)) continue;
            FieldMetadata data = new FieldMetadata(field);

            nameToField.put(field.getName(), data);
        }
        typeToFields.put(type, nameToField);
        return nameToField;
    }
    
    public void toUBJson(Object object, OutputStream stream){
        toUBJson(object, object == null ? null : object.getClass(), null, null, stream);
    }
    
    public void toUBJson(Object object, Class knownType, OutputStream stream){
        toUBJson(object, knownType, null, null, stream);
    }
    
    public void toUBJson(Object object, Class knownType, Class elementType, OutputStream stream){
        toUBJson(object, knownType, elementType, null, stream);
    }

    public void toUBJson(Object object, Class knownType, Class elementType, Class keyType, OutputStream stream){
        toJson(object, knownType, elementType, keyType, new UBJsonWriter(stream));
    }

    public JsonValue toJsonValue(Object object){
        return toJsonValue(object, object == null ? null : object.getClass(), null, null);
    }
    
    public JsonValue toJsonValue(Object object, Class knownType){
        return toJsonValue(object, knownType, null, null);
    }
    
    public JsonValue toJsonValue(Object object, Class knownType, Class elementType){
        return toJsonValue(object, knownType, elementType, null);
    }

    public JsonValue toJsonValue(Object object, Class knownType, Class elementType, Class keyType){
        JsonWriterBuilder builder = new JsonWriterBuilder();
        toJson(object, knownType, elementType, keyType, builder);
        JsonValue json = builder.getJson();
        builder.reset(); // helps GC
        return json;
    }
    
    public String toJson(Object object){
        return toJson(object, object == null ? null : object.getClass(), (Class)null);
    }

    /** @param knownType May be null if the type is unknown. */
    public String toJson(Object object, Class knownType){
        return toJson(object, knownType, (Class)null);
    }

    /**
     * @param knownType   May be null if the type is unknown.
     * @param elementType May be null if the type is unknown.
     */
    public String toJson(Object object, Class knownType, Class elementType){
        return toJson(object, knownType, elementType, (Class)null);
    }

    /**
     * @param knownType   May be null if the type is unknown.
     * @param elementType May be null if the type is unknown.
     * @param keyType     May be null if the type is unknown.
     */
    public String toJson(Object object, Class knownType, Class elementType, Class keyType){
        StringWriter buffer = new StringWriter();
        toJson(object, knownType, elementType, keyType, buffer);
        return buffer.toString();
    }

    public void toJson(Object object, Fi file){ 
        toJson(object, object == null ? null : object.getClass(), null, file); 
    }

    /** @param knownType May be null if the type is unknown. */
    public void toJson(Object object, Class knownType, Fi file){
        toJson(object, knownType, null, file);
    }

    /**
     * @param knownType   May be null if the type is unknown.
     * @param elementType May be null if the type is unknown.
     */
    public void toJson(Object object, Class knownType, Class elementType, Fi file){
        toJson(object, knownType, elementType, null, file);
    }

    /**
     * @param knownType   May be null if the type is unknown.
     * @param elementType May be null if the type is unknown.
     * @param keyType     May be null if the type is unknown.
     */
    public void toJson(Object object, Class knownType, Class elementType, Class keyType, Fi file){
        try(Writer writer = file.writer(false)){
            toJson(object, knownType, elementType, keyType, writer);
        }catch(Exception ex){
            throw new SerializationException("Error writing file: " + file, ex);
        }
    }

    public void toJson(Object object, Writer writer){ 
        toJson(object, object == null ? null : object.getClass(), null, writer); 
    }

    /** @param knownType May be null if the type is unknown. */
    public void toJson(Object object, Class knownType, Writer writer){
        toJson(object, knownType, null, writer);
    }

    /**
     * @param knownType   May be null if the type is unknown.
     * @param elementType May be null if the type is unknown.
     */
    public void toJson(Object object, Class knownType, Class elementType, Writer writer){
        toJson(object, knownType, elementType, null, writer);
    }

    public void toJson(Object object, Class knownType, Class elementType, Class keyType, Writer writer){
        toJson(object, knownType, elementType, keyType, (BaseJsonWriter)new JsonWriter(writer));
    }
    
    protected void toJson(Object object, Class knownType, Class elementType, Class keyType, BaseJsonWriter writer){
        setWriter(writer);
        try{
            writeValue(object, knownType, elementType, keyType);
        }finally{
            Streams.close(this.writer);
            this.writer = null;
        }
    }

    public BaseJsonWriter getWriter(){
        return writer;
    }

    /** Sets the writer where JSON output will be written. This is only necessary when not using the toJson methods. */
    public void setWriter(BaseJsonWriter writer){
        //if(!(writer instanceof BaseJsonWriter)) writer = new JsonWriter(writer);
        this.writer = writer;
        this.writer.setOutputType(outputType);
        this.writer.setQuoteLongValues(quoteLongValues);
    }

    /** Writes all fields of the specified object to the current JSON object. */
    public void writeFields(Object object){
        Class type = object.getClass();

        Object[] defaultValues = getDefaultValues(type);

        OrderedMap<String, FieldMetadata> fields = getFields(type);
        int i = 0;
        for(FieldMetadata metadata : new OrderedMap.OrderedMapValues<>(fields)){
            Field field = metadata.field;
            if(readDeprecated && ignoreDeprecated && field.isAnnotationPresent(Deprecated.class)) continue;
            try{
                Object value = field.get(object);
                if(defaultValues != null){
                    Object defaultValue = defaultValues[i++];
                    if(value == null && defaultValue == null) continue;
                    if(value != null && defaultValue != null){
                        if(value.equals(defaultValue)) continue;
                        if(value.getClass().isArray() && defaultValue.getClass().isArray()){
                            equals1[0] = value;
                            equals2[0] = defaultValue;
                            if(Arrays.deepEquals(equals1, equals2)) continue;
                        }
                    }
                }

                if(debug) System.out.println("Writing field: " + field.getName() + " (" + type.getName() + ")");
                writer.name(field.getName());
                writeValue(value, field.getType(), metadata.elementType);
            }catch(IllegalAccessException ex){
                throw new SerializationException("Error accessing field: " + field.getName() + " (" + type.getName() + ")", ex);
            }catch(SerializationException ex){
                ex.addTrace(field + " (" + type.getName() + ")");
                throw ex;
            }catch(Exception runtimeEx){
                SerializationException ex = new SerializationException(runtimeEx);
                ex.addTrace(field + " (" + type.getName() + ")");
                throw ex;
            }
        }
    }

    private Object[] getDefaultValues(Class type){
        if(!usePrototypes) return null;
        if(classToDefaultValues.containsKey(type)) return classToDefaultValues.get(type);
        Object object;
        try{
            object = newInstance(type);
        }catch(Exception ex){
            classToDefaultValues.put(type, null);
            return null;
        }

        ObjectMap<String, FieldMetadata> fields = getFields(type);
        Object[] values = new Object[fields.size];
        classToDefaultValues.put(type, values);

        int i = 0;
        for(FieldMetadata metadata : fields.values()){
            Field field = metadata.field;
            if(readDeprecated && ignoreDeprecated && field.isAnnotationPresent(Deprecated.class)) continue;
            try{
                values[i++] = field.get(object);
            }catch(IllegalAccessException ex){
                throw new SerializationException("Error accessing field: " + field.getName() + " (" + type.getName() + ")", ex);
            }catch(SerializationException ex){
                ex.addTrace(field + " (" + type.getName() + ")");
                throw ex;
            }catch(RuntimeException runtimeEx){
                SerializationException ex = new SerializationException(runtimeEx);
                ex.addTrace(field + " (" + type.getName() + ")");
                throw ex;
            }
        }
        return values;
    }

    /** @see #writeField(Object, String, String, Class) */
    public void writeField(Object object, String name){
        writeField(object, name, name, null);
    }

    /**
     * @param elementType May be null if the type is unknown.
     * @see #writeField(Object, String, String, Class)
     */
    public void writeField(Object object, String name, Class elementType){
        writeField(object, name, name, elementType);
    }

    /** @see #writeField(Object, String, String, Class) */
    public void writeField(Object object, String fieldName, String jsonName){
        writeField(object, fieldName, jsonName, null);
    }

    /**
     * Writes the specified field to the current JSON object.
     * @param elementType May be null if the type is unknown.
     */
    public void writeField(Object object, String fieldName, String jsonName, Class elementType){
        Class type = object.getClass();
        ObjectMap<String, FieldMetadata> fields = getFields(type);
        FieldMetadata metadata = fields.get(fieldName);
        if(metadata == null)
            throw new SerializationException("Field not found: " + fieldName + " (" + type.getName() + ")");
        Field field = metadata.field;
        if(elementType == null) elementType = metadata.elementType;
        try{
            if(debug) System.out.println("Writing field: " + field.getName() + " (" + type.getName() + ")");
            writer.name(jsonName);
            writeValue(field.get(object), field.getType(), elementType);
        }catch(IllegalAccessException ex){
            throw new SerializationException("Error accessing field: " + field.getName() + " (" + type.getName() + ")", ex);
        }catch(SerializationException ex){
            ex.addTrace(field + " (" + type.getName() + ")");
            throw ex;
        }catch(Exception runtimeEx){
            SerializationException ex = new SerializationException(runtimeEx);
            ex.addTrace(field + " (" + type.getName() + ")");
            throw ex;
        }
    }

    /**
     * Writes the value as a field on the current JSON object, without writing the actual class.
     * @param value May be null.
     * @see #writeValue(String, Object, Class, Class)
     */
    public void writeValue(String name, Object value){
        try{
            writer.name(name);
        }catch(IOException ex){
            throw new SerializationException(ex);
        }
        if(value == null)
            writeValue(value, null, null);
        else
            writeValue(value, value.getClass(), null);
    }

    /**
     * Writes the value as a field on the current JSON object, writing the class of the object if it differs from the specified
     * known type.
     * @param value May be null.
     * @param knownType May be null if the type is unknown.
     * @see #writeValue(String, Object, Class, Class)
     */
    public void writeValue(String name, Object value, Class knownType){
        try{
            writer.name(name);
        }catch(IOException ex){
            throw new SerializationException(ex);
        }
        writeValue(value, knownType, null);
    }

    /**
     * Writes the value as a field on the current JSON object, writing the class of the object if it differs from the specified
     * known type. The specified element type is used as the default type for collections.
     * @param value May be null.
     * @param knownType May be null if the type is unknown.
     * @param elementType May be null if the type is unknown.
     */
    public void writeValue(String name, Object value, Class knownType, Class elementType){
        try{
            writer.name(name);
        }catch(IOException ex){
            throw new SerializationException(ex);
        }
        writeValue(value, knownType, elementType);
    }

    /**
     * Writes the value, without writing the class of the object.
     * @param value May be null.
     */
    public void writeValue(Object value){
        if(value == null)
            writeValue(value, null, null);
        else
            writeValue(value, value.getClass(), null);
    }

    /**
     * Writes the value, writing the class of the object if it differs from the specified known type.
     * @param value May be null.
     * @param knownType May be null if the type is unknown.
     */
    public void writeValue(Object value, Class knownType){
        writeValue(value, knownType, null);
    }

    /**
     * Writes the value, writing the class of the object if it differs from the specified known type. 
     * The specified element type is used as the default type for collections.
     * 
     * @param value       May be null.
     * @param knownType   May be null if the type is unknown.
     * @param elementType May be null if the type is unknown.
     */
    public void writeValue(Object value, Class knownType, Class elementType){
        writeValue(value, knownType, elementType, null);
    }

    /**
     * Writes the value, writing the class of the object if it differs from the specified known type. 
     * The specified element type is used as the default type for collections. 
     * And the specified key type is used as the default key type for maps.
     * 
     * @param value       May be null.
     * @param knownType   May be null if the type is unknown.
     * @param elementType May be null if the type is unknown.
     */
    public void writeValue(Object value, Class knownType, Class elementType, Class keyType){
        knownType = findActualType(knownType);
        
        try{
            if(value == null){ 
                writer.value(null); 
                return; 
            }
            
            if((knownType != null && knownType.isPrimitive()) || knownType == String.class || Reflect.isWrapper(knownType)){
                writer.value(value);
                return;
            }
            
            Class actualType = findActualType(value.getClass());
            
            if(actualType.isPrimitive() || actualType == String.class || Reflect.isWrapper(actualType)){
                writeObjectStart(actualType, null);
                writeValue("value", value);
                writeObjectEnd();
                return;
            }
            
            if(value instanceof JsonSerializable){
                writeObjectStart(actualType, knownType);
                ((JsonSerializable)value).write(this);
                writeObjectEnd();
                return;
            }
            
            Serializer serializer = findSerializer(actualType);
            
            if(serializer != null){
                serializer.write(this, value, knownType, elementType, keyType);
                return;
            }
            
            // Java arrays (e.g. Object[]) special case
            if(actualType.isArray()){
                if(elementType == null) elementType = actualType.getComponentType();
                writeArrayStart();
                for(int i = 0; i < Array.getLength(value); i++) 
                    writeValue(Array.get(value, i), elementType, null);
                writeArrayEnd();
                return;
            }
            
            writeObjectStart(actualType, knownType);
            writeFields(value);
            writeObjectEnd();
            
        }catch(IOException e){
            throw new SerializationException(e);
        }
    }

    public void writeObjectStart(String name){
        try{
            writer.name(name);
        }catch(IOException ex){
            throw new SerializationException(ex);
        }
        writeObjectStart();
    }

    /** @param knownType May be null if the type is unknown. */
    public void writeObjectStart(String name, Class actualType, Class knownType){
        try{
            writer.name(name);
        }catch(IOException ex){
            throw new SerializationException(ex);
        }
        writeObjectStart(actualType, knownType);
    }

    public void writeObjectStart(){
        try{
            writer.object();
        }catch(IOException ex){
            throw new SerializationException(ex);
        }
    }

    /**
     * Starts writing an object, writing the actualType to a field if needed.
     * @param knownType May be null if the type is unknown.
     */
    public void writeObjectStart(Class actualType, Class knownType){
        try{
            writer.object();
        }catch(IOException ex){
            throw new SerializationException(ex);
        }
        if(knownType == null || knownType != actualType) writeType(actualType);
    }

    public void writeObjectEnd(){
        try{
            writer.pop();
        }catch(IOException ex){
            throw new SerializationException(ex);
        }
    }

    public void writeArrayStart(String name){
        try{
            writer.name(name);
            writer.array();
        }catch(IOException ex){
            throw new SerializationException(ex);
        }
    }

    public void writeArrayStart(){
        try{
            writer.array();
        }catch(IOException ex){
            throw new SerializationException(ex);
        }
    }

    public void writeArrayEnd(){
        try{
            writer.pop();
        }catch(IOException ex){
            throw new SerializationException(ex);
        }
    }

    public void writeType(Class type){
        if(typeName == null) return;
        String className = getTag(type);
        if(className == null) className = type.getName();
        try{
            writer.set(typeName, className);
        }catch(IOException ex){
            throw new SerializationException(ex);
        }
        if(debug) System.out.println("Writing type: " + type.getName());
    }

    /**
     * @param type May be null if the type is unknown.
     * @return May be null.
     */
    public <T> T fromJson(Class<T> type, Reader reader){
        return readValue(type, null, new JsonReader().parse(reader));
    }

    /**
     * @param type May be null if the type is unknown.
     * @param elementType May be null if the type is unknown.
     * @return May be null.
     */
    public <T> T fromJson(Class<T> type, Class elementType, Reader reader){
        return readValue(type, elementType, new JsonReader().parse(reader));
    }
    
    /**
     * @param type May be null if the type is unknown.
     * @param elementType May be null if the type is unknown.
     * @param keyType May be null if the type is unknown.
     * @return May be null.
     */
    public <T> T fromJson(Class<T> type, Class elementType, Class keyType, Reader reader){
        return readValue(type, elementType, keyType, new JsonReader().parse(reader));
    }

    /**
     * @param type May be null if the type is unknown.
     * @return May be null.
     */
    public <T> T fromJson(Class<T> type, InputStream input){
        return readValue(type, null, new JsonReader().parse(input));
    }

    /**
     * @param type May be null if the type is unknown.
     * @param elementType May be null if the type is unknown.
     * @return May be null.
     */
    public <T> T fromJson(Class<T> type, Class elementType, InputStream input){
        return readValue(type, elementType, new JsonReader().parse(input));
    }

    /**
     * @param type May be null if the type is unknown.
     * @param elementType May be null if the type is unknown.
     * @param keyType May be null if the type is unknown.
     * @return May be null.
     */
    public <T> T fromJson(Class<T> type, Class elementType, Class keyType, InputStream input){
        return readValue(type, elementType, keyType, new JsonReader().parse(input));
    }
    
    /**
     * @param type May be null if the type is unknown.
     * @return May be null.
     */
    public <T> T fromJson(Class<T> type, Fi file){
        try{
            return readValue(type, null, new JsonReader().parse(file));
        }catch(Exception ex){
            throw new SerializationException("Error reading file: " + file, ex);
        }
    }

    /**
     * @param type May be null if the type is unknown.
     * @param elementType May be null if the type is unknown.
     * @return May be null.
     */
    public <T> T fromJson(Class<T> type, Class elementType, Fi file){
        try{
            return readValue(type, elementType, new JsonReader().parse(file));
        }catch(Exception ex){
            throw new SerializationException("Error reading file: " + file, ex);
        }
    }
    
    /**
     * @param type May be null if the type is unknown.
     * @param elementType May be null if the type is unknown.
     * @param keyType May be null if the type is unknown.
     * @return May be null.
     */
    public <T> T fromJson(Class<T> type, Class elementType, Class keyType, Fi file){
        try{
            return readValue(type, elementType, keyType, new JsonReader().parse(file));
        }catch(Exception ex){
            throw new SerializationException("Error reading file: " + file, ex);
        }
    }

    /**
     * @param type May be null if the type is unknown.
     * @return May be null.
     */
    public <T> T fromJson(Class<T> type, char[] data, int offset, int length){
        return readValue(type, null, new JsonReader().parse(data, offset, length));
    }

    /**
     * @param type May be null if the type is unknown.
     * @param elementType May be null if the type is unknown.
     * @return May be null.
     */
    public <T> T fromJson(Class<T> type, Class elementType, char[] data, int offset, int length){
        return readValue(type, elementType, new JsonReader().parse(data, offset, length));
    }
  
    /**
     * @param type May be null if the type is unknown.
     * @param elementType May be null if the type is unknown.
     * @param keyType May be null if the type is unknown.
     * @return May be null.
     */
    public <T> T fromJson(Class<T> type, Class elementType, Class keyType, char[] data, int offset, int length){
        return readValue(type, elementType, keyType, new JsonReader().parse(data, offset, length));
    }
    
    /**
     * @param type May be null if the type is unknown.
     * @return May be null.
     */
    public <T> T fromJson(Class<T> type, String json){
        return readValue(type, null, new JsonReader().parse(json));
    }

    /**
     * @param type May be null if the type is unknown.
     * @return May be null.
     */
    public <T> T fromJson(Class<T> type, Class elementType, String json){
        return readValue(type, elementType, new JsonReader().parse(json));
    }

    /**
     * @param type May be null if the type is unknown.
     * @param elementType May be null if the type is unknown.
     * @param keyType May be null if the type is unknown.
     * @return May be null.
     */      
    public <T> T fromJson(Class<T> type, Class elementType, Class keyType, String json){
        return readValue(type, elementType, keyType, new JsonReader().parse(json));
    }

    
    public void readField(Object object, String name, JsonValue jsonData){
        readField(object, name, name, null, null, jsonData);
    }

    public void readField(Object object, String name, Class elementType, JsonValue jsonData){
        readField(object, name, name, elementType, null, jsonData);
    }
    
    public void readField(Object object, String name, Class elementType, Class keyType, JsonValue jsonData){
        readField(object, name, name, elementType, keyType, jsonData);
    }

    public void readField(Object object, String fieldName, String jsonName, JsonValue jsonData){
        readField(object, fieldName, jsonName, null, null, jsonData);
    }

    /** @param elementType May be null if the type is unknown. */
    public void readField(Object object, String fieldName, String jsonName, Class elementType, JsonValue jsonMap){
        readField(object, fieldName, jsonName, elementType, null, jsonMap);
    }
    
    /** 
     * @param elementType May be null if the type is unknown.
     * @param keyType May be null if the type is unknown.
     * */
    public void readField(Object object, String fieldName, String jsonName, Class elementType, Class keyType, JsonValue jsonMap){
        Class type = object.getClass();
        ObjectMap<String, FieldMetadata> fields = getFields(type);
        FieldMetadata metadata = fields.get(fieldName);
        if(metadata == null)
            throw new SerializationException("Field not found: " + fieldName + " (" + type.getName() + ")");
        Field field = metadata.field;
        if(elementType == null) elementType = metadata.elementType;
        readField(object, field, jsonName, elementType, keyType, jsonMap);
    }

    /**
     * @param object May be null if the field is static.
     * @param elementType May be null if the type is unknown.
     */
    public void readField(Object object, Field field, String jsonName, Class elementType, JsonValue jsonMap){
        readField(object, field, jsonName, elementType, null, jsonMap);
    }
    
    /**
     * @param object May be null if the field is static.
     * @param elementType May be null if the type is unknown.
     * @param keyType May be null if the type is unknown.
     *  
     */
    public void readField(Object object, Field field, String jsonName, Class elementType, Class keyType, JsonValue jsonMap){
        JsonValue jsonValue = jsonMap.get(jsonName);
        if(jsonValue == null) return;
        try{
            field.set(object, readValue(field.getType(), elementType, keyType, jsonValue));
        }catch(IllegalAccessException ex){
            throw new SerializationException(
            "Error accessing field: " + field.getName() + " (" + field.getDeclaringClass().getName() + ")", ex);
        }catch(SerializationException ex){
            ex.addTrace(field.getName() + " (" + field.getDeclaringClass().getName() + ")");
            throw ex;
        }catch(RuntimeException runtimeEx){
            SerializationException ex = new SerializationException(runtimeEx);
            ex.addTrace(jsonValue.trace());
            ex.addTrace(field.getName() + " (" + field.getDeclaringClass().getName() + ")");
            throw ex;
        }
    }

    public void readFields(Object object, JsonValue jsonMap){
        Class type = object.getClass();
        ObjectMap<String, FieldMetadata> fields = getFields(type);
        for(JsonValue child = jsonMap.child; child != null; child = child.next){
            FieldMetadata metadata = fields.get(child.name().replace(" ", "_"));
            if(metadata == null){
                if(child.name.equals(typeName)) continue;
                if(ignoreUnknownFields || ignoreUnknownField(type, child.name)){
                    if(debug) Log.warn("Ignoring unknown field: " + child.name + " (" + type.getName() + ")");
                    continue;
                }else{
                    SerializationException ex = new SerializationException("Field not found: " + child.name + " (" + type.getName() + ")");
                    ex.addTrace(child.trace());
                    throw ex;
                }
            }
            Field field = metadata.field;
            try{
                field.set(object, readValue(field.getType(), metadata.elementType, metadata.keyType, child));
            }catch(IllegalAccessException ex){
                throw new SerializationException("Error accessing field: " + field.getName() + " (" + type.getName() + ")", ex);
            }catch(SerializationException ex){
                ex.addTrace(field.getName() + " (" + type.getName() + ")");
                throw ex;
            }catch(RuntimeException runtimeEx){
                SerializationException ex = new SerializationException(runtimeEx);
                ex.addTrace(child.trace());
                ex.addTrace(field.getName() + " (" + type.getName() + ")");
                throw ex;
            }
        }
    }

    public <T> T readField(Class type, String name, JsonValue jsonData){
        return readField(type, name, name, null, null, jsonData);
    }

    public <T> T readField(Class type, String name, Class elementType, JsonValue jsonData){
        return readField(type, name, name, elementType, null, jsonData);
    }

    public <T> T readField(Class type, String fieldName, String jsonName, JsonValue jsonData){
        return readField(type, fieldName, jsonName, null, null, jsonData);
    }

    /** @param elementType May be null if the type is unknown. */
    public <T> T readField(Class type, String fieldName, String jsonName, Class elementType, JsonValue jsonMap){
        return readField(type, fieldName, jsonName, elementType, null, jsonMap);
    }

    /**
     * @param elementType May be null if the type is unknown.
     * @param keyType     May be null if the type is unknown.
     */
    public <T> T readField(Class type, String fieldName, String jsonName, Class elementType, Class keyType, JsonValue jsonMap){
        ObjectMap<String, FieldMetadata> fields = getFields(type);
        FieldMetadata metadata = fields.get(fieldName);
        if(metadata == null) throw new SerializationException("Field not found: " + fieldName + " (" + type.getName() + ")");
        if(elementType == null) elementType = metadata.elementType;
        return readField(metadata.field, jsonName, elementType, keyType, jsonMap);
    }

    /**
     * @param object      May be null if the field is static.
     * @param elementType May be null if the type is unknown.
     */
    public <T> T readField(Field field, String jsonName, Class elementType, JsonValue jsonMap){
        return readField(field, jsonName, elementType, null, jsonMap);
    }

    /**
     * @param object      May be null if the field is static.
     * @param elementType May be null if the type is unknown.
     * @param keyType     May be null if the type is unknown.
     */
    public <T> T readField(Field field, String jsonName, Class elementType, Class keyType, JsonValue jsonMap){
        JsonValue jsonValue = jsonMap.get(jsonName);
        if(jsonValue == null) return null;
        try{
            return (T)readValue(field.getType(), elementType, keyType, jsonValue);
        }catch(SerializationException e){
            e.addTrace(field.getName() + " (" + field.getDeclaringClass().getName() + ")");
            throw e;
        }catch(RuntimeException re){
            SerializationException e = new SerializationException(re);
            e.addTrace(jsonValue.trace());
            e.addTrace(field.getName() + " (" + field.getDeclaringClass().getName() + ")");
            throw e;
        }
    }

    /** @return the read fields, instead of setting them on the object */
    public ObjectMap<Field, Object> readFields(Class type, JsonValue jsonMap){
        ObjectMap<String, FieldMetadata> fields = getFields(type);
        ObjectMap<Field, Object> reads = new ObjectMap<>(fields.size); // i think ordered is useless
        for(JsonValue child = jsonMap.child; child != null; child = child.next){
            FieldMetadata metadata = fields.get(child.name().replace(" ", "_"));
            if(metadata == null){
                if(child.name.equals(typeName)) continue;
                if(getIgnoreUnknownFields() || ignoreUnknownField(type, child.name)) continue;
                SerializationException e = new SerializationException("Field not found: " + child.name + " (" + type.getName() + ")");
                e.addTrace(child.trace());
                throw e;
            }
            Field field = metadata.field;
            try{
                reads.put(field, readValue(field.getType(), metadata.elementType, metadata.keyType, child));
            }catch(SerializationException e){
                e.addTrace(field.getName() + " (" + type.getName() + ")");
                throw e;
            }catch(RuntimeException re){
                SerializationException e = new SerializationException(re);
                e.addTrace(child.trace());
                e.addTrace(field.getName() + " (" + type.getName() + ")");
                throw e;
            }
        }
        return reads;
    }

    /**
     * Called for each unknown field name encountered by {@link #readFields(Object, JsonValue)} when {@link #ignoreUnknownFields}
     * is false to determine whether the unknown field name should be ignored.
     * @param type The object type being read.
     * @param fieldName A field name encountered in the JSON for which there is no matching class field.
     * @return true if the field name should be ignored and an exception won't be thrown by
     * {@link #readFields(Object, JsonValue)}.
     */
    protected boolean ignoreUnknownField(Class type, String fieldName){
        return false;
    }

    /**
     * @param type May be null if the type is unknown.
     * @return May be null.
     */
    public <T> T readValue(String name, Class<T> type, JsonValue jsonMap){
        return readValue(type, null, jsonMap.get(name));
    }

    /**
     * @param type May be null if the type is unknown.
     * @return May be null.
     */
    public <T> T readValue(String name, Class<T> type, T defaultValue, JsonValue jsonMap){
        JsonValue jsonValue = jsonMap.get(name);
        if(jsonValue == null) return defaultValue;
        return readValue(type, null, jsonValue);
    }

    /**
     * @param type May be null if the type is unknown.
     * @param elementType May be null if the type is unknown.
     * @return May be null.
     */
    public <T> T readValue(String name, Class<T> type, Class elementType, JsonValue jsonMap){
        return readValue(type, elementType, jsonMap.get(name));
    }

    /**
     * @param type May be null if the type is unknown.
     * @param elementType May be null if the type is unknown.
     * @return May be null.
     */
    public <T> T readValue(String name, Class<T> type, Class elementType, T defaultValue, JsonValue jsonMap){
        return readValue(type, elementType, defaultValue, jsonMap.get(name));
    }

    /**
     * @param type May be null if the type is unknown.
     * @param elementType May be null if the type is unknown.
     * @return May be null.
     */
    public <T> T readValue(Class<T> type, Class elementType, T defaultValue, JsonValue jsonData){
        if(jsonData == null) return defaultValue;
        return readValue(type, elementType, jsonData);
    }

    /**
     * @param type May be null if the type is unknown.
     * @return May be null.
     */
    public <T> T readValue(Class<T> type, JsonValue jsonData){
        return readValue(type, null, jsonData);
    }

    public <T> T readValue(Class<T> type, Class elementType, JsonValue jsonData){
        return readValue(type, elementType, (Class)null, jsonData);
    }

    /**
     * @param type May be null if the type is unknown.
     * @param elementType May be null if the type is unknown.
     * @return May be null.
     */
    public <T> T readValue(Class<T> type, Class elementType, Class keytype, JsonValue jsonData){
        if(jsonData == null) return null;

        if(jsonData.isObject()){
            String className = isTypeNameSet() ? jsonData.getString(typeName, null) : null;
            if(className != null){
                jsonData.remove(typeName);
                type = getClass(className);
                if(type == null){
                    try{
                        type = (Class<T>)Class.forName(className);
                    }catch(Throwable e){
                        throw new SerializationException(e);
                    }
                }
            }
            
            if(type == null){
                if(defaultSerializer != null) return (T)defaultSerializer.read(this, jsonData, type);
                return (T)jsonData;
            }
            
        } else if (jsonData.isArray() && (type == null || type == Object.class)){
            type = (Class<T>)Seq.class;
        }
        
        if (type != null) {
            Serializer serializer = findSerializer(type);
            if(serializer != null){
                return (T)serializer.read(this, jsonData, type, elementType, keytype);
            }
            
            if(jsonData.isObject()){
                if(type.isPrimitive() || type == String.class || Reflect.isWrapper(type)) {
                    return readValue("value", type, jsonData);
                }
                
                Object object = newInstance(type);
                if(object instanceof JsonSerializable){
                    ((JsonSerializable)object).read(this, jsonData);
                    return (T)object;
                }
                
                readFields(object, jsonData);
                return (T)object;                
            }
        }
        
        if(jsonData.isArray()){
            if(!type.isArray()) throw new SerializationException(
                "Unable to convert value to required type: " + jsonData + " (" + type.getName() + ")");
            Class componentType = type.getComponentType();
            if(elementType == null) elementType = componentType;
            Object result = Array.newInstance(componentType, jsonData.size);
            int i = 0;
            for(JsonValue child = jsonData.child; child != null; child = child.next)
                Array.set(result, i++, readValue(elementType, null, child));
            return (T)result;
        }

        if(jsonData.isNumber()){
            try{
                if(type == null || type == float.class || type == Float.class) return (T)(Float)jsonData.asFloat();
                if(type == int.class || type == Integer.class) return (T)(Integer)jsonData.asInt();
                if(type == long.class || type == Long.class) return (T)(Long)jsonData.asLong();
                if(type == double.class || type == Double.class) return (T)(Double)jsonData.asDouble();
                if(type == String.class) return (T)jsonData.asString();
                if(type == short.class || type == Short.class) return (T)(Short)jsonData.asShort();
                if(type == byte.class || type == Byte.class) return (T)(Byte)jsonData.asByte();
            }catch(NumberFormatException ignored){}
            jsonData = new JsonValue(jsonData.asString());
        }

        if(jsonData.isBoolean()){
            try{
                if(type == null || type == boolean.class || type == Boolean.class) return (T)(Boolean)jsonData.asBoolean();
            }catch(NumberFormatException ignored){}
            jsonData = new JsonValue(jsonData.asString());
        }

        if(jsonData.isString()){
            String string = jsonData.asString();
            if(type == null || type == String.class) return (T)string;
            try{
                if(type == int.class || type == Integer.class) return (T)Integer.valueOf(string);
                if(type == float.class || type == Float.class) return string.endsWith("f") ? (T)Float.valueOf(string.substring(0, string.length() - 1)) : string.endsWith("f,") ? (T)Float.valueOf(string.substring(0, string.length() - 2)) : (T)Float.valueOf(string);
                if(type == long.class || type == Long.class) return (T)Long.valueOf(string);
                if(type == double.class || type == Double.class) return (T)Double.valueOf(string);
                if(type == short.class || type == Short.class) return (T)Short.valueOf(string);
                if(type == byte.class || type == Byte.class) return (T)Byte.valueOf(string);
            }catch(NumberFormatException ignored){}
            if(type == boolean.class || type == Boolean.class) return (T)Boolean.valueOf(string);
            if(type == char.class || type == Character.class) return (T)(Character)string.charAt(0);
            if(type == CharSequence.class) return (T)string;
            throw new SerializationException("Unable to convert value to required type: " + jsonData + " (" + type.getName() + ")");
        }

        return null;
    }

    /**
     * Each field on the <code>to</code> object is set to the value for the field with the same name on the <code>from</code>
     * object. The <code>to</code> object must have at least all the fields of the <code>from</code> object with the same name and
     * type.
     */
    public void copyFields(Object from, Object to){
        copyFields(from, to, false);
    }

    public void copyFields(Object from, Object to, boolean setFinals){
        ObjectMap<String, FieldMetadata> toFields = getFields(from.getClass());
        for(ObjectMap.Entry<String, FieldMetadata> entry : getFields(from.getClass())){
            FieldMetadata toField = toFields.get(entry.key);
            Field fromField = entry.value.field;
            if(Modifier.isFinal(fromField.getModifiers()) && !setFinals) continue;

            if(toField == null) throw new SerializationException("To object is missing field" + entry.key);
            try{
                toField.field.set(to, fromField.get(from));
            }catch(IllegalAccessException ex){
                throw new SerializationException("Error copying field: " + fromField.getName(), ex);
            }
        }
    }

    private String convertToString(Enum e){
        return enumNames ? e.name() : e.toString();
    }

    protected String convertToString(Object object, Class knownType){
        if(object == null) return String.valueOf(object);
        Serializer serializer = findSerializer(findActualType(object.getClass()));
        if(serializer != null) return serializer.toKey(this, object, findActualType(knownType));
        if(object instanceof Enum) return convertToString((Enum)object);
        if(object instanceof Class) return ((Class)object).getName();
        return String.valueOf(object);
    }

    protected <T> T convertFromString(String value, Class type){
        if(value == null) return null;
        if(type != null){
            Serializer serializer = findSerializer(type);
            if(serializer != null) return (T)serializer.fromKey(this, value, type); 
        }
        return (T)value;
    }

    protected static Object newInstance(Class type){
        try{
            return type.getDeclaredConstructor().newInstance();
        }catch(Exception ex){
            try{
                // Try a private constructor.
                Constructor constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            }catch(SecurityException ignored){
            }catch(IllegalAccessException ignored){
                if(Enum.class.isAssignableFrom(type)){
                    if(type.getEnumConstants() == null) type = type.getSuperclass();
                    return type.getEnumConstants()[0];
                }
                if(type.isArray())
                    throw new SerializationException("Encountered JSON object when expected array of type: " + type.getName(), ex);
                else if(type.isMemberClass() && !Modifier.isStatic(type.getModifiers()))
                    throw new SerializationException("Class cannot be created (non-static member class): " + type.getName(), ex);
                else
                    throw new SerializationException("Class cannot be created (missing no-arg constructor): " + type.getName(), ex);
            }catch(Exception privateConstructorException){
                ex = privateConstructorException;
            }
            throw new SerializationException("Error constructing instance of class: " + type.getName(), ex);
        }
    }
    
    protected static Class findActualType(Class clazz) {
        if (clazz == null) return clazz;
        while (clazz.isAnonymousClass() && clazz != Object.class) 
            clazz = clazz.getSuperclass();
        return clazz;
    }

    public String prettyPrint(Object object){
        return prettyPrint(object, outputType);
    }

    public String prettyPrint(String json){
        return prettyPrint(json, outputType);
    }

    public String prettyPrint(Object object, OutputType outputType){
        return prettyPrint(toJson(object), outputType);
    }

    public String prettyPrint(String json, OutputType outputType){
        return new JsonReader().parse(json).prettyPrint(outputType);
    }

    public interface Serializer<T>{
        /** @deprecated Please override {@link #write(Json, Object, Class, Class, Class)} instead. */
        @Deprecated
        default void write(Json json, T object, Class knownType){
            write(json, object, knownType, null, null);
        }
        /** @deprecated Please override {@link #read(Json, JsonValue, Class, Class, Class)} instead. */
        @Deprecated
        default T read(Json json, JsonValue jsonData, Class type){
            return read(json, jsonData, type, null, null);
        }

        default void write(Json json, T object, Class knownType, Class elementType, Class keyType){
            write(json, object, knownType);
        }
        default T read(Json json, JsonValue jsonData, Class type, Class elementType, Class keyType){
            return read(json, jsonData, type);
        }

        default String toKey(Json json, T object, Class knownType){
            return String.valueOf(object); 
        }
        default T fromKey(Json json, String key, Class type){ 
            return (T)key; // =/ 
        }
    }

    public interface JsonSerializable{
        void write(Json json);
        void read(Json json, JsonValue jsonData);
    }

    public static class FieldMetadata{
        public final Field field;
        public @Nullable Class elementType;
        public @Nullable Class keyType;

        public FieldMetadata(Field field){
            boolean isMap = ObjectMap.class.isAssignableFrom(field.getType())
            || Map.class.isAssignableFrom(field.getType());

            this.field = field;
            this.elementType = getElementType(field, isMap ? 1 : 0);
            keyType = isMap ? getElementType(field, 0) : null;
        }
    }

    static Class getElementType(Field field, int index){
        Type genericType = field.getGenericType();
        if(genericType instanceof ParameterizedType){
            Type[] actualTypes = ((ParameterizedType)genericType).getActualTypeArguments();
            if(actualTypes.length - 1 >= index){
                Type actualType = actualTypes[index];
                if(actualType instanceof Class)
                    return (Class)actualType;
                else if(actualType instanceof ParameterizedType)
                    return (Class)((ParameterizedType)actualType).getRawType();
                else if(actualType instanceof GenericArrayType){
                    Type componentType = ((GenericArrayType)actualType).getGenericComponentType();
                    if(componentType instanceof Class)
                        return java.lang.reflect.Array.newInstance((Class)componentType, 0).getClass();
                }
            }
        }
        return null;
    }
    
    // Default serializers
    static{
        // Array serializers
        defaultInheritSerializers.put(Seq.class, new Serializer<Seq>(){
            @Override
            public void write(Json json, Seq object, Class knownType, Class elementType, Class keyType){
                Class actualType = findActualType(object.getClass());
                if(knownType != null && actualType != knownType && actualType != Seq.class)
                    throw new SerializationException("Serialization of an Array other than the known type is not supported.\n"
                            + "Known type: " + knownType + "\nActual type: " + actualType);
                json.writeArrayStart();
                Seq array = (Seq)object;
                for(int i = 0, n = array.size; i < n; i++) 
                    json.writeValue(array.get(i), elementType, null);
                json.writeArrayEnd();
            }

            @Override
            public Seq read(Json json, JsonValue jsonData, Class type, Class elementType, Class keyType){
                Seq result = type == Seq.class ? new Seq() : (Seq)newInstance(type);
                for(JsonValue child = jsonData.child; child != null; child = child.next)
                    result.add(json.readValue(elementType, null, child));
                return result;
            }
        });
        
        defaultInheritSerializers.put(ObjectSet.class, new Serializer<ObjectSet>(){
            @Override
            public void write(Json json, ObjectSet object, Class knownType, Class elementType, Class keyType){
                Class actualType = findActualType(object.getClass());
                if(knownType == null) knownType = ObjectSet.class;
                json.writeObjectStart(actualType, knownType);
                json.writeArrayStart("values");
                for(Object entry : (ObjectSet)object) 
                    json.writeValue(entry, elementType, null);
                json.writeArrayEnd();
                json.writeObjectEnd();
            }

            @Override
            public ObjectSet read(Json json, JsonValue jsonData, Class type, Class elementType, Class keyType){
                ObjectSet result = type == ObjectSet.class ? new ObjectSet() : (ObjectSet)newInstance(type);
                if(jsonData.isObject()) jsonData = jsonData.getChild("values");
                for(JsonValue child = jsonData.child; child != null; child = child.next)
                    result.add(json.readValue(elementType, null, child));
                return result;
            }
        });
        
        defaultInheritSerializers.put(IntSet.class, new Serializer<IntSet>(){
            @Override
            public void write(Json json, IntSet object, Class knownType, Class elementType, Class keyType){
                Class actualType = findActualType(object.getClass());
                if(knownType == null) knownType = IntSet.class;
                json.writeObjectStart(actualType, knownType);
                json.writeArrayStart("values");
                for(IntSet.IntSetIterator iter = ((IntSet)object).iterator(); iter.hasNext;)
                    json.writeValue(iter.next(), Integer.class, null);
                json.writeArrayEnd();
                json.writeObjectEnd();
            }

            @Override
            public IntSet read(Json json, JsonValue jsonData, Class type, Class elementType, Class keyType){
                IntSet result = type == IntSet.class ? new IntSet() : (IntSet)newInstance(type);
                for(JsonValue child = jsonData.getChild("values"); child != null; child = child.next) 
                    result.add(child.asInt());
                return result;
            }
        });
        
        defaultInheritSerializers.put(IntSeq.class, new Serializer<IntSeq>(){
            @Override
            public void write(Json json, IntSeq object, Class knownType, Class elementType, Class keyType){
                json.writeArrayStart();
                IntSeq array = (IntSeq)object;
                for(int i = 0, n = array.size; i < n; i++) 
                    json.writeValue(array.get(i), Integer.class, null);
                json.writeArrayEnd();
            }

            @Override
            public IntSeq read(Json json, JsonValue jsonData, Class type, Class elementType, Class keyType){
                IntSeq result = type == IntSeq.class ? new IntSeq() : (IntSeq)newInstance(type);
                for(JsonValue child = jsonData.child; child != null; child = child.next) 
                    result.add(child.asInt());
                return result;
            }
        });
        
        defaultInheritSerializers.put(Queue.class, new Serializer<Queue>(){
            @Override
            public void write(Json json, Queue object, Class knownType, Class elementType, Class keyType){
                Class actualType = findActualType(object.getClass());
                if(knownType != null && actualType != knownType && actualType != Queue.class)
                    throw new SerializationException("Serialization of a Queue other than the known type is not supported. "
                    + "Known type: " + knownType + ". Actual type: " + actualType);
                json.writeArrayStart();
                Queue queue = (Queue)object;
                for(int i = 0, n = queue.size; i < n; i++) 
                    json.writeValue(queue.get(i), elementType, null);
                json.writeArrayEnd();
            }

            @Override
            public Queue read(Json json, JsonValue jsonData, Class type, Class elementType, Class keyType){
                Queue result = type == Queue.class ? new Queue() : (Queue)newInstance(type);
                for(JsonValue child = jsonData.child; child != null; child = child.next)
                    result.addLast(json.readValue(elementType, null, child));
                return result;
            }
        });
        
        defaultInheritSerializers.put(Collection.class, new Serializer<Collection>(){
            @Override
            public void write(Json json, Collection object, Class knownType, Class elementType, Class keyType){
                Class actualType = findActualType(object.getClass());
                if(json.isTypeNameSet() && actualType != ArrayList.class && (knownType == null || knownType != actualType)){
                    json.writeObjectStart(actualType, knownType);
                    json.writeArrayStart("items");
                    for(Object item : (Collection)object) 
                        json.writeValue(item, elementType, null);
                    json.writeArrayEnd();
                    json.writeObjectEnd();
                }else{
                    json.writeArrayStart();
                    for(Object item : (Collection)object) 
                        json.writeValue(item, elementType, null);
                    json.writeArrayEnd();
                }
            }

            @Override
            public Collection read(Json json, JsonValue jsonData, Class type, Class elementType, Class keyType){
                if(jsonData.isObject() && json.isTypeNameSet()) {
                    jsonData = jsonData.get("items");
                    if(jsonData == null) throw new SerializationException(
                        "Unable to convert object to struct: " + jsonData + " (" + type.getName() + ")");
                }
                Collection result = type.isInterface() ? new ArrayList() : (Collection)newInstance(type);
                for(JsonValue child = jsonData.child; child != null; child = child.next)
                    result.add(json.readValue(elementType, null, child));
                return result;
            }
        });
        
        // Map serializers
        defaultInheritSerializers.put(ObjectMap.class, new Serializer<ObjectMap>(){
            @Override
            public void write(Json json, ObjectMap object, Class knownType, Class elementType, Class keyType){
                Class actualType = findActualType(object.getClass());
                if(knownType == null) knownType = ObjectMap.class;
                json.writeObjectStart(actualType, knownType);
                for(ObjectMap.Entry entry : ((ObjectMap<?, ?>)object).entries())
                    json.writeValue(json.convertToString(entry.key, keyType), entry.value, elementType);
                json.writeObjectEnd();
            }

            @Override
            public ObjectMap read(Json json, JsonValue jsonData, Class type, Class elementType, Class keyType){
                ObjectMap result = type == ObjectMap.class ? new ObjectMap() : (ObjectMap)newInstance(type);
                for(JsonValue child = jsonData.child; child != null; child = child.next)
                    result.put(json.convertFromString(child.name, keyType), json.readValue(elementType, null, child));
                return result;
            }
        });
        
        defaultInheritSerializers.put(ObjectIntMap.class, new Serializer<ObjectIntMap>(){
            @Override
            public void write(Json json, ObjectIntMap object, Class knownType, Class elementType, Class keyType){
                Class actualType = findActualType(object.getClass());
                if(knownType == null) knownType = ObjectIntMap.class;
                json.writeObjectStart(actualType, knownType);
                for(ObjectIntMap.Entry entry : ((ObjectIntMap<?>)object).entries()) 
                    json.writeValue(json.convertToString(entry.key, keyType), entry.value, Integer.class);
                json.writeObjectEnd();
            }

            @Override
            public ObjectIntMap read(Json json, JsonValue jsonData, Class type, Class elementType, Class keyType){
                ObjectIntMap result = type == ObjectIntMap.class ? new ObjectIntMap() : (ObjectIntMap)newInstance(type);
                for(JsonValue child = jsonData.child; child != null; child = child.next)
                    result.put(json.convertFromString(child.name, elementType), child.asInt());
                return result;
            }
        });
        
        defaultInheritSerializers.put(ObjectFloatMap.class, new Serializer<ObjectFloatMap>(){
            @Override
            public void write(Json json, ObjectFloatMap object, Class knownType, Class elementType, Class keyType){
                Class actualType = findActualType(object.getClass());
                if(knownType == null) knownType = ObjectFloatMap.class;
                json.writeObjectStart(actualType, knownType);
                for(ObjectFloatMap.Entry entry : ((ObjectFloatMap<?>)object).entries())
                    json.writeValue(json.convertToString(entry.key, keyType), entry.value, Float.class);
                json.writeObjectEnd();
            }

            @Override
            public ObjectFloatMap read(Json json, JsonValue jsonData, Class type, Class elementType, Class keyType){
                ObjectFloatMap result = type == ObjectFloatMap.class ? new ObjectFloatMap() : (ObjectFloatMap)newInstance(type);
                for(JsonValue child = jsonData.child; child != null; child = child.next)
                    result.put(json.convertFromString(child.name, elementType), child.asFloat());
                return result;
            }
        });
        
        defaultInheritSerializers.put(IntMap.class, new Serializer<IntMap>(){
            @Override
            public void write(Json json, IntMap object, Class knownType, Class elementType, Class keyType){
                Class actualType = findActualType(object.getClass());
                if(knownType == null) knownType = IntMap.class;
                json.writeObjectStart(actualType, knownType);
                for(IntMap.Entry entry : ((IntMap<?>)object).entries())
                    json.writeValue(Integer.toString(entry.key), entry.value, elementType);
                json.writeObjectEnd();
            }

            @Override
            public IntMap read(Json json, JsonValue jsonData, Class type, Class elementType, Class keyType){
                IntMap result = type == IntMap.class ? new IntMap() : (IntMap)newInstance(type);
                for(JsonValue child = jsonData.child; child != null; child = child.next)
                    result.put(Integer.parseInt(child.name), json.readValue(elementType, null, child));
                return result;
            }
        });
        
        defaultInheritSerializers.put(ArrayMap.class, new Serializer<ArrayMap>(){
            @Override
            public void write(Json json, ArrayMap object, Class knownType, Class elementType, Class keyType){
                Class actualType = findActualType(object.getClass());
                if(knownType == null) knownType = ArrayMap.class;
                json.writeObjectStart(actualType, knownType);
                ArrayMap map = (ArrayMap)object;
                for(int i = 0, n = map.size; i < n; i++)
                    json.writeValue(json.convertToString(map.keys[i], keyType), map.values[i], elementType);
                json.writeObjectEnd();
            }

            @Override
            public ArrayMap read(Json json, JsonValue jsonData, Class type, Class elementType, Class keyType){
                ArrayMap result = type == ArrayMap.class ? new ArrayMap() : (ArrayMap)newInstance(type);
                for(JsonValue child = jsonData.child; child != null; child = child.next)
                    result.put(json.convertFromString(child.name, keyType), json.readValue(elementType, null, child));
                return result;
            }
        });
        
        defaultInheritSerializers.put(Map.class, new Serializer<Map>(){
            @Override
            public void write(Json json, Map object, Class knownType, Class elementType, Class keyType){
                Class actualType = findActualType(object.getClass());
                if(knownType == null) knownType = HashMap.class;
                json.writeObjectStart(actualType, knownType);
                for(Map.Entry entry : ((Map<?, ?>)object).entrySet())
                    json.writeValue(json.convertToString(entry.getKey(), keyType), entry.getValue(), elementType);
                json.writeObjectEnd();
            }

            @Override
            public Map read(Json json, JsonValue jsonData, Class type, Class elementType, Class keyType){
                Map result = type == ObjectMap.class ? new HashMap() : (Map)newInstance(type);
                for(JsonValue child = jsonData.child; child != null; child = child.next)
                    result.put(json.convertFromString(child.name, keyType), json.readValue(elementType, null, child)); 
                return result;
            }
        });
        
        // Enum
        defaultInheritSerializers.put(Enum.class, new Serializer<Enum>(){
            @Override
            public void write(Json json, Enum object, Class knownType, Class elementType, Class keyType){
                Class actualType = findActualType(object.getClass());
                if(json.isTypeNameSet() && (knownType == null || knownType != actualType)){
                    // Ensures that enums with specific implementations (abstract logic) serialize correctly.
                    if(actualType.getEnumConstants() == null) actualType = actualType.getSuperclass();
                    json.writeObjectStart(actualType, null);
                    json.writeValue("value", json.convertToString(object), String.class);
                    json.writeObjectEnd();
                }else{
                    json.writeValue((Object)json.convertToString(object), String.class);
                }
            }

            @Override
            public Enum read(Json json, JsonValue jsonData, Class type, Class elementType, Class keyType){
                if(jsonData.isObject()) jsonData = jsonData.get("value");
                String string = jsonData.asString();
                Enum[] constants = (Enum[])type.getEnumConstants();
                for(int i = 0, n = constants.length; i < n; i++){
                    Enum e = constants[i];
                    if(string.equals(json.convertToString(e))) return e;
                }
                throw new SerializationException("unknown enum field '" + string + "' for type: " + type.getName());
            }
        });
    }
}
