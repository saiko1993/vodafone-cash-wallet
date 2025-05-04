package com.timoautorecharge.assistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timoautorecharge.assistant.data.model.Settings
import com.timoautorecharge.assistant.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    
    val settings: StateFlow<Settings> = settingsRepository
        .getSettings()
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            Settings(
                vodafoneWalletNumber = "",
                adminPhoneNumber = ""
            )
        )
    
    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(settings)
        }
    }
    
    fun updateVodafoneWalletNumber(number: String) {
        viewModelScope.launch {
            val currentSettings = settings.value
            settingsRepository.updateSettings(currentSettings.copy(vodafoneWalletNumber = number))
        }
    }
    
    fun updateAdminPhoneNumber(number: String) {
        viewModelScope.launch {
            val currentSettings = settings.value
            settingsRepository.updateSettings(currentSettings.copy(adminPhoneNumber = number))
        }
    }
    
    fun updateTierRates(
        tier1Rate: Double, 
        tier2Rate: Double, 
        tier3Rate: Double,
        tier1Threshold: Int,
        tier2Threshold: Int
    ) {
        viewModelScope.launch {
            val currentSettings = settings.value
            settingsRepository.updateSettings(
                currentSettings.copy(
                    tier1Rate = tier1Rate,
                    tier2Rate = tier2Rate,
                    tier3Rate = tier3Rate,
                    tier1Threshold = tier1Threshold,
                    tier2Threshold = tier2Threshold
                )
            )
        }
    }
    
    fun updateSmsSettings(
        template: String,
        senderNumbers: List<String>,
        matchByLastDigits: Int,
        paymentTimeoutMinutes: Int
    ) {
        viewModelScope.launch {
            val currentSettings = settings.value
            settingsRepository.updateSettings(
                currentSettings.copy(
                    vodafoneSmsTemplate = template,
                    vodafoneSenderNumbers = senderNumbers,
                    smsMatchByLastDigits = matchByLastDigits,
                    paymentTimeoutMinutes = paymentTimeoutMinutes
                )
            )
        }
    }
    
    fun updateWhatsAppSettings(
        rechargeRequestPattern: String,
        paymentInstructionsTemplate: String,
        paymentConfirmationTemplate: String,
        rechargeConfirmationTemplate: String
    ) {
        viewModelScope.launch {
            val currentSettings = settings.value
            settingsRepository.updateSettings(
                currentSettings.copy(
                    whatsappRechargeRequestPattern = rechargeRequestPattern,
                    whatsappPaymentInstructionsTemplate = paymentInstructionsTemplate,
                    whatsappPaymentConfirmationTemplate = paymentConfirmationTemplate,
                    whatsappRechargeConfirmationTemplate = rechargeConfirmationTemplate
                )
            )
        }
    }
    
    fun updateTimoAppSettings(
        packageName: String,
        rechargeButtonId: String,
        timoIdInputId: String,
        coinQuantityInputId: String,
        confirmButtonId: String
    ) {
        viewModelScope.launch {
            val currentSettings = settings.value
            settingsRepository.updateSettings(
                currentSettings.copy(
                    timoPackageName = packageName,
                    rechargeButtonId = rechargeButtonId,
                    timoIdInputId = timoIdInputId,
                    coinQuantityInputId = coinQuantityInputId,
                    confirmButtonId = confirmButtonId
                )
            )
        }
    }
    
    fun updateNavigationSettings(
        navigationDelayMs: Long,
        maxNavigationRetries: Int,
        navigationTimeoutMs: Long
    ) {
        viewModelScope.launch {
            val currentSettings = settings.value
            settingsRepository.updateSettings(
                currentSettings.copy(
                    navigationDelayMs = navigationDelayMs,
                    maxNavigationRetries = maxNavigationRetries,
                    navigationTimeoutMs = navigationTimeoutMs
                )
            )
        }
    }
}
