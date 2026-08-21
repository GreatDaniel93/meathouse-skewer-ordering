package au.com.meathouseskewer.bridge;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

public class WatchdogReceiver extends BroadcastReceiver {
    public static final String ACTION_WATCHDOG = "au.com.meathouseskewer.bridge.WATCHDOG";
    private static final long WATCHDOG_MS = 5 * 60 * 1000L;

    @Override public void onReceive(Context context, Intent intent) {
        boolean enabled = context.getSharedPreferences(BridgeConfig.PREFS, Context.MODE_PRIVATE)
                .getBoolean("enabled", false);
        if (enabled) {
            try {
                Intent service = new Intent(context, BridgeService.class);
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
                else context.startService(service);
            } catch (Throwable ignored) {}
            schedule(context, WATCHDOG_MS);
        }
    }

    public static void schedule(Context context, long delayMs) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(context, WatchdogReceiver.class).setAction(ACTION_WATCHDOG);
            PendingIntent pi = PendingIntent.getBroadcast(
                    context, 2002, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long when = SystemClock.elapsedRealtime() + Math.max(5000L, delayMs);
            if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, pi);
            } else {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, pi);
            }
        } catch (Throwable ignored) {}
    }

    public static void cancel(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(context, WatchdogReceiver.class).setAction(ACTION_WATCHDOG);
            PendingIntent pi = PendingIntent.getBroadcast(
                    context, 2002, i,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null) am.cancel(pi);
        } catch (Throwable ignored) {}
    }
}
