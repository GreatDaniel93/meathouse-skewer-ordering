package com.daniel.cupertinoStatus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.WindowManager

class OverlayService : Service() {

    companion object {
        const val ACTION_START = "cupertino.status.START"
        const val ACTION_STOP = "cupertino.status.STOP"
        const val ACTION_REFRESH = "cupertino.status.REFRESH"
        private const val CHANNEL_ID = "paw_status_overlay_v12"
        private const val NOTIFICATION_ID = 42
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: StatusOverlayView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var batteryReceiverRegistered = false
    private var networkCallbackRegistered = false
    private var telephonyCallbackRegistered = false

    private val prefs by lazy { getSharedPreferences("paw_status_prefs", MODE_PRIVATE) }
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private val telephony by lazy { getSystemService(TelephonyManager::class.java) }

    private fun profilePrefix(): String = if (resources.configuration.screenWidthDp >= 500) "inner_" else "outer_"
    private fun prefInt(name: String, default: Int): Int = prefs.getInt(profilePrefix() + name, default)

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val percent = ((level * 100f) / scale).toInt().coerceIn(0, 100)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val view = overlayView ?: return
            if (view.batteryPercent != percent || view.charging != isCharging) {
                view.batteryPercent = percent
                view.charging = isCharging
                view.invalidate()
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = updateCurrentNetwork()

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            applyWifiCapabilities(networkCapabilities)
        }

        override fun onLost(network: Network) = updateCurrentNetwork()
    }

    private val signalCallback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            val next = signalStrength.level.coerceIn(0, 4)
            val view = overlayView ?: return
            if (view.signalLevel != next) {
                view.signalLevel = next
                view.invalidate()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        createChannel()
        startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> {
                removeOverlay()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> if (overlayView == null) showOverlay() else refreshLayout()
            else -> showOverlay()
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshLayout()
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        if (overlayView != null) {
            refreshLayout()
            return
        }

        overlayView = StatusOverlayView(this).apply { darkStyle = false }
        layoutParams = buildLayoutParams()
        try {
            windowManager.addView(overlayView, layoutParams)
        } catch (_: Exception) {
            overlayView = null
            layoutParams = null
            return
        }
        registerLiveData()
        updateCurrentNetwork()
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val size = dp(prefInt("widget_size", 56))
        return WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(prefInt("right_offset", 3))
            y = dp(prefInt("top_offset", 0))
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    private fun refreshLayout() {
        val view = overlayView ?: return
        val lp = layoutParams ?: return
        val size = dp(prefInt("widget_size", 56))
        lp.width = size
        lp.height = size
        lp.x = dp(prefInt("right_offset", 3))
        lp.y = dp(prefInt("top_offset", 0))
        view.darkStyle = false
        try { windowManager.updateViewLayout(view, lp) } catch (_: Exception) { }
    }

    private fun registerLiveData() {
        if (!batteryReceiverRegistered) {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(batteryReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(batteryReceiver, filter)
            }
            batteryReceiverRegistered = true
        }

        if (!networkCallbackRegistered) {
            try {
                connectivity.registerDefaultNetworkCallback(networkCallback)
                networkCallbackRegistered = true
            } catch (_: Exception) { }
        }

        if (!telephonyCallbackRegistered) {
            try {
                telephony.registerTelephonyCallback(mainExecutor, signalCallback)
                telephonyCallbackRegistered = true
            } catch (_: Exception) {
                overlayView?.signalLevel = 0
            }
        }
    }

    private fun updateCurrentNetwork() {
        try {
            val network = connectivity.activeNetwork
            val caps = network?.let { connectivity.getNetworkCapabilities(it) }
            if (caps == null) applyWifiState(false, 0) else applyWifiCapabilities(caps)
        } catch (_: Exception) {
            applyWifiState(false, 0)
        }
    }

    private fun applyWifiCapabilities(caps: NetworkCapabilities) {
        val connected = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        if (!connected) {
            applyWifiState(false, 0)
            return
        }

        val wifiInfo = caps.transportInfo as? WifiInfo
        val level = if (wifiInfo != null) rssiToFourLevels(wifiInfo.rssi) else 1
        applyWifiState(true, level)
    }

    private fun rssiToFourLevels(rssi: Int): Int = when {
        rssi >= -55 -> 4
        rssi >= -67 -> 3
        rssi >= -75 -> 2
        else -> 1
    }

    private fun applyWifiState(connected: Boolean, level: Int) {
        val view = overlayView ?: return
        val nextLevel = if (connected) level.coerceIn(1, 4) else 0
        if (view.wifiConnected != connected || view.wifiLevel != nextLevel) {
            view.wifiConnected = connected
            view.wifiLevel = nextLevel
            view.invalidate()
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        overlayView = null
        layoutParams = null

        if (batteryReceiverRegistered) {
            try { unregisterReceiver(batteryReceiver) } catch (_: Exception) { }
            batteryReceiverRegistered = false
        }
        if (networkCallbackRegistered) {
            try { connectivity.unregisterNetworkCallback(networkCallback) } catch (_: Exception) { }
            networkCallbackRegistered = false
        }
        if (telephonyCallbackRegistered) {
            try { telephony.unregisterTelephonyCallback(signalCallback) } catch (_: Exception) { }
            telephonyCallbackRegistered = false
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Paw Status · Family Edition", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps your custom family status icon running"
                setShowBadge(false)
            }
        )
    }

    private fun startAsForeground() {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle("Paw Status 已开启")
            .setContentText("电量圆环 · Wi-Fi 宠物亮度 · 狗爪手机信号")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .apply {
                if (Build.VERSION.SDK_INT >= 31) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            }
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
