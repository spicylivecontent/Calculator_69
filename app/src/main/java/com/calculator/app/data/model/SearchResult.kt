package com.calculator.app.data.model

data class SearchResult(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val duration: String?,
    val detailUrl: String,
    val sourceSite: String = "eporner",
    /** Verified direct media URL when the result has passed the stream availability check. */
    val streamUrl: String? = null
)
