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
    private static final String LOCAL_ACK_PREFIX="printed_";
    private static final long WATCHDOG_MS=5*60*1000L;
    private ScheduledExecutorService executor;
    private volatile boolean running=true;
    private PowerManager.WakeLock wakeLock;

    @Override public void onCreate(){
        super.onCreate();
        createChannel();
        startForeground(1001,notification("Bridge starting..."));
        acquireWakeLock();
        WatchdogReceiver.schedule(this,WATCHDOG_MS);
        setStatus("Bridge running - connecting to server...");
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&ACTION_STOP.equals(intent.getAction())){stopBridge();return START_NOT_STICKY;}
        running=true;
        getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE).edit().putBoolean("enabled",true).apply();
        WatchdogReceiver.schedule(this,WATCHDOG_MS);
        if(executor==null||executor.isShutdown()){
            executor=Executors.newSingleThreadScheduledExecutor();
            executor.scheduleWithFixedDelay(()->{
                try{poll();}
                catch(Throwable t){setStatus("Bridge fatal: "+shortMsg(t));}
            },0,2,TimeUnit.SECONDS);
        }
        return START_STICKY;
    }

    private void poll(){
        if(!running)return;
        SharedPreferences p=getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE);
        String secret=BridgeConfig.BRIDGE_KEY;
        try{
            setStatus("Online - checking for orders...");
            JSONArray jobs=rpcJobs(secret);
            if(jobs.length()==0){setStatus("Online - waiting for orders");return;}
            String ip1=p.getString("ip1","192.168.0.192"),ip2=p.getString("ip2","192.168.0.193");
            int port=p.getInt("port",9100);
            for(int i=0;i<jobs.length();i++){
                JSONObject job=jobs.getJSONObject(i);
                String jid=job.getString("job_id");
                String table=job.optString("table_name","table");
                if(!job.optBoolean("printer1_done")) processPrinter(secret,jid,1,ip1,port,job,table);
                else clearLocalPrinted(jid,1);
                if(!job.optBoolean("printer2_done")) processPrinter(secret,jid,2,ip2,port,job,table);
                else clearLocalPrinted(jid,2);
            }
        }catch(Throwable e){
            setStatus("Server error: "+shortMsg(e));
        }
    }

    private void processPrinter(String secret,String jid,int printer,String ip,int port,JSONObject job,String table){
        try{
            if(isLocalPrinted(jid,printer)){
                setStatus("Confirming "+table+" Printer "+printer+"...");
                ackWithRetry(secret,jid,printer);
                clearLocalPrinted(jid,printer);
                setStatus("Confirmed "+table+" Printer "+printer);
                return;
            }
            setStatus("Printing "+table+" on Printer "+printer+"...");
            PrinterClient.printOrder(ip,port,job);
            markLocalPrinted(jid,printer);
            ackWithRetry(secret,jid,printer);
            clearLocalPrinted(jid,printer);
            setStatus("Printed "+table+" on Printer "+printer);
        }catch(Throwable e){
            setStatus("Printer "+printer+" error: "+shortMsg(e));
        }
    }

    private void ackWithRetry(String secret,String jobId,int printer)throws Exception{
        Exception last=null;
        for(int attempt=1;attempt<=3;attempt++){
            try{ack(secret,jobId,printer);return;}
            catch(Exception e){last=e;try{Thread.sleep(350L*attempt);}catch(InterruptedException ie){Thread.currentThread().interrupt();throw ie;}}
        }
        throw last==null?new IOException("ACK failed"):last;
    }

    private String localPrintedKey(String jobId,int printer){return LOCAL_ACK_PREFIX+jobId+"_p"+printer;}
    private boolean isLocalPrinted(String jobId,int printer){return getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE).getBoolean(localPrintedKey(jobId,printer),false);}
    private void markLocalPrinted(String jobId,int printer){getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE).edit().putBoolean(localPrintedKey(jobId,printer),true).commit();}
    private void clearLocalPrinted(String jobId,int printer){getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE).edit().remove(localPrintedKey(jobId,printer)).apply();}

    private JSONArray rpcJobs(String secret)throws Exception{
        String body=new JSONObject().put("p_secret",secret).toString();
        return new JSONArray(post(BridgeConfig.SUPABASE_URL+"/rest/v1/rpc/bridge_get_print_jobs",body));
    }

    private void ack(String secret,String jobId,int printer)throws Exception{
        JSONObject b=new JSONObject().put("p_secret",secret).put("p_job_id",jobId).put("p_printer",printer);
        post(BridgeConfig.SUPABASE_URL+"/rest/v1/rpc/bridge_ack_print_job",b.toString());
    }

    private String post(String url,String body)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        try{
            c.setConnectTimeout(7000);
            c.setReadTimeout(10000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json");
            c.setRequestProperty("Accept","application/json");
            c.setRequestProperty("apikey",BridgeConfig.SUPABASE_KEY);
            try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));o.flush();}
            int code=c.getResponseCode();
            InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
            String text=read(in);
            if(code<200||code>=300)throw new IOException("HTTP "+code+" "+text);
            return text;
        }finally{
            c.disconnect();
        }
    }

    private static String read(InputStream in)throws Exception{
        if(in==null)return"";
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        byte[] buf=new byte[4096];
        int n;
        while((n=in.read(buf))>0)b.write(buf,0,n);
        return new String(b.toByteArray(),StandardCharsets.UTF_8);
    }

    private void setStatus(String s){
        getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE).edit().putString("status",s).putLong("statusAt",System.currentTimeMillis()).apply();
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(1001,notification(s));
    }

    private Notification notification(String text){
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setContentTitle("Meat House Skewer Bridge").setContentText(text).setSmallIcon(android.R.drawable.stat_notify_sync).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi).build();
    }

    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel(CHANNEL,"Skewer Bridge",NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Keeps kitchen skewer printing online");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    private void acquireWakeLock(){try{PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MeatHouse:SkewerBridge");wakeLock.setReferenceCounted(false);wakeLock.acquire();}catch(Throwable ignored){}}
    private void releaseWakeLock(){try{if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();}catch(Throwable ignored){}}
    private static String shortMsg(Throwable e){String s=e.getMessage();if(s==null||s.trim().isEmpty())s=e.getClass().getSimpleName();return s.length()>160?s.substring(0,160):s;}

    @Override public void onTaskRemoved(Intent rootIntent){
        if(getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE).getBoolean("enabled",false)){
            WatchdogReceiver.schedule(this,5000L);
            setStatus("Bridge protected - restart scheduled");
        }
        super.onTaskRemoved(rootIntent);
    }

    private void stopBridge(){
        running=false;
        getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE).edit().putBoolean("enabled",false).putString("status","Stopped").apply();
        WatchdogReceiver.cancel(this);
        if(executor!=null)executor.shutdownNow();
        releaseWakeLock();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy(){
        running=false;
        if(executor!=null)executor.shutdownNow();
        releaseWakeLock();
        boolean enabled=getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE).getBoolean("enabled",false);
        if(enabled){
            getSharedPreferences(BridgeConfig.PREFS,MODE_PRIVATE).edit().putString("status","Service restarting...").apply();
            WatchdogReceiver.schedule(this,5000L);
        }
        super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent intent){return null;}
}
