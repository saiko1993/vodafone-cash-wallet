package com.timoautorecharge.assistant.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.timoautorecharge.assistant.data.model.Customer
import com.timoautorecharge.assistant.data.model.Log
import com.timoautorecharge.assistant.data.model.Settings
import com.timoautorecharge.assistant.data.model.Transaction

@Database(
    entities = [Transaction::class, Settings::class, Customer::class, Log::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun settingsDao(): SettingsDao
    abstract fun customerDao(): CustomerDao
    abstract fun logDao(): LogDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "timo_autorecharge_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
