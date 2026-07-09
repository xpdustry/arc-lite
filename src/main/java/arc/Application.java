package arc;

import arc.struct.*;
import arc.util.*;

import java.net.*;

public interface Application extends Disposable{

    /** Returns a list of all the application listeners used. */
    Seq<ApplicationListener> getListeners();

    /** Adds a new application listener. */
    default void addListener(ApplicationListener listener){
        synchronized(getListeners()){
            getListeners().add(listener);
        }
    }

    /** Removes an application listener. */
    default void removeListener(ApplicationListener listener){
        post(() -> {
            synchronized(getListeners()){
                getListeners().remove(listener);
            }
        });
    }

    /** Call this before update() in each backend. */
    default void defaultUpdate(){
        Core.settings.save();
        Time.updateGlobal();
    }

    /** @return what {@link ApplicationType} this application has, e.g. Android or Desktop */
    @Deprecated
    default ApplicationType getType(){
        return ApplicationType.headless;
    }

    @Deprecated
    default boolean isDesktop(){
        return getType() == ApplicationType.desktop;
    }

    @Deprecated
    default boolean isHeadless(){
        return getType() == ApplicationType.headless;
    }

    @Deprecated
    default boolean isAndroid(){
        return getType() == ApplicationType.android;
    }

    @Deprecated
    default boolean isIOS(){
        return getType() == ApplicationType.iOS;
    }

    @Deprecated
    default boolean isMobile(){
        return isAndroid() || isIOS();
    }

    @Deprecated
    default boolean isWeb(){
        return getType() == ApplicationType.web;
    }

    /** @return the Android API level on Android, the major OS version on iOS (5, 6, 7, ..), or 0 on the desktop. */
    default int getVersion(){
        return 0;
    }

    /**
     * Returns the id of the current frame. The general contract of this method is that the id is incremented only when the
     * application is in the running state right before calling the {@link ApplicationListener#update()} method. Also, the id of
     * the first frame is 0; the id of subsequent frames is guaranteed to take increasing values for 2<sup>63</sup>-1 rendering
     * cycles.
     * @return the id of the current frame
     */
    default long getFrameId(){
        return 0;
    }

    /** @return the time span between the current frame and the last frame in seconds. Might be smoothed over n frames. */
    default float getDeltaTime(){
        return 1f;
    }

    /** @return the average number of frames per second */
    default int getFramesPerSecond(){
        return 60;
    }

    /** @return the Java heap memory use in bytes. */
    default long getJavaHeap(){
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    /** @return the main graphics thread, upon which all ApplicationListener methods are called. May return null if not initialized. */
    default @Nullable Thread getMainThread(){
        return null;
    }

    /**
     * @return whether the currently executing thread is the main thread.
     * If the main thread is not initialized, returns true by default. This is used for error checking purposes.
     * */
    default boolean isOnMainThread(){
        Thread thread = getMainThread();
        return thread == null || Thread.currentThread() == thread;
    }

    default void getDnsServers(Seq<InetSocketAddress> out){}

    /** Posts a runnable on the main loop thread.*/
    void post(Runnable runnable);

    /**
     * Schedule an exit from the application. On android, this will cause a call to pause() and dispose() some time in the future,
     * it will not immediately finish your application.
     */
    void exit();

    /** Disposes of core resources. */
    @Override
    default void dispose(){
        //flush any changes to settings upon dispose
        if(Core.settings != null){
            Core.settings.save();
        }
    }

    /**
     * Enumeration of possible {@link Application} types.
     * @deprecated Useless as this fork only contains utilities for headless apps.
     */
    @Deprecated
    enum ApplicationType{
        @Deprecated android,
        @Deprecated desktop,
        @Deprecated headless,
        @Deprecated web,
        @Deprecated iOS
    }
}
