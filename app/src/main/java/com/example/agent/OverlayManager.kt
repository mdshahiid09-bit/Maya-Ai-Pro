package com.example.agent

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView

class OverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    var onStopClicked: (() -> Unit)? = null

    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    @SuppressLint("InflateParams")
    fun showOverlay(statusText: String) {
        if (!canDrawOverlays()) return

        if (overlayView != null) {
            updateStatus(statusText)
            return
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 120
        }

        // Programmatic sleek dark neon pill layout with red STOP button
        val container = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(16, 16, 16, 16)
        }

        val pill = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(32, 16, 24, 16)
            background = createPillDrawable()
            elevation = 16f
        }

        val dot = View(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(24, 24).apply {
                marginEnd = 16
            }
            background = createDotDrawable()
        }
        pill.addView(dot)

        val textView = TextView(context).apply {
            id = View.generateViewId()
            text = statusText
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 1
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            ).apply {
                marginEnd = 24
            }
        }
        pill.addView(textView)

        val stopBtn = Button(context).apply {
            text = "STOP"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#E53935"))
            setPadding(24, 8, 24, 8)
            setOnClickListener {
                onStopClicked?.invoke()
                hideOverlay()
            }
        }
        pill.addView(stopBtn)

        container.addView(pill)
        overlayView = container

        try {
            windowManager.addView(container, params)
        } catch (_: Exception) {
            overlayView = null
        }
    }

    fun updateStatus(statusText: String) {
        val view = overlayView ?: return
        val tv = view.findViewById<TextView>(android.R.id.text1)
            ?: (view as? FrameLayout)?.findViewById(1)
        tv?.text = statusText
    }

    fun hideOverlay() {
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView)
            } catch (_: Exception) { }
            overlayView = null
        }
    }

    private fun createPillDrawable(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 48f
            setColor(Color.parseColor("#EE121722"))
            setStroke(2, Color.parseColor("#4400E5FF"))
        }
    }

    private fun createDotDrawable(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(Color.parseColor("#00E5FF"))
        }
    }
}
