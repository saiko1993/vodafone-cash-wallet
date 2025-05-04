package com.timoautorecharge.assistant.data.repository

import com.timoautorecharge.assistant.data.database.LogDao
import com.timoautorecharge.assistant.data.model.Log
import com.timoautorecharge.assistant.data.model.LogType
import kotlinx.coroutines.flow.Flow

class LogRepository(private val logDao: LogDao) {
    
    fun getAllLogs(): Flow<List<Log>> {
        return logDao.getAllLogs()
    }
    
    fun getLogsByType(type: LogType): Flow<List<Log>> {
        return logDao.getLogsByType(type)
    }
    
    fun getLogsByTransactionId(transactionId: Long): Flow<List<Log>> {
        return logDao.getLogsByTransactionId(transactionId)
    }
    
    suspend fun logInfo(type: LogType, message: String, transactionId: Long? = null) {
        val log = Log(
            timestamp = System.currentTimeMillis(),
            type = type,
            message = message,
            transactionId = transactionId
        )
        logDao.insertLog(log)
    }
    
    suspend fun logWarning(type: LogType, message: String, transactionId: Long? = null) {
        val log = Log(
            timestamp = System.currentTimeMillis(),
            type = type,
            message = "WARNING: $message",
            transactionId = transactionId
        )
        logDao.insertLog(log)
    }
    
    suspend fun logError(type: LogType, message: String, transactionId: Long? = null) {
        val log = Log(
            timestamp = System.currentTimeMillis(),
            type = type,
            message = "ERROR: $message",
            transactionId = transactionId
        )
        logDao.insertLog(log)
    }
    
    fun getRecentLogs(limit: Int = 20): Flow<List<Log>> {
        return logDao.getRecentLogs(limit)
    }
}
