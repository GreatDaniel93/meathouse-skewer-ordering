package com.daniel.cupertinoStatus

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * v0.6 optical reference match.
 *
 * This revision is based on the real-device side-by-side screenshot rather than
 * a generic Wi-Fi glyph.  The Apple reference is visually lighter: a thinner
 * outer progress ring, a larger two-band Wi-Fi mark, and four smaller dots that
 * sit below (not against) the ring endpoints.
 */
class StatusOverlayView(context: Context) : View(context) {

    var batteryPercent: Int = 100
    var charging: Boolean = false
    var signalLevel: Int = 4
    var wifiConnected: Boolean = true
    var darkStyle: Boolean = false // false = white marks on dark status bars

    private val density = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val s = min(width, height).toFloat()
        val cx = width * 0.50f
        val cy = height * 0.390f
        val r = s * 0.305f

        // Reference ring is noticeably lighter/thinner than v0.5.
        val ringStroke = max(dp(2.45f), r * 0.128f)

        val primary = if (darkStyle) Color.BLACK else Color.WHITE
        val secondary = if (darkStyle) {
            Color.argb(72, 0, 0, 0)
        } else {
            Color.argb(100, 255, 255, 255)
        }

        drawBatteryRing(canvas, cx, cy, r, ringStroke, primary, secondary)
        drawAppleWifi(canvas, cx, cy + r * 0.055f, r, primary, secondary)
        drawSignalDots(canvas, cx, cy + r * 1.105f, r, primary, secondary)

        if (charging) {
            drawBolt(canvas, cx + r * 0.61f, cy - r * 0.49f, primary)
        }
    }

    private fun drawBatteryRing(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        stroke: Float,
        primary: Int,
        secondary: Int
    ) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = stroke
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND

        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // Open lower arc. The inactive remainder lives only on the right side.
        val startAngle = 139f
        val totalSweep = 261f

        p.color = secondary
        canvas.drawArc(rect, startAngle, totalSweep, false, p)

        val fraction = batteryPercent.coerceIn(0, 100) / 100f
        p.color = when {
            charging -> Color.rgb(48, 209, 88)
            batteryPercent <= 20 -> Color.rgb(255, 69, 58)
            else -> primary
        }
        canvas.drawArc(rect, startAngle, totalSweep * fraction, false, p)
    }

    private fun drawAppleWifi(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        primary: Int,
        secondary: Int
    ) {
        val color = if (wifiConnected) primary else secondary
        p.color = color
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND

        // The reference Wi-Fi is larger than v0.5 and has more air between bands.
        p.strokeWidth = max(dp(2.10f), radius * 0.096f)
        val outerHalf = radius * 0.445f
        val outerY = cy + radius * 0.015f
        val outer = Path().apply {
            moveTo(cx - outerHalf, outerY)
            cubicTo(
                cx - radius * 0.275f, cy - radius * 0.205f,
                cx + radius * 0.275f, cy - radius * 0.205f,
                cx + outerHalf, outerY
            )
        }
        canvas.drawPath(outer, p)

        p.strokeWidth = max(dp(2.05f), radius * 0.094f)
        val innerHalf = radius * 0.278f
        val innerY = cy + radius * 0.245f
        val inner = Path().apply {
            moveTo(cx - innerHalf, innerY)
            cubicTo(
                cx - radius * 0.165f, cy + radius * 0.105f,
                cx + radius * 0.165f, cy + radius * 0.105f,
                cx + innerHalf, innerY
            )
        }
        canvas.drawPath(inner, p)

        // The reference bottom Wi-Fi mark reads almost as a round dot with a
        // subtly softened/pointed bottom, not the tiny sharp teardrop from v0.5.
        p.style = Paint.Style.FILL
        val dotRadius = max(dp(2.25f), radius * 0.118f)
        val dotCy = cy + radius * 0.455f
        canvas.drawCircle(cx, dotCy, dotRadius, p)

        // Add a very small soft point to the bottom to preserve the Apple-like drop silhouette.
        val tip = Path().apply {
            moveTo(cx - dotRadius * 0.42f, dotCy + dotRadius * 0.50f)
            quadTo(cx, dotCy + dotRadius * 1.25f, cx + dotRadius * 0.42f, dotCy + dotRadius * 0.50f)
            close()
        }
        canvas.drawPath(tip, p)
    }

    private fun drawSignalDots(
        canvas: Canvas,
        cx: Float,
        baseY: Float,
        radius: Float,
        primary: Int,
        secondary: Int
    ) {
        // Measured from the supplied reference: outer dots are higher, inner dots lower.
        // All four sit inward from the ring endpoints and are clearly separated from them.
        val xOffsets = floatArrayOf(-0.455f, -0.155f, 0.155f, 0.455f)
        val yOffsets = floatArrayOf(-0.060f, 0.050f, 0.050f, -0.060f)
        val dotRadius = max(dp(1.55f), radius * 0.078f)

        for (i in 0..3) {
            p.style = Paint.Style.FILL
            p.color = if (i < signalLevel.coerceIn(0, 4)) primary else secondary
            canvas.drawCircle(
                cx + radius * xOffsets[i],
                baseY + radius * yOffsets[i],
                dotRadius,
                p
            )
        }
    }

    private fun drawBolt(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        val path = Path().apply {
            moveTo(cx + dp(0.2f), cy - dp(3.8f))
            lineTo(cx - dp(1.8f), cy - dp(0.2f))
            lineTo(cx - dp(0.3f), cy - dp(0.2f))
            lineTo(cx - dp(1.2f), cy + dp(3.4f))
            lineTo(cx + dp(2.0f), cy - dp(0.8f))
            lineTo(cx + dp(0.6f), cy - dp(0.8f))
            close()
        }
        p.style = Paint.Style.FILL
        p.color = color
        canvas.drawPath(path, p)
    }

    private fun dp(value: Float): Float = value * density
}
