package arc;

import arc.files.*;
import arc.util.*;

public class Settings extends JsonSettings{
    protected Fi dataDirectory;
    public String appName = "app";
    
    public Settings() {
        // No auto save, the app must do it manually
        super(Core.files.external(".stub"), false);
    }
    
    public String getAppName(){
        return appName;
    }

    public void setAppName(String name){
        appName = name;
    }
    
    /** 
     * Set whether the data should autosave immediately upon changing a value.
     * Default value: true. 
     * @deprecated Use {@link #setAutosaved(boolean)} instead.
     */
    @Deprecated
    public void setAutosave(boolean autosave){
        setAutosaved(autosave);
    }

    @Override
    public void setAutosaved(boolean autosave){
        autosaved = autosave;
    }

    /**
     * Manually save, if the settings have been loaded at some point. 
     * 
     * @deprecated Use {@link #save()} instead.
     */
    @Deprecated
    public void manualSave(){
        save();
    }

    /** 
     * Saves if any modifications were done. 
     * 
     * @deprecated This is useless.
     */
    @Deprecated
    public void autosave(){
        if(autosaved) save();
    }

    /** 
     * Loads a settings file into {@link #values} using the specified appName.
     * 
     * @deprecated Just for compatibility.
     */
    @Deprecated
    public void loadValues(){
        loadValues(file());
    }

    /** 
     * @deprecated Just for compatibility. 
     * 
     * Saves all entries from {@link #values} into the correct location.
     */
    @Deprecated
    public void saveValues(){
        saveValues(file());
    }

    /** 
     * @deprecated Just for compatibility. Use {@link #file()} instead. 
     * 
     * Returns the file used for writing settings to. Not available on all platforms!
     */
    @Deprecated
    public Fi getSettingsFile(){
        return file();
    }

    /** @deprecated Not used. */
    @Deprecated
    public Fi getBackupFolder(){
        return getDataDirectory().child("settings_backups");
    }

    /** @deprecated Just for compatibility. Use {@link #backupFile()} instead. */
    @Deprecated
    public Fi getBackupSettingsFile(){
        return backupFile();
    }

    public Fi file(){ 
        return getDataDirectory().child("settings." + (plainJson ? "json" : "bin")); 
    }

    public Fi backupFile(){ 
        return getDataDirectory().child("settings_backup." + (plainJson ? "json" : "bin")); 
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

    /** @deprecated Just for compatibility. Use {@link #size()} instead. */
    @Deprecated
    public int keySize(){
        return size(); 
    }

    //TODO: put/get bytes/json
}
