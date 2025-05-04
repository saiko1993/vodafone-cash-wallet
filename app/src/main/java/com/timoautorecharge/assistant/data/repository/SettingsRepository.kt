package com.timoautorecharge.assistant.data.repository

import com.timoautorecharge.assistant.data.database.SettingsDao
import com.timoautorecharge.assistant.data.model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val settingsDao: SettingsDao) {
    
    fun getSettings(): Flow<Settings> {
        return settingsDao.getSettings().map { it ?: createDefaultSettings() }
    }
    
    suspend fun getSettingsSync(): Settings {
        return settingsDao.getSettingsSync() ?: createDefaultSettings()
    }
    
    suspend fun updateSettings(settings: Settings) {
        settingsDao.insertSettings(settings)
    }
    
    private fun createDefaultSettings(): Settings {
        return Settings(
            vodafoneWalletNumber = "01XXXXXXXXX", // Default placeholder
            adminPhoneNumber = "01XXXXXXXXX"      // Default placeholder
        )
    }
}
