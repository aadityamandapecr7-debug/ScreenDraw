package com.screendraw.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View

/**
 * Full-spectrum color picker: a saturation/value square on top of a hue strip,
 * exactly like the classic picker in image editors. Drag anywhere to pick any
 * of millions of colors, not just presets.
 */
class SpectrumColorPickerView(context: Context) : View(context) {

    var onColorPicked: ((Int) -> Unit)? = null

    private var hue = 210f          // 0..360
    private var sat = 0.55f         // 0..1
    private var value = 0.95f       // 0..1

    private val svRect = RectF()
    private val hueRect = RectF()
    private val cursorPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
        isAntiAlias = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val hueBarHeight = h * 0.22f
        svRect.set(0f, 0f, w.toFloat(), h - hueBarHeight - 16f)
        hueRect.set(0f, h - hueBarHeight, w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Saturation/Value square for the current hue
        val hueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        val satShader = LinearGradient(
            svRect.left, 0f, svRect.right, 0f,
            Color.WHITE, hueColor, Shader.TileMode.CLAMP
        )
        val valShader = LinearGradient(
            0f, svRect.top, 0f, svRect.bottom,
            Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP
        )
        val satPaint = Paint().apply { shader = satShader }
        canvas.drawRect(svRect, satPaint)

        val valPaint = Paint().apply {
            shader = valShader
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.MULTIPLY)
        }
        canvas.drawRect(svRect, valPaint)

        // Hue strip, full rainbow
        val hueColors = intArrayOf(
            Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN,
            Color.GREEN, Color.YELLOW, Color.RED
        )
        val hueShader = LinearGradient(
            hueRect.left, 0f, hueRect.right, 0f,
            hueColors, null, Shader.TileMode.CLAMP
        )
        val hueBarPaint = Paint().apply { shader = hueShader }
        canvas.drawRect(hueRect, hueBarPaint)

        // Cursor on SV square
        val cx = svRect.left + sat * svRect.width()
        val cy = svRect.top + (1 - value) * svRect.height()
        canvas.drawCircle(cx, cy, 14f, cursorPaint)

        // Cursor on hue strip
        val hx = hueRect.left + (hue / 360f) * hueRect.width()
        canvas.drawRect(hx - 4f, hueRect.top, hx + 4f, hueRect.bottom, cursorPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when {
            svRect.contains(event.x, event.y) || isNear(svRect, event) -> {
                sat = ((event.x - svRect.left) / svRect.width()).coerceIn(0f, 1f)
                value = (1 - (event.y - svRect.top) / svRect.height()).coerceIn(0f, 1f)
            }
            hueRect.contains(event.x, event.y) || isNear(hueRect, event) -> {
                hue = ((event.x - hueRect.left) / hueRect.width() * 360f).coerceIn(0f, 360f)
            }
            else -> return false
        }
        invalidate()
        onColorPicked?.invoke(currentColor())
        return true
    }

    private fun isNear(r: RectF, e: MotionEvent): Boolean {
        // Slightly forgiving hit area near the strip edges
        return e.y in (r.top - 20f)..(r.bottom + 20f) && e.x in r.left..r.right
    }

    fun currentColor(): Int = Color.HSVToColor(floatArrayOf(hue, sat, value))
}
