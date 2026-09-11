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
import android.widget.Toast

class MainActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("paw_status_prefs", MODE_PRIVATE) }
    private lateinit var permissionText: TextView
    private lateinit var screenText: TextView
    private lateinit var batteryModeText: TextView
    private lateinit var errorText: TextView
    private lateinit var topValue: TextView
    private lateinit var rightValue: TextView
    private lateinit var sizeValue: TextView

    private fun profilePrefix(): String = if (resources.configuration.screenWidthDp >= 500) "inner_" else "outer_"
    private fun profileName(): String = if (resources.configuration.screenWidthDp >= 500) "Fold 内屏" else "Fold 外屏"
    private fun prefInt(name: String, default: Int): Int = prefs.getInt(profilePrefix() + name, default)
    private fun putPrefInt(name: String, value: Int) = prefs.edit().putInt(profilePrefix() + name, value).apply()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
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

            root.addView(title("Paw Status · Family Edition v1.2.1"))
            root.addView(body("这是稳定性修正版：先保证 App 能正常打开、服务不会反复崩溃。宠物头像 Wi‑Fi 分档、电量圆环和狗爪信号逻辑都保留。"))

            screenText = body("")
            root.addView(screenText)
            permissionText = body("")
            root.addView(permissionText)
            batteryModeText = body("")
            root.addView(batteryModeText)
            errorText = body("")
            root.addView(errorText)

            root.addView(button("1 · 授权显示在其他 App 上层") {
                safeRun("打开悬浮窗权限") {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            })

            root.addView(button("2 · 开启家庭状态图标") {
                if (!Settings.canDrawOverlays(this)) {
                    safeRun("打开悬浮窗权限") {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    }
                } else {
                    safeRun("启动状态图标") {
                        startForegroundService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_START))
                        Toast.makeText(this, "正在启动 Paw Status", Toast.LENGTH_SHORT).show()
                    }
                }
            })

            root.addView(button("允许后台持续运行（推荐）") { requestBatteryExemption() })

            root.addView(button("停止 / 重置状态服务") {
                safeRun("停止状态服务") {
                    stopService(Intent(this, OverlayService::class.java))
                    prefs.edit().remove("last_error").apply()
                    updateLabels()
                    Toast.makeText(this, "状态服务已停止，可重新开启", Toast.LENGTH_SHORT).show()
                }
            })

            root.addView(section("当前位置 / 大小"))
            topValue = body("")
            root.addView(topValue)
            root.addView(SeekBar(this).apply {
                max = 28
                progress = prefInt("top_offset", 0).coerceIn(0, 28)
                setOnSeekBarChangeListener(progressListener { putPrefInt("top_offset", it); updateLabels() })
            })

            rightValue = body("")
            root.addView(rightValue)
            root.addView(SeekBar(this).apply {
                max = 40
                progress = prefInt("right_offset", 3).coerceIn(0, 40)
                setOnSeekBarChangeListener(progressListener { putPrefInt("right_offset", it); updateLabels() })
            })

            sizeValue = body("")
            root.addView(sizeValue)
            root.addView(SeekBar(this).apply {
                max = 44
                progress = (prefInt("widget_size", 56) - 36).coerceIn(0, 44)
                setOnSeekBarChangeListener(progressListener { putPrefInt("widget_size", it + 36); updateLabels() })
            })

            root.addView(button("应用当前位置与大小") { applyRefreshIfPossible() })

            root.addView(section("状态逻辑"))
            root.addView(body("电量：暖橙圆环；≤20% 变红；充电变绿。\n狗爪：手机信号 1–4 格。\n四只头像：Wi‑Fi 1–4 格依次点亮；没有 Wi‑Fi 时全部保持暗色。"))

            val scroll = ScrollView(this).apply {
                setBackgroundColor(Color.rgb(11, 11, 13))
                addView(root)
            }
            setContentView(scroll)
            updateLabels()
        } catch (t: Throwable) {
            prefs.edit().putString("last_error", "activity: ${t.javaClass.simpleName}: ${t.message ?: "unknown"}").apply()
            val tv = TextView(this).apply {
                text = "Paw Status 启动界面发生异常\n\n${t.javaClass.simpleName}: ${t.message ?: "unknown"}\n\n请把这一页截图给我。"
                textSize = 17f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(11, 11, 13))
                setPadding(dp(24), dp(40), dp(24), dp(24))
            }
            setContentView(tv)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::screenText.isInitialized) updateLabels()
    }

    private fun requestBatteryExemption() {
        safeRun("后台电池权限") {
            val pm = getSystemService(PowerManager::class.java)
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                updateLabels()
                return@safeRun
            }
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } catch (_: Throwable) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun applyRefreshIfPossible() {
        if (Settings.canDrawOverlays(this)) {
            safeRun("刷新位置") {
                startForegroundService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_REFRESH))
            }
        }
    }

    private fun safeRun(label: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            prefs.edit().putString("last_error", "$label: ${t.javaClass.simpleName}: ${t.message ?: "unknown"}").apply()
            Toast.makeText(this, "$label 失败：${t.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            updateLabels()
        }
    }

    private fun updateLabels() {
        if (!::screenText.isInitialized) return
        screenText.text = "当前配置：${profileName()}"
        permissionText.text = if (Settings.canDrawOverlays(this)) "顶部显示权限：已授权 ✓" else "顶部显示权限：未授权"
        val pm = getSystemService(PowerManager::class.java)
        batteryModeText.text = if (pm.isIgnoringBatteryOptimizations(packageName)) {
            "后台电池优化：已允许持续运行 ✓"
        } else {
            "后台电池优化：系统仍可能休眠此 App"
        }
        val err = prefs.getString("last_error", null)
        errorText.text = if (err.isNullOrBlank()) "最近运行状态：未记录到错误 ✓" else "最近错误：$err"
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
