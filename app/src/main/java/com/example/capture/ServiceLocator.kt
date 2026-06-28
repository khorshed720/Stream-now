package com.example.capture

import android.content.Intent

object ServiceLocator {
    var rtmpUrl: String? = null
    var resultCode: Int = 0
    var data: Intent? = null
    var youtubeToken: String? = null
    var broadcastId: String? = null
    var resolution: String = "1080p"
    var audioSource: String = "Internal"
    var bannerBytes: ByteArray? = null
    var bannerInterval: Int = 10
}
