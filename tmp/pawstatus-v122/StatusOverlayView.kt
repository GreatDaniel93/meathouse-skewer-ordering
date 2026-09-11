package com.daniel.cupertinoStatus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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
 * Paw Status · Family Edition v1.2.3
 *
 * Visual fix:
 * - all four pets stay visibly present even when Wi-Fi is disconnected
 * - Wi-Fi 1..4 progressively restores each pet to full colour/brightness
 * - no Wi-Fi = all pets remain dim/desaturated, not invisible
 * - preserves the v1.2.2 service/startup path that works on the user's Fold
 */
class StatusOverlayView(context: Context) : View(context) {

    var batteryPercent: Int = 100
    var charging: Boolean = false
    var signalLevel: Int = 4
    var wifiConnected: Boolean = false
    var wifiLevel: Int = 0
    var darkStyle: Boolean = false

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val familyCluster: Bitmap? by lazy {
        try { BitmapFactory.decodeResource(resources, R.drawable.family_cluster) } catch (_: Throwable) { null }
    }

    private val dimFilter by lazy {
        val matrix = ColorMatrix().apply {
            setSaturation(0.20f)
            postConcat(ColorMatrix(floatArrayOf(
                0.74f,0f,0f,0f,0f,
                0f,0.74f,0f,0f,0f,
                0f,0f,0.74f,0f,0f,
                0f,0f,0f,1f,0f
            )))
        }
        ColorMatrixColorFilter(matrix)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        try {
            drawStatus(canvas)
        } catch (_: Throwable) {
            drawFallback(canvas)
        }
    }

    private fun drawStatus(canvas: Canvas) {
        val side = min(width, height).toFloat()
        if (side <= 0f) return
        val left = (width - side) / 2f
        val top = (height - side) / 2f
        val cx = left + side * 0.50f
        val cy = top + side * 0.44f
        val ringRadius = side * 0.355f
        val ringStroke = max(dp(2.20f), side * 0.053f)

        val inactive = Color.argb(86, 210, 210, 215)
        val active = when {
            charging -> Color.rgb(142, 255, 161)
            batteryPercent <= 20 -> Color.rgb(255, 101, 78)
            else -> Color.rgb(255, 193, 112)
        }

        drawFamily(canvas, left, top, side)

        val ringBox = RectF(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)
        val startAngle = 145f
        val totalSweep = 250f

        paint.colorFilter = null
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = ringStroke
        paint.alpha = 255
        paint.color = inactive
        canvas.drawArc(ringBox, startAngle, totalSweep, false, paint)

        paint.color = active
        canvas.drawArc(ringBox, startAngle, totalSweep * (batteryPercent.coerceIn(0, 100) / 100f), false, paint)

        val pawAngles = floatArrayOf(58f, 79.33f, 100.67f, 122f)
        val pawOrbit = ringRadius * 1.015f
        val pawSize = side * 0.076f
        for (i in 0..3) {
            val a = Math.toRadians(pawAngles[i].toDouble())
            val px = cx + cos(a).toFloat() * pawOrbit
            val py = cy + sin(a).toFloat() * pawOrbit
            val enabled = i < signalLevel.coerceIn(0, 4)
            drawPaw(canvas, px, py, pawSize, if (enabled) active else inactive)
        }

        if (charging) drawBolt(canvas, cx + ringRadius * 0.58f, cy - ringRadius * 0.90f, side, active)
    }

    private fun drawFamily(canvas: Canvas, left: Float, top: Float, side: Float) {
        val bitmap = familyCluster ?: return
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return

        val src = Rect(0, 0, bitmap.width, bitmap.height)
        val dst = RectF(
            left + side * 0.205f,
            top + side * 0.125f,
            left + side * 0.795f,
            top + side * 0.715f
        )

        paint.style = Paint.Style.FILL
        paint.colorFilter = dimFilter
        paint.alpha = if (wifiConnected) 128 else 112
        canvas.drawBitmap(bitmap, src, dst, paint)

        val lit = if (wifiConnected) wifiLevel.coerceIn(0, 4) else 0
        if (lit > 0) {
            val zones = arrayOf(
                normalizedOval(dst, 0.29f, 0.29f, 0.55f, 0.55f),
                normalizedOval(dst, 0.71f, 0.29f, 0.55f, 0.55f),
                normalizedOval(dst, 0.29f, 0.71f, 0.55f, 0.55f),
                normalizedOval(dst, 0.71f, 0.71f, 0.55f, 0.55f)
            )

            paint.colorFilter = null
            for (i in 0 until lit) {
                val save = canvas.save()
                try {
                    val path = Path().apply { addOval(zones[i], Path.Direction.CW) }
                    canvas.clipPath(path)
                    paint.alpha = 255
                    canvas.drawBitmap(bitmap, src, dst, paint)
                } finally {
                    canvas.restoreToCount(save)
                }
            }
        }

        paint.colorFilter = null
        paint.alpha = 255
    }

    private fun normalizedOval(dst: RectF, nx: Float, ny: Float, nw: Float, nh: Float): RectF {
        val x = dst.left + dst.width() * nx
        val y = dst.top + dst.height() * ny
        val hw = dst.width() * nw * 0.5f
        val hh = dst.height() * nh * 0.5f
        return RectF(x - hw, y - hh, x + hw, y + hh)
    }

    private fun drawPaw(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        paint.colorFilter = null
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
        paint.colorFilter = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = 255
        canvas.drawPath(path, paint)
    }

    private fun drawFallback(canvas: Canvas) {
        val side = min(width, height).toFloat()
        if (side <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        paint.colorFilter = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(dp(2f), side * 0.05f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.rgb(255, 193, 112)
        paint.alpha = 255
        val r = side * 0.32f
        canvas.drawArc(RectF(cx-r, cy-r, cx+r, cy+r), 145f, 250f, false, paint)
    }

    private fun dp(value: Float): Float = value * density
}
