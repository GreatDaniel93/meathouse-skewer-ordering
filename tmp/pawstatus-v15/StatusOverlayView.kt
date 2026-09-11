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
import kotlin.math.max
import kotlin.math.min

/**
 * Paw Status · Family Edition v1.5
 *
 * Sole visual reference: the approved Paw Status · Family master artwork.
 * - incomplete battery ring around the upper/side area
 * - four separate pet portraits in a compact 2x2 family cluster
 * - four paw prints below the portraits = real SIM/mobile signal strength
 * - pet portraits light progressively according to Wi‑Fi strength
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

    private val pets: Array<Bitmap?> by lazy {
        arrayOf(
            safeDecode(R.drawable.pet_cat_orange),
            safeDecode(R.drawable.pet_cat_white),
            safeDecode(R.drawable.pet_dog_brown),
            safeDecode(R.drawable.pet_dog_black)
        )
    }

    private val dimFilter by lazy {
        val saturation = ColorMatrix().apply { setSaturation(0.12f) }
        val brightness = ColorMatrix(floatArrayOf(
            0.46f, 0f, 0f, 0f, 0f,
            0f, 0.46f, 0f, 0f, 0f,
            0f, 0f, 0.46f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        saturation.postConcat(brightness)
        ColorMatrixColorFilter(saturation)
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
        val cy = top + side * 0.405f
        val ringRadius = side * 0.355f
        val ringStroke = max(dp(2.0f), side * 0.050f)

        val active = Color.rgb(79, 248, 142)
        val inactive = Color.rgb(91, 102, 126)
        val inactivePaw = Color.rgb(154, 159, 174)

        val ringBox = RectF(
            cx - ringRadius,
            cy - ringRadius,
            cx + ringRadius,
            cy + ringRadius
        )
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

        paint.color = active
        val batteryProgress = batteryPercent.coerceIn(0, 100) / 100f
        canvas.drawArc(ringBox, startAngle, totalSweep * batteryProgress, false, paint)

        val avatarRadius = side * 0.115f
        val centers = arrayOf(
            floatArrayOf(left + side * 0.395f, top + side * 0.335f),
            floatArrayOf(left + side * 0.605f, top + side * 0.335f),
            floatArrayOf(left + side * 0.395f, top + side * 0.535f),
            floatArrayOf(left + side * 0.605f, top + side * 0.535f)
        )
        val litCount = if (wifiConnected) wifiLevel.coerceIn(0, 4) else 0
        val order = intArrayOf(0, 2, 1, 3)
        val lit = BooleanArray(4)
        for (i in 0 until litCount) lit[order[i]] = true

        for (i in 0..3) {
            drawAvatar(
                canvas = canvas,
                bitmap = pets[i],
                cx = centers[i][0],
                cy = centers[i][1],
                radius = avatarRadius,
                isLit = lit[i]
            )
        }

        val pawXs = floatArrayOf(0.315f, 0.435f, 0.565f, 0.685f)
        val pawYs = floatArrayOf(0.742f, 0.776f, 0.776f, 0.742f)
        val pawSize = side * 0.092f
        val bars = signalLevel.coerceIn(0, 4)
        for (i in 0..3) {
            val px = left + side * pawXs[i]
            val py = top + side * pawYs[i]
            drawPaw(canvas, px, py, pawSize, if (i < bars) active else inactivePaw)
        }

        drawBolt(
            canvas,
            cx + ringRadius * 0.72f,
            cy - ringRadius * 0.82f,
            side,
            active
        )
    }

    private fun drawAvatar(
        canvas: Canvas,
        bitmap: Bitmap?,
        cx: Float,
        cy: Float,
        radius: Float,
        isLit: Boolean
    ) {
        if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return

        val save = canvas.save()
        try {
            val clip = Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) }
            canvas.clipPath(clip)

            val srcSize = min(bitmap.width, bitmap.height)
            val srcLeft = (bitmap.width - srcSize) / 2
            val srcTop = (bitmap.height - srcSize) / 2
            val src = Rect(srcLeft, srcTop, srcLeft + srcSize, srcTop + srcSize)
            val dst = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

            paint.style = Paint.Style.FILL
            paint.alpha = 255
            paint.colorFilter = if (isLit) null else dimFilter
            canvas.drawBitmap(bitmap, src, dst, paint)
        } finally {
            canvas.restoreToCount(save)
        }

        paint.colorFilter = null
        paint.alpha = if (isLit) 72 else 40
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(dp(0.55f), radius * 0.035f)
        paint.color = if (isLit) Color.WHITE else Color.rgb(160, 166, 178)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.alpha = 255
    }

    private fun drawPaw(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        paint.colorFilter = null
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        paint.color = color

        canvas.drawOval(
            RectF(cx - s * 0.24f, cy + s * 0.02f, cx + s * 0.24f, cy + s * 0.31f),
            paint
        )
        drawToe(canvas, cx - s * 0.29f, cy - s * 0.22f, s * 0.105f, s * 0.145f)
        drawToe(canvas, cx - s * 0.10f, cy - s * 0.32f, s * 0.095f, s * 0.145f)
        drawToe(canvas, cx + s * 0.10f, cy - s * 0.32f, s * 0.095f, s * 0.145f)
        drawToe(canvas, cx + s * 0.29f, cy - s * 0.22f, s * 0.105f, s * 0.145f)
    }

    private fun drawToe(canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float) {
        canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), paint)
    }

    private fun drawBolt(canvas: Canvas, cx: Float, cy: Float, side: Float, color: Int) {
        val s = side * 0.064f
        val path = Path().apply {
            moveTo(cx + s * 0.00f, cy - s * 0.58f)
            lineTo(cx - s * 0.28f, cy - s * 0.02f)
            lineTo(cx - s * 0.03f, cy - s * 0.02f)
            lineTo(cx - s * 0.19f, cy + s * 0.48f)
            lineTo(cx + s * 0.34f, cy - s * 0.18f)
            lineTo(cx + s * 0.08f, cy - s * 0.18f)
            close()
        }
        paint.colorFilter = null
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        paint.color = color
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
        paint.color = Color.rgb(79, 248, 142)
        canvas.drawArc(RectF(cx-r, cy-r, cx+r, cy+r), 145f, 250f, false, paint)
    }

    private fun safeDecode(id: Int): Bitmap? = try {
        BitmapFactory.decodeResource(resources, id)
    } catch (_: Throwable) {
        null
    }

    private fun dp(value: Float): Float = value * density
}
