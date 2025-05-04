package com.timoautorecharge.assistant.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.timoautorecharge.assistant.data.model.LogType
import com.timoautorecharge.assistant.data.repository.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class WhatsAppNotificationListener : NotificationListenerService() {
    
    private val whatsAppPackageName = "com.whatsapp"
    private val whatsAppService: WhatsAppService by inject(WhatsAppService::class.java)
    private val logRepository: LogRepository by inject(LogRepository::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == whatsAppPackageName) {
            val extras = sbn.notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE)
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            
            if (title != null && text != null) {
                scope.launch {
                    try {
                        logRepository.logInfo(
                            LogType.WHATSAPP,
                            "Received WhatsApp message from $title: $text"
                        )
                        
                        whatsAppService.processWhatsAppMessage(text, title)
                    } catch (e: Exception) {
                        logRepository.logError(
                            LogType.WHATSAPP,
                            "Error processing WhatsApp message: ${e.message}"
                        )
                    }
                }
            }
        }
    }
}
