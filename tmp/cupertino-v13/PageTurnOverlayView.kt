package com.daniel.cupertinoStatus

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import kotlin.math.sin

class PageTurnOverlayView(context: Context) : View(context) {
    var progress: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    var rightToLeft: Boolean = true

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val page = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 1f || h <= 1f) return
        val bend = sin(progress * Math.PI).toFloat() * w * 0.035f
        if (rightToLeft) {
            val edgeX = w * (1f - progress)
            page.reset(); page.moveTo(0f, 0f); page.lineTo(edgeX, 0f)
            page.cubicTo(edgeX - bend * .20f, h * .26f, edgeX + bend, h * .70f, edgeX - bend * .18f, h)
            page.lineTo(0f, h); page.close(); drawSheet(canvas, page, edgeX, h, true)
        } else {
            val edgeX = w * progress
            page.reset(); page.moveTo(edgeX, 0f); page.lineTo(w, 0f); page.lineTo(w, h); page.lineTo(edgeX, h)
            page.cubicTo(edgeX + bend * .18f, h * .70f, edgeX - bend, h * .26f, edgeX + bend * .20f, 0f)
            page.close(); drawSheet(canvas, page, edgeX, h, false)
        }
    }

    private fun drawSheet(canvas: Canvas, shape: Path, edgeX: Float, h: Float, rtl: Boolean) {
        fill.color = Color.rgb(13, 13, 17)
        canvas.drawPath(shape, fill)
        val sw = width * .14f
        shadow.shader = if (rtl) {
            LinearGradient(edgeX - sw, 0f, edgeX + sw * .18f, 0f,
                intArrayOf(Color.TRANSPARENT, Color.argb(105,0,0,0), Color.argb(30,255,255,255)),
                floatArrayOf(0f,.78f,1f), Shader.TileMode.CLAMP)
        } else {
            LinearGradient(edgeX - sw * .18f, 0f, edgeX + sw, 0f,
                intArrayOf(Color.argb(30,255,255,255), Color.argb(105,0,0,0), Color.TRANSPARENT),
                floatArrayOf(0f,.22f,1f), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(edgeX - sw, 0f, edgeX + sw, h, shadow)
        shadow.shader = null
        edge.color = Color.argb(80,255,255,255); edge.strokeWidth = resources.displayMetrics.density * .8f; edge.style = Paint.Style.STROKE
        canvas.drawPath(shape, edge); edge.style = Paint.Style.FILL
    }
}
