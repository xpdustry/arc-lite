package arc.util.serialization;

import arc.files.*;
import arc.util.*;
import arc.util.io.*;

import java.io.*;
import java.util.Arrays;

/**
 * Lightweight UBJSON parser.<br>
 * <br>
 * The default behavior is to parse the JSON into a DOM containing {@link JsonValue} objects. Extend this class and override
 * methods to perform event driven parsing. When this is done, the parse methods will return null. <br>
 * <br>
 * To limit allocations, strings are decoded from a reused buffer. So an instance is not thread-safe.
 * @author Xoppa
 */
public class UBJsonReader implements BaseJsonReader{
    private static final byte[] empty = {};
    protected static final int trustedStringSize = 1 << 16, maxRetainedBuffer = 1 << 16;
    private byte[] buffer = empty;

    /**
     * Parses the UBJSON from the given stream. <br>
     * For best performance you should provide buffered streams to this method!
     */
    @Override
    public JsonValue parse(InputStream input){
        DataInputStream din = null;
        try{
            din = new DataInputStream(input);
            return parse(din);
        }catch(IOException ex){
            throw new SerializationException(ex);
        }finally{
            Streams.close(din);
        }
    }

    @Override
    public JsonValue parse(Fi file){
        try{
            return parse(file.read(8192));
        }catch(Exception ex){
            throw new SerializationException("Error parsing file: " + file, ex);
        }
    }

    public JsonValue parse(final DataInputStream din) throws IOException{
        try{
            return parse(din, din.readByte());
        }finally{
            releaseBuffer();
            Streams.close(din);
        }
    }

    public JsonValue parseWihoutClosing(final DataInputStream din) throws IOException{
        try{
            return parse(din, din.readByte());
        }finally{
            releaseBuffer();
        }
    }

    /** Don't keep a big buffer after reading a big string. */
    protected void releaseBuffer(){
        if(buffer.length > maxRetainedBuffer) buffer = empty;
    }

    protected JsonValue parse(final DataInputStream din, final byte type) throws IOException{
        if(type == '[')
            return parseArray(din);
        else if(type == '{')
            return parseObject(din);
        else if(type == 'Z')
            return new JsonValue(JsonValue.ValueType.nullValue);
        else if(type == 'T')
            return new JsonValue(true);
        else if(type == 'F')
            return new JsonValue(false);
        else if(type == 'B')
            return new JsonValue(readUChar(din));
        else if(type == 'U')
            return new JsonValue(readUChar(din));
        else if(type == 'i')
            return new JsonValue(din.readByte());
        else if(type == 'I')
            return new JsonValue(din.readShort());
        else if(type == 'l')
            return new JsonValue(din.readInt());
        else if(type == 'L')
            return new JsonValue(din.readLong());
        else if(type == 'd')
            return new JsonValue(din.readFloat());
        else if(type == 'D')
            return new JsonValue(din.readDouble());
        else if(type == 's' || type == 'S')
            return new JsonValue(parseString(din, type));
        else if(type == 'a' || type == 'A')
            return parseData(din, type);
        else if(type == 'C')
            return new JsonValue(din.readChar());
        else
            throw new ArcRuntimeException("Unrecognized data type");
    }

    protected JsonValue parseArray(final DataInputStream din) throws IOException{
        JsonValue result = new JsonValue(JsonValue.ValueType.array);
        byte type = din.readByte();
        byte valueType = 0;
        if(type == '$'){
            valueType = din.readByte();
            type = din.readByte();
        }
        long size = -1;
        if(type == '#'){
            size = parseSize(din, false, -1);
            if(size < 0) throw new ArcRuntimeException("Unrecognized data type");
            if(size == 0) return result;
            type = valueType == 0 ? din.readByte() : valueType;
        }
        JsonValue prev = null;
        long c = 0;
        while(type != ']'){
            final JsonValue val = parse(din, type);
            val.parent = result;
            if(prev != null){
                val.prev = prev;
                prev.next = val;
                result.size++;
            }else{
                result.child = val;
                result.size = 1;
            }
            prev = val;
            if(size > 0 && ++c >= size) break;
            type = valueType == 0 ? din.readByte() : valueType;
        }
        return result;
    }

