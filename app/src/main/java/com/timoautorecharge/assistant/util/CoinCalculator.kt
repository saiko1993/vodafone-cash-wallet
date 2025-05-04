package com.timoautorecharge.assistant.util

import com.timoautorecharge.assistant.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class CoinCalculator(private val settingsRepository: SettingsRepository) {
    
    fun calculateCoins(amount: Double): Int {
        val settings = runBlocking { settingsRepository.getSettingsSync() }
        
        return when {
            amount <= 0 -> 0
            // Use configurable tier thresholds
            amount < (settings.tier1Threshold * settings.tier1Rate / 100) -> 
                (amount / settings.tier1Rate * 100).toInt()
            amount < (settings.tier2Threshold * settings.tier2Rate / 100) -> 
                (amount / settings.tier2Rate * 100).toInt()
            else -> 
                (amount / settings.tier3Rate * 100).toInt()
        }
    }
    
    fun calculateAmount(coins: Int): Double {
        val settings = runBlocking { settingsRepository.getSettingsSync() }
        
        return when {
            coins <= 0 -> 0.0
            // Use configurable tier thresholds
            coins <= settings.tier1Threshold -> 
                coins * settings.tier1Rate / 100
            coins <= settings.tier2Threshold -> 
                coins * settings.tier2Rate / 100
            else -> 
                coins * settings.tier3Rate / 100
        }
    }
}
