package com.calculator.app.data.model

data class SearchResult(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val duration: String?,
    val detailUrl: String,
    val sourceSite: String = "eporner"
)
