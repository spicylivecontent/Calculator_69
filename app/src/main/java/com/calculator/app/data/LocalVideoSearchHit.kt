package com.calculator.app.data

import androidx.room.ColumnInfo

/** Search result from the private local full-text index. */
data class LocalVideoSearchHit(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
    val sourceSite: String,
    val detailPageUrl: String?,
    val tags: String,
    val quality: String?,
    @ColumnInfo(name = "relevance") val relevance: Double,
    @ColumnInfo(name = "snippet") val snippet: String
)
