package com.screendraw.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

enum class Tool { PEN, LASER, ERASER_PIXEL, ERASER_OBJECT, SHAPE, SELECT }

/**
 * Transparent overlay canvas that sits on top of whatever app is behind it.
 * Owns every object drawn (strokes + shapes), the current tool, and the
 * select/move/recolor logic.
 */
class DrawView(context: Context) : View(context) {

    private val objects = mutableListOf<DrawObject>()
    private val redoStack = mutableListOf<DrawObject>()

    var currentTool = Tool.PEN
    var currentShapeType = ShapeType.LINE
    var currentColor = Color.parseColor("#2196F3")
        private set
    var strokeWidthPx = 10f

    private var activeObject: DrawObject? = null
    private var selectedObject: DrawObject? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // --- laser pointer state: short-lived glowing trail that fades on its own ---
    private data class LaserPoint(val x: Float, val y: Float, val bornAt: Long)
    private val laserTrail = mutableListOf<LaserPoint>()
    private val laserLifetimeMs = 550L
    private val fadeHandler = Handler(Looper.getMainLooper())
    private val fadeTicker = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            laserTrail.removeAll { now - it.bornAt > laserLifetimeMs }
            invalidate()
            if (laserTrail.isNotEmpty()) fadeHandler.postDelayed(this, 16)
        }
    }

    private val selectionPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#FFC107")
        pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
        isAntiAlias = true
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    // ---------- public controls used by the toolbar ----------

    fun setColor(color: Int) {
        currentColor = color
        // If something is selected, recolor it live instead of just changing the "next stroke" color.
        selectedObject?.let { it.color = color; invalidate() }
    }

    fun setStrokeWidth(widthPx: Float) {
        strokeWidthPx = widthPx
        selectedObject?.let { it.strokeWidth = widthPx; invalidate() }
    }

    fun undo() {
        if (objects.isNotEmpty()) {
            redoStack.add(objects.removeAt(objects.size - 1))
            selectedObject = null
            invalidate()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            objects.add(redoStack.removeAt(redoStack.size - 1))
            invalidate()
        }
    }

    fun clearAll() {
        objects.clear()
        redoStack.clear()
        selectedObject = null
        invalidate()
    }

    fun deselect() {
        selectedObject = null
        invalidate()
    }

    // ---------- touch handling ----------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (currentTool) {
            Tool.PEN, Tool.ERASER_PIXEL -> handleFreehand(event, x, y, isEraser = currentTool == Tool.ERASER_PIXEL)
            Tool.LASER -> handleLaser(event, x, y)
            Tool.ERASER_OBJECT -> handleObjectEraser(event, x, y)
            Tool.SHAPE -> handleShape(event, x, y)
            Tool.SELECT -> handleSelect(event, x, y)
        }
        invalidate()
        return true
    }

    private fun handleFreehand(event: MotionEvent, x: Float, y: Float, isEraser: Boolean) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                redoStack.clear()
                val obj = DrawObject(
                    ShapeType.FREEHAND,
                    currentColor,
                    if (isEraser) strokeWidthPx * 4 else strokeWidthPx,
                    isEraserStroke = isEraser
                )
                obj.freehandPoints.add(PointF(x, y))
                activeObject = obj
                objects.add(obj)
            }
            MotionEvent.ACTION_MOVE -> {
                activeObject?.freehandPoints?.add(PointF(x, y))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeObject = null
            }
        }
    }

    private fun handleLaser(event: MotionEvent, x: Float, y: Float) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                laserTrail.add(LaserPoint(x, y, System.currentTimeMillis()))
                fadeHandler.removeCallbacks(fadeTicker)
                fadeHandler.post(fadeTicker)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                fadeHandler.post(fadeTicker) // let existing points fade out on their own
            }
        }
    }

    private fun handleObjectEraser(event: MotionEvent, x: Float, y: Float) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
            val hit = objects.lastOrNull { it.containsPoint(x, y) }
            if (hit != null) {
                objects.remove(hit)
                if (selectedObject === hit) selectedObject = null
            }
        }
    }

    private fun handleShape(event: MotionEvent, x: Float, y: Float) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                redoStack.clear()
                val obj = DrawObject(currentShapeType, currentColor, strokeWidthPx)
                obj.startX = x; obj.startY = y; obj.endX = x; obj.endY = y
                activeObject = obj
                objects.add(obj)
            }
            MotionEvent.ACTION_MOVE -> {
                activeObject?.let { it.endX = x; it.endY = y }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeObject = null
            }
        }
    }

    private fun handleSelect(event: MotionEvent, x: Float, y: Float) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = x; lastTouchY = y
                selectedObject = objects.lastOrNull { it.containsPoint(x, y) }
            }
            MotionEvent.ACTION_MOVE -> {
                selectedObject?.let {
                    it.translate(x - lastTouchX, y - lastTouchY)
                    lastTouchX = x; lastTouchY = y
                }
            }
        }
    }

    // ---------- drawing ----------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (obj in objects) {
            canvas.drawPath(obj.toPath(), obj.toPaint())
        }

        selectedObject?.let { canvas.drawRect(it.boundingBox(), selectionPaint) }

        // Laser trail: glowing dots that shrink/fade with age.
        val now = System.currentTimeMillis()
        for (p in laserTrail) {
            val age = (now - p.bornAt).toFloat() / laserLifetimeMs
            val alpha = ((1f - age).coerceIn(0f, 1f) * 255).toInt()
            val radius = 14f * (1f - age * 0.6f)
            val glow = Paint().apply {
                isAntiAlias = true
                color = Color.RED
                this.alpha = alpha
            }
            canvas.drawCircle(p.x, p.y, radius, glow)
        }
    }
}
