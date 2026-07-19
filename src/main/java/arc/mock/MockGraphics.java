package arc.mock;

import arc.*;

/**
 * The headless backend does its best to mock elements. This is intended to make code-sharing between
 * server and client as simple as possible.
 *
 * @deprecated Just here for compatibility reasons, only contains methods about frame rate.
 *             Please use same methods from {@link Application} instead.
 */
@Deprecated
public class MockGraphics extends Graphics{
    private Application app;

    @Deprecated
    public MockGraphics(Application app){
        this.app = app;
    }

    @Deprecated
    @Override
    public long getFrameId(){
        return app.getFrameId();
    }

    @Deprecated
    @Override
    public float getDeltaTime(){
        return app.getDeltaTime();
    }

    @Deprecated
    @Override
    public int getFramesPerSecond(){
        return app.getFramesPerSecond();
    }
}
