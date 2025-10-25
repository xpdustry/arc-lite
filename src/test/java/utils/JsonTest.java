package utils;

import org.junit.*;

import static org.junit.Assert.*;

import arc.util.serialization.Json;
import arc.util.serialization.JsonValue;
import arc.util.serialization.Json.Serializer;


@SuppressWarnings("rawtypes")
public class JsonTest{
    static class A{
        public final String value;

        public A(String value){ 
            this.value = value; 
        }

        @Override
        public boolean equals(Object obj){
            if(obj instanceof A){ 
                A other = (A)obj; 
                return value == null ? other.value == null : value.equals(other.value); 
            }
            return false;
        }

        @Override
        public int hashCode(){ 
            return value != null ? value.hashCode() : 0; 
        }
    }

    static class B extends A{
        public B(String value){ 
            super(value); 
        }
    }

    static class C extends B{
        public C(String value){ 
            super(value); 
        }
    }

    static class TestSerializerA implements Serializer<A>{
        @Override
        public void write(Json json, A object, Class knownType, Class elementType, Class keyType){
            json.writeObjectStart();
            json.writeValue("value", object.value);
            json.writeValue("serializer", "A");
            json.writeObjectEnd();
        }

        @Override
        public A read(Json json, JsonValue jsonData, Class type, Class elementType, Class keyType){
            return new A(json.readValue("value", String.class, jsonData));
        }
    }

    static class TestSerializerB implements Serializer<B>{
        @Override
        public void write(Json json, B object, Class knownType, Class elementType, Class keyType){
            json.writeObjectStart();
            json.writeValue("value", object.value);
            json.writeValue("serializer", "B");
            json.writeObjectEnd();
        }

        @Override
        public B read(Json json, JsonValue jsonData, Class type, Class elementType, Class keyType){
            return new B(json.readValue("value", String.class, jsonData));
        }
    }

    static class TestSerializerC implements Serializer<C>{
        @Override
        public void write(Json json, C object, Class knownType, Class elementType, Class keyType){
            json.writeObjectStart();
            json.writeValue("value", object.value);
            json.writeValue("serializer", "C");
            json.writeObjectEnd();
        }

        @Override
        public C read(Json json, JsonValue jsonData, Class type, Class elementType, Class keyType){
            return new C(json.readValue("value", String.class, jsonData));
        }
    }
    
    Json json;
    TestSerializerA serializerA;
    TestSerializerB serializerB;
    TestSerializerC serializerC;
    
    @Before
    public void init() {
        json = new Json();
        json.setInheritSerializer(A.class, serializerA = new TestSerializerA());
        json.setInheritSerializer(B.class, serializerB = new TestSerializerB());
        json.setInheritSerializer(C.class, serializerC = new TestSerializerC());
    }

    @Test
    public void testInheritSerializerHierarchy(){
        // Check that for C, C's serializer is returned
        assertEquals(serializerC, json.getInheritSerializer(C.class));
        assertEquals(serializerB, json.getInheritSerializer(B.class));
        assertEquals(serializerA, json.getInheritSerializer(A.class));
        
        // Now, test with a new Json instance, add in different order
        Json json2 = new Json();
        // Add C first, then B, then A
        json2.setInheritSerializer(C.class, serializerC);
        json2.setInheritSerializer(B.class, serializerB);
        json2.setInheritSerializer(A.class, serializerA);
        // Should still return the most specific
        assertEquals(serializerC, json2.getInheritSerializer(C.class));
        assertEquals(serializerB, json2.getInheritSerializer(B.class));
        assertEquals(serializerA, json2.getInheritSerializer(A.class));
        
        // Test with only A and B, for C should get B
        Json json3 = new Json();
        json3.setInheritSerializer(A.class, serializerA);
        json3.setInheritSerializer(B.class, serializerB);
        assertEquals(serializerB, json3.getInheritSerializer(C.class));
        assertEquals(serializerB, json3.getInheritSerializer(B.class));
        assertEquals(serializerA, json3.getInheritSerializer(A.class));
    }

    @Test
    public void testSerializationWithInheritSerializers(){
        // Test serialization of A
        A a = new A("testA");
        JsonValue jsonA = json.toJsonValue(a, A.class);
        assertEquals("A", jsonA.getString("serializer"));
        assertEquals("testA", jsonA.getString("value"));
        // Deserialize A
        A deserializedA = json.readValue(A.class, jsonA);
        assertEquals(a, deserializedA);
        // Test serialization of B (should use inherit serializer B)
        B b = new B("testB");
        JsonValue jsonB = json.toJsonValue(b, B.class);
        assertEquals("B", jsonB.getString("serializer"));
        assertEquals("testB", jsonB.getString("value"));
        // Deserialize B
        B deserializedB = json.readValue(B.class, jsonB);
        assertEquals(b, deserializedB);
        
        // Test serialization of C (should use inherit serializer C)
        C c = new C("testC");
        JsonValue jsonC = json.toJsonValue(c, C.class);
        assertEquals("C", jsonC.getString("serializer"));
        assertEquals("testC", jsonC.getString("value"));
        // Deserialize C
        C deserializedC = json.readValue(C.class, jsonC);
        assertEquals(c, deserializedC);
    }
}