package com.calculator.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Key-value storage for non-secret app settings. PINs are stored separately via Android Keystore. */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val key: String,
    val value: String
)
