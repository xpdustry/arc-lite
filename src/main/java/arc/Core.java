package arc;

import arc.util.*;

import java.util.concurrent.*;

/** Global references to all of Arc's core modules. */
public class Core{
    public static Application app;
    public static Files files;
    public static Settings settings;
    public static I18NBundle bundle = I18NBundle.createEmptyBundle();

    public static ExecutorService executor = Threads.executor("Main Executor", OS.cores);

}