    protected JsonValue parseObject(final DataInputStream din) throws IOException{
        JsonValue result = new JsonValue(JsonValue.ValueType.object);
        byte type = din.readByte();
        byte valueType = 0;
        if(type == '$'){
            valueType = din.readByte();
            type = din.readByte();
        }
        long size = -1;
        if(type == '#'){
            size = parseSize(din, false, -1);
            if(size < 0) throw new ArcRuntimeException("Unrecognized data type");
            if(size == 0) return result;
            type = din.readByte();
        }
        JsonValue prev = null;
        long c = 0;
        while(type != '}'){
            final String key = parseString(din, true, type);
            final JsonValue child = parse(din, valueType == 0 ? din.readByte() : valueType);
            child.setName(key);
            child.parent = result;
            if(prev != null){
                child.prev = prev;
                prev.next = child;
                result.size++;
            }else{
                result.child = child;
                result.size = 1;
            }
            prev = child;
            if(size > 0 && ++c >= size) break;
            type = din.readByte();
        }
        return result;
    }

    protected JsonValue parseData(final DataInputStream din, final byte blockType) throws IOException{
        // FIXME: a/A is currently not following the specs because it lacks strong typed, fixed sized containers,
        // see: https://github.com/thebuzzmedia/universal-binary-json/issues/27
        final byte dataType = din.readByte();
        final long size = blockType == 'A' ? readUInt(din) : (long)readUChar(din);
        final JsonValue result = new JsonValue(JsonValue.ValueType.array);
        JsonValue prev = null;
        for(long i = 0; i < size; i++){
            final JsonValue val = parse(din, dataType);
            val.parent = result;
            if(prev != null){
                val.prev = prev;
                prev.next = val;
                result.size++;
            }else{
                result.child = val;
                result.size = 1;
            }
            prev = val;
        }
        return result;
    }

    protected String parseString(final DataInputStream din, final byte type) throws IOException{
        return parseString(din, false, type);
    }

    protected String parseString(final DataInputStream din, final boolean sOptional, final byte type) throws IOException{
        long size = -1;
        if(type == 'S'){
            size = parseSize(din, true, -1);
        }else if(type == 's')
            size = (long)readUChar(din);
        else if(sOptional) size = parseSize(din, type, false, -1);
        if(size < 0) throw new ArcRuntimeException("Unrecognized data type, string expected");
        return size > 0 ? readString(din, size) : "";
    }

    protected long parseSize(final DataInputStream din, final boolean useIntOnError, final long defaultValue) throws IOException{
        return parseSize(din, din.readByte(), useIntOnError, defaultValue);
    }

    protected long parseSize(final DataInputStream din, final byte type, final boolean useIntOnError, final long defaultValue)
    throws IOException{
        if(type == 'i') return (long)readUChar(din);
        if(type == 'I') return (long)readUShort(din);
        if(type == 'l') return readUInt(din);
        if(type == 'L') return din.readLong();
        if(useIntOnError){
            long result = (long)((short)type & 0xFF) << 24;
            result |= (long)((short)din.readByte() & 0xFF) << 16;
            result |= (long)((short)din.readByte() & 0xFF) << 8;
            result |= (long)((short)din.readByte() & 0xFF);
            return result;
        }
        return defaultValue;
    }

    protected short readUChar(final DataInputStream din) throws IOException{
        return (short)((short)din.readByte() & 0xFF);
    }

    protected int readUShort(final DataInputStream din) throws IOException{
        return ((int)din.readShort() & 0xFFFF);
    }

    /** Note: despite the name, this is a signed int32, as specified by UBJSON for the 'l' type. */
    protected long readUInt(final DataInputStream din) throws IOException{
        return ((long)din.readInt());
    }

    protected String readString(final DataInputStream din, final long size) throws IOException{
        // Size protection
        if(size < 0 || size > Integer.MAX_VALUE - 8) throw new IOException("Invalid string length: " + size);
        final int length = (int)size;

        byte[] buffer = this.buffer;
        if(buffer.length < length) buffer = this.buffer = new byte[Math.min(length, Math.max(trustedStringSize, buffer.length))];
        int read = 0;
        while(read < length){
            if(read == buffer.length) buffer = this.buffer = Arrays.copyOf(buffer, (int)Math.min(length, buffer.length * 2L));
            int n = din.read(buffer, read, Math.min(buffer.length, length) - read);
            if(n < 0) throw new EOFException("Unexpected end of data, " + (length - read) + " bytes of string remaining.");
            read += n;
        }

        return new String(buffer, 0, length, Strings.utf8);
    }
}
