package com.daniel.cupertinoStatus

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
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.WindowManager

/** Stable service path + live battery, Wi-Fi and SIM signal data. */
class OverlayService : Service() {

    companion object {
        const val ACTION_START = "cupertino.status.START"
        const val ACTION_STOP = "cupertino.status.STOP"
        const val ACTION_REFRESH = "cupertino.status.REFRESH"
        private const val CHANNEL_ID = "paw_status_overlay_v13"
        private const val NOTIFICATION_ID = 42
        private const val WIFI_REFRESH_MS = 1200L
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: StatusOverlayView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var batteryReceiverRegistered = false
    private var wifiCallbackRegistered = false
    private var telephonyCallbackRegistered = false

    private val prefs by lazy { getSharedPreferences("paw_status_prefs", MODE_PRIVATE) }
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private val telephony by lazy { getSystemService(TelephonyManager::class.java) }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private var wifiRefreshRunning = false
    private val wifiRefreshRunnable = object : Runnable {
        override fun run() {
            if (!wifiRefreshRunning || overlayView == null) return
            refreshWifiStateNow()
            mainHandler.postDelayed(this, WIFI_REFRESH_MS)
        }
    }

    private fun profilePrefix(): String = if (resources.configuration.screenWidthDp >= 500) "inner_" else "outer_"
    private fun prefInt(name: String, default: Int): Int = prefs.getInt(profilePrefix() + name, default)

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            try {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                val percent = ((level * 100f) / scale).toInt().coerceIn(0, 100)
                val isCharging = plugged != 0 &&
                    (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
                mainHandler.post {
                    overlayView?.batteryPercent = percent
                    overlayView?.charging = isCharging
                    overlayView?.invalidate()
                }
            } catch (_: Throwable) { }
        }
    }

    /**
     * Network callbacks give an immediate refresh, while the lightweight 1.2 s poll below
     * keeps RSSI changes moving even when Samsung does not emit capability updates in background.
     */
    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = scheduleWifiRefresh()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = scheduleWifiRefresh()
        override fun onLost(network: Network) = scheduleWifiRefresh()
        override fun onUnavailable() = scheduleWifiRefresh()
    }

    private val signalCallback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            val level = try { signalStrength.level.coerceIn(0, 4) } catch (_: Throwable) { 0 }
            mainHandler.post {
                overlayView?.signalLevel = level
                overlayView?.invalidate()
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
                try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) { }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> {
                if (overlayView == null) showOverlay() else {
                    refreshLayout()
                    scheduleWifiRefresh()
                    startWifiRefreshLoop()
                }
            }
            else -> showOverlay()
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshLayout()
        scheduleWifiRefresh()
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
            scheduleWifiRefresh()
            startWifiRefreshLoop()
            return
        }

        try {
            val view = StatusOverlayView(this).apply { darkStyle = false }
            val lp = buildLayoutParams()
            windowManager.addView(view, lp)
            overlayView = view
            layoutParams = lp
            registerLiveData()
            scheduleWifiRefresh()
            startWifiRefreshLoop()
        } catch (_: Throwable) {
            overlayView = null
            layoutParams = null
        }
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
        try { windowManager.updateViewLayout(view, lp) } catch (_: Throwable) { }
        view.invalidate()
    }

    private fun registerLiveData() {
        if (!batteryReceiverRegistered) {
            try {
                val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                if (Build.VERSION.SDK_INT >= 33) {
                    registerReceiver(batteryReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    @Suppress("DEPRECATION")
                    registerReceiver(batteryReceiver, filter)
                }
                batteryReceiverRegistered = true
            } catch (_: Throwable) { }
        }

        if (!wifiCallbackRegistered) {
            try {
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build()
                connectivity.registerNetworkCallback(request, wifiCallback)
                wifiCallbackRegistered = true
            } catch (_: Throwable) {
                applyWifi(false, 0)
            }
        }

        if (!telephonyCallbackRegistered) {
            try {
                telephony.registerTelephonyCallback(mainExecutor, signalCallback)
                telephonyCallbackRegistered = true
            } catch (_: Throwable) {
                mainHandler.post {
                    overlayView?.signalLevel = 0
                    overlayView?.invalidate()
                }
            }
        }
    }

    private fun scheduleWifiRefresh() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            refreshWifiStateNow()
        } else {
            mainHandler.post { refreshWifiStateNow() }
        }
    }

    private fun startWifiRefreshLoop() {
        if (wifiRefreshRunning) return
        wifiRefreshRunning = true
        mainHandler.removeCallbacks(wifiRefreshRunnable)
        mainHandler.post(wifiRefreshRunnable)
    }

    private fun stopWifiRefreshLoop() {
        wifiRefreshRunning = false
        mainHandler.removeCallbacks(wifiRefreshRunnable)
    }

    private fun refreshWifiStateNow() {
        if (overlayView == null) return
        try {
            var bestCaps: NetworkCapabilities? = null
            var bestRssi = Int.MIN_VALUE

            for (network in connectivity.allNetworks) {
                val caps = connectivity.getNetworkCapabilities(network) ?: continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
                val rssi = wifiRssi(caps)
                if (bestCaps == null || rssi > bestRssi) {
                    bestCaps = caps
                    bestRssi = rssi
                }
            }

            if (bestCaps == null) {
                applyWifi(false, 0)
                return
            }

            val level = when {
                bestRssi >= -55 -> 4
                bestRssi >= -67 -> 3
                bestRssi >= -75 -> 2
                else -> 1
            }
            applyWifi(true, level)
        } catch (_: Throwable) {
            applyWifi(false, 0)
        }
    }

    private fun wifiRssi(caps: NetworkCapabilities): Int {
        return try {
            val info = caps.transportInfo as? WifiInfo
            val fromInfo = info?.rssi ?: Int.MIN_VALUE
            if (fromInfo in -126..0) {
                fromInfo
            } else {
                val fromCaps = caps.signalStrength
                if (fromCaps == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) -80 else fromCaps
            }
        } catch (_: Throwable) {
            val fromCaps = caps.signalStrength
            if (fromCaps == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) -80 else fromCaps
        }
    }

    private fun applyWifi(connected: Boolean, level: Int) {
        val safeLevel = if (connected) level.coerceIn(1, 4) else 0
        val view = overlayView ?: return
        if (view.wifiConnected == connected && view.wifiLevel == safeLevel) return
        view.wifiConnected = connected
        view.wifiLevel = safeLevel
        view.invalidate()
    }

    private fun removeOverlay() {
        stopWifiRefreshLoop()
        overlayView?.let { try { windowManager.removeView(it) } catch (_: Throwable) { } }
        overlayView = null
        layoutParams = null
        if (batteryReceiverRegistered) {
            try { unregisterReceiver(batteryReceiver) } catch (_: Throwable) { }
            batteryReceiverRegistered = false
        }
        if (wifiCallbackRegistered) {
            try { connectivity.unregisterNetworkCallback(wifiCallback) } catch (_: Throwable) { }
            wifiCallbackRegistered = false
        }
        if (telephonyCallbackRegistered) {
            try { telephony.unregisterTelephonyCallback(signalCallback) } catch (_: Throwable) { }
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
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = android.app.Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle("Paw Status 已开启")
            .setContentText("电量圆环 · Wi-Fi 宠物头像 · SIM 狗爪信号")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
