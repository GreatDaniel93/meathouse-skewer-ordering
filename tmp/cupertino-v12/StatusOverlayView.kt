package com.daniel.cupertinoStatus

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * v0.12 — traced from the supplied reference image.
 *
 * The renderer uses a fixed 720×720 design coordinate system measured from the
 * user's original reference.  It is always uniformly scaled, so the circle can
 * never become an ellipse.  The Wi‑Fi silhouette is contour-traced from the
 * reference and the four cellular dots are regularised onto the same circular
 * path as the outer ring.
 */
class StatusOverlayView(context: Context) : View(context) {

    var batteryPercent: Int = 100
    var charging: Boolean = false
    var signalLevel: Int = 4
    var wifiConnected: Boolean = true
    var darkStyle: Boolean = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringRect = RectF()

    // Reference geometry (720×720 coordinate system)
    private val cx = 359f
    private val cy = 360f
    private val ringRadius = 304.5f
    private val ringStroke = 62f
    private val ringStart = 142f
    private val ringSweep = 253f

    // The four dots in the supplied reference lie on essentially the same
    // radius as the ring centreline, with ~21.15° equal angular spacing.
    private val dotOrbit = 302.4f
    private val dotRadius = 29.5f
    private val dotAngles = floatArrayOf(121.27f, 100.11f, 78.96f, 57.81f)

    // Contours traced directly from the supplied reference screenshot.
    // Wi‑Fi group bounding box is centred on the outer ring centre.
    private val wifiOuter = floatArrayOf(
        213.7f,324f, 213.7f,329f, 225.7f,346f, 232.7f,349f,
        252.7f,348f, 268.7f,334f, 275.7f,332f, 282.7f,325f,
        308.7f,311f, 350.7f,304f, 371.7f,305f, 407.7f,311f,
        449.7f,334f, 463.7f,347f, 472.7f,350f, 487.7f,348f,
        496.7f,341f, 501.7f,329f, 501.7f,319f, 495.7f,306f,
        456.7f,277f, 436.7f,267f, 420.7f,263f, 413.7f,259f,
        385.7f,253f, 366.7f,251f, 327.7f,253f, 316.7f,257f,
        303.7f,259f, 293.7f,265f, 279.7f,267f, 273.7f,272f,
        253.7f,282f, 224.7f,304f
    )

    private val wifiInner = floatArrayOf(
        266.7f,374f, 269.7f,386f, 275.7f,396f, 287.7f,399f,
        298.7f,399f, 305.7f,397f, 324.7f,383f, 357.7f,375f,
        370.7f,376f, 391.7f,383f, 404.7f,390f, 411.7f,397f,
        421.7f,399f, 437.7f,398f, 449.7f,386f, 451.7f,379f,
        449.7f,364f, 434.7f,348f, 404.7f,331f, 372.7f,324f,
        345.7f,324f, 315.7f,330f, 306.7f,337f, 297.7f,339f,
        281.7f,349f, 272.7f,358f
    )

    private val wifiDrop = floatArrayOf(
        357.7f,396f, 330.7f,405f, 319.7f,414f, 315.7f,427f,
        319.7f,439f, 334.7f,455f, 351.7f,469f, 364.7f,470f,
        385.7f,453f, 387.7f,449f, 401.7f,436f, 403.7f,430f,
        401.7f,419f, 390.7f,406f, 383.7f,402f
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val side = min(width, height).toFloat()
        val scale = side / 720f
        val left = (width - side) / 2f
        val top = (height - side) / 2f

        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)

        val primary = if (darkStyle) Color.BLACK else Color.WHITE
        val secondary = if (darkStyle) Color.argb(105, 0, 0, 0) else Color.argb(112, 255, 255, 255)
        val batteryColor = when {
            charging -> Color.rgb(48, 209, 88)
            batteryPercent <= 20 -> Color.rgb(255, 69, 58)
            else -> primary
        }

        drawRing(canvas, secondary, batteryColor)
        drawWifi(canvas, if (wifiConnected) primary else secondary)
        drawDots(canvas, primary, secondary)

        canvas.restore()
    }

    private fun drawRing(canvas: Canvas, secondary: Int, active: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = ringStroke
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        ringRect.set(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)

        paint.color = secondary
        canvas.drawArc(ringRect, ringStart, ringSweep, false, paint)

        paint.color = active
        val fraction = batteryPercent.coerceIn(0, 100) / 100f
        canvas.drawArc(ringRect, ringStart, ringSweep * fraction, false, paint)
    }

    private fun drawWifi(canvas: Canvas, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        drawPolygon(canvas, wifiOuter)
        drawPolygon(canvas, wifiInner)
        drawPolygon(canvas, wifiDrop)
    }

    private fun drawDots(canvas: Canvas, primary: Int, secondary: Int) {
        paint.style = Paint.Style.FILL
        for (i in 0..3) {
            val a = Math.toRadians(dotAngles[i].toDouble())
            val x = cx + cos(a).toFloat() * dotOrbit
            val y = cy + sin(a).toFloat() * dotOrbit
            paint.color = if (i < signalLevel.coerceIn(0, 4)) primary else secondary
            canvas.drawCircle(x, y, dotRadius, paint)
        }
    }

    private fun drawPolygon(canvas: Canvas, pts: FloatArray) {
        val path = Path()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) {
            path.lineTo(pts[i], pts[i + 1])
            i += 2
        }
        path.close()
        canvas.drawPath(path, paint)
    }
}
