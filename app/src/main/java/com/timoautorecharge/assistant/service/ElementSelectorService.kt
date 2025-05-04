package com.timoautorecharge.assistant.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.timoautorecharge.assistant.R
import com.timoautorecharge.assistant.data.model.UiElementInfo
import com.timoautorecharge.assistant.data.repository.LogRepository
import com.timoautorecharge.assistant.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import kotlinx.coroutines.flow.first
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Service for displaying floating UI element selector overlay.
 * Allows users to visually select UI elements in other apps.
 */
class ElementSelectorService : Service() {
    
    private val settingsRepository: SettingsRepository by inject(SettingsRepository::class.java)
    private val logRepository: LogRepository by inject(LogRepository::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var selectionBox: View
    
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isMoving: Boolean = false
    private var isResizing: Boolean = false
    private var resizeHandle: Int = 0 // 0: none, 1: top-left, 2: top-right, 3: bottom-left, 4: bottom-right
    
    private var elementType: UiElementInfo.ElementType = UiElementInfo.ElementType.BUTTON
    private var targetPackageName: String = ""
    private var targetElementKey: String = ""
    
    companion object {
        const val EXTRA_ELEMENT_TYPE = "element_type"
        const val EXTRA_TARGET_PACKAGE = "target_package"
        const val EXTRA_TARGET_ELEMENT_KEY = "target_element_key"
        
        // Intent action for when an element is selected
        const val ACTION_ELEMENT_SELECTED = "com.timoautorecharge.assistant.ACTION_ELEMENT_SELECTED"
        // Intent extra key for the selected element info
        const val EXTRA_ELEMENT_INFO = "element_info"
        
        /**
         * Checks if the overlay permission is granted.
         *
         * @param context The context to check the permission from
         * @return True if the permission is granted, false otherwise
         */
        fun hasOverlayPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }
        
        /**
         * Starts the element selector service.
         *
         * @param context The context to start the service from
         * @param elementType The type of element to select (button or input field)
         * @param targetPackage The package name of the target app
         * @param targetElementKey The key for the target element in settings
         */
        fun startSelector(
            context: Context,
            elementType: UiElementInfo.ElementType,
            targetPackage: String,
            targetElementKey: String
        ) {
            if (!hasOverlayPermission(context)) {
                return
            }
            
            val intent = Intent(context, ElementSelectorService::class.java).apply {
                putExtra(EXTRA_ELEMENT_TYPE, elementType.name)
                putExtra(EXTRA_TARGET_PACKAGE, targetPackage)
                putExtra(EXTRA_TARGET_ELEMENT_KEY, targetElementKey)
            }
            
            ContextCompat.startForegroundService(context, intent)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            elementType = UiElementInfo.ElementType.valueOf(
                it.getStringExtra(EXTRA_ELEMENT_TYPE) ?: UiElementInfo.ElementType.BUTTON.name
            )
            targetPackageName = it.getStringExtra(EXTRA_TARGET_PACKAGE) ?: ""
            targetElementKey = it.getStringExtra(EXTRA_TARGET_ELEMENT_KEY) ?: ""
            
            createOverlay()
        }
        
        return START_NOT_STICKY
    }
    
    private fun createOverlay() {
        // Create the overlay layout
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_element_selector, null)
        
        // Set up the layout parameters for the overlay
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        
        // Add the overlay to the window
        windowManager.addView(overlayView, params)
        
        // Set up the selection box
        selectionBox = overlayView.findViewById(R.id.selection_box)
        
        // Set up buttons
        val captureButton = overlayView.findViewById<Button>(R.id.capture_button)
        val cancelButton = overlayView.findViewById<Button>(R.id.cancel_button)
        
        captureButton.setOnClickListener {
            captureElementInfo()
        }
        
        cancelButton.setOnClickListener {
            stopSelf()
        }
        
