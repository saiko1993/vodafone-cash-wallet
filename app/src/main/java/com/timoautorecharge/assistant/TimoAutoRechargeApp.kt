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
import com.timoautorecharge.assistant.ui.viewmodel.MainViewModel
import com.timoautorecharge.assistant.ui.viewmodel.SettingsViewModel
import com.timoautorecharge.assistant.util.CoinCalculator
import com.timoautorecharge.assistant.util.NotificationService
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import timber.log.Timber

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
        
        // ViewModels
        viewModel { MainViewModel(get(), get(), get()) }
        viewModel { SettingsViewModel(get()) }
    }
}
