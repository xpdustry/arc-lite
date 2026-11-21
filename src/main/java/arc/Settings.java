package arc;

import arc.files.*;
import arc.func.*;
import arc.util.*;
import arc.util.serialization.*;

public class Settings extends JsonSettings{
    protected Fi dataDirectory;
    public String appName = "app";

    public Settings() {
        super(new Fi(".stub"), true);
    }

    public String getAppName(){
        return appName;
    }

    public void setAppName(String name){
        appName = name;
    }

    @Override
    public Fi file(){
        return getSettingsFile();
    }

    @Override
    public Fi backupFile(){
        return getBackupSettingsFile();
    }

    private Fi file(boolean isBackup, boolean withExt){
        String name = "settings";
        if (isBackup) name += "_backup";
        if (withExt) name += "." + (plainJson ? "json" : "bin");
        return getDataDirectory().child(name);
    }

    /** Returns the directory where all settings and data is placed. */
    public Fi getDataDirectory(){
        return dataDirectory != null ? dataDirectory :
               Core.files.absolute(OS.getAppDataDirectoryString(appName));
    }

    /** Sets the settings file where everything is written to. */
    public void setDataDirectory(Fi file){
        this.dataDirectory = file;
    }

    /** @deprecated Use {@link #getJson()} instead. */
    @Deprecated public void setJson(Json json){ this.json = json; }
    /** @deprecated Use {@link #setAutosaved(boolean)} instead. instead. */
    @Deprecated public void setAutosave(boolean autosave){ setAutosaved(autosave); }
    @Override public void setAutosaved(boolean autosave){ autosaved = autosave; }
    /** @deprecated Use {@link #save()} instead. instead. */
    @Deprecated public void manualSave(){ save(); }
    @Deprecated public void autosave(){ if(autosaved) save(); }
    @Deprecated public void loadValues(){ loadValues(file()); }
    @Deprecated public void saveValues(){ saveValues(file()); }
    /** @deprecated Use {@link #file()} instead. instead. */
    @Deprecated public Fi getSettingsFile(){ return file(false, true); }
    @Deprecated public Fi getBackupFolder(){ return file(true, false); }
    /** @deprecated Use {@link #backupFile()} instead. instead. */
    @Deprecated public Fi getBackupSettingsFile(){ return file(true, true); }
    /** @deprecated Use {@link #size()} instead. instead. */
    @Deprecated public int keySize(){ return size(); }
    /** @deprecated Use {@link #modified()} instead. */
    @Deprecated public boolean isModified(){ return modified(); }
    /** @deprecated Use {@link #get(String, Class, Prov)} instead. */
    @Deprecated
    public Object get(String name, Object def){
        return has(name) ? get(name, def == null ? null : def.getClass()) : def;
    }
    /** @deprecated Use {@link #get(String, Class, Prov)} instead. */
    @Deprecated public byte[] getBytes(String name){ return getBytes(name, (byte[])getDefault(name)); }
    /** @deprecated Use {@link #get(String, Class, Prov)} instead. */
    @Deprecated public byte[] getBytes(String name, byte[] def){ return has(name) ? get(name, byte[].class) : def; }
    /** @deprecated Use {@link #put(String, Object)} instead. */
    @Deprecated public void putJson(String name, Object value){ put(name, value); }
    /** @deprecated Use {@link #put(String, Class, Object)} instead. */
    @Deprecated public void putJson(String name, Class<?> elementType, Object value){ put(name, elementType, value); }
    /** @deprecated Use {@link #get(String, Class, Prov)} instead. */
    @Deprecated public <T> T getJson(String name, Class<T> type, Prov<T> def){ return getJson(name, type, null, def); }
    /** @deprecated Use {@link #get(String, Class, Class, Prov)} instead. */
    @Deprecated
    public <T> T getJson(String name, Class<T> type, Class<?> elementType, Prov<T> def){
        try{
            return get(name, type, elementType, def);
        }catch(Throwable e){
            Log.err("Error reading JSON with key '" + name + "'", e);
            return def.get();
        }
    }
}
