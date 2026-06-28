package com.example.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.overlay.OverlayManager
import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

class StreamService : Service(), ConnectChecker {

    inner class StreamBinder : Binder() {
        fun getService(): StreamService = this@StreamService
    }

    private val binder = StreamBinder()
    private lateinit var rtmpDisplay: RtmpDisplay
    private lateinit var overlayManager: OverlayManager
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // Banner config
    private var isBannerActive = false
    private var bannerIntervalMs = 60000L
    private var bannerDurationMs = 10000L

    override fun onCreate() {
        super.onCreate()
        overlayManager = OverlayManager(this)
        
        // Setup RootEncoder RtmpDisplay
        rtmpDisplay = RtmpDisplay(applicationContext, true, this)
        
        createNotificationChannel()
        startForeground(1, createNotification())
    }

    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val rtmpUrl = ServiceLocator.rtmpUrl
        val resultCode = ServiceLocator.resultCode
        val data = ServiceLocator.data
        val token = ServiceLocator.youtubeToken
        val broadcastId = ServiceLocator.broadcastId

        if (rtmpUrl != null && data != null && token != null && broadcastId != null) {
            initStream(resultCode, data, rtmpUrl, token, broadcastId)
        }
        return START_NOT_STICKY
    }

    fun initStream(resultCode: Int, data: Intent, rtmpUrl: String, token: String, broadcastId: String) {
        // Must set Intent data for MediaProjection
        rtmpDisplay.setIntentResult(resultCode, data)

        // Setup Overlay Callbacks
        overlayManager.onStartClicked = {
            if (!rtmpDisplay.isStreaming) {
                overlayManager.updateStatus("Connecting...")
                
                // Determine resolution
                val resolutionStr = ServiceLocator.resolution
                val width = if (resolutionStr == "1080p") 1920 else if (resolutionStr == "720p") 1280 else 854
                val height = if (resolutionStr == "1080p") 1080 else if (resolutionStr == "720p") 720 else 480
                val bitrate = if (resolutionStr == "1080p") 6000 * 1024 else if (resolutionStr == "720p") 3500 * 1024 else 1500 * 1024

                // For portrait mode streaming, we swap width and height
                val isPortrait = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                val finalWidth = if (isPortrait) height else width
                val finalHeight = if (isPortrait) width else height

                if (rtmpDisplay.prepareVideo(finalWidth, finalHeight, 60, bitrate, 0, resources.displayMetrics.densityDpi)) {
                    val audioSource = ServiceLocator.audioSource
                    
                    // Enable Adaptive Bitrate if supported
                    try {
                        val method = rtmpDisplay.javaClass.getMethod("setAdaptiveBitrate", Boolean::class.javaPrimitiveType)
                        method.invoke(rtmpDisplay, true)
                    } catch (e: Exception) {
                        try {
                            val method = rtmpDisplay.javaClass.getMethod("setAdaptiveBitrate", Boolean::class.java)
                            method.invoke(rtmpDisplay, true)
                        } catch (e2: Exception) {
                            // Ignore if not present
                        }
                    }

                    val audioPrepared = when (audioSource) {
                        "Internal" -> {
                            // Android 10+ internal audio requires MediaProjection
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                rtmpDisplay.prepareAudio(256 * 1024, 44100, true, true, true)
                            } else {
                                rtmpDisplay.prepareAudio() // Fallback to mic for older devices
                            }
                        }
                        "Internal + Mic" -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                rtmpDisplay.prepareAudio(256 * 1024, 44100, true, true, true)
                            } else {
                                rtmpDisplay.prepareAudio()
                            }
                        }
                        else -> rtmpDisplay.prepareAudio() // Mic
                    }

                    if (audioPrepared) {
                        rtmpDisplay.startStream(rtmpUrl)
                    } else {
                        overlayManager.updateStatus("Audio error")
                    }
                } else {
                    overlayManager.updateStatus("Video error")
                }
            }
        }

        overlayManager.onStopClicked = {
            if (rtmpDisplay.isStreaming) {
                rtmpDisplay.stopStream()
                overlayManager.updateStatus("Stopped")
                val t = ServiceLocator.youtubeToken
                val bId = ServiceLocator.broadcastId
                if (t != null && bId != null) {
                    scope.launch {
                        try {
                            val repo = com.example.api.YouTubeRepository()
                            repo.endBroadcast(t, bId)
                        } catch (e: Exception) {}
                    }
                }
            }
        }
        
        overlayManager.onMicToggled = { isOn ->
            if (isOn) {
                rtmpDisplay.enableAudio()
            } else {
                rtmpDisplay.disableAudio()
            }
        }
        
        var bannerJob: kotlinx.coroutines.Job? = null

        overlayManager.onBannerToggled = { isOn ->
            isBannerActive = isOn
            val bytes = ServiceLocator.bannerBytes
            bannerJob?.cancel()
            bannerJob = null
            if (isOn && bytes != null) {
                bannerJob = scope.launch(Dispatchers.Main) {
                    val intervalMillis = (ServiceLocator.bannerInterval * 1000).toLong()
                    while (isActive) {
                        overlayManager.showBanner(bytes)
                        delay(5000) // Show for 5 seconds
                        overlayManager.hideBanner()
                        delay(intervalMillis)
                    }
                }
            } else {
                scope.launch(Dispatchers.Main) {
                    overlayManager.hideBanner()
                }
            }
        }

        overlayManager.showOverlay()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        overlayManager.hideOverlay()
        if (rtmpDisplay.isStreaming) {
            rtmpDisplay.stopStream()
        }
    }

    // ConnectChecker Callbacks
    override fun onConnectionStarted(rtmpUrl: String) {
        overlayManager.updateStatus("Connecting...")
    }

    override fun onConnectionSuccess() {
        overlayManager.updateStatus("LIVE")
        val token = ServiceLocator.youtubeToken
        val broadcastId = ServiceLocator.broadcastId
        if (token != null && broadcastId != null) {
            scope.launch {
                try {
                    val repo = com.example.api.YouTubeRepository()
                    repo.transitionToLive(token, broadcastId)
                } catch (e: Exception) {
                    Log.e("StreamService", "Failed to transition to live", e)
                }
            }
        }
    }

    override fun onConnectionFailed(reason: String) {
        overlayManager.updateStatus("Connection Failed")
        if (rtmpDisplay.isStreaming) {
            rtmpDisplay.stopStream()
        }
    }

    override fun onNewBitrate(bitrate: Long) {
        if (rtmpDisplay.isStreaming) {
            rtmpDisplay.setVideoBitrateOnFly(bitrate.toInt())
        }
    }

    override fun onDisconnect() {
        overlayManager.updateStatus("Disconnected")
    }

    override fun onAuthError() {
        overlayManager.updateStatus("Auth Error")
    }

    override fun onAuthSuccess() {
        // Auth success
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "stream_channel",
                "Livestream Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "stream_channel")
            .setContentTitle("Livestream Controller")
            .setContentText("Screen capture is active")
            //.setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }
}
