package com.timoautorecharge.assistant.util

import com.timoautorecharge.assistant.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class CoinCalculator(private val settingsRepository: SettingsRepository) {
    
    fun calculateCoins(amount: Double): Int {
        val settings = runBlocking { settingsRepository.getSettingsSync() }
        
        return when {
            amount < 500 -> (amount / settings.tier1Rate * 100).toInt()
            amount < 7500 -> (amount / settings.tier2Rate * 100).toInt()
            else -> (amount / settings.tier3Rate * 100).toInt()
        }
    }
    
    fun calculateAmount(coins: Int): Double {
        val settings = runBlocking { settingsRepository.getSettingsSync() }
        
        return when {
            coins < 50000 -> coins * settings.tier1Rate / 100
            coins < 750000 -> coins * settings.tier2Rate / 100
            else -> coins * settings.tier3Rate / 100
        }
    }
}
