package com.calculator.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_presets")
data class SearchPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                 // t.ex. "My perfect morning"
    val query: String,                // den sparade sökfrasen
    val negativeKeywords: String = "", // kommaseparerade
    val createdAt: Long = System.currentTimeMillis()
)
