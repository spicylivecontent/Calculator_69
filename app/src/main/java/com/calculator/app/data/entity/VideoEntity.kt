package com.calculator.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val id: String,                    // Unik ID (t.ex. site_videoId)
    val title: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Int? = null,
    val sourceSite: String,                        // t.ex. "eporner", "xvideos"
    val detailPageUrl: String? = null,
    val streamUrl: String? = null,                 // Direkt stream-länk om vi har den
    val tags: String = "",                         // Kommaseparerade taggar
    val quality: String? = null,                   // "1080p", "720p" etc.
    val addedAt: Long = System.currentTimeMillis(),
    val lastWatchedAt: Long? = null
)
