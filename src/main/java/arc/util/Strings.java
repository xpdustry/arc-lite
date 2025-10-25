package arc.util;

import arc.func.*;
import arc.graphics.*;
import arc.math.*;
import arc.struct.*;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;

public class Strings{
    private static StringBuilder tmp1 = new StringBuilder(), tmp2 = new StringBuilder();
    private static Pattern
        filenamePattern = Pattern.compile("[\0/\"<>|:*?\\\\]"),
        reservedFilenamePattern = Pattern.compile("(CON|AUX|PRN|NUL|(COM[0-9])|(LPT[0-9]))((\\..*$)|$)", Pattern.CASE_INSENSITIVE);

    public static final Charset utf8 = Charset.forName("UTF-8");

    /** @return whether the name matches the query; case-insensitive. Always returns true if query is empty. */
    public static boolean matches(String query, String name){
        return query == null || query.isEmpty() || (name != null && name.toLowerCase().contains(query.toLowerCase()));
    }

    public static int count(CharSequence s, char c){
        int total = 0;
        for(int i = 0; i < s.length(); i++){
            if(s.charAt(i) == c) total ++;
        }
        return total;
    }
    
    public static int count(String str, String substring){
        int lastIndex = 0;
        int count = 0;

        while(lastIndex != -1){

            lastIndex = str.indexOf(substring, lastIndex);

            if(lastIndex != -1){
                count ++;
                lastIndex += substring.length();
            }
        }
        return count;
    }

    public static String truncate(String s, int length){
        return s.length() <= length ? s : s.substring(0, length);
    }

    public static String truncate(String s, int length, String ellipsis){
        return s.length() <= length ? s : s.substring(0, length) + ellipsis;
    }

    public static Seq<Throwable> getCauses(Throwable e){
        Seq<Throwable> arr = new Seq<>();
        while(e != null){
            arr.add(e);
            e = e.getCause();
        }
        return arr;
    }

    public static String getSimpleMessage(Throwable e){
        Throwable fcause = getFinalCause(e);
        return fcause.getMessage() == null ? fcause.getClass().getSimpleName() : fcause.getClass().getSimpleName() + ": " + fcause.getMessage();
    }

    public static String getFinalMessage(Throwable e){
        String message = e.getMessage();
        while(e.getCause() != null){
            e = e.getCause();
            if(e.getMessage() != null){
                message = e.getMessage();
            }
        }
        return message;
    }

    public static Throwable getFinalCause(Throwable e){
        while(e.getCause() != null){
            e = e.getCause();
        }
        return e;
    }

