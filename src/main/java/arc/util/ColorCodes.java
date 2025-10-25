package arc.util;

import static org.fusesource.jansi.internal.Kernel32.*;

import arc.struct.*;

/** Note that these color codes will only work on linux or mac terminals. And the Windows Terminal app. */
public class ColorCodes{
    public static String
    prefix = "&",
    
    flush = "\033[H\033[2J",
    reset = "\u001B[0m",
    bold = "\u001B[1m",
    italic = "\u001B[3m",
    underline = "\u001B[4m",
    black = "\u001B[30m",
    red = "\u001B[31m",
    green = "\u001B[32m",
    yellow = "\u001B[33m",
    blue = "\u001B[34m",
    purple = "\u001B[35m",
    cyan = "\u001B[36m",
    lightBlack = "\u001b[90m",
    lightRed = "\u001B[91m",
    lightGreen = "\u001B[92m",
    lightYellow = "\u001B[93m",
    lightBlue = "\u001B[94m",
    lightMagenta = "\u001B[95m",
    lightCyan = "\u001B[96m",
    lightWhite = "\u001b[97m",
    white = "\u001B[37m",

    backDefault = "\u001B[49m",
    backRed = "\u001B[41m",
    backGreen = "\u001B[42m",
    backYellow = "\u001B[43m",
    backBlue = "\u001B[44m";

    public static final String[] codes, values;

    static{
        if((OS.isWindows && !OS.hasEnv("WT_SESSION") && !install()) || OS.isAndroid){
            flush = reset = bold = underline = black = red = green = yellow = blue = purple = cyan = lightWhite
            = lightBlack = lightRed = lightGreen = lightYellow = lightBlue = lightMagenta = lightCyan
            = white = backDefault = backRed = backYellow = backBlue = backGreen = italic = "";
        }

        ObjectMap<String, String> map = ObjectMap.of(
        "ff", flush,
        "fr", reset,
        "fb", bold,
        "fi", italic,
        "fu", underline,
        "k", black,
        "lk", lightBlack,
        "lw", lightWhite,
        "r", red,
        "g", green,
        "y", yellow,
        "b", blue,
        "p", purple,
        "c", cyan,
        "lr", lightRed,
        "lg", lightGreen,
        "ly", lightYellow,
        "lm", lightMagenta,
        "lb", lightBlue,
        "lc", lightCyan,
        "w", white,

        "bd", backDefault,
        "br", backRed,
        "bg", backGreen,
        "by", backYellow,
        "bb", backBlue
        );

        codes = map.keys().toSeq().toArray(String.class);
        values = map.values().toSeq().toArray(String.class);
    }
    
    public static String apply(String text){
        return apply(text, true);
    }
    
    public static String apply(String text, boolean useColors){
        if(useColors){
            for(int i = 0; i < codes.length; i++) 
                text = text.replace(prefix + codes[i], values[i]);
        }else{
            for(String color : codes) 
                text = text.replace(prefix + color, "");
        }
        return text;
    }
    
    //////////
    // https://github.com/xpdustry/ansi-enabler/blob/master/src/main/java/com/xpdustry/ansi/Ansi.java
    
    private static final int ENABLE_PROCESSED_OUTPUT = 0x0001,
                             ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004;
  
    private static boolean install(){
        if (!jansiPresent()) return false;
        if (enable()) return true; 
        // At this point we cannot use arc.util.Log
        System.out.print("WARNING: Failed to enable ANSI colors: " + getLastError());
        return false;
    }
    
    private static boolean jansiPresent(){
        try {
            Class.forName("org.fusesource.jansi.internal.Kernel32");
            return true;
        }catch(Throwable e) {
            return false;
        }
    }
    
    private static boolean enable(){
        return enable(STD_INPUT_HANDLE)
            && enable(STD_OUTPUT_HANDLE)
            && enable(STD_ERROR_HANDLE);
    }
    
    private static boolean enable(final int hConsoleHandle){
        final long console = GetStdHandle(hConsoleHandle);
        final int[] mode = new int[1];
        return GetConsoleMode(console, mode) != 0
            && SetConsoleMode(console, mode[0] | ENABLE_PROCESSED_OUTPUT | ENABLE_VIRTUAL_TERMINAL_PROCESSING) != 0;
    }
    
    private static String getLastError(){
        final int errorCode = GetLastError();
        final int bufferSize = 160;
        final byte data[] = new byte[bufferSize]; 
        FormatMessageW(FORMAT_MESSAGE_FROM_SYSTEM, 0, errorCode, 0, data, bufferSize, null);
        return new String(data);
    }
    
    //////////
}
