package com.timoautorecharge.assistant.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.timoautorecharge.assistant.service.ConnectionTestService
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ConnectionViewModel(
    private val application: Application,
    private val connectionTestService: ConnectionTestService
) : AndroidViewModel(application) {
    
    val internetStatus: StateFlow<ConnectionTestService.ConnectionStatus> = 
        connectionTestService.internetStatus
    
    val whatsAppStatus: StateFlow<ConnectionTestService.ConnectionStatus> = 
        connectionTestService.whatsAppStatus
    
    val timoStatus: StateFlow<ConnectionTestService.ConnectionStatus> = 
        connectionTestService.timoStatus
    
    val smsStatus: StateFlow<ConnectionTestService.ConnectionStatus> = 
        connectionTestService.smsStatus
    
    fun testAllConnections() {
        viewModelScope.launch {
            connectionTestService.testAllConnections()
        }
    }
    
    fun fixInternetConnection() {
        // Open network settings
        val intent = Intent(AndroidSettings.ACTION_WIRELESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        application.startActivity(intent)
    }
    
    fun fixWhatsAppConnection() {
        // Open accessibility settings
        val intent = Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        application.startActivity(intent)
    }
    
    fun fixTimoConnection() {
        // Open accessibility settings
        val intent = Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        application.startActivity(intent)
    }
    
    fun fixSmsConnection() {
        // Open app permissions
        val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", application.packageName, null)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        application.startActivity(intent)
    }
}