    public static String getStackTrace(Throwable e){
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /** @return a neat error message of a throwable, with stack trace. */
    public static String neatError(Throwable e){
        return neatError(e, true);
    }

    /** @return a neat error message of a throwable, with stack trace. */
    public static String neatError(Throwable e, boolean stacktrace){
        StringBuilder build = new StringBuilder();

        while(e != null){
            String name = e.getClass().toString().substring("class ".length()).replace("Exception", "");
            if(name.indexOf('.') != -1){
                name = name.substring(name.lastIndexOf('.') + 1);
            }

            build.append("> ").append(name);
            if(e.getMessage() != null){
                build.append(": ");
                build.append("'").append(e.getMessage()).append("'");
            }

            if(stacktrace){
                for(StackTraceElement s : e.getStackTrace()){
                    if(s.getClassName().contains("MethodAccessor") || s.getClassName().substring(s.getClassName().lastIndexOf(".") + 1).equals("Method")) continue;
                    build.append("\n");

                    String className = s.getClassName();
                    build.append(className.substring(className.lastIndexOf(".") + 1)).append(".").append(s.getMethodName()).append(": ").append(s.getLineNumber());
                }
            }

            build.append("\n");

            e = e.getCause();
        }


        return build.toString();
    }

    public static String stripColors(CharSequence str){
        StringBuilder out = new StringBuilder(str.length());

        int i = 0;
        while(i < str.length()){
            char c = str.charAt(i);

            // Possible color tag.
            if(c == '['){
                int length = parseColorMarkup(str, i + 1, str.length());
                if(length >= 0){
                    i += length + 2;
                }else{
                    out.append(c);
                    //escaped string
                    i++;
                }
            }else{
                out.append(c);
                i++;
            }
        }

        return out.toString();
    }

    public static String stripGlyphs(CharSequence str){
        StringBuilder out = new StringBuilder(str.length());

        for(int i = 0; i < str.length(); i++){
            int c = str.charAt(i);
            if(c >= 0xE000 && c <= 0xF8FF) continue;
            out.append((char)c);
        }

        return out.toString();
    }

    private static int parseColorMarkup(CharSequence str, int start, int end){
        if(start >= end) return -1; // String ended with "[".
        switch(str.charAt(start)){
            case '#':
                // Parse hex color RRGGBBAA where AA is optional and defaults to 0xFF if less than 6 chars are used.
                for(int i = start + 1; i < end; i++){
                    char ch = str.charAt(i);
                    if(ch == ']'){
                        if(i < start + 2 || i > start + 9) break; // Illegal number of hex digits.
                        return i - start;
                    }
                    if(!(ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'f' || ch >= 'A' && ch <= 'F')){
                        break; // Unexpected character in hex color.
                    }
                }
                return -1;
            case '[': // "[[" is an escaped left square bracket.
                return -2;
            case ']': // "[]" is a "pop" color tag.
                //pop the color stack here if needed
                return 0;
        }
        // Parse named color.
        for(int i = start + 1; i < end; i++){
            char ch = str.charAt(i);
            if(ch != ']') continue;
            Color namedColor = Colors.get(str.subSequence(start, i).toString());
            if(namedColor == null) return -1; // Unknown color name.
            //namedColor is the result color here
            return i - start;
        }
        return -1; // Unclosed color tag.
    }
    
    /** Removes colors, glyphs and leading spaces. */
    public static String normalize(String str){
        return stripGlyphs(stripColors(str)).trim();
    }

    /** Replaces non-safe filename characters with '_'. Handles reserved window file names. */
    public static String sanitizeFilename(String str){
        if(str.equals(".")){
            return "_";
        }else if(str.equals("..")){
            return "__";
        }else if(reservedFilenamePattern.matcher(str).matches()){
            //turn things like con.msch -> _con.msch, which is no longer reserved
            str = "_" + str;
        }
        return filenamePattern.matcher(str).replaceAll("_");
    }

    public static String encode(String str){
        try{
            return URLEncoder.encode(str, "UTF-8");
        }catch(UnsupportedEncodingException why){
            //why the HECK does this even throw an exception
            throw new RuntimeException(why);
        }
    }

    public static String format(String text, Object... args){
        if(args.length > 0){            
            StringBuilder out = new StringBuilder(text.length() + args.length*2);
            format(out, text, args);
            return out.toString();
        }

        return text;
    }

    public static void format(StringBuilder out, String text, Object... args){
        if(args.length > 0){
            for(int i = 0, argi = 0, n = text.length(); i < n; i++){
                char c = text.charAt(i);
                if(c == '@' && argi < args.length) out.append(args[argi++]);
                else out.append(c);
            }
        }else out.append(text);
    }

    public static String join(String separator, String... strings){
        StringBuilder builder = new StringBuilder();
        for(String s : strings){
            builder.append(s);
            builder.append(separator);
        }
        builder.setLength(builder.length() - separator.length());
        return builder.toString();
    }

    public static String join(String separator, Iterable<String> strings){
        StringBuilder builder = new StringBuilder();
        for(String s : strings){
            builder.append(s);
            builder.append(separator);
        }
        builder.setLength(builder.length() - separator.length());
        return builder.toString();
    }

    public static String join(CharSequence delimiter, CharSequence[] elements, int start, int end){
        if(elements == null || delimiter == null) return null;
        if(elements.length == 0 || start < 0 || end > elements.length || start >= end) return "";
        StringJoiner joiner = new StringJoiner(delimiter);
        for(int i = start; i < end; i++) joiner.add(elements[i]);
        return joiner.toString();
    }

    /** Returns the levenshtein distance between two strings. */
    public static int levenshtein(String x, String y){
        int[][] dp = new int[x.length() + 1][y.length() + 1];

        for(int i = 0; i <= x.length(); i++){
            for(int j = 0; j <= y.length(); j++){
                if(i == 0){
                    dp[i][j] = j;
                }else if(j == 0){
                    dp[i][j] = i;
                }else{
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j - 1]
                    + (x.charAt(i - 1) == y.charAt(j - 1) ? 0 : 1),
                    dp[i - 1][j] + 1),
                    dp[i][j - 1] + 1);
                }
            }
        }

        return dp[x.length()][y.length()];
    }
    
    /** Taken from the {@link String#repeat(int)} method of JDK 11 */
    public static String repeat(String str, int count){
        if(count < 0) throw new IllegalArgumentException("count is negative: " + count);
        if(count == 1) return str;
        
        final byte[] value = str.getBytes();
        final int len = value.length;
        if(len == 0 || count == 0) return "";
        if(Integer.MAX_VALUE / count < len) 
            throw new OutOfMemoryError("Required length exceeds implementation limit");
        if(len == 1){ 
            final byte[] single = new byte[count]; 
            java.util.Arrays.fill(single, value[0]); 
            return new String(single); 
        }
        
        final int limit = len * count;
        final byte[] multiple = new byte[limit];
        System.arraycopy(value, 0, multiple, 0, len);
        int copied = len;
        for(; copied < limit - copied; copied <<= 1) 
            System.arraycopy(multiple, 0, multiple, copied, copied);
        System.arraycopy(multiple, 0, multiple, copied, limit - copied);
        return new String(multiple);
    }

    public static String animated(float time, int length, float scale, String replacement){
        return new String(new char[Math.abs((int)(time / scale) % length)]).replace("\0", replacement);
    }

    public static String kebabToCamel(String s){
        StringBuilder result = new StringBuilder(s.length());

        for(int i = 0; i < s.length(); i++){
            char c = s.charAt(i);
            if(c != '_' && c != '-'){
                if(i != 0 && (s.charAt(i - 1) == '_' || s.charAt(i - 1) == '-')){
                    result.append(Character.toUpperCase(c));
                }else{
                    result.append(Character.toLowerCase(c));
                }
            }
        }

        return result.toString();
    }

    public static String camelToKebab(String s){
        StringBuilder result = new StringBuilder(s.length() + 1);

        for(int i = 0; i < s.length(); i++){
            char c = s.charAt(i);
            if(i > 0 && Character.isUpperCase(s.charAt(i))){
                result.append('-');
            }

            result.append(Character.toLowerCase(c));

        }

        return result.toString();
    }
    
    public static String kebabize(String s){
        StringBuilder sb = new StringBuilder(s.length());
        boolean sep = true;
        for(int i = 0, n = s.length(); i < n; i++){
            char c = s.charAt(i);
            if(c == '_' || c == '-' || c == ' '){
                if(!sep) sb.append('-');
                sep = true;
            }else if(Character.isUpperCase(c)){
                if(!sep) sb.append('-');
                sb.append(Character.toLowerCase(c));
                sep = false;
            }else{
                sb.append(c);
                sep = false;
            }
        }
        return sb.toString();
    }

    /**Converts a snake_case or kebab-case string to Upper Case.
     * For example: "test_string" -> "Test String"*/
    public static String capitalize(String s){
        StringBuilder result = new StringBuilder(s.length());

        for(int i = 0; i < s.length(); i++){
            char c = s.charAt(i);
            if(c == '_' || c == '-'){
                result.append(' ');
            }else if(i == 0 || s.charAt(i - 1) == '_' || s.charAt(i - 1) == '-'){
                result.append(Character.toUpperCase(c));
            }else{
                result.append(c);
            }
        }

        return result.toString();
    }

    /** Adds spaces to a camel/pascal case string. */
    public static String insertSpaces(String s){
        StringBuilder result = new StringBuilder(s.length() + 1);

        for(int i = 0; i < s.length(); i++){
            char c = s.charAt(i);

            if(i > 0 && Character.isUpperCase(c)){
                result.append(' ');
            }

            result.append(c);
        }

        return result.toString();
    }

    /**Converts a Space Separated string to camelCase.
     * For example: "Camel Case" -> "camelCase"*/
    public static String camelize(String s){
        StringBuilder result = new StringBuilder(s.length());
        boolean space = false;

        for(int i = 0; i < s.length(); i++){
            char c = s.charAt(i);
            if(i == 0){
                result.append(Character.toLowerCase(c));
            }else if(c != ' '){
                result.append(space ? Character.toUpperCase(c) : c);
                space = false;
            }else{
                space = true;
            }

        }

        return result.toString();
    }

    public static boolean canParseInt(String s){
        return parseInt(s) != Integer.MIN_VALUE;
    }

    public static boolean canParsePositiveInt(String s){
        int p = parseInt(s);
        return p >= 0;
    }
    
    /** Returns Integer.MIN_VALUE if parsing failed. */
    public static int parseInt(String s){
        return parseInt(s, Integer.MIN_VALUE);
    }
    public static int parseInt(String s, int defaultValue){
        return parseInt(s, 10, defaultValue);
    }
    public static int parseInt(String s, int radix, int defaultValue){
        return parseInt(s, radix, defaultValue, 0, s.length());
    }
    public static int parseInt(String s, int radix, int defaultValue, int start, int end){
        boolean negative = false;
        int i = start, len = end - start, limit = -2147483647;
        if(len <= 0){
            return defaultValue;
        }else{
            char firstChar = s.charAt(i);
            if(firstChar < '0'){
                if(firstChar == '-'){
                    negative = true;
                    limit = -2147483648;
                }else if(firstChar != '+'){
                    return defaultValue;
                }

                if(len == 1) return defaultValue;

                ++i;
            }

            int limitForMaxRadix = (-Integer.MAX_VALUE) / 36;
            int limitBeforeMul = limitForMaxRadix;

            int digit, result = 0;
            while(i < end){
                digit = Character.digit(s.charAt(i++), radix);
                if(digit < 0) return defaultValue;
                if(result < limitBeforeMul){
                    if(limitBeforeMul == limitForMaxRadix){
                        limitBeforeMul = limit / radix;

                        if(result < limitBeforeMul){
                            return defaultValue;
                        }
                    }else{
                        return defaultValue;
                    }
                }

                result *= radix;
                if(result < limit + digit){
                    return defaultValue;
                }

                result -= digit;
            }

            return negative ? result : -result;
        }
    }
    
    public static boolean canParseLong(String s){
        return parseLong(s) != Long.MIN_VALUE;
    }

    public static boolean canParsePositiveLong(String s){
        long p = parseLong(s);
        return p >= 0;
    }

    public static long parseLong(String s){
        return parseLong(s, 10, Long.MIN_VALUE);
    }
    public static long parseLong(String s, long defaultValue){
        return parseLong(s, 10, defaultValue);
    }
    public static long parseLong(String s, int radix, long defaultValue){
        return parseLong(s, radix, 0, s.length(), defaultValue);
    }

    public static long parseLong(String s, int radix, int start, int end, long defaultValue){
        boolean negative = false;
        int i = start, len = end - start;
        long limit = -9223372036854775807L;
        if(len <= 0){
            return defaultValue;
        }else{
            char firstChar = s.charAt(i);
            if(firstChar < '0'){
                if(firstChar == '-'){
                    negative = true;
                    limit = -9223372036854775808L;
                }else if(firstChar != '+'){
                    return defaultValue;
                }

                if(len == 1) return defaultValue;

                ++i;
            }

            long result;
            int digit;
            for(result = 0L; i < end; result -= digit){
                digit = Character.digit(s.charAt(i++), radix);
                if(digit < 0){
                    return defaultValue;
                }

                result *= radix;
                if(result < limit + (long)digit){
                    return defaultValue;
                }
            }

            return negative ? result : -result;
        }
    }
    public static double parseDouble(String value){
        return parseDouble(value, Double.MIN_VALUE);
    }      
    public static double parseDouble(String value, double defaultValue){
        return parseDouble(value, 0, value.length(), defaultValue);
    }
    /** Faster double parser that doesn't throw exceptions. */
    public static double parseDouble(String value, int start, int end, double defaultValue){
        int len = end - start;
        if(len <= 0) return defaultValue;

        int sign = 1;
        char last = value.charAt(len - 1), first = value.charAt(0);
        if(last == 'F' || last == 'f' || last == '.'){
            end --;
        }
        if(first == '+'){
            start = 1;
        }
        if(first == '-'){
            start = 1;
            sign = -1;
        }

        int dot = -1, e = -1;
        for(int i = start; i < end; i++){
            char c = value.charAt(i);
            if(c == '.') dot = i;
            if(c == 'e' || c == 'E') e = i;
        }

        if(dot != -1 && dot < end){
            //negation as first character
            long whole = start == dot ? 0 : parseLong(value, 10, start, dot, Long.MIN_VALUE);
            if(whole == Long.MIN_VALUE) return defaultValue;
            long dec = parseLong(value, 10, dot + 1, end, Long.MIN_VALUE);
            if(dec < 0) return defaultValue;
            return (whole + Math.copySign(dec / Math.pow(10, (end - dot - 1)), whole)) * sign;
        }

        //check scientific notation
        if(e != -1){
            long whole = parseLong(value, 10, start, e, Long.MIN_VALUE);
            if(whole == Long.MIN_VALUE) return defaultValue;
            long power = parseLong(value, 10, e + 1, end, Long.MIN_VALUE);
            if(power == Long.MIN_VALUE) return defaultValue;
            return whole * Math.pow(10, power) * sign;
        }

        //parse as standard integer
        long out = parseLong(value, 10, start, end, Long.MIN_VALUE);
        return out == Long.MIN_VALUE ? defaultValue : out*sign;
    }

    public static boolean canParseFloat(String s){
        try{
            Float.parseFloat(s);
            return true;
        }catch(Exception e){
            return false;
        }
    }

    public static boolean canParsePositiveFloat(String s){
        try{
            return Float.parseFloat(s) >= 0;
        }catch(Exception e){
            return false;
        }
    }

    /** Returns Float.NEGATIVE_INFINITY if parsing failed. */
    public static float parseFloat(String s){
        return parseFloat(s, Float.MIN_VALUE);
    }
    public static float parseFloat(String s, float defaultValue){
        try{
            return Float.parseFloat(s);
        }catch(Exception e){
            return defaultValue;
        }
    }

    public static String autoFixed(float value, int max){

        //truncate extra digits past the max
        value = (float)Mathf.floor(value * Mathf.pow(10, max) + 0.001f) / Mathf.pow(10, max);

        int precision =
                Math.abs(Mathf.floor(value) - value) < 0.0001f ? 0 :
                Math.abs(Mathf.floor(value * 10) - value * 10) < 0.0001f ? 1 :
                Math.abs(Mathf.floor(value * 100) - value * 100) < 0.0001f ? 2 :
                Math.abs(Mathf.floor(value * 1000) - value * 1000) < 0.0001f ? 3 :
                4;

        return fixed(value, Math.min(max, precision));
    }

    public static String fixed(float d, int decimalPlaces){
        return fixedBuilder(d, decimalPlaces).toString();
    }

    public static StringBuilder fixedBuilder(float d, int decimalPlaces){
        if(decimalPlaces < 0 || decimalPlaces > 8){
            throw new IllegalArgumentException("Unsupported number of " + "decimal places: " + decimalPlaces);
        }
        boolean negative = d < 0;
        d = Math.abs(d);
        StringBuilder dec = tmp2;
        dec.setLength(0);
        dec.append((int)(float)(d * Math.pow(10, decimalPlaces) + 0.0001f));

        int len = dec.length();
        int decimalPosition = len - decimalPlaces;
        StringBuilder result = tmp1;
        result.setLength(0);
        if(negative) result.append('-');
        if(decimalPlaces == 0){
            if(negative) dec.insert(0, '-');
            return dec;
        }else if(decimalPosition > 0){
            // Insert a dot in the right place
            result.append(dec, 0, decimalPosition);
            result.append(".");
            result.append(dec, decimalPosition, dec.length());
        }else{
            result.append("0.");
            // Insert leading zeroes into the decimal part
            while(decimalPosition++ < 0){
                result.append("0");
            }
            result.append(dec);
        }
        return result;
    }

    public static String formatMillis(long val){
        StringBuilder buf = new StringBuilder(20);
        String sgn = "";

        if(val < 0) sgn = "-";
        val = Math.abs(val);

        append(buf, sgn, 0, (val / 3600000));
        val %= 3600000;
        append(buf, ":", 2, (val / 60000));
        val %= 60000;
        append(buf, ":", 2, (val / 1000));
        return buf.toString();
    }

    private static void append(StringBuilder tgt, String pfx, int dgt, long val){
        tgt.append(pfx);
        if(dgt > 1){
            int pad = (dgt - 1);
            for(long xa = val; xa > 9 && pad > 0; xa /= 10) pad--;
            for(int xa = 0; xa < pad; xa++) tgt.append('0');
        }
        tgt.append(val);
    }

    /** Replaces all instances of {@code find} with {@code replace}. */
    public static StringBuilder replace(StringBuilder builder, String find, String replace){
        int findLength = find.length(), replaceLength = replace.length();
        int index = 0;
        while(true){
            index = builder.indexOf(find, index);
            if(index == -1) break;
            builder.replace(index, index + findLength, replace);
            index += replaceLength;
        }
        return builder;
    }

    /** Replaces all instances of {@code find} with {@code replace}. */
    public static StringBuilder replace(StringBuilder builder, char find, String replace) {
        int replaceLength = replace.length();
        int index = 0;
        while(true){
            while(true){
                if(index == builder.length()) return builder;
                if(builder.charAt(index) == find) break;
                index++;
            }
            builder.replace(index, index + 1, replace);
            index += replaceLength;
        }
    }

    /** Converts a list to a human readable sentence. E.g. [1, 2, 3, 4, 5] -> "1, 2, 3, 4 and 5" */
    public static <T> String toSentence(Iterable<T> list, Func<T, String> stringifier){
        StringBuilder builder = new StringBuilder();
        toSentence(builder, list, (b, e) -> b.append(stringifier.get(e)));
        return builder.toString();
    }

    /** Converts a list to a human readable sentence. E.g. [1, 2, 3, 4, 5] -> "1, 2, 3, 4 and 5" */
    public static <T> String toSentence(Iterable<T> list, Func<T, String> stringifier, String or, String and){
        StringBuilder builder = new StringBuilder();
        toSentence(builder, list, (b, e) -> b.append(stringifier.get(e)), or, and);
        return builder.toString();
    }

    /** Converts a list to a human readable sentence. E.g. [1, 2, 3, 4, 5] -> "1, 2, 3, 4 and 5" */
    public static <T> void toSentence(StringBuilder builder, Iterable<T> list, Cons2<StringBuilder, T> stringifier){
        toSentence(builder, list, stringifier, ", ", " and ");
    }

    /** Converts a list to a human readable sentence. E.g. [1, 2, 3, 4, 5] -> "1, 2, 3, 4 and 5" */
    public static <T> void toSentence(StringBuilder builder, Iterable<T> list, Cons2<StringBuilder, T> stringifier, String or, String and){
        Iterator<T> iter = list.iterator();
        if(!iter.hasNext()) return;
        
        stringifier.get(builder, iter.next());
        while(iter.hasNext()){
            T tmp = iter.next();
            builder.append(iter.hasNext() ? or : and);
            stringifier.get(builder, tmp);
        }
    }

    /** {@link String#contains(CharSequence)} but with a predicate. */
    public static boolean contains(String src, Boolf<Character> predicate){
        for(int i = 0, n = src.length(); i < n; i++){
            if(predicate.get(src.charAt(i))) return true;
        }
        return false;
    }

    public static String hueToColorTag(int hue){
        StringBuilder builder = new StringBuilder(9);
        hueToColorTag(builder, hue);
        return builder.toString();
    }

    /** Re-implementation of {@link Color#fromHsv(float, float, float)} that handles only the hue. */
    public static void hueToColorTag(StringBuilder builder, int hue){
        int r, g, b;
        float x = (hue / 60f + 6) % 6;
        float f = x - (int)x;
        float q = 1 - f;
        switch((int)x){
            case 0:
                r = 255;
                g = (int)(f * 255);
                b = 0;
                break;
            case 1:
                r = (int)(q * 255);
                g = 255;
                b = 0;
                break;
            case 2:
                r = 0;
                g = 255;
                b = (int)(f * 255);
                break;
            case 3:
                r = 0;
                g = (int)(q * 255);
                ;
                b = 255;
                break;
            case 4:
                r = (int)(f * 255);
                g = 0;
                b = 255;
                break;
            default:
                r = 255;
                g = 0;
                b = (int)(q * 255);
        }
        builder.append("[#");
        if(r < 16) builder.append('0');
        builder.append(Integer.toHexString(r));
        if(g > 0 || b > 0){
            if(g < 16) builder.append('0');
            builder.append(Integer.toHexString(g));
            if(b > 0){ if(b < 16) builder.append('0'); builder.append(Integer.toHexString(b)); }
        }
        builder.append(']');
    }

    public static String rainbowify(String src, int startHue, int addedHue){
        StringBuilder builder = new StringBuilder(src.length() * 9);
        for(int i = 0, n = src.length(); i < n; i++){
            hueToColorTag(builder, startHue);
            char c = src.charAt(i);
            if(c == '[') builder.append('[');
            builder.append(c).append("[]");
            startHue += addedHue;
            startHue %= 360;
        }
        return builder.toString();
    }

    /** @return whether the specified string mean true */
    public static boolean isTrue(String str){ return isTrue(str, true); }

    /** @return whether the specified string mean true */
    public static boolean isTrue(String str, boolean isValue){
        if(isValue){
            switch(str.toLowerCase()){
                case "1":
                case "true":
                case "on":
                case "enable":
                case "activate":
                case "yes":
                    return true;
                default:
                    return false;
            }
        }else{
            switch(str.toLowerCase()){
                case "on":
                case "enable":
                case "activate":
                    return true;
                default:
                    return false;
            }
        }
    }

    /** @return whether the specified string mean false */
    public static boolean isFalse(String str){ return isFalse(str, true); }

    /** @return whether the specified string mean false */
    public static boolean isFalse(String str, boolean isValue){
        if(isValue){
            switch(str.toLowerCase()){
                case "0":
                case "false":
                case "off":
                case "disable":
                case "desactivate":
                case "no":
                    return true;
                default:
                    return false;
            }
        }else{
            switch(str.toLowerCase()){
                case "off":
                case "disable":
                case "desactivate":
                    return true;
                default:
                    return false;
            }
        }
    }

    /**
     * @return whether {@code newVersion} is greater than {@code currentVersion}. (e.g. {@code "v146" > "124.1"})
     * @apiNote can handle dots and dashes in the version and makes very fast comparison. <br>
     *          Also ignores non-int parts. (e.g. {@code "v1.2-rc36"}, the {@code "rc36"} part will be ignored)
     */
    public static boolean isVersionAtLeast(String currentVersion, String newVersion){
        if(currentVersion == null || newVersion == null || currentVersion.isEmpty() || newVersion.isEmpty()) 
            return false;
        
        int last1 = currentVersion.charAt(0) == 'v' ? 1 : 0, last2 = newVersion.charAt(0) == 'v' ? 1 : 0,
            len1 = currentVersion.length(), len2 = newVersion.length(), 
            dot1 = 0, dot2 = 0, 
            dash1 = 0, dash2 = 0, 
            part1 = 0, part2 = 0;
        
        while((dot1 != -1 && dot2 != -1) && (last1 < len1 && last2 < len2)){
            dot1 = currentVersion.indexOf('.', last1);
            dash1 = currentVersion.indexOf('-', last1);
            dot2 = newVersion.indexOf('.', last2);
            dash2 = newVersion.indexOf('-', last2);
            
            if(dot1 == -1) dot1 = dash1;
            if(dash1 != -1) dot1 = Math.min(dot1, dash1);
            if(dot1 == -1) dot1 = len1;
            if(dot2 == -1) dot2 = dash2;
            if(dash2 != -1) dot2 = Math.min(dot2, dash2);
            if(dot2 == -1) dot2 = len2;
            
            part1 = parseInt(currentVersion, 10, 0, last1, dot1);
            part2 = parseInt(newVersion, 10, 0, last2, dot2);
            last1 = dot1 + 1;
            last2 = dot2 + 1;
            
            if(part1 != part2) return part2 > part1;
        }
        
        // Continue iteration on newVersion to see if it's just leading zeros.
        while(dot2 != -1 && last2 < len2){
            dot2 = newVersion.indexOf('.', last2);
            dash2 = newVersion.indexOf('-', last2);
            
            if(dot2 == -1) dot2 = dash2;
            if(dash2 != -1) dot2 = Math.min(dot2, dash2);
            if(dot2 == -1) dot2 = len2;
            
            part2 = parseInt(newVersion, 10, 0, last2, dot2);
            last2 = dot2 + 1;
            
            if(part2 > 0) return true;
        }
        return false;
    }
}
