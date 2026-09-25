package com.screendraw.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Only job of this screen: get the "draw over other apps" permission,
 * then start the floating bubble service and get out of the way.
 */
class MainActivity : AppCompatActivity() {

    private val overlayPermissionRequestCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
        }

        val title = TextView(this).apply {
            text = "ScreenDraw"
            textSize = 24f
            gravity = Gravity.CENTER
        }

        val desc = TextView(this).apply {
            text = "Draw on top of any app with your stylus.\n\n" +
                "Tap Start below, then allow ScreenDraw to appear over other apps. " +
                "A small floating circle will show up — tap it any time to open the pen tools."
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 48)
        }

        val startButton = Button(this).apply {
            text = "Start floating toolbar"
            setOnClickListener { requestOverlayPermissionThenStart() }
        }

        root.addView(title)
        root.addView(desc)
        root.addView(startButton)
        return root
    }

    private fun requestOverlayPermissionThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, overlayPermissionRequestCode)
        } else {
            startOverlayService()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == overlayPermissionRequestCode) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                startOverlayService()
            }
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        moveTaskToBack(true)
    }
}
