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

/** Paw Status · Family Edition v1.3.1 */
class StatusOverlayView(context: Context) : View(context) {

    var batteryPercent: Int = 100
    var charging: Boolean = false
    var signalLevel: Int = 0
    var wifiConnected: Boolean = false
    var wifiLevel: Int = 0
    var darkStyle: Boolean = false

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val familyCluster: Bitmap? by lazy {
        try { BitmapFactory.decodeResource(resources, R.drawable.family_cluster) } catch (_: Throwable) { null }
    }

    // Unlit Wi-Fi avatars stay clearly visible, but become neutral and dim instead of looking empty.
    private val dimFilter by lazy {
        val matrix = ColorMatrix().apply {
            setSaturation(0.08f)
            postConcat(ColorMatrix(floatArrayOf(
                0.76f,0f,0f,0f,2f,
                0f,0.76f,0f,0f,2f,
                0f,0f,0.76f,0f,2f,
                0f,0f,0f,1f,0f
            )))
        }
        ColorMatrixColorFilter(matrix)
    }

    private val brightFilter by lazy {
        ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            1.12f,0f,0f,0f,5f,
            0f,1.12f,0f,0f,5f,
            0f,0f,1.12f,0f,5f,
            0f,0f,0f,1f,0f
        )))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        try { drawStatus(canvas) } catch (_: Throwable) { drawFallback(canvas) }
    }

    private fun drawStatus(canvas: Canvas) {
        val side = min(width, height).toFloat()
        if (side <= 0f) return
        val left = (width - side) / 2f
        val top = (height - side) / 2f
        val cx = left + side * 0.50f
        val cy = top + side * 0.43f
        val ringRadius = side * 0.355f
        val ringStroke = max(dp(2.0f), side * 0.048f)

        val inactive = Color.argb(92, 194, 199, 210)
        val batteryActive = when {
            charging -> Color.rgb(127, 246, 157)
            batteryPercent <= 20 -> Color.rgb(255, 101, 78)
            else -> Color.rgb(255, 193, 112)
        }
        val signalActive = Color.rgb(118, 248, 157)
        val wifiActive = Color.rgb(118, 248, 157)

        drawFamily(canvas, cx, cy, side, wifiActive)

        val ringBox = RectF(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)
        val startAngle = 145f
        val totalSweep = 250f

        paint.colorFilter = null
        paint.alpha = 255
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = ringStroke
        paint.color = inactive
        canvas.drawArc(ringBox, startAngle, totalSweep, false, paint)

        paint.color = batteryActive
        canvas.drawArc(
            ringBox,
            startAngle,
            totalSweep * (batteryPercent.coerceIn(0, 100) / 100f),
            false,
            paint
        )

        // Real SIM/mobile signal: four paws fill from left to right.
        // Slightly larger than v1.3 while keeping the Apple-style circular gap composition.
        val pawAngles = floatArrayOf(122f, 100.7f, 79.3f, 58f)
        val pawOrbit = ringRadius * 1.065f
        val pawSize = side * 0.086f
        val mobileBars = signalLevel.coerceIn(0, 4)
        for (i in 0..3) {
            val a = Math.toRadians(pawAngles[i].toDouble())
            val px = cx + cos(a).toFloat() * pawOrbit
            val py = cy + sin(a).toFloat() * pawOrbit
            drawPaw(canvas, px, py, pawSize, if (i < mobileBars) signalActive else inactive)
        }

        if (charging) drawBolt(canvas, cx + ringRadius * 0.59f, cy - ringRadius * 0.89f, side, batteryActive)
    }

    private fun drawFamily(canvas: Canvas, cx: Float, cy: Float, side: Float, wifiActive: Int) {
        val bitmap = familyCluster ?: return
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return

        val w = bitmap.width
        val h = bitmap.height
        val src = arrayOf(
            Rect((w * 0.02f).toInt(), 0, (w * 0.52f).toInt(), (h * 0.52f).toInt()),
            Rect((w * 0.48f).toInt(), 0, w, (h * 0.52f).toInt()),
            Rect(0, (h * 0.45f).toInt(), (w * 0.53f).toInt(), h),
            Rect((w * 0.47f).toInt(), (h * 0.45f).toInt(), w, h)
        )

        // Larger, cleaner 2x2 family grid. Avatars no longer overlap each other.
        val avatarRadius = side * 0.134f
        val dx = side * 0.139f
        val dy = side * 0.139f
        val familyCy = cy - side * 0.002f
        val centers = arrayOf(
            floatArrayOf(cx - dx, familyCy - dy),
            floatArrayOf(cx + dx, familyCy - dy),
            floatArrayOf(cx - dx, familyCy + dy),
            floatArrayOf(cx + dx, familyCy + dy)
        )
        val litCount = if (wifiConnected) wifiLevel.coerceIn(0, 4) else 0

        for (i in 0..3) {
            drawAvatar(
                canvas = canvas,
                bitmap = bitmap,
                source = src[i],
                cx = centers[i][0],
                cy = centers[i][1],
                radius = avatarRadius,
                lit = i < litCount,
                activeColor = wifiActive
            )
        }
    }

    private fun drawAvatar(
        canvas: Canvas,
        bitmap: Bitmap,
        source: Rect,
        cx: Float,
        cy: Float,
        radius: Float,
        lit: Boolean,
        activeColor: Int
    ) {
        paint.colorFilter = null
        paint.style = Paint.Style.FILL

        // Compact dark separation between faces without the heavy green circles from v1.3.
        paint.color = Color.argb(88, 0, 0, 0)
        canvas.drawCircle(cx, cy, radius * 1.045f, paint)

        if (lit) {
            paint.color = Color.argb(28, Color.red(activeColor), Color.green(activeColor), Color.blue(activeColor))
            canvas.drawCircle(cx, cy, radius * 1.075f, paint)
        }

        val save = canvas.save()
        try {
            canvas.clipPath(Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) })
            paint.style = Paint.Style.FILL
            paint.alpha = if (lit) 255 else 225
            paint.colorFilter = if (lit) brightFilter else dimFilter
            canvas.drawBitmap(
                bitmap,
                source,
                RectF(cx - radius, cy - radius, cx + radius, cy + radius),
                paint
            )
        } finally {
            canvas.restoreToCount(save)
        }

        paint.colorFilter = null
        paint.alpha = 255
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(dp(0.55f), radius * 0.043f)
        paint.color = if (lit) {
            Color.argb(185, Color.red(activeColor), Color.green(activeColor), Color.blue(activeColor))
        } else {
            Color.argb(95, 184, 190, 201)
        }
        canvas.drawCircle(cx, cy, radius, paint)
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
        val s = side * 0.082f
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
        val r = side * 0.32f
        paint.colorFilter = null
        paint.alpha = 255
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(dp(2f), side * 0.05f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.rgb(255, 193, 112)
        canvas.drawArc(RectF(cx-r, cy-r, cx+r, cy+r), 145f, 250f, false, paint)
    }

    private fun dp(value: Float): Float = value * density
}
