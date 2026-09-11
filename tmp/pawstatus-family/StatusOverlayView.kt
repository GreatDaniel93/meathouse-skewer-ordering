package com.daniel.cupertinoStatus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/**
 * Paw Status v1.0
 *
 * Battery = incomplete circular ring.
 * Signal = four paw prints that complete the missing lower arc.
 * Centre = the user's real four pets.
 */
class StatusOverlayView(context: Context) : View(context) {

    var batteryPercent: Int = 100
    var charging: Boolean = false
    var signalLevel: Int = 4
    var wifiConnected: Boolean = true
    var darkStyle: Boolean = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val src = Rect(0, 0, 720, 720)
    private val dst = RectF()

    private val ringMask: Bitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.apple_ring_mask)
    }
    private val familyCluster: Bitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.family_cluster)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val side = min(width, height).toFloat()
        val left = (width - side) / 2f
        val top = (height - side) / 2f
        dst.set(left, top, left + side, top + side)

        val inactive = if (darkStyle) Color.argb(90, 0, 0, 0) else Color.argb(100, 255, 255, 255)
        val activeWarm = Color.rgb(255, 184, 108)
        val activeBattery = when {
            charging -> Color.rgb(122, 232, 138)
            batteryPercent <= 20 -> Color.rgb(255, 86, 72)
            else -> activeWarm
        }

        drawTintedMask(canvas, ringMask, inactive)

        val centerX = left + side * (359f / 720f)
        val centerY = top + side * (360f / 720f)
        val clipRadius = side * 0.60f
        val progress = batteryPercent.coerceIn(0, 100) / 100f

        val saveCount = canvas.save()
        val wedge = Path().apply {
            moveTo(centerX, centerY)
            val arcBox = RectF(
                centerX - clipRadius,
                centerY - clipRadius,
                centerX + clipRadius,
                centerY + clipRadius
            )
            arcTo(arcBox, 142f, 253f * progress, false)
            close()
        }
        canvas.clipPath(wedge)
        drawTintedMask(canvas, ringMask, activeBattery)
        canvas.restoreToCount(saveCount)

        paint.alpha = 255
        paint.colorFilter = null
        canvas.drawBitmap(familyCluster, src, dst, paint)

        val pawCenters = arrayOf(
            Pair(203f, 619f),
            Pair(304.5f, 658f),
            Pair(418.5f, 656.5f),
            Pair(520.5f, 616.5f)
        )
        val pawSize = side * (42f / 720f)

        pawCenters.forEachIndexed { index, point ->
            val x = left + side * (point.first / 720f)
            val y = top + side * (point.second / 720f)
            val color = if (index < signalLevel.coerceIn(0, 4)) activeWarm else inactive
            drawPaw(canvas, x, y, pawSize, color)
        }
    }

    private fun drawPaw(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = Color.alpha(color)
        paint.colorFilter = null

        canvas.drawOval(
            RectF(cx - s * 0.32f, cy - s * 0.06f, cx + s * 0.32f, cy + s * 0.34f),
            paint
        )

        drawToe(canvas, cx - s * 0.29f, cy - s * 0.32f, s * 0.13f, s * 0.16f)
        drawToe(canvas, cx - s * 0.10f, cy - s * 0.41f, s * 0.12f, s * 0.16f)
        drawToe(canvas, cx + s * 0.11f, cy - s * 0.41f, s * 0.12f, s * 0.16f)
        drawToe(canvas, cx + s * 0.30f, cy - s * 0.29f, s * 0.13f, s * 0.16f)

        paint.alpha = 255
    }

    private fun drawToe(canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float) {
        canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), paint)
    }

    private fun drawTintedMask(canvas: Canvas, bitmap: Bitmap, color: Int) {
        paint.alpha = Color.alpha(color)
        paint.colorFilter = PorterDuffColorFilter(
            Color.rgb(Color.red(color), Color.green(color), Color.blue(color)),
            PorterDuff.Mode.SRC_IN
        )
        canvas.drawBitmap(bitmap, src, dst, paint)
        paint.alpha = 255
        paint.colorFilter = null
    }
}
