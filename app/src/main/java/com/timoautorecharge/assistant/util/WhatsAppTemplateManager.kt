package com.timoautorecharge.assistant.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.timoautorecharge.assistant.data.model.Settings
import com.timoautorecharge.assistant.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.InputStreamReader
import java.util.regex.Pattern

/**
 * Manages WhatsApp message templates for the application.
 * Provides functionality to load templates from assets and parse messages.
 */
class WhatsAppTemplateManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    /**
     * Loads WhatsApp templates from assets and returns them as a WhatsAppTemplates object.
     * 
     * @return WhatsAppTemplates object containing the templates
     */
    fun loadTemplatesFromAssets(): WhatsAppTemplates {
        val inputStream = context.assets.open("whatsapp_templates.json")
        val reader = InputStreamReader(inputStream)
        return Gson().fromJson(reader, WhatsAppTemplates::class.java)
    }
    
    /**
     * Updates the settings with the selected template.
     * 
     * @param templateIndex The index of the template to use
     */
    suspend fun applyTemplate(templateIndex: Int) {
        val templates = loadTemplatesFromAssets()
        if (templateIndex >= 0 && templateIndex < templates.rechargeRequestTemplates.size) {
            val template = templates.rechargeRequestTemplates[templateIndex]
            val settings = settingsRepository.getSettings().first()
            
            settingsRepository.updateSettings(
                settings.copy(
                    whatsAppRechargeRequestPattern = template.pattern
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
        return loadTemplatesFromAssets().rechargeRequestTemplates.map { it.name }
    }
    
    /**
     * Gets the descriptions of all available templates.
     * 
     * @return List of template descriptions
     */
    fun getTemplateDescriptions(): List<String> {
        return loadTemplatesFromAssets().rechargeRequestTemplates.map { it.description }
    }
    
    /**
     * Parses a WhatsApp message to extract recharge request details.
     * 
     * @param message The message to parse
     * @return Pair containing Timo ID and amount, or null if not a valid recharge request
     */
    fun parseRechargeRequest(message: String): Pair<String, Double>? {
        val settings = runBlocking { settingsRepository.getSettingsSync() }
        
        // Try with the configured pattern first
        val configuredPattern = Pattern.compile(
            settings.whatsAppRechargeRequestPattern,
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )
        
        val configuredMatcher = configuredPattern.matcher(message)
        if (configuredMatcher.matches()) {
            try {
                val amount = configuredMatcher.group(1)?.toDoubleOrNull() ?: return null
                val timoId = configuredMatcher.group(2) ?: return null
                return Pair(timoId, amount)
            } catch (e: Exception) {
                // If there's an error with the configured pattern, fall back to default patterns
            }
        }
        
        // Fall back to default patterns from the templates file
        val templates = loadTemplatesFromAssets()
        for (template in templates.rechargeRequestTemplates) {
            val pattern = Pattern.compile(template.pattern, Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
            val matcher = pattern.matcher(message)
            
            if (matcher.matches()) {
                try {
                    val amount = matcher.group(1)?.toDoubleOrNull() ?: continue
                    val timoId = matcher.group(2) ?: continue
                    return Pair(timoId, amount)
                } catch (e: Exception) {
                    // Try next pattern
                    continue
                }
            }
        }
        
        return null
    }
}

/**
 * Data class representing the WhatsApp templates loaded from JSON.
 */
data class WhatsAppTemplates(
    @SerializedName("recharge_request_templates")
    val rechargeRequestTemplates: List<WhatsAppTemplate>
)

/**
 * Data class representing a single WhatsApp template.
 */
data class WhatsAppTemplate(
    val name: String,
    val pattern: String,
    val description: String
)
