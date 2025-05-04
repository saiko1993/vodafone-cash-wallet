package com.timoautorecharge.assistant.data.repository

import com.timoautorecharge.assistant.data.database.UiElementRegistryDao
import com.timoautorecharge.assistant.data.model.UiElementInfo
import com.timoautorecharge.assistant.data.model.UiElementRegistry
import kotlinx.coroutines.flow.Flow

class UiElementRegistryRepository(
    private val uiElementRegistryDao: UiElementRegistryDao
) {
    fun getAllElements(): Flow<List<UiElementRegistry>> {
        return uiElementRegistryDao.getAllElements()
    }
    
    fun getElementByKey(keyName: String): Flow<UiElementRegistry?> {
        return uiElementRegistryDao.getElementByKey(keyName)
    }
    
    fun getElementsByAppName(appName: String): Flow<List<UiElementRegistry>> {
        return uiElementRegistryDao.getElementsByAppName(appName)
    }
    
    suspend fun saveElement(
        keyName: String,
        elementInfo: UiElementInfo,
        appName: String = "",
        screenName: String = "",
        description: String = ""
    ) {
        val element = UiElementRegistry(
            keyName = keyName,
            elementInfo = elementInfo.copy(keyName = keyName),
            appName = appName,
            screenName = screenName,
            description = description
        )
        uiElementRegistryDao.insertElement(element)
    }
    
    suspend fun updateElement(element: UiElementRegistry) {
        uiElementRegistryDao.updateElement(element)
    }
    
    suspend fun deleteElement(keyName: String) {
        uiElementRegistryDao.deleteElementByKey(keyName)
    }
}
