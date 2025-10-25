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
}
