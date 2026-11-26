package arc.util.serialization;

import arc.files.*;
import arc.func.*;
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
@SuppressWarnings({"unchecked", "rawtypes", "resource"})
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
        putInheritSerializer(inheritSerializers, type, serializer);
    }

    /** Finds the serializer of the closest registered parent. */
    public <T> Serializer<T> getInheritSerializer(Class<T> type){
        Serializer<T> serializer = inheritSerializers.get(inheritType(inheritSerializers, type));
        return serializer != null ? serializer : defaultInheritSerializers.get(inheritType(defaultInheritSerializers, type));
    }

    protected static void putInheritSerializer(OrderedMap<Class, Serializer> serializers, Class type, Serializer serializer){
        serializers.remove(type);
        Seq<Class> keys = serializers.orderedKeys();
        int index = keys.indexOf(c -> c.isAssignableFrom(type));
        serializers.put(type, serializer); // Added at the end
        if(index != -1) keys.insert(index, keys.pop());
    }

    protected static @Nullable Class inheritType(OrderedMap<Class, Serializer> serializers, Class type){
        return serializers.orderedKeys().find(t -> t.isAssignableFrom(type));
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

    @SuppressWarnings("deprecation")
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
        if(type.isAnonymousClass()) type = type.getSuperclass();
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

            if(Reflect.isValueType(knownType) || !isTypeNameSet() && Reflect.isValueType(value.getClass())){
                writer.value(value);
                return;
            }

            Class actualType = findActualType(value.getClass());

            if(Reflect.isValueType(actualType)){
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
        if(keyType == null) keyType = metadata.keyType;
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
        if(keyType == null) keyType = metadata.keyType;
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

    /** @deprecated use {@link #readValue(Class, Class, Class, JsonValue)} */
    @Deprecated
    public <T> T readValue(Class<T> type, Class elementType, JsonValue jsonData, Class keyType){
        return readValue(type, elementType, keyType, jsonData);
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
                type = resolveClass(className);
            }

            if(type == null){
                if(defaultSerializer != null) return (T)defaultSerializer.read(this, jsonData, type);
                return (T)jsonData;
            }

        } else if (jsonData.isArray() && (type == null || type == Object.class)){
            type = (Class<T>)Seq.class;
        }

        if (type != null) {
            Serializer serializer = getSerializer(type);
            boolean serializable = JsonSerializable.class.isAssignableFrom(type);

            // A Serializable may be read as an array, string, etc, even though it will be written as an object.
            if(serializer == null && !(serializable && jsonData.isObject()))
                serializer = getInheritSerializer(type);
            if(serializer != null)
                return (T)serializer.read(this, jsonData, type, elementType, keytype);

            if(serializable){
                JsonSerializable object = (JsonSerializable)newInstance(type);
                object.read(this, jsonData);
                return (T)object;
            }

            if(jsonData.isObject()){
                if(Reflect.isValueType(type)) return readValue("value", type, jsonData);
                Object object = newInstance(type);
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

        return readPrimitive(type, jsonData);
    }

    protected <T> T readPrimitive(Class<T> type, JsonValue jsonData){
        Class<?> boxed = type == Object.class ? null : Reflect.box(type);

        if(jsonData.isNumber()){
            try{
                if(boxed == null || boxed == Number.class)
                    return jsonData.isLong() ? (T)(Long)jsonData.asLong() : (T)(Double)jsonData.asDouble();
                if(boxed == Float.class) return (T)(Float)jsonData.asFloat();
                if(boxed == Integer.class) return (T)(Integer)jsonData.asInt();
                if(boxed == Long.class) return (T)(Long)jsonData.asLong();
                if(boxed == Double.class) return (T)(Double)jsonData.asDouble();
                if(boxed == String.class) return (T)jsonData.asString();
                if(boxed == Short.class) return (T)(Short)jsonData.asShort();
                if(boxed == Byte.class) return (T)(Byte)jsonData.asByte();
            }catch(NumberFormatException ignored){}
            jsonData = new JsonValue(jsonData.asString());
        }

        if(jsonData.isBoolean()){
            try{
                if(boxed == null || boxed == Boolean.class) return (T)(Boolean)jsonData.asBoolean();
            }catch(NumberFormatException ignored){}
            jsonData = new JsonValue(jsonData.asString());
        }

        if(jsonData.isString()){
            String string = jsonData.asString();
            if(boxed == null || boxed == String.class) return (T)string;
            try{
                if(boxed == Integer.class) return (T)Integer.valueOf(string);
                if(boxed == Float.class) return
                    string.endsWith("f") ? (T)Float.valueOf(string.substring(0, string.length() - 1)) :
                    string.endsWith("f,") ? (T)Float.valueOf(string.substring(0, string.length() - 2)) :
                    (T)Float.valueOf(string);
                if(boxed == Long.class) return (T)Long.valueOf(string);
                if(boxed == Double.class) return (T)Double.valueOf(string);
                if(boxed == Short.class) return (T)Short.valueOf(string);
                if(boxed == Byte.class) return (T)Byte.valueOf(string);
            }catch(NumberFormatException ignored){}
            if(boxed == Boolean.class) return (T)Boolean.valueOf(string);
            if(boxed == Character.class && !string.isEmpty()) return (T)(Character)string.charAt(0);
            if(boxed == CharSequence.class) return (T)string;
            throw new SerializationException("Unable to convert '" + jsonData + "' to " +
                                             (type.isArray() ? type.getComponentType() + "[]" : type.getName()));
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

    protected <T> Class<T> resolveClass(String className){
        Class<T> type = getClass(className);
        if(type == null){
            try{
                type = (Class<T>)Class.forName(className, false, getClass().getClassLoader());
                // Allow JDK collections too
                if(JsonSerializable.class.isAssignableFrom(type) || AllowSerialization.class.isAssignableFrom(type) ||
                   type == String.class || Reflect.isWrapper(type) ||
                   className.startsWith("java.util.") &&
                   (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type))){
                    return type;
                }
                throw new SerializationException("Class must implement JsonSerializable or AllowSerialization: " + className);
            }catch(Throwable ex){
                throw new SerializationException(ex);
            }
        }
        return type;
    }

    private String convertToString(Enum e){
        return enumNames ? e.name() : e.toString();
    }

    /** @deprecated use {@link #convertToString(Object, Class)} */
    @Deprecated
    protected String convertToString(Object object){
        return convertToString(object, null);
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
        if(type == null || type == String.class || type == CharSequence.class || type == Object.class) return (T)value;
        Serializer serializer = findSerializer(type);
        if(serializer != null) return (T)serializer.fromKey(this, value, type);
        return readValue((Class<T>)type, null, new JsonValue(value));
    }

    /** @return whether the child is the type field, which must be ignored when reading a map. */
    protected boolean isTypeField(JsonValue child){
        return isTypeNameSet() && typeName.equals(child.name);
    }

    /** Creates the default type directly, else the specified type with reflection. */
    protected <T> T newInstance(Class type, Class<?> defaultType, Prov<T> creator){
        return type == defaultType || type.isInterface() && type.isAssignableFrom(defaultType)
             ? creator.get()
             : (T)newInstance(type);
    }

    protected Object newInstance(Class type){
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

    // Here for compatibility
    @Deprecated public String prettyPrint(Object object, int singleLineColumns){ return prettyPrint(object); }
    @Deprecated public String prettyPrint(String json, int singleLineColumns){ return prettyPrint(json); }
    @Deprecated public String prettyPrint(Object object, JsonValue.PrettyPrintSettings settings){ return prettyPrint(object, settings.outputType); }
    @Deprecated public String prettyPrint(String json, JsonValue.PrettyPrintSettings settings){ return prettyPrint(json, settings.outputType); }

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
            return read(json, new JsonValue(key), type, null, null);
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
            Class type = field.getType();
            boolean isMap = ObjectMap.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type) ||
                            ArrayMap.class.isAssignableFrom(type);
            Type[] types = typeArguments(field);
            this.field = field;
            elementType = typeArgument(types, isMap ? 1 : 0);
            keyType = isMap ? typeArgument(types, 0) : null;
        }

    }

    static Class typeArgument(Type[] types, int index){
        return index < types.length ? rawClass(types[index]) : null;
    }

    static Class rawClass(Type type){
        if(type instanceof Class) return (Class)type;
        if(type instanceof ParameterizedType) return (Class)((ParameterizedType)type).getRawType();
        if(!(type instanceof GenericArrayType)) return null;
        Class component = rawClass(((GenericArrayType)type).getGenericComponentType());
        return component == null ? null : Array.newInstance(component, 0).getClass();
    }

    /** Returns the type arguments of the field type, resolved for the default struct it inherits if any. */
    static Type[] typeArguments(Field field){
        Class type = field.getType(), struct = inheritType(defaultInheritSerializers, type);
        Type generic = field.getGenericType();
        Type[] arguments = generic instanceof ParameterizedType ? ((ParameterizedType)generic).getActualTypeArguments() : type.getTypeParameters();
        return defaultInheritSerializers.get(struct) instanceof StructSerializer ? typeArguments(type, arguments, struct) : arguments;
    }

    /** @return the type arguments of the parent, resolved from the ones of the type when possible. */
    static Type[] typeArguments(Class type, Type[] arguments, Class parent){
        if(type == parent) return arguments;
        Type[] interfaces = type.getGenericInterfaces();
        for(int i = -1; i < interfaces.length; i++){
            Type direct = i == -1 ? type.getGenericSuperclass() : interfaces[i];
            Class raw = rawClass(direct);
            if(raw == null || !parent.isAssignableFrom(raw)) continue;
            if(!(direct instanceof ParameterizedType)) return typeArguments(raw, raw.getTypeParameters(), parent); // Raw parent, so unknown
            Type[] directArguments = ((ParameterizedType)direct).getActualTypeArguments().clone();
            TypeVariable[] variables = type.getTypeParameters();
            for(int j = 0; j < directArguments.length; j++) directArguments[j] = resolve(directArguments[j], variables, arguments);
            return typeArguments(raw, directArguments, parent);
        }
        return arguments; // Unreachable, the parent being one of the type
    }

    static Type resolve(Type type, TypeVariable[] variables, Type[] values){
        if(type instanceof GenericArrayType){
            Class component = rawClass(resolve(((GenericArrayType)type).getGenericComponentType(), variables, values));
            return component == null ? type : Array.newInstance(component, 0).getClass();
        }
        int index = Structs.indexOf(variables, type);
        return index == -1 ? type : values[index];
    }

    protected static void checkKnownType(Class actualType, Class knownType, Class baseType){
        if(knownType != null && actualType != knownType && actualType != baseType)
            throw new SerializationException("Serialization of a " + baseType.getSimpleName()
                                           + " other than the known type is not supported.\n"
                                           + "Known type: " + knownType + "\nActual type: " + actualType);
    }


    /** How a struct is written. */
    public enum StructLayout{
        /** An object of entries, with the type if needed. */
        map(null),
        /** An array of items, which can't hold the type. */
        array(null),
        /** An object with the type if needed, and the array of items as {@code values}. */
        values("values"),
        /** An array of items, or an object with {@code items} and the type when needed. */
        items("items");

        public final @Nullable String wrapper;
        StructLayout(@Nullable String wrapper){
            this.wrapper = wrapper;
        }
    }

    public static class StructSerializer<S> implements Serializer<S>{
        /** The type created directly, and assumed when writing without known type. */
        public final Class<?> defaultType;
        public final StructLayout layout;
        /** Types of the elements and of the keys of a struct of primitives, replacing the given ones, or null. */
        public final @Nullable Class fixedElementType, fixedKeyType;
        public final Prov<S> creator;
        public final ItemsWriter<S> writer;
        public final ItemReader<S> reader;

        public StructSerializer(Class<?> defaultType, Prov<S> creator, StructLayout layout, @Nullable Class fixedElementType,
                                @Nullable Class fixedKeyType, ItemsWriter<S> writer, ItemReader<S> reader){
            this.defaultType = defaultType;
            this.creator = creator;
            this.layout = layout;
            this.fixedElementType = fixedElementType;
            this.fixedKeyType = fixedKeyType;
            this.writer = writer;
            this.reader = reader;
        }

        /** Creates the serializer of a list, which writes each item and adds each item read. */
        public static <S> StructSerializer<S> list(Class<?> defaultType, Prov<S> creator, StructLayout layout,
                                                   Cons2<S, Cons> each, Cons2<S, Object> add){
            return list(defaultType, creator, layout, null, each, add);
        }

        /** @param elementType The type of the items, for a list of primitives. */
        public static <S> StructSerializer<S> list(Class<?> defaultType, Prov<S> creator, StructLayout layout, @Nullable Class elementType,
                                                   Cons2<S, Cons> each, Cons2<S, Object> add){
            return new StructSerializer<>(defaultType, creator, layout, elementType, null,
                (j, l, e, k) -> each.get(l, item -> j.writeValue(item, e, null)),
                (j, l, c, e, k) -> add.get(l, j.readValue(e, null, c)));
        }

        /** Creates the serializer of a map, which writes each entry with its key converted to a string, and puts each entry read. */
        public static <S> StructSerializer<S> map(Class<?> defaultType, Prov<S> creator,
                                                  Cons2<S, Cons2> each, Cons3<S, Object, Object> put){
            return map(defaultType, creator, null, null, each, put);
        }

        /**
         * @param keyType The type of the keys, for a map of primitive keys.
         * @param valueType The type of the values, for a map of primitive values. The element type given is then the key type.
         */
        public static <S> StructSerializer<S> map(Class<?> defaultType, Prov<S> creator, @Nullable Class keyType, @Nullable Class valueType,
                                                  Cons2<S, Cons2> each, Cons3<S, Object, Object> put){
            return new StructSerializer<>(defaultType, creator, StructLayout.map, valueType, keyType,
                (j, m, e, k) -> each.get(m, (key, value) -> j.writeValue(j.convertToString(key, k), value, e)),
                (j, m, c, e, k) -> put.get(m, j.convertFromString(c.name, k), j.readValue(e, null, c)));
        }

        /** @return the fixed type of the elements, else the given one. */
        public Class elementType(Class elementType){
            return fixedElementType != null ? fixedElementType : elementType;
        }

        /** @return the fixed type of the keys, else the given one, else the given element type if the type of the elements is fixed. */
        public Class keyType(Class elementType, Class keyType){
            if(fixedKeyType != null) return fixedKeyType;
            return keyType == null && fixedElementType != null ? elementType : keyType;
        }

        @Override
        public void write(Json json, S object, Class knownType, Class elementType, Class keyType){
            Class actualType = findActualType(object.getClass());
            boolean array = layout != StructLayout.map;
            boolean wrapped =
                layout == StructLayout.map || layout == StructLayout.values ||
                layout == StructLayout.items && json.isTypeNameSet() && actualType != defaultType && knownType != actualType;

            if(wrapped) json.writeObjectStart(actualType, knownType != null ? knownType : defaultType);
            else if(layout == StructLayout.array) checkKnownType(actualType, knownType, defaultType);
            if(array && wrapped) json.writeArrayStart(layout.wrapper);
            else if(array) json.writeArrayStart();
            writer.write(json, object, elementType(elementType), keyType(elementType, keyType));
            if(array) json.writeArrayEnd();
            if(wrapped) json.writeObjectEnd();
        }

        @Override
        public S read(Json json, JsonValue jsonData, Class type, Class elementType, Class keyType){
            S result = json.newInstance(type, defaultType, creator);
            // A wrapped array can also be read bare
            JsonValue items = layout.wrapper != null && jsonData.isObject() ? jsonData.get(layout.wrapper) : jsonData;
            if(items == null) throw new SerializationException("Unable to convert object to struct: "
                                                             + jsonData + " (" + type.getName() + ")");
            Class elements = elementType(elementType), keys = keyType(elementType, keyType);
            for(JsonValue child = items.child; child != null; child = child.next){
                if(layout != StructLayout.map || !json.isTypeField(child))
                    reader.read(json, result, child, elements, keys);
            }
            return result;
        }

        public interface ItemsWriter<S>{
            void write(Json json, S struct, Class elementType, Class keyType);
        }

        public interface ItemReader<S>{
            void read(Json json, S struct, JsonValue child, Class elementType, Class keyType);
        }
    }


    // Default serializers
    static{
        // Lists
        putInheritSerializer(defaultInheritSerializers, Seq.class,
            StructSerializer.list(Seq.class, Seq::new, StructLayout.array, Seq::each, Seq::add));
        putInheritSerializer(defaultInheritSerializers, Queue.class,
            StructSerializer.list(Queue.class, Queue::new, StructLayout.array, Queue::each, Queue::addLast));
        putInheritSerializer(defaultInheritSerializers, IntSeq.class,
            StructSerializer.list(IntSeq.class, IntSeq::new, StructLayout.array, Integer.class,
            (s, c) -> s.each(c::get), (s, v) -> s.add((Integer)v)));
        putInheritSerializer(defaultInheritSerializers, ObjectSet.class,
            StructSerializer.list(ObjectSet.class, ObjectSet::new, StructLayout.values, ObjectSet::each, ObjectSet::add));
        putInheritSerializer(defaultInheritSerializers, IntSet.class,
            StructSerializer.list(IntSet.class, IntSet::new, StructLayout.values, Integer.class,
            (s, c) -> s.each(c::get), (s, v) -> s.add((Integer)v)));
        putInheritSerializer(defaultInheritSerializers, Collection.class,
            StructSerializer.<Collection>list(ArrayList.class, ArrayList::new, StructLayout.items, Structs::each, Collection::add));

        // Maps
        putInheritSerializer(defaultInheritSerializers, ObjectMap.class,
            StructSerializer.map(ObjectMap.class, ObjectMap::new, ObjectMap::each, ObjectMap::put));
        putInheritSerializer(defaultInheritSerializers, ArrayMap.class,
            StructSerializer.map(ArrayMap.class, ArrayMap::new, ArrayMap::each, ArrayMap::put));
        putInheritSerializer(defaultInheritSerializers, Map.class,
            StructSerializer.<Map>map(HashMap.class, HashMap::new,
            (m, c) -> Structs.each(((Map<?, ?>)m).entrySet(), o -> c.get(o.getKey(), o.getValue())), Map::put));
        putInheritSerializer(defaultInheritSerializers, IntMap.class,
            StructSerializer.map(IntMap.class, IntMap::new, Integer.class, null, IntMap::each,
            (m, k, v) -> m.put((Integer)k, v)));
        putInheritSerializer(defaultInheritSerializers, ObjectIntMap.class,
            StructSerializer.map(ObjectIntMap.class, ObjectIntMap::new, null, Integer.class, ObjectIntMap::each,
            (m, k, v) -> m.put(k, (Integer)v)));
        putInheritSerializer(defaultInheritSerializers, ObjectFloatMap.class,
            StructSerializer.map(ObjectFloatMap.class, ObjectFloatMap::new, null, Float.class, ObjectFloatMap::each,
            (m, k, v) -> m.put(k, (Float)v)));

        // Enum
        putInheritSerializer(defaultInheritSerializers, Enum.class, new Serializer<Enum>(){
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
                Enum found = Structs.find((Enum[])type.getEnumConstants(), e -> string.equals(json.convertToString(e)));
                if(found != null) return found;
                throw new SerializationException("unknown enum field '" + string + "' for type: " + type.getName());
            }

            @Override
            public String toKey(Json json, Enum object, Class knownType){
                return json.convertToString(object);
            }
        });
    }
}
