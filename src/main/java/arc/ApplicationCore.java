package arc;

import arc.files.Fi;
import arc.util.Structs;

public abstract class ApplicationCore implements ApplicationListener{
    protected ApplicationListener[] modules = {};

    public void add(ApplicationListener module){
        //use an array instead of a seq/list, for faster iteration;
        //modules do not get added often, so a resize each time is acceptable
        modules = Structs.add(modules, module);
    }

    public abstract void setup();

    @Override
    public void init(){
        setup();
        Structs.each(ApplicationListener::init, modules);
    }

    @Override
    public void resize(int width, int height){
        for(ApplicationListener listener : modules){
            listener.resize(width, height);
        }
    }

    @Override
    public void update(){
        Structs.each(ApplicationListener::update, modules);
    }

    @Override
    public void pause(){
        Structs.each(ApplicationListener::pause, modules);
    }

    @Override
    public void resume(){
        Structs.each(ApplicationListener::resume, modules);
    }

    @Override
    public void dispose(){
        Structs.each(ApplicationListener::dispose, modules);
    }

    @Override
    public void fileDropped(Fi file){
        for(ApplicationListener listener : modules){
            listener.fileDropped(file);
        }
    }
}