        // Set up touch listeners for moving and resizing the selection box
        setupTouchListeners()
    }
    
    private fun getOverlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }
    
    private fun setupTouchListeners() {
        selectionBox.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = (view.layoutParams as FrameLayout.LayoutParams).leftMargin
                    initialY = (view.layoutParams as FrameLayout.LayoutParams).topMargin
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    
                    // Determine if we're resizing or moving
                    val x = event.x.toInt()
                    val y = event.y.toInt()
                    val width = view.width
                    val height = view.height
                    val handleSize = 50
                    
                    resizeHandle = when {
                        x < handleSize && y < handleSize -> 1 // top-left
                        x > width - handleSize && y < handleSize -> 2 // top-right
                        x < handleSize && y > height - handleSize -> 3 // bottom-left
                        x > width - handleSize && y > height - handleSize -> 4 // bottom-right
                        else -> 0 // moving
                    }
                    
                    isMoving = resizeHandle == 0
                    isResizing = !isMoving
                    
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val params = view.layoutParams as FrameLayout.LayoutParams
                    
                    if (isMoving) {
                        // Move the selection box
                        params.leftMargin = (initialX + (event.rawX - initialTouchX)).toInt()
                        params.topMargin = (initialY + (event.rawY - initialTouchY)).toInt()
                    } else if (isResizing) {
                        // Resize the selection box
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        
                        when (resizeHandle) {
                            1 -> { // top-left
                                params.leftMargin = initialX + dx
                                params.topMargin = initialY + dy
                                params.width = params.width - dx
                                params.height = params.height - dy
                            }
                            2 -> { // top-right
                                params.topMargin = initialY + dy
                                params.width = params.width + dx
                                params.height = params.height - dy
                            }
                            3 -> { // bottom-left
                                params.leftMargin = initialX + dx
                                params.width = params.width - dx
                                params.height = params.height + dy
                            }
                            4 -> { // bottom-right
                                params.width = params.width + dx
                                params.height = params.height + dy
                            }
                        }
                    }
                    
                    view.layoutParams = params
                    true
                }
                MotionEvent.ACTION_UP -> {
                    isMoving = false
                    isResizing = false
                    true
                }
                else -> false
            }
        }
    }
    
    private fun captureElementInfo() {
        // Get the screen coordinates of the selection box
        val screenLocation = IntArray(2)
        selectionBox.getLocationOnScreen(screenLocation)
        
        val selectionBounds = android.graphics.Rect(
            screenLocation[0],
            screenLocation[1],
            screenLocation[0] + selectionBox.width,
            screenLocation[1] + selectionBox.height
        )
        
        // Request an accessibility service to capture the element at these coordinates
        scope.launch {
            val result = if (targetPackageName == "com.whatsapp") {
                WhatsAppAccessibilityService.captureElementAtLocation(selectionBounds, elementType)
            } else {
                TimoAccessibilityService.captureElementAtLocation(selectionBounds, elementType)
            }
            
            result?.let { elementInfo ->
                // Update settings based on the captured element
                updateSettings(elementInfo)
                
                // Log the capture
                logRepository.addLog(
                    com.timoautorecharge.assistant.data.model.Log(
                        message = "Captured UI element: ${elementInfo.elementId} (${elementInfo.elementType})",
                        timestamp = System.currentTimeMillis(),
                        level = com.timoautorecharge.assistant.data.model.Log.Level.INFO,
                        source = "ElementSelectorService"
                    )
                )
                
                // Broadcast the result for any listeners
                val intent = Intent(ACTION_ELEMENT_SELECTED).apply {
                    putExtra(EXTRA_ELEMENT_INFO, elementInfo)
                }
                sendBroadcast(intent)
            }
            
            // Stop the service
            stopSelf()
        }
    }
    
    private suspend fun updateSettings(elementInfo: UiElementInfo) {
        val settings = settingsRepository.getSettings().first()
        
        // Update the appropriate setting based on the target element key
        val updatedSettings = when (targetElementKey) {
            "rechargeButtonId" -> settings.copy(rechargeButtonId = elementInfo.elementId)
            "timoIdInputId" -> settings.copy(timoIdInputId = elementInfo.elementId)
            "coinQuantityInputId" -> settings.copy(coinQuantityInputId = elementInfo.elementId)
            "confirmButtonId" -> settings.copy(confirmButtonId = elementInfo.elementId)
            else -> settings
        }
        
        settingsRepository.updateSettings(updatedSettings)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }
}
