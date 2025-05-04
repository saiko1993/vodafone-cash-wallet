package com.timoautorecharge.assistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.timoautorecharge.assistant.data.database.Converters

/**
 * Entity to store registered UI elements for interaction with external apps.
 * Supports dynamic storage of multiple UI elements.
 */
@Entity(tableName = "ui_element_registry")
@TypeConverters(Converters::class)
data class UiElementRegistry(
    @PrimaryKey
    val keyName: String,
    val elementInfo: UiElementInfo,
    val appName: String = "",
    val screenName: String = "",
    val description: String = "",
    val lastModified: Long = System.currentTimeMillis()
)
