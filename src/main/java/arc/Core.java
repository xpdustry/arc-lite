package arc;

import arc.util.*;

import java.util.concurrent.*;

/** Global references to all of Arc's core modules. */
public class Core{
    public static Application app;
    public static Files files;
    /**
     * @deprecated Just here for compatibility reasons, only contains methods about frame rate.
     *             Please use same methods from {@link #app} instead.
     */
    @Deprecated
    public static Graphics graphics;
    public static Settings settings;
    public static I18NBundle bundle = I18NBundle.createEmptyBundle();

    public static ExecutorService executor = Threads.executor("Main Executor", OS.cores);
}
