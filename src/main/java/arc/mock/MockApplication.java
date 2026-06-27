package arc.mock;

import arc.*;
import arc.struct.*;

public class MockApplication implements Application{

    @Override
    public Seq<ApplicationListener> getListeners(){
        return new Seq<>(0);
    }

    @Override
    public ApplicationType getType(){
        return ApplicationType.headless;
    }

    @Override
    public void post(Runnable runnable){
        runnable.run();
    }

    @Override
    public void exit(){

    }
}
