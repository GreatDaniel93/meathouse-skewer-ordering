package au.com.meathouseskewer.bridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent){
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())){
            boolean enabled=context.getSharedPreferences(BridgeConfig.PREFS,Context.MODE_PRIVATE).getBoolean("enabled",false);
            if(enabled){Intent service=new Intent(context,BridgeService.class);if(Build.VERSION.SDK_INT>=26)context.startForegroundService(service);else context.startService(service);}
        }
    }
}
