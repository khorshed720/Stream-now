package com.example.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

interface YouTubeApi {

    @POST("youtube/v3/liveBroadcasts")
    suspend fun createBroadcast(
        @Header("Authorization") authHeader: String,
        @Query("part") part: String = "snippet,status,contentDetails",
        @Body broadcast: LiveBroadcast
    ): LiveBroadcast

    @POST("youtube/v3/liveStreams")
    suspend fun createStream(
        @Header("Authorization") authHeader: String,
        @Query("part") part: String = "snippet,cdn,status",
        @Body stream: LiveStream
    ): LiveStream

    @POST("youtube/v3/liveBroadcasts/bind")
    suspend fun bindBroadcast(
        @Header("Authorization") authHeader: String,
        @Query("id") broadcastId: String,
        @Query("part") part: String = "id,contentDetails",
        @Query("streamId") streamId: String
    ): LiveBroadcast

    @POST("youtube/v3/liveBroadcasts/transition")
    suspend fun transitionBroadcast(
        @Header("Authorization") authHeader: String,
        @Query("broadcastStatus") status: String, // testing, live, complete
        @Query("id") broadcastId: String,
        @Query("part") part: String = "id,status"
    ): LiveBroadcast

    @GET("youtube/v3/liveStreams")
    suspend fun getStream(
        @Header("Authorization") authHeader: String,
        @Query("part") part: String = "status",
        @Query("id") streamId: String
    ): LiveStreamListResponse
    @POST("upload/youtube/v3/thumbnails/set?uploadType=media")
    suspend fun setThumbnail(
        @Header("Authorization") authHeader: String,
        @Query("videoId") videoId: String,
        @Header("Content-Type") contentType: String = "image/jpeg",
        @Body body: okhttp3.RequestBody
    ): okhttp3.ResponseBody
}
