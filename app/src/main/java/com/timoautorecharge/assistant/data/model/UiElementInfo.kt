package com.timoautorecharge.assistant.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Model class for UI element information captured by the floating selector.
 * Stores information about UI elements in other apps.
 */
@Parcelize
data class UiElementInfo(
    val elementId: String = "",
    val packageName: String = "",
    val elementType: ElementType = ElementType.BUTTON,
    val contentDescription: String = "",
    val text: String = "",
    val bounds: android.graphics.Rect = android.graphics.Rect(),
    val className: String = "",
    val keyName: String = "" // Unique identifier for this element in settings
) : Parcelable {
    @Parcelize
    enum class ElementType : Parcelable {
        BUTTON,
        INPUT_FIELD,
        TEXT_FIELD,
        CHECKBOX,
        RADIO_BUTTON,
        TOGGLE
    }
}
