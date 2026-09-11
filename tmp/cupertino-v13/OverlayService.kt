package com.daniel.cupertinoStatus

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
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
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.PathInterpolator

class OverlayService : Service() {
    companion object {
        const val ACTION_START = "cupertino.status.START"
        const val ACTION_STOP = "cupertino.status.STOP"
        const val ACTION_REFRESH = "cupertino.status.REFRESH"
        private const val CHANNEL_ID = "cupertino_status_overlay_v3"
        private const val NOTIFICATION_ID = 42
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: StatusOverlayView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var pageView: PageTurnOverlayView? = null
    private var pageAnimator: ValueAnimator? = null
    private var batteryReceiverRegistered = false
    private var systemReceiverRegistered = false
    private var networkCallbackRegistered = false
    private var telephonyCallbackRegistered = false
    private var lastWidthDp = 0
    private var lastPageAt = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    private val prefs by lazy { getSharedPreferences("status_prefs", MODE_PRIVATE) }
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
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            overlayView?.batteryPercent = ((level * 100f) / scale).toInt().coerceIn(0, 100)
            overlayView?.charging = charging
            overlayView?.invalidate()
        }
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) playPageTurn()
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            overlayView?.wifiConnected = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            overlayView?.invalidate()
        }
        override fun onLost(network: Network) = updateCurrentNetwork()
    }

    private val signalCallback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            overlayView?.signalLevel = signalStrength.level.coerceIn(0, 4)
            overlayView?.invalidate()
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        lastWidthDp = resources.configuration.screenWidthDp
        createChannel(); startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> { removeOverlay(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
            ACTION_REFRESH -> if (overlayView == null) showOverlay() else refreshLayout()
            else -> showOverlay()
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val oldInner = lastWidthDp >= 500
        val newInner = newConfig.screenWidthDp >= 500
        lastWidthDp = newConfig.screenWidthDp
        refreshLayout()
        if (!oldInner && newInner) mainHandler.postDelayed({ playPageTurn() }, 90L)
    }

    override fun onDestroy() { removeOverlay(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        if (overlayView != null) { refreshLayout(); return }
        overlayView = StatusOverlayView(this).apply { darkStyle = prefs.getBoolean("dark_style", false) }
        layoutParams = buildLayoutParams()
        windowManager.addView(overlayView, layoutParams)
        registerLiveData(); registerSystemReceiver(); updateCurrentNetwork()
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val size = dp(prefInt("widget_size", 46))
        return WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(prefInt("right_offset", 3)); y = dp(prefInt("top_offset", 0))
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    private fun refreshLayout() {
        val view = overlayView ?: return; val lp = layoutParams ?: return
        val size = dp(prefInt("widget_size", 46)); lp.width = size; lp.height = size
        lp.x = dp(prefInt("right_offset", 3)); lp.y = dp(prefInt("top_offset", 0))
        view.darkStyle = prefs.getBoolean("dark_style", false)
        try { windowManager.updateViewLayout(view, lp) } catch (_: Exception) {}
        view.invalidate()
    }

    private fun playPageTurn() {
        if (!Settings.canDrawOverlays(this)) return
        val now = SystemClock.elapsedRealtime(); if (now - lastPageAt < 700L) return; lastPageAt = now
        pageAnimator?.cancel()
        pageView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        val view = PageTurnOverlayView(this).apply { rightToLeft = true; progress = 0f }
        pageView = view
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        try { windowManager.addView(view, lp) } catch (_: Exception) { return }
        pageAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
            interpolator = PathInterpolator(.18f,.78f,.22f,1f)
            addUpdateListener { view.progress = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = clearPage(view)
                override fun onAnimationCancel(animation: Animator) = clearPage(view)
            }); start()
        }
    }

    private fun clearPage(view: PageTurnOverlayView) {
        if (pageView !== view) return
        try { windowManager.removeView(view) } catch (_: Exception) {}
        pageView = null; pageAnimator = null
    }

    private fun registerSystemReceiver() {
        if (systemReceiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(systemReceiver, filter, Context.RECEIVER_EXPORTED)
        else { @Suppress("DEPRECATION") registerReceiver(systemReceiver, filter) }
        systemReceiverRegistered = true
    }

    private fun registerLiveData() {
        if (!batteryReceiverRegistered) {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(batteryReceiver, filter, Context.RECEIVER_EXPORTED)
            else { @Suppress("DEPRECATION") registerReceiver(batteryReceiver, filter) }
            batteryReceiverRegistered = true
        }
        if (!networkCallbackRegistered) try { connectivity.registerDefaultNetworkCallback(networkCallback); networkCallbackRegistered = true } catch (_: Exception) {}
        if (!telephonyCallbackRegistered) { try { telephony.registerTelephonyCallback(mainExecutor, signalCallback) } catch (_: Exception) { overlayView?.signalLevel = 4 }; telephonyCallbackRegistered = true }
    }

    private fun updateCurrentNetwork() {
        try {
            val network = connectivity.activeNetwork
            val caps = if (network != null) connectivity.getNetworkCapabilities(network) else null
            overlayView?.wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            overlayView?.invalidate()
        } catch (_: Exception) {}
    }

    private fun removeOverlay() {
        pageAnimator?.cancel(); pageView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }; pageView = null; pageAnimator = null
        overlayView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }; overlayView = null; layoutParams = null
        if (batteryReceiverRegistered) { try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}; batteryReceiverRegistered = false }
        if (systemReceiverRegistered) { try { unregisterReceiver(systemReceiver) } catch (_: Exception) {}; systemReceiverRegistered = false }
        if (networkCallbackRegistered) { try { connectivity.unregisterNetworkCallback(networkCallback) } catch (_: Exception) {}; networkCallbackRegistered = false }
        if (telephonyCallbackRegistered) { try { telephony.unregisterTelephonyCallback(signalCallback) } catch (_: Exception) {}; telephonyCallbackRegistered = false }
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "Cupertino Status Duo", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
    }

    private fun startAsForeground() {
        val openApp = PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = android.app.Notification.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_status).setContentTitle("Cupertino Status Duo 已开启").setContentText("状态栏 + Duo 翻页动画").setContentIntent(openApp).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(NOTIFICATION_ID,n)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
