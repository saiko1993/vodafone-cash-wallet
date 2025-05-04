package com.timoautorecharge.assistant.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.timoautorecharge.assistant.data.model.Log
import com.timoautorecharge.assistant.data.repository.LogRepository
import com.timoautorecharge.assistant.ui.theme.TimoAutoRechargeTheme
import com.timoautorecharge.assistant.util.BatteryOptimizationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    
    private val logRepository: LogRepository by inject()
    private lateinit var batteryOptimizationManager: BatteryOptimizationManager
    
    // Register for activity result to handle permission requests
    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Check if battery optimization is now disabled
        if (batteryOptimizationManager.isIgnoringBatteryOptimizations()) {
            CoroutineScope(Dispatchers.IO).launch {
                logRepository.addLog(
                    Log(
                        message = "Battery optimization exemption granted",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.INFO,
                        source = "MainActivity"
                    )
                )
            }
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                logRepository.addLog(
                    Log(
                        message = "Battery optimization exemption not granted",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.WARNING,
                        source = "MainActivity"
                    )
                )
            }
        }
    }
    
    // Register for activity result to handle accessibility service settings
    private val accessibilitySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Just log that we returned from accessibility settings
        CoroutineScope(Dispatchers.IO).launch {
            logRepository.addLog(
                Log(
                    message = "Returned from accessibility settings",
                    timestamp = System.currentTimeMillis(),
                    level = Log.Level.INFO,
                    source = "MainActivity"
                )
            )
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize battery optimization manager
        batteryOptimizationManager = BatteryOptimizationManager(this)
        
        // Check battery optimization status
        batteryOptimizationManager.checkBatteryOptimizationStatus()
        
        setContent {
            TimoAutoRechargeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // State to track if we need to show battery optimization dialog
                    var showBatteryOptimizationRequest by remember { 
                        mutableStateOf(!batteryOptimizationManager.isIgnoringBatteryOptimizations()) 
                    }
                    
                    // State to track if we need to show manufacturer-specific settings dialog
                    var showManufacturerSettings by remember { 
                        mutableStateOf(batteryOptimizationManager.hasManufacturerSpecificBatterySettings()) 
                    }
                    
                    // Request battery optimization exemption if needed
                    LaunchedEffect(key1 = showBatteryOptimizationRequest) {
                        if (showBatteryOptimizationRequest) {
                            try {
                                batteryOptimizationLauncher.launch(
                                    batteryOptimizationManager.createBatteryOptimizationExemptionIntent()
                                )
                                showBatteryOptimizationRequest = false
                            } catch (e: Exception) {
                                logRepository.addLog(
                                    Log(
                                        message = "Failed to launch battery optimization settings: ${e.message}",
                                        timestamp = System.currentTimeMillis(),
                                        level = Log.Level.ERROR,
                                        source = "MainActivity"
                                    )
                                )
                            }
                        }
                    }
                    
                    // Show manufacturer-specific settings if needed
                    LaunchedEffect(key1 = showManufacturerSettings) {
                        if (showManufacturerSettings) {
                            try {
                                val intent = batteryOptimizationManager.createManufacturerSpecificSettingsIntent()
                                if (intent != null) {
                                    batteryOptimizationLauncher.launch(intent)
                                }
                                showManufacturerSettings = false
                            } catch (e: Exception) {
                                logRepository.addLog(
                                    Log(
                                        message = "Failed to launch manufacturer settings: ${e.message}",
                                        timestamp = System.currentTimeMillis(),
                                        level = Log.Level.ERROR,
                                        source = "MainActivity"
                                    )
                                )
                            }
                        }
                    }
                    
                    // Check if accessibility services are enabled
                    LaunchedEffect(key1 = Unit) {
                        if (!isAccessibilityServiceEnabled()) {
                            try {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                accessibilitySettingsLauncher.launch(intent)
                            } catch (e: Exception) {
                                logRepository.addLog(
                                    Log(
                                        message = "Failed to launch accessibility settings: ${e.message}",
                                        timestamp = System.currentTimeMillis(),
                                        level = Log.Level.ERROR,
                                        source = "MainActivity"
                                    )
                                )
                            }
                        }
                    }
                    
                    // Main screen content
                    MainScreen()
                }
            }
        }
    }
    
    /**
     * Checks if the required accessibility services are enabled.
     * This is a simplified check - in a real app, you would check for specific services.
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityEnabled = try {
            Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (e: Settings.SettingNotFoundException) {
            0
        }
        
        return accessibilityEnabled == 1
    }
    
    override fun onResume() {
        super.onResume()
        
        // Check battery optimization status on resume
        batteryOptimizationManager.checkBatteryOptimizationStatus()
    }
}
