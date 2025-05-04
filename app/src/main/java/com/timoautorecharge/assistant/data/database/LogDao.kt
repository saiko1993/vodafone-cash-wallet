package com.timoautorecharge.assistant.data.database

import androidx.room.*
import com.timoautorecharge.assistant.data.model.Log
import com.timoautorecharge.assistant.data.model.LogType
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<Log>>
    
    @Query("SELECT * FROM logs WHERE type = :type ORDER BY timestamp DESC")
    fun getLogsByType(type: LogType): Flow<List<Log>>
    
    @Query("SELECT * FROM logs WHERE transactionId = :transactionId ORDER BY timestamp DESC")
    fun getLogsByTransactionId(transactionId: Long): Flow<List<Log>>
    
    @Insert
    suspend fun insertLog(log: Log): Long
    
    @Query("SELECT * FROM logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int): Flow<List<Log>>
}
