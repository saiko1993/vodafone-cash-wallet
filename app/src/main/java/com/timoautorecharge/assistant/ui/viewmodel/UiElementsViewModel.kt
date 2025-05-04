package com.timoautorecharge.assistant.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.timoautorecharge.assistant.data.model.UiElementInfo
import com.timoautorecharge.assistant.data.model.UiElementRegistry
import com.timoautorecharge.assistant.data.repository.UiElementRegistryRepository
import com.timoautorecharge.assistant.service.ElementSelectorService
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

/**
 * ViewModel for managing UI elements used in automation.
 * Provides methods for capturing, updating, and deleting UI elements.
 */
class UiElementsViewModel(
    private val application: Application,
    private val uiElementRegistryRepository: UiElementRegistryRepository
) : AndroidViewModel(application) {

    val uiElements: StateFlow<List<UiElementRegistry>> = 
        uiElementRegistryRepository.getAllElements()
            .stateIn(
                viewModelScope,
                SharingStarted.Lazily,
                emptyList()
            )
    
    /**
     * Captures a UI element using the floating selector.
     * 
     * @param keyName Unique identifier for the element
     * @param elementType Type of element to capture
     * @param appName Name of the app containing the element
     * @param screenName Name of the screen containing the element
     * @param description Description of the element's purpose
     */
    fun captureElement(
        keyName: String, 
        elementType: UiElementInfo.ElementType,
        appName: String = "",
        screenName: String = "",
        description: String = ""
    ) {
        // Check if we have overlay permission
        if (!ElementSelectorService.hasOverlayPermission(application)) {
            // Request overlay permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${application.packageName}")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                application.startActivity(intent)
            }
            return
        }
        
        // Create a temporary element in the registry
        viewModelScope.launch {
            // Create a placeholder element that will be updated by the selector service
            val placeholderElement = UiElementRegistry(
                keyName = keyName,
                elementInfo = UiElementInfo(
                    keyName = keyName,
                    elementType = elementType
                ),
                appName = appName,
                screenName = screenName,
                description = description
            )
            
            // Save the placeholder
            uiElementRegistryRepository.updateElement(placeholderElement)
            
            // Launch the element selector service
            ElementSelectorService.startSelector(
                application,
                elementType,
                appName, // Will be updated by the service if empty
                keyName
            )
        }
    }
    
    /**
     * Updates an existing UI element in the registry.
     * 
     * @param element The updated element
     */
    fun updateElement(element: UiElementRegistry) {
        viewModelScope.launch {
            uiElementRegistryRepository.updateElement(element)
        }
    }
    
    /**
     * Deletes a UI element from the registry.
     * 
     * @param keyName The key of the element to delete
     */
    fun deleteElement(keyName: String) {
        viewModelScope.launch {
            uiElementRegistryRepository.deleteElement(keyName)
        }
    }
    
    /**
     * Gets a UI element by its key.
     * 
     * @param keyName The key of the element to retrieve
     * @return A Flow of the element, or null if not found
     */
    fun getElementByKey(keyName: String) = 
        uiElementRegistryRepository.getElementByKey(keyName)
    
    /**
     * Gets all UI elements for a specific app.
     * 
     * @param appName The name of the app
     * @return A Flow of elements for the app
     */
    fun getElementsByAppName(appName: String) = 
        uiElementRegistryRepository.getElementsByAppName(appName)
}
