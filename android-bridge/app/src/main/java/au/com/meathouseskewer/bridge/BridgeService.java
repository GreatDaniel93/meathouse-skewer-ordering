package au.com.meathouseskewer.bridge;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class BridgeService extends Service {
    public static final String ACTION_STOP="STOP";
    private static final String CHANNEL="bridge";
    private ScheduledExecutorService executor;
    private volatile boolean running=true;
    private PowerManager.WakeLock wakeLock;

    @Override public void onCreate(){super.onCreate();createChannel();startForeground(1001,notification("Bridge starting..."));acquireWakeLock();setStatus("Bridge running - connecting to server...");}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&ACTION_STOP.equals(intent.getAction())){stopBridge();return START_NOT_STICKY;}
        running=true;
        if(executor==null||executor.isShutdown()){
            getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE).edit().putBoolean("enabled",true).apply();
            executor=Executors.newSingleThreadScheduledExecutor();executor.scheduleWithFixedDelay(this::poll,0,2,TimeUnit.SECONDS);
        }
        return START_STICKY;
    }
    private void poll(){
        if(!running)return;
        SharedPreferences p=getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE);String secret=BridgeConfig.BRIDGE_KEY;
        try{
            JSONArray jobs=rpcJobs(secret);
            if(jobs.length()==0){setStatus("Online - waiting for orders");return;}
            String ip1=p.getString("ip1","192.168.0.192"),ip2=p.getString("ip2","192.168.0.193");int port=p.getInt("port",9100);
            for(int i=0;i<jobs.length();i++){
                JSONObject job=jobs.getJSONObject(i);String jid=job.getString("job_id");
                if(!job.optBoolean("printer1_done"))try{PrinterClient.printOrder(ip1,port,job);ack(secret,jid,1);setStatus("Printed "+job.optString("table_name")+" on Printer 1");}catch(Exception e){setStatus("Printer 1 error: "+shortMsg(e));}
                if(!job.optBoolean("printer2_done"))try{PrinterClient.printOrder(ip2,port,job);ack(secret,jid,2);setStatus("Printed "+job.optString("table_name")+" on Printer 2");}catch(Exception e){setStatus("Printer 2 error: "+shortMsg(e));}
            }
        }catch(Exception e){setStatus("Server error: "+shortMsg(e));}
    }
    private JSONArray rpcJobs(String secret)throws Exception{String body=new JSONObject().put("p_secret",secret).toString();return new JSONArray(post(BridgeConfig.SUPABASE_URL+"/rest/v1/rpc/bridge_get_print_jobs",body));}
    private void ack(String secret,String jobId,int printer)throws Exception{JSONObject b=new JSONObject().put("p_secret",secret).put("p_job_id",jobId).put("p_printer",printer);post(BridgeConfig.SUPABASE_URL+"/rest/v1/rpc/bridge_ack_print_job",b.toString());}
    private String post(String url,String body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(8000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("apikey",BridgeConfig.SUPABASE_KEY);
        try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));}
        int code=c.getResponseCode();InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();String text=read(in);if(code<200||code>=300)throw new IOException("HTTP "+code+" "+text);return text;
    }
    private static String read(InputStream in)throws Exception{if(in==null)return"";ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))>0)b.write(buf,0,n);return b.toString(StandardCharsets.UTF_8);}
    private void setStatus(String s){getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE).edit().putString("status",s).putLong("statusAt",System.currentTimeMillis()).apply();((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(1001,notification(s));}
    private Notification notification(String text){Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);return new Notification.Builder(this,CHANNEL).setContentTitle("Meat House Skewer Bridge").setContentText(text).setSmallIcon(android.R.drawable.stat_notify_sync).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi).build();}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Skewer Bridge",NotificationManager.IMPORTANCE_LOW);c.setDescription("Keeps kitchen skewer printing online");((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}}
    private void acquireWakeLock(){try{PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MeatHouse:SkewerBridge");wakeLock.setReferenceCounted(false);wakeLock.acquire();}catch(Exception ignored){}}
    private void releaseWakeLock(){try{if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();}catch(Exception ignored){}}
    private static String shortMsg(Exception e){String s=e.getMessage();return s==null?e.getClass().getSimpleName():(s.length()>120?s.substring(0,120):s);}
    private void stopBridge(){running=false;getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE).edit().putBoolean("enabled",false).putString("status","Stopped").apply();if(executor!=null)executor.shutdownNow();releaseWakeLock();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public void onDestroy(){running=false;if(executor!=null)executor.shutdownNow();releaseWakeLock();if(getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE).getBoolean("enabled",false))getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE).edit().putString("status","Service restarting...").apply();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent intent){return null;}
}
