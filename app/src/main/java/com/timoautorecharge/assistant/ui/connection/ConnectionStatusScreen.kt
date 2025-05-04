package com.timoautorecharge.assistant.ui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.timoautorecharge.assistant.service.ConnectionTestService
import com.timoautorecharge.assistant.ui.viewmodel.ConnectionViewModel
import org.koin.androidx.compose.getViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionStatusScreen(
    navigateBack: () -> Unit,
    viewModel: ConnectionViewModel = getViewModel()
) {
    val internetStatus by viewModel.internetStatus.collectAsState()
    val whatsAppStatus by viewModel.whatsAppStatus.collectAsState()
    val timoStatus by viewModel.timoStatus.collectAsState()
    val smsStatus by viewModel.smsStatus.collectAsState()
    
    var isRefreshing by remember { mutableStateOf(false) }
    
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.testAllConnections()
            isRefreshing = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connection Status") },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isRefreshing = true }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            ConnectionStatusItem(
                title = "Internet",
                status = internetStatus,
                onFixClick = { viewModel.fixInternetConnection() }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            ConnectionStatusItem(
                title = "WhatsApp",
                status = whatsAppStatus,
                onFixClick = { viewModel.fixWhatsAppConnection() }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            ConnectionStatusItem(
                title = "Timo",
                status = timoStatus,
                onFixClick = { viewModel.fixTimoConnection() }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            ConnectionStatusItem(
                title = "SMS",
                status = smsStatus,
                onFixClick = { viewModel.fixSmsConnection() }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { viewModel.testAllConnections() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Test All Connections")
            }
        }
    }
}

@Composable
fun ConnectionStatusItem(
    title: String,
    status: ConnectionTestService.ConnectionStatus,
    onFixClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                
                StatusIndicator(status = status)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = getStatusDescription(status),
                style = MaterialTheme.typography.bodyMedium
            )
            
            if (status != ConnectionTestService.ConnectionStatus.CONNECTED) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = onFixClick,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Fix")
                }
            }
        }
    }
}

@Composable
fun StatusIndicator(status: ConnectionTestService.ConnectionStatus) {
    val color = when (status) {
        ConnectionTestService.ConnectionStatus.CONNECTED -> Color.Green
        ConnectionTestService.ConnectionStatus.LIMITED -> Color(0xFFFFA500) // Orange
        ConnectionTestService.ConnectionStatus.DISCONNECTED -> Color.Red
        ConnectionTestService.ConnectionStatus.ERROR -> Color.Red
        ConnectionTestService.ConnectionStatus.NOT_AVAILABLE -> Color.Gray
        ConnectionTestService.ConnectionStatus.NOT_CONFIGURED -> Color.Gray
        ConnectionTestService.ConnectionStatus.UNKNOWN -> Color.Gray
    }
    
    val text = when (status) {
        ConnectionTestService.ConnectionStatus.CONNECTED -> "Connected"
        ConnectionTestService.ConnectionStatus.LIMITED -> "Limited"
        ConnectionTestService.ConnectionStatus.DISCONNECTED -> "Disconnected"
        ConnectionTestService.ConnectionStatus.ERROR -> "Error"
        ConnectionTestService.ConnectionStatus.NOT_AVAILABLE -> "Not Available"
        ConnectionTestService.ConnectionStatus.NOT_CONFIGURED -> "Not Configured"
        ConnectionTestService.ConnectionStatus.UNKNOWN -> "Unknown"
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, shape = androidx.compose.foundation.shape.CircleShape)
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

fun getStatusDescription(status: ConnectionTestService.ConnectionStatus): String {
    return when (status) {
        ConnectionTestService.ConnectionStatus.CONNECTED -> 
            "Connection is active and working properly."
        ConnectionTestService.ConnectionStatus.LIMITED -> 
            "Connection is available but with limited functionality."
        ConnectionTestService.ConnectionStatus.DISCONNECTED -> 
            "Connection is not active. Please check your settings."
        ConnectionTestService.ConnectionStatus.ERROR -> 
            "An error occurred while testing the connection."
        ConnectionTestService.ConnectionStatus.NOT_AVAILABLE -> 
            "The service is not available on this device."
        ConnectionTestService.ConnectionStatus.NOT_CONFIGURED -> 
            "The service is not properly configured."
        ConnectionTestService.ConnectionStatus.UNKNOWN -> 
            "Connection status is unknown."
    }
}
