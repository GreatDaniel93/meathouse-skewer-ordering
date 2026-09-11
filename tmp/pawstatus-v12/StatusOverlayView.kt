package com.daniel.cupertinoStatus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Paw Status · Family Edition v1.2
 *
 * Refinements:
 * - side paws have more breathing room from the battery-ring endpoints
 * - the four pets are composed as one family cluster instead of four badge-like circles
 * - each pet lights progressively with Wi-Fi strength (0..4); no Wi-Fi = no lit pet
 * - rendering avoids software shadow layers to reduce CPU/battery pressure
 */
class StatusOverlayView(context: Context) : View(context) {

    var batteryPercent: Int = 100
        set(value) { field = value.coerceIn(0, 100) }
    var charging: Boolean = false
    var signalLevel: Int = 4
        set(value) { field = value.coerceIn(0, 4) }
    var wifiConnected: Boolean = false
    var wifiLevel: Int = 0
        set(value) { field = value.coerceIn(0, 4) }
    var darkStyle: Boolean = false

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val familyCluster: Bitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.family_cluster)
    }
    private val src: Rect by lazy { Rect(0, 0, familyCluster.width, familyCluster.height) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val side = min(width, height).toFloat()
        val left = (width - side) / 2f
        val top = (height - side) / 2f
        val cx = left + side * 0.50f
        val cy = top + side * 0.44f
        val ringRadius = side * 0.355f
        val ringStroke = max(dp(2.2f), side * 0.053f)

        val inactive = Color.argb(82, 205, 205, 212)
        val active = when {
            charging -> Color.rgb(142, 255, 161)
            batteryPercent <= 20 -> Color.rgb(255, 101, 78)
            else -> Color.rgb(255, 193, 112)
        }

        drawFamily(canvas, left, top, side)

        val ringBox = RectF(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)
        val startAngle = 145f
        val totalSweep = 250f

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND

        paint.strokeWidth = ringStroke
        paint.color = inactive
        paint.alpha = 255
        canvas.drawArc(ringBox, startAngle, totalSweep, false, paint)

        val progress = batteryPercent / 100f
        val activeSweep = totalSweep * progress
        paint.strokeWidth = ringStroke * 1.48f
        paint.color = withAlpha(active, 46)
        canvas.drawArc(ringBox, startAngle, activeSweep, false, paint)
        paint.strokeWidth = ringStroke
        paint.color = active
        canvas.drawArc(ringBox, startAngle, activeSweep, false, paint)

        val pawAngles = floatArrayOf(58f, 79.33f, 100.67f, 122f)
        val pawOrbit = ringRadius * 1.015f
        val pawSize = side * 0.076f
        for (i in 0..3) {
            val angle = Math.toRadians(pawAngles[i].toDouble())
            val px = cx + cos(angle).toFloat() * pawOrbit
            val py = cy + sin(angle).toFloat() * pawOrbit
            val enabled = i < signalLevel
            val color = if (enabled) active else inactive
            drawPaw(canvas, px, py, pawSize, color, enabled)
        }

        if (charging) {
            drawBolt(canvas, cx + ringRadius * 0.58f, cy - ringRadius * 0.90f, side, active)
        }
    }

    private fun drawFamily(canvas: Canvas, left: Float, top: Float, side: Float) {
        val dst = RectF(
            left + side * 0.180f,
            top + side * 0.105f,
            left + side * 0.820f,
            top + side * 0.745f
        )

        paint.style = Paint.Style.FILL
        paint.alpha = if (wifiConnected) 70 else 42
        canvas.drawBitmap(familyCluster, src, dst, paint)

        val lit = if (wifiConnected) wifiLevel else 0
        if (lit <= 0) {
            paint.alpha = 255
            return
        }

        val zones = arrayOf(
            normalizedOval(dst, 0.34f, 0.31f, 0.34f, 0.33f),
            normalizedOval(dst, 0.66f, 0.31f, 0.34f, 0.33f),
            normalizedOval(dst, 0.34f, 0.68f, 0.34f, 0.36f),
            normalizedOval(dst, 0.66f, 0.68f, 0.34f, 0.36f)
        )

        for (i in 0 until lit.coerceAtMost(4)) {
            val save = canvas.save()
            val path = Path().apply { addOval(zones[i], Path.Direction.CW) }
            canvas.clipPath(path)
            paint.alpha = 255
            canvas.drawBitmap(familyCluster, src, dst, paint)
            canvas.restoreToCount(save)
        }
        paint.alpha = 255
    }

    private fun normalizedOval(dst: RectF, cx: Float, cy: Float, rw: Float, rh: Float): RectF {
        val x = dst.left + dst.width() * cx
        val y = dst.top + dst.height() * cy
        val halfW = dst.width() * rw * 0.5f
        val halfH = dst.height() * rh * 0.5f
        return RectF(x - halfW, y - halfH, x + halfW, y + halfH)
    }

    private fun drawPaw(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int, glow: Boolean) {
        if (glow) {
            drawPawShape(canvas, cx, cy, s * 1.18f, withAlpha(color, 42))
        }
        drawPawShape(canvas, cx, cy, s, color)
    }

    private fun drawPawShape(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = 255
        canvas.drawOval(RectF(cx - s * 0.25f, cy + s * 0.01f, cx + s * 0.25f, cy + s * 0.29f), paint)
        drawToe(canvas, cx - s * 0.25f, cy - s * 0.21f, s * 0.10f, s * 0.13f)
        drawToe(canvas, cx - s * 0.08f, cy - s * 0.29f, s * 0.09f, s * 0.13f)
        drawToe(canvas, cx + s * 0.09f, cy - s * 0.29f, s * 0.09f, s * 0.13f)
        drawToe(canvas, cx + s * 0.26f, cy - s * 0.20f, s * 0.10f, s * 0.13f)
    }

    private fun drawToe(canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float) {
        canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), paint)
    }

    private fun drawBolt(canvas: Canvas, cx: Float, cy: Float, side: Float, color: Int) {
        val s = side * 0.085f
        val path = Path().apply {
            moveTo(cx + s * 0.06f, cy - s * 0.54f)
            lineTo(cx - s * 0.28f, cy + s * 0.02f)
            lineTo(cx - s * 0.02f, cy + s * 0.02f)
            lineTo(cx - s * 0.18f, cy + s * 0.56f)
            lineTo(cx + s * 0.34f, cy - s * 0.10f)
            lineTo(cx + s * 0.08f, cy - s * 0.10f)
            close()
        }
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(color, 55)
        canvas.save()
        canvas.scale(1.22f, 1.22f, cx, cy)
        canvas.drawPath(path, paint)
        canvas.restore()
        paint.color = color
        canvas.drawPath(path, paint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha.coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )

    private fun dp(value: Float): Float = value * density
}
