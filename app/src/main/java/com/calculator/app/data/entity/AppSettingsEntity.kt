package com.calculator.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Enkel key-value store för appinställningar (PIN sparas här krypterat senare).
 */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val key: String,
    val value: String
)
