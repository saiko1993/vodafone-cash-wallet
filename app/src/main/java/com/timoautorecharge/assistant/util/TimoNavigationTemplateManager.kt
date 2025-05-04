package com.timoautorecharge.assistant.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.timoautorecharge.assistant.data.model.Settings
import com.timoautorecharge.assistant.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.InputStreamReader

/**
 * Manages navigation templates for the Timo app.
 * Provides functionality to load templates from assets and update settings.
 */
class TimoNavigationTemplateManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    /**
     * Loads Timo navigation templates from assets and returns them as a TimoNavigationTemplates object.
     * 
     * @return TimoNavigationTemplates object containing the templates
     */
    fun loadTemplatesFromAssets(): TimoNavigationTemplates {
        val inputStream = context.assets.open("timo_navigation_templates.json")
        val reader = InputStreamReader(inputStream)
        return Gson().fromJson(reader, TimoNavigationTemplates::class.java)
    }
    
    /**
     * Updates the settings with the selected template.
     * 
     * @param templateIndex The index of the template to use
     */
    suspend fun applyTemplate(templateIndex: Int) {
        val templates = loadTemplatesFromAssets()
        if (templateIndex >= 0 && templateIndex < templates.navigationTemplates.size) {
            val template = templates.navigationTemplates[templateIndex]
            val settings = settingsRepository.getSettings().first()
            
            settingsRepository.updateSettings(
                settings.copy(
                    timoPackageName = template.packageName,
                    rechargeButtonId = template.rechargeButtonId,
                    timoIdInputId = template.timoIdInputId,
                    coinQuantityInputId = template.coinQuantityInputId,
                    confirmButtonId = template.confirmButtonId,
                    navigationDelayMs = template.navigationDelayMs,
                    maxNavigationRetries = template.maxRetries
                )
            )
        }
    }
    
    /**
     * Gets the names of all available templates.
     * 
     * @return List of template names
     */
    fun getTemplateNames(): List<String> {
        return loadTemplatesFromAssets().navigationTemplates.map { it.name }
    }
    
    /**
     * Gets the descriptions of all available templates.
     * 
     * @return List of template descriptions
     */
    fun getTemplateDescriptions(): List<String> {
        return loadTemplatesFromAssets().navigationTemplates.map { it.description }
    }
    
    /**
     * Gets the current navigation template settings.
     * 
     * @return Current navigation settings
     */
    fun getCurrentNavigationSettings(): TimoNavigationTemplate {
        val settings = runBlocking { settingsRepository.getSettingsSync() }
        
        return TimoNavigationTemplate(
            name = "Current Settings",
            description = "Current navigation settings from database",
            packageName = settings.timoPackageName,
            rechargeButtonId = settings.rechargeButtonId,
            timoIdInputId = settings.timoIdInputId,
            coinQuantityInputId = settings.coinQuantityInputId,
            confirmButtonId = settings.confirmButtonId,
            navigationDelayMs = settings.navigationDelayMs,
            maxRetries = settings.maxNavigationRetries,
            additionalSteps = emptyList()
        )
    }
}

/**
 * Data class representing the Timo navigation templates loaded from JSON.
 */
data class TimoNavigationTemplates(
    @SerializedName("navigation_templates")
    val navigationTemplates: List<TimoNavigationTemplate>
)

/**
 * Data class representing a single Timo navigation template.
 */
data class TimoNavigationTemplate(
    val name: String,
    val description: String,
    val packageName: String,
    val rechargeButtonId: String,
    val timoIdInputId: String,
    val coinQuantityInputId: String,
    val confirmButtonId: String,
    val navigationDelayMs: Long,
    val maxRetries: Int,
    val additionalSteps: List<NavigationStep>
)

/**
 * Data class representing a navigation step.
 */
data class NavigationStep(
    val name: String,
    val type: String, // "click", "input", "wait"
    val targetId: String,
    val value: String? = null,
    val delayAfterMs: Long = 500
)
