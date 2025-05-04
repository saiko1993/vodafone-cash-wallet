package com.timoautorecharge.assistant

import android.app.Application
import android.content.Intent
import android.os.Build
import com.timoautorecharge.assistant.data.database.AppDatabase
import com.timoautorecharge.assistant.data.repository.CustomerRepository
import com.timoautorecharge.assistant.data.repository.LogRepository
import com.timoautorecharge.assistant.data.repository.SettingsRepository
import com.timoautorecharge.assistant.data.repository.TransactionRepository
import com.timoautorecharge.assistant.service.AutoRechargeService
import com.timoautorecharge.assistant.service.SmsService
import com.timoautorecharge.assistant.service.TimoAccessibilityService
import com.timoautorecharge.assistant.service.TransactionCoordinatorService
import com.timoautorecharge.assistant.service.WhatsAppAccessibilityService
import com.timoautorecharge.assistant.service.WhatsAppService
import com.timoautorecharge.assistant.ui.viewmodel.MainViewModel
import com.timoautorecharge.assistant.ui.viewmodel.SettingsViewModel
import com.timoautorecharge.assistant.util.CoinCalculator
import com.timoautorecharge.assistant.util.NotificationService
import com.timoautorecharge.assistant.util.SmsTemplateManager
import com.timoautorecharge.assistant.util.TimoNavigationTemplateManager
import com.timoautorecharge.assistant.util.WhatsAppTemplateManager
import com.timoautorecharge.assistant.worker.RechargeWorker
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.context.startKoin
import org.koin.dsl.module
import timber.log.Timber

/**
 * Main application class that initializes dependencies and starts the background service.
 */
class TimoAutoRechargeApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        // Initialize Koin for dependency injection
        startKoin {
            androidContext(this@TimoAutoRechargeApp)
            modules(appModule)
        }
        
        // Start foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, AutoRechargeService::class.java))
        } else {
            startService(Intent(this, AutoRechargeService::class.java))
        }
    }
    
    private val appModule = module {
        // Database
        single { AppDatabase.getDatabase(get()) }
        single { get<AppDatabase>().transactionDao() }
        single { get<AppDatabase>().settingsDao() }
        single { get<AppDatabase>().customerDao() }
        single { get<AppDatabase>().logDao() }
        
        // Repositories
        single { TransactionRepository(get()) }
        single { SettingsRepository(get()) }
        single { CustomerRepository(get()) }
        single { LogRepository(get()) }
        
        // Utilities
        single { CoinCalculator(get()) }
        single { NotificationService(get(), get()) }
        single { SmsTemplateManager(get(), get()) }
        single { WhatsAppTemplateManager(get(), get()) }
        single { TimoNavigationTemplateManager(get(), get()) }
        
        // Services
        single { WhatsAppAccessibilityService() }
        single { TimoAccessibilityService() }
        single { 
            WhatsAppService(
                context = get(),
                settingsRepository = get(),
                customerRepository = get(),
                logRepository = get(),
                templateManager = get()
            ) 
        }
        single { 
            SmsService(
                context = get(),
                transactionRepository = get(),
                settingsRepository = get(),
                logRepository = get(),
                notificationService = get()
            ) 
        }
        single { 
            TransactionCoordinatorService(
                context = get(),
                transactionRepository = get(),
                logRepository = get(),
                settingsRepository = get(),
                whatsAppService = get(),
                smsService = get(),
                notificationService = get(),
                coinCalculator = get()
            ) 
        }
        
        // Workers
        worker { RechargeWorker(get(), get()) }
        
        // ViewModels
        viewModel { MainViewModel(get(), get(), get(), get(), get(), get()) }
        viewModel { SettingsViewModel(get(), get()) }
    }
}
