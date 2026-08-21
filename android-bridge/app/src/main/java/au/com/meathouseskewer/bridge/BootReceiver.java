package au.com.meathouseskewer.bridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent){
        String action = intent == null ? null : intent.getAction();
        if(Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)){
            boolean enabled=context.getSharedPreferences(BridgeConfig.PREFS,Context.MODE_PRIVATE).getBoolean("enabled",false);
            if(enabled){
                try{
                    Intent service=new Intent(context,BridgeService.class);
                    if(Build.VERSION.SDK_INT>=26)context.startForegroundService(service);else context.startService(service);
                }catch(Throwable ignored){}
                WatchdogReceiver.schedule(context, 30_000L);
            }
        }
    }
}
