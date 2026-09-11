package com.daniel.cupertinoStatus

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("paw_status_prefs", MODE_PRIVATE) }
    private lateinit var permissionText: TextView
    private lateinit var screenText: TextView
    private lateinit var batteryModeText: TextView
    private lateinit var topValue: TextView
    private lateinit var rightValue: TextView
    private lateinit var sizeValue: TextView

    private fun profilePrefix(): String = if (resources.configuration.screenWidthDp >= 500) "inner_" else "outer_"
    private fun profileName(): String = if (resources.configuration.screenWidthDp >= 500) "Fold 内屏" else "Fold 外屏"
    private fun prefInt(name: String, default: Int): Int = prefs.getInt(profilePrefix() + name, default)
    private fun putPrefInt(name: String, value: Int) = prefs.edit().putInt(profilePrefix() + name, value).apply()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(11, 11, 13)
        window.navigationBarColor = Color.rgb(11, 11, 13)

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 200)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(28))
            setBackgroundColor(Color.rgb(11, 11, 13))
        }

        root.addView(title("Paw Status · Family Edition v1.2"))
        root.addView(body("四只家人重新排成一体式头像。圆环显示电量，狗爪显示手机信号；中间四只头像会按 Wi‑Fi 强度 1–4 档依次亮起，没有连接 Wi‑Fi 时全部保持暗色。"))

        screenText = body("")
        root.addView(screenText)
        permissionText = body("")
        root.addView(permissionText)
        batteryModeText = body("")
        root.addView(batteryModeText)

        root.addView(button("1 · 授权显示在其他 App 上层") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })

        root.addView(button("2 · 开启家庭状态图标") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                startForegroundService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_START))
            }
        })

        root.addView(button("允许后台持续运行（推荐）") {
            requestBatteryExemption()
        })

        root.addView(button("停止家庭状态图标") {
            startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
        })

        root.addView(section("当前位置 / 大小"))
        topValue = body("")
        root.addView(topValue)
        root.addView(SeekBar(this).apply {
            max = 28
            progress = prefInt("top_offset", 0)
            setOnSeekBarChangeListener(progressListener { putPrefInt("top_offset", it); updateLabels() })
        })

        rightValue = body("")
        root.addView(rightValue)
        root.addView(SeekBar(this).apply {
            max = 40
            progress = prefInt("right_offset", 3)
            setOnSeekBarChangeListener(progressListener { putPrefInt("right_offset", it); updateLabels() })
        })

        sizeValue = body("")
        root.addView(sizeValue)
        root.addView(SeekBar(this).apply {
            max = 44
            progress = prefInt("widget_size", 56) - 36
            setOnSeekBarChangeListener(progressListener { putPrefInt("widget_size", it + 36); updateLabels() })
        })

        root.addView(button("应用当前位置与大小") { applyRefreshIfPossible() })

        root.addView(section("v1.2 图标逻辑"))
        root.addView(body("电量：暖橙圆环；≤20% 变红；充电变绿。\n狗爪：手机信号 1–4 格。\n四只头像：Wi‑Fi 1–4 格依次点亮；没有 Wi‑Fi 时不点亮。\n左右两侧狗爪已向内收，和圆环端点留出更自然的间距。"))

        root.addView(section("Samsung 后台稳定性"))
        root.addView(body("v1.2 已减少重复重绘、移除高耗 CPU 的软件阴影，并使用更规范的常驻前台服务。建议再点上面的“允许后台持续运行”，并在 Samsung 设置 → 应用 → Paw Status → 电池中选择“不受限制/Unrestricted”。"))

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(11, 11, 13))
            addView(root)
        }
        setContentView(scroll)
        updateLabels()
    }

    override fun onResume() {
        super.onResume()
        updateLabels()
    }

    private fun requestBatteryExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            updateLabels()
            return
        }
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun applyRefreshIfPossible() {
        if (Settings.canDrawOverlays(this)) {
            startForegroundService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_REFRESH))
        }
    }

    private fun updateLabels() {
        screenText.text = "当前配置：${profileName()}"
        permissionText.text = if (Settings.canDrawOverlays(this)) "顶部显示权限：已授权 ✓" else "顶部显示权限：未授权"
        val pm = getSystemService(PowerManager::class.java)
        batteryModeText.text = if (pm.isIgnoringBatteryOptimizations(packageName)) {
            "后台电池优化：已允许持续运行 ✓"
        } else {
            "后台电池优化：系统仍可能休眠此 App"
        }
        topValue.text = "上下位置：${prefInt("top_offset", 0)} dp"
        rightValue.text = "右侧边距：${prefInt("right_offset", 3)} dp"
        sizeValue.text = "组件大小：${prefInt("widget_size", 56)} dp"
    }

    private fun progressListener(changed: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = changed(progress)
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private fun title(text: String) = TextView(this).apply {
        this.text = text
        textSize = 28f
        setTextColor(Color.WHITE)
        setPadding(0, 0, 0, dp(12))
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun section(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTextColor(Color.WHITE)
        setPadding(0, dp(22), 0, dp(7))
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun body(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.rgb(190, 190, 198))
        setLineSpacing(0f, 1.18f)
        setPadding(0, dp(3), 0, dp(10))
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 15f
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(7) }
        gravity = Gravity.CENTER
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
