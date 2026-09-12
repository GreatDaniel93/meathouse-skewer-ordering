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
import android.os.IBinder
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

    private val wifiNetworks = linkedSetOf<Network>()

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
                overlayView?.batteryPercent = ((level * 100f) / scale).toInt().coerceIn(0, 100)
                overlayView?.charging = plugged != 0 &&
                    (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
                overlayView?.invalidate()
            } catch (_: Throwable) { }
        }
    }

    /**
     * v1.5.4: track Wi-Fi itself instead of the phone's default network.
     * On Samsung the default-network callback may keep the previous Wi-Fi capabilities briefly
     * while mobile data takes over, which can leave one avatar lit after Wi-Fi is disabled.
     */
    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            synchronized(wifiNetworks) { wifiNetworks.add(network) }
            refreshWifiState()
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                synchronized(wifiNetworks) { wifiNetworks.add(network) }
                applyBestWifiState(network, caps)
            } else {
                synchronized(wifiNetworks) { wifiNetworks.remove(network) }
                refreshWifiState()
            }
        }

        override fun onLost(network: Network) {
            val empty = synchronized(wifiNetworks) {
                wifiNetworks.remove(network)
                wifiNetworks.isEmpty()
            }
            if (empty) applyWifi(false, 0) else refreshWifiState()
        }

        override fun onUnavailable() {
            synchronized(wifiNetworks) { wifiNetworks.clear() }
            applyWifi(false, 0)
        }
    }

    private val signalCallback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            try {
                overlayView?.signalLevel = signalStrength.level.coerceIn(0, 4)
                overlayView?.invalidate()
            } catch (_: Throwable) {
                overlayView?.signalLevel = 0
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

        try {
            val view = StatusOverlayView(this).apply { darkStyle = false }
            val lp = buildLayoutParams()
            windowManager.addView(view, lp)
            overlayView = view
            layoutParams = lp
            registerLiveData()
            refreshWifiState()
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
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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
                overlayView?.signalLevel = 0
                overlayView?.invalidate()
            }
        }
    }

    private fun refreshWifiState() {
        try {
            val candidates = mutableListOf<Pair<Network, NetworkCapabilities>>()
            for (network in connectivity.allNetworks) {
                val caps = connectivity.getNetworkCapabilities(network) ?: continue
                if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
                candidates += network to caps
            }

            synchronized(wifiNetworks) {
                wifiNetworks.clear()
                candidates.forEach { wifiNetworks.add(it.first) }
            }

            if (candidates.isEmpty()) {
                applyWifi(false, 0)
                return
            }

            val best = candidates.maxByOrNull { wifiRssi(it.second) } ?: candidates.first()
            applyBestWifiState(best.first, best.second)
        } catch (_: Throwable) {
            synchronized(wifiNetworks) { wifiNetworks.clear() }
            applyWifi(false, 0)
        }
    }

    private fun applyBestWifiState(network: Network, caps: NetworkCapabilities) {
        try {
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                synchronized(wifiNetworks) { wifiNetworks.remove(network) }
                refreshWifiState()
                return
            }
            val rssi = wifiRssi(caps)
            val level = when {
                rssi >= -55 -> 4
                rssi >= -67 -> 3
                rssi >= -75 -> 2
                else -> 1
            }
            applyWifi(true, level)
        } catch (_: Throwable) {
            refreshWifiState()
        }
    }

    private fun wifiRssi(caps: NetworkCapabilities): Int {
        return try {
            val info = caps.transportInfo as? WifiInfo
            val fromInfo = info?.rssi ?: Int.MIN_VALUE
            if (fromInfo != Int.MIN_VALUE && fromInfo != WifiInfo.INVALID_RSSI) {
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
        overlayView?.wifiConnected = connected
        overlayView?.wifiLevel = if (connected) level.coerceIn(1, 4) else 0
        overlayView?.invalidate()
    }

    private fun removeOverlay() {
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
        synchronized(wifiNetworks) { wifiNetworks.clear() }
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
