package com.example.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class OverlayManager(private val context: Context) {

    private var windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    
    // UI state flows
    private val _streamState = MutableStateFlow("Offline")
    val streamState: StateFlow<String> = _streamState
    
    var onStartClicked: (() -> Unit)? = null
    var onStopClicked: (() -> Unit)? = null
    var onMicToggled: ((Boolean) -> Unit)? = null
    var onBannerToggled: ((Boolean) -> Unit)? = null
    var onSettingsClicked: (() -> Unit)? = null

    private var isMicOn = true
    private var isBannerOn = false

    @SuppressLint("ClickableViewAccessibility")
    fun showOverlay() {
        if (overlayView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100
        
        // This is CRUCIAL: it prevents the window from being screen captured.
        // On Android 12+ (API 31+), TYPE_APPLICATION_OVERLAY windows are captured by default unless FLAG_SECURE is used, 
        // OR we can use window-level APIs. FLAG_SECURE makes the window black on capture, but we want it completely excluded if possible.
        // Actually, FLAG_SECURE is the only reliable way to hide a window from MediaProjection across all devices without compositing.
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_SECURE

        overlayView = LayoutInflater.from(context).inflate(R.layout.overlay_layout, null)
        
        val btnStart = overlayView?.findViewById<Button>(R.id.btnStart)
        val btnStop = overlayView?.findViewById<Button>(R.id.btnStop)
        val btnMic = overlayView?.findViewById<Button>(R.id.btnMic)
        val btnBanner = overlayView?.findViewById<Button>(R.id.btnBanner)
        val btnSettings = overlayView?.findViewById<Button>(R.id.btnSettings)
        val tvStatus = overlayView?.findViewById<TextView>(R.id.tvStatus)

        btnStart?.setOnClickListener { onStartClicked?.invoke() }
        btnStop?.setOnClickListener { onStopClicked?.invoke() }
        btnMic?.setOnClickListener {
            isMicOn = !isMicOn
            btnMic.text = if (isMicOn) "Mic: ON" else "Mic: OFF"
            onMicToggled?.invoke(isMicOn)
        }
        btnBanner?.setOnClickListener {
            isBannerOn = !isBannerOn
            btnBanner.text = if (isBannerOn) "Banner: ON" else "Banner: OFF"
            onBannerToggled?.invoke(isBannerOn)
        }
        btnSettings?.setOnClickListener { onSettingsClicked?.invoke() }

        // Dragging logic
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        overlayView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(overlayView, params)
    }

    fun updateStatus(status: String) {
        _streamState.value = status
        overlayView?.findViewById<TextView>(R.id.tvStatus)?.text = status
    }

    fun hideOverlay() {
        if (overlayView != null) {
            windowManager.removeView(overlayView)
            overlayView = null
        }
    }
}
