package com.calculator.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Regler för "Never show again".
 * type kan vara: "keyword", "tag", "performer", "video", "site"
 */
@Entity(tableName = "dislike_rules")
data class DislikeRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,                 // keyword / tag / performer / video / site
    val value: String,                // det som ska filtreras bort
    val createdAt: Long = System.currentTimeMillis()
)
