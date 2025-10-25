package arc.backend.headless;

import arc.*;
import arc.func.*;
import arc.mock.*;
import arc.struct.*;
import arc.util.*;

/**
 * a headless implementation of an application primarily intended to be used in servers
 * @author Jon Renner
 */
public class HeadlessApplication implements Application{
    protected final Seq<ApplicationListener> listeners = new Seq<>();
    protected final TaskQueue runnables = new TaskQueue();
    protected final Cons<Throwable> exceptionHandler;
    protected long renderInterval;
    protected Thread mainLoopThread;
    protected boolean running = true;
    long frameId = -1;
    float deltaTime = 0;
    long frameStart = 0;
    int frames = 0;
    int fps;
    long lastTime = System.nanoTime();

    public HeadlessApplication(ApplicationListener listener){
        this(listener, 1f / 60f, t -> { throw new RuntimeException(t); });
    }

    public HeadlessApplication(ApplicationListener listener, Cons<Throwable> exceptionHandler){
        this(listener, 1f / 60f, exceptionHandler);
    }

    public HeadlessApplication(ApplicationListener listener, float renderIntervalSec, Cons<Throwable> exceptionHandler){
        addListener(listener);
        this.exceptionHandler = exceptionHandler;
        
        Core.settings = new Settings();
        Core.app = this;
        Core.files = new MockFiles();

        renderInterval = renderIntervalSec > 0 ? (long)(renderIntervalSec * 1000000000f) : (renderIntervalSec < 0 ? -1 : 0);

        initialize();
    }

    protected void initialize(){
        mainLoopThread = new Thread(this::mainLoop, "HeadlessApplication");
        mainLoopThread.setUncaughtExceptionHandler((t, e) -> exceptionHandler.get(e));
        mainLoopThread.start();
    }

    void mainLoop(){
        synchronized(listeners){
            for(ApplicationListener listener : listeners){
                listener.init();
            }
        }

        long t = Time.nanos() + renderInterval;
        if(renderInterval >= 0f){
            while(running){
                final long n = Time.nanos();
                if(t > n){
                    long sleep = t - n;
                    Threads.sleep(sleep / 1000000, (int)(sleep % 1000000));

                    t += renderInterval;
                }else{
                    t = n + renderInterval;
                }

                runnables.run();
                incrementFrameId();
                defaultUpdate();

                synchronized(listeners){
                    for(ApplicationListener listener : listeners){
                        listener.update();
                    }
                }
                updateTime();
            }
        }

        synchronized(listeners){
            for(ApplicationListener listener : listeners){
                listener.pause();
                listener.dispose();
            }
            dispose();
        }
    }

    public void updateTime(){
        long time = System.nanoTime();
        deltaTime = (time - lastTime) / 1000000000.0f;
        lastTime = time;

        if(time - frameStart >= 1000000000){
            fps = frames;
            frames = 0;
            frameStart = time;
        }
        frames++;
    }

    
    public void incrementFrameId() {
        frameId++;
    }
    
    @Override
    public long getFrameId(){
        return frameId;
    }

    @Override
    public float getDeltaTime(){
        return deltaTime;
    }

    @Override
    public int getFramesPerSecond(){
        return fps;
    }

    @Override
    public Thread getMainThread(){
        return mainLoopThread;
    }

    @Override
    public ApplicationType getType(){
        return ApplicationType.headless;
    }

    @Override
    public String getClipboardText(){
        return null;
    }

    @Override
    public void setClipboardText(String text){

    }

    @Override
    public Seq<ApplicationListener> getListeners(){
        return listeners;
    }

    @Override
    public void post(Runnable runnable){
        runnables.post(runnable);
    }

    @Override
    public void exit(){
        post(() -> running = false);
    }

}
