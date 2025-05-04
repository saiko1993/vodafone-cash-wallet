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
 * Manages SMS templates for the application.
 * Provides functionality to load templates from assets and update settings.
 */
class SmsTemplateManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    /**
     * Loads SMS templates from assets and returns them as a SmsTemplates object.
     * 
     * @return SmsTemplates object containing the templates
     */
    fun loadTemplatesFromAssets(): SmsTemplates {
        val inputStream = context.assets.open("sms_templates.json")
        val reader = InputStreamReader(inputStream)
        return Gson().fromJson(reader, SmsTemplates::class.java)
    }
    
    /**
     * Updates the settings with the selected template.
     * 
     * @param templateIndex The index of the template to use
     */
    suspend fun applyTemplate(templateIndex: Int) {
        val templates = loadTemplatesFromAssets()
        if (templateIndex >= 0 && templateIndex < templates.vodafoneCashTemplates.size) {
            val template = templates.vodafoneCashTemplates[templateIndex]
            val settings = settingsRepository.getSettings().first()
            
            settingsRepository.updateSettings(
                settings.copy(
                    vodafoneSmsTemplate = template.pattern,
                    vodafoneSenderNumbers = templates.senderNumbers,
                    smsMatchByLastDigits = templates.matchByLastDigits,
                    paymentTimeoutMinutes = templates.paymentTimeoutMinutes
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
        return loadTemplatesFromAssets().vodafoneCashTemplates.map { it.name }
    }
    
    /**
     * Gets the descriptions of all available templates.
     * 
     * @return List of template descriptions
     */
    fun getTemplateDescriptions(): List<String> {
        return loadTemplatesFromAssets().vodafoneCashTemplates.map { it.description }
    }
}

/**
 * Data class representing the SMS templates loaded from JSON.
 */
data class SmsTemplates(
    @SerializedName("vodafone_cash_templates")
    val vodafoneCashTemplates: List<SmsTemplate>,
    
    @SerializedName("sender_numbers")
    val senderNumbers: List<String>,
    
    @SerializedName("match_by_last_digits")
    val matchByLastDigits: Int,
    
    @SerializedName("payment_timeout_minutes")
    val paymentTimeoutMinutes: Int
)

/**
 * Data class representing a single SMS template.
 */
data class SmsTemplate(
    val name: String,
    val pattern: String,
    val description: String
)
