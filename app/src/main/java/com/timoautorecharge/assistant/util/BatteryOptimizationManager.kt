package com.timoautorecharge.assistant.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.timoautorecharge.assistant.data.model.Log
import com.timoautorecharge.assistant.data.repository.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

/**
 * Manages battery optimization exemption requests and checks.
 * This is crucial for ensuring the app can run reliably in the background.
 */
class BatteryOptimizationManager(private val context: Context) {
    
    private val logRepository: LogRepository by inject(LogRepository::class.java)
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Checks if the app is exempt from battery optimizations.
     * 
     * @return True if the app is exempt, false otherwise
     */
    fun isIgnoringBatteryOptimizations(): Boolean {
        val packageName = context.packageName
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            // Battery optimizations were introduced in Android M (API 23)
            true
        }
    }
    
    /**
     * Creates an intent to request battery optimization exemption.
     * This should be used to start an activity from the UI.
     * 
     * @return Intent to request battery optimization exemption
     */
    fun createBatteryOptimizationExemptionIntent(): Intent {
        val packageName = context.packageName
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:$packageName")
            }
        } else {
            // Fallback for older Android versions
            Intent(Settings.ACTION_SETTINGS)
        }
    }
    
    /**
     * Checks if the app is exempt from battery optimizations and logs the result.
     * Should be called periodically to ensure the app remains exempt.
     */
    fun checkBatteryOptimizationStatus() {
        scope.launch {
            val isExempt = isIgnoringBatteryOptimizations()
            val message = if (isExempt) {
                "App is exempt from battery optimizations"
            } else {
                "App is subject to battery optimizations, which may affect background operation"
            }
            
            logRepository.addLog(
                Log(
                    message = message,
                    timestamp = System.currentTimeMillis(),
                    level = if (isExempt) Log.Level.INFO else Log.Level.WARNING,
                    source = "BatteryOptimizationManager"
                )
            )
        }
    }
    
    /**
     * Checks if the device has any manufacturer-specific battery optimization settings
     * that might affect the app's background operation.
     * 
     * @return True if manufacturer-specific settings are detected, false otherwise
     */
    fun hasManufacturerSpecificBatterySettings(): Boolean {
        val manufacturer = Build.MANUFACTURER.toLowerCase()
        
        // Check for known manufacturers with aggressive battery optimizations
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> true
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> true
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> true
            manufacturer.contains("vivo") -> true
            manufacturer.contains("samsung") -> true
            else -> false
        }
    }
    
    /**
     * Creates an intent to open manufacturer-specific battery optimization settings.
     * This is needed because some manufacturers have their own battery optimization
     * systems that can affect background services.
     * 
     * @return Intent to open manufacturer-specific settings, or null if not applicable
     */
    fun createManufacturerSpecificSettingsIntent(): Intent? {
        val manufacturer = Build.MANUFACTURER.toLowerCase()
        val packageName = context.packageName
        
        return when {
            // Xiaomi/Redmi
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                }
            }
            
            // Huawei/Honor
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                    )
                }
            }
            
            // OPPO/Realme/OnePlus
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                }
            }
            
            // Vivo
            manufacturer.contains("vivo") -> {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                }
            }
            
            // Samsung
            manufacturer.contains("samsung") -> {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    )
                }
            }
            
            // Default to standard battery optimization settings
            else -> null
        }
    }
}
