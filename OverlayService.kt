package com.screendraw.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * Owns the overlay windows:
 *  1. bubble    - small draggable circle, tap = toggle toolbar
 *  2. toolbar   - main row of tools, plus swappable sub-panels (shapes / color / size)
 *  3. drawView  - fullscreen transparent canvas, only "touchable" while expanded
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private lateinit var toolbarRoot: LinearLayout
    private lateinit var mainRow: LinearLayout
    private lateinit var subPanel: LinearLayout
    private lateinit var drawView: DrawView

    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var toolbarParams: WindowManager.LayoutParams
    private lateinit var drawParams: WindowManager.LayoutParams

    private var expanded = false
    private val channelId = "screendraw_channel"

    private val presetColors = listOf(
        "#4CAF50", "#F44336", "#2196F3", "#1DE9B6", "#9C27B0", "#FFFFFF", "#000000"
    )

    override fun onBind(intent: android.content.Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        addDrawLayer()
        addToolbar()
        addBubble()
        setExpanded(false)
    }

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "ScreenDraw", NotificationManager.IMPORTANCE_MIN)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("ScreenDraw is running")
            .setContentText("Tap the floating circle to draw on your screen")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(1, notification)
    }

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    // ---------- fullscreen transparent draw canvas ----------

    private fun addDrawLayer() {
        drawView = DrawView(this)
        drawParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(drawView, drawParams)
    }

    // ---------- toolbar: main row + one swappable sub-panel ----------

    private fun addToolbar() {
        toolbarRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
            background = GradientDrawable().apply {
                cornerRadius = 32f
                setColor(Color.parseColor("#E1222222"))
            }
        }

        mainRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        subPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
        }

        buildMainRow()

        toolbarRoot.addView(mainRow)
        toolbarRoot.addView(subPanel)

        toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 120
        }
        windowManager.addView(toolbarRoot, toolbarParams)
    }

    private fun buildMainRow() {
        mainRow.removeAllViews()
        mainRow.addView(toolButton("✏") { drawView.currentTool = Tool.PEN; closeSubPanel() })
        mainRow.addView(toolButton("⬠") { showShapesPanel() })
        mainRow.addView(toolButton(eraserLabel()) { cycleEraserMode(it) })
        mainRow.addView(toolButton("🔴") { drawView.currentTool = Tool.LASER; closeSubPanel() })
        mainRow.addView(toolButton("⬚") { drawView.currentTool = Tool.SELECT; closeSubPanel() })
        mainRow.addView(toolButton("↶") { drawView.undo() })
        mainRow.addView(toolButton("↷") { drawView.redo() })
        mainRow.addView(toolButton("🗑") { drawView.clearAll() })
        mainRow.addView(toolButton("⭘") { showSizePanel() })
        mainRow.addView(toolButton("🎨") { showColorPanel() })
        mainRow.addView(toolButton("✕") { setExpanded(false) })
    }

    private var eraserModeIsObject = false
    private fun eraserLabel() = if (eraserModeIsObject) "⌫◆" else "⌫·"

    private fun cycleEraserMode(button: Button) {
        // Tapping repeatedly toggles between erase-whole-object and erase-pixels.
        eraserModeIsObject = !eraserModeIsObject
        drawView.currentTool = if (eraserModeIsObject) Tool.ERASER_OBJECT else Tool.ERASER_PIXEL
        button.text = eraserLabel()
        closeSubPanel()
    }

    // ---- shapes sub-panel ----

    private fun showShapesPanel() {
        subPanel.removeAllViews()
        subPanel.addView(toolButton("／") { pickShape(ShapeType.LINE) })
        subPanel.addView(toolButton("▭") { pickShape(ShapeType.RECTANGLE) })
        subPanel.addView(toolButton("△") { pickShape(ShapeType.TRIANGLE) })
        subPanel.addView(toolButton("○") { pickShape(ShapeType.CIRCLE) })
        subPanel.addView(toolButton("⬣") { pickShape(ShapeType.OCTAGON) })
        subPanel.addView(toolButton("▦") { pickShape(ShapeType.CUBE) })
        subPanel.visibility = View.VISIBLE
    }

    private fun pickShape(type: ShapeType) {
        drawView.currentTool = Tool.SHAPE
        drawView.currentShapeType = type
        closeSubPanel()
    }

    // ---- color sub-panel: presets + full spectrum picker ----

    private fun showColorPanel() {
        subPanel.removeAllViews()
        for (hex in presetColors) {
            subPanel.addView(colorSwatch(hex))
        }

        val spectrum = SpectrumColorPickerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(360, 220)
            onColorPicked = { color -> drawView.setColor(color) }
        }
        subPanel.addView(spectrum)
        subPanel.visibility = View.VISIBLE
    }

    private fun colorSwatch(hex: String): View {
        val size = 56
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(6, 6, 6, 6) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(hex))
                setStroke(3, Color.WHITE)
            }
            setOnClickListener { drawView.setColor(Color.parseColor(hex)) }
        }
    }

    // ---- size sub-panel: one slider controls pen / shape / eraser thickness ----

    private fun showSizePanel() {
        subPanel.removeAllViews()
        val label = TextView(this).apply {
            text = "Size"
            setTextColor(Color.WHITE)
            setPadding(12, 0, 12, 0)
        }
        val seekBar = SeekBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(400, LinearLayout.LayoutParams.WRAP_CONTENT)
            max = 60
            progress = drawView.strokeWidthPx.toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                    drawView.setStrokeWidth(value.coerceAtLeast(2).toFloat())
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        subPanel.addView(label)
        subPanel.addView(seekBar)
        subPanel.visibility = View.VISIBLE
    }

    private fun closeSubPanel() {
        subPanel.visibility = View.GONE
    }

    private fun toolButton(label: String, onClick: (Button) -> Unit): Button {
        return Button(this).apply {
            text = label
            setPadding(14, 6, 14, 6)
            setOnClickListener { onClick(this) }
        }
    }

    // ---------- the draggable bubble ----------

    private fun addBubble() {
        bubbleView = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2196F3"))
            }
        }

        bubbleParams = WindowManager.LayoutParams(
            140, 140,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        var downX = 0f; var downY = 0f
        var downParamX = 0; var downParamY = 0
        var moved = false

        bubbleView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    downParamX = bubbleParams.x; downParamY = bubbleParams.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (Math.abs(dx) > 12 || Math.abs(dy) > 12) moved = true
                    bubbleParams.x = downParamX + dx
                    bubbleParams.y = downParamY + dy
                    windowManager.updateViewLayout(bubbleView, bubbleParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) setExpanded(!expanded)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubbleView, bubbleParams)
    }

    // ---------- expand / collapse ----------

    private fun setExpanded(show: Boolean) {
        expanded = show
        toolbarRoot.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) closeSubPanel()

        drawParams.flags = if (show) {
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        windowManager.updateViewLayout(drawView, drawParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { windowManager.removeView(bubbleView) }
        runCatching { windowManager.removeView(toolbarRoot) }
        runCatching { windowManager.removeView(drawView) }
    }
}
