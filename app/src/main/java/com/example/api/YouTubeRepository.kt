package com.example.api

import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class YouTubeRepository {

    private val api: YouTubeApi

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()

        api = retrofit.create(YouTubeApi::class.java)
    }

    suspend fun createAndBindBroadcast(
        token: String,
        title: String,
        description: String
    ): Pair<LiveBroadcast, LiveStream> {
        val authHeader = "Bearer $token"

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startTime = dateFormat.format(Date())

        // 1. Create Broadcast
        val broadcast = LiveBroadcast(
            snippet = BroadcastSnippet(
                title = title,
                description = description,
                scheduledStartTime = startTime
            ),
            status = BroadcastStatus(
                privacyStatus = "unlisted"
            )
        )
        val createdBroadcast = api.createBroadcast(authHeader, broadcast = broadcast)
        val broadcastId = createdBroadcast.id ?: throw Exception("Failed to create broadcast")

        // 2. Create Stream
        val stream = LiveStream(
            snippet = StreamSnippet(title = title),
            cdn = CdnSettings(
                ingestionType = "rtmp",
                resolution = "1080p",
                frameRate = "60fps"
            )
        )
        val createdStream = api.createStream(authHeader, stream = stream)
        val streamId = createdStream.id ?: throw Exception("Failed to create stream")

        // 3. Bind
        val boundBroadcast = api.bindBroadcast(
            authHeader = authHeader,
            broadcastId = broadcastId,
            streamId = streamId
        )

        return Pair(boundBroadcast, createdStream)
    }

    suspend fun waitForStreamActive(token: String, streamId: String): Boolean {
        val authHeader = "Bearer $token"
        // Poll up to 30 times (1 minute)
        for (i in 1..30) {
            try {
                val response = api.getStream(authHeader, streamId = streamId)
                val status = response.items?.firstOrNull()?.status?.streamStatus
                if (status == "active") {
                    return true
                }
            } catch (e: Exception) {
                // Ignore network errors while polling
            }
            delay(2000)
        }
        return false
    }

    suspend fun transitionToLive(token: String, broadcastId: String) {
        val authHeader = "Bearer $token"
        
        // Sometimes requires going to testing first depending on monitorStream settings,
        // but if enableMonitorStream is false, we can go straight to live or it auto starts.
        // The API says we must transition to 'testing', then 'live', if monitor stream is enabled.
        // We created it with default settings, let's try testing then live.
        try {
            api.transitionBroadcast(authHeader, status = "testing", broadcastId = broadcastId)
            delay(3000)
        } catch (e: Exception) {
            // It might fail if already testing or not ready
        }
        
        api.transitionBroadcast(authHeader, status = "live", broadcastId = broadcastId)
    }

    suspend fun endBroadcast(token: String, broadcastId: String) {
        val authHeader = "Bearer $token"
        try {
            api.transitionBroadcast(authHeader, status = "complete", broadcastId = broadcastId)
        } catch (e: Exception) {
            // Ignore errors on complete
        }
    }
}
