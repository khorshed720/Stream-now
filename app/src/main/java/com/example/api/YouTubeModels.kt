package com.example.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LiveBroadcastListResponse(val items: List<LiveBroadcast>? = null)

@JsonClass(generateAdapter = true)
data class LiveBroadcast(
    val id: String? = null,
    val snippet: BroadcastSnippet? = null,
    val status: BroadcastStatus? = null,
    val contentDetails: BroadcastContentDetails? = null
)

@JsonClass(generateAdapter = true)
data class BroadcastSnippet(
    val title: String? = null,
    val description: String? = null,
    val scheduledStartTime: String? = null
)

@JsonClass(generateAdapter = true)
data class BroadcastStatus(
    val lifeCycleStatus: String? = null,
    val privacyStatus: String? = null,
    val recordingStatus: String? = null
)

@JsonClass(generateAdapter = true)
data class BroadcastContentDetails(
    val boundStreamId: String? = null,
    val monitorStream: MonitorStreamInfo? = null,
    val enableAutoStart: Boolean? = null,
    val enableAutoStop: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class MonitorStreamInfo(
    val enableMonitorStream: Boolean? = null,
    val broadcastStreamDelayMs: Int? = null,
    val embedHtml: String? = null
)

@JsonClass(generateAdapter = true)
data class LiveStreamListResponse(val items: List<LiveStream>? = null)

@JsonClass(generateAdapter = true)
data class LiveStream(
    val id: String? = null,
    val snippet: StreamSnippet? = null,
    val cdn: CdnSettings? = null,
    val status: StreamStatus? = null
)

@JsonClass(generateAdapter = true)
data class StreamSnippet(
    val title: String? = null
)

@JsonClass(generateAdapter = true)
data class CdnSettings(
    val ingestionType: String? = null,
    val ingestionInfo: IngestionInfo? = null,
    val resolution: String? = null,
    val frameRate: String? = null
)

@JsonClass(generateAdapter = true)
data class IngestionInfo(
    val streamName: String? = null,
    val ingestionAddress: String? = null,
    val backupIngestionAddress: String? = null
)

@JsonClass(generateAdapter = true)
data class StreamStatus(
    val streamStatus: String? = null
)
