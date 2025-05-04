package com.timoautorecharge.assistant.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.timoautorecharge.assistant.R
import com.timoautorecharge.assistant.service.TimoAccessibilityService
import com.timoautorecharge.assistant.service.WhatsAppService
import com.timoautorecharge.assistant.ui.components.ServiceStatusIndicator
import com.timoautorecharge.assistant.ui.components.StatusCard
import com.timoautorecharge.assistant.ui.components.TransactionItem
import com.timoautorecharge.assistant.ui.viewmodel.MainViewModel

@Composable
fun DashboardScreen(
    navigateToUiElements: () -> Unit,
    navigateToConnectionStatus: () -> Unit,
    viewModel: MainViewModel
) {
    val pendingPayments by viewModel.pendingPayments.collectAsState()
    val pendingRecharges by viewModel.pendingRecharges.collectAsState()
    val recentTransactions by viewModel.recentTransactions.collectAsState()
    val whatsAppServiceStatus by viewModel.whatsAppServiceStatus.collectAsState()
    val timoServiceStatus by viewModel.timoServiceStatus.collectAsState()
    val smsServiceStatus by viewModel.smsServiceStatus.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.dashboard),
                style = MaterialTheme.typography.headlineMedium
            )
            
            IconButton(onClick = { viewModel.refreshDashboard() }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh"
                )
            }
        }
        
        // Service Status Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.service_status),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ServiceStatusIndicator(
                        title = "WhatsApp",
                        isActive = whatsAppServiceStatus == WhatsAppService.ServiceStatus.ACTIVE,
                        modifier = Modifier.weight(1f)
                    )
                    
                    ServiceStatusIndicator(
                        title = "Timo",
                        isActive = timoServiceStatus == TimoAccessibilityService.ServiceStatus.ACTIVE,
                        modifier = Modifier.weight(1f)
                    )
                    
                    ServiceStatusIndicator(
                        title = "SMS",
                        isActive = smsServiceStatus,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        
        // Transaction Status Cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatusCard(
                title = stringResource(R.string.pending_payments),
                count = pendingPayments.size,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.showPendingPayments() }
            )
            Spacer(modifier = Modifier.width(16.dp))
            StatusCard(
                title = stringResource(R.string.pending_recharges),
                count = pendingRecharges.size,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.showPendingRecharges() }
            )
        }
        
        // Recent Transactions Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .weight(1f),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.recent_transactions),
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    TextButton(onClick = { viewModel.navigateToTransactions() }) {
                        Text(text = stringResource(R.string.view_all))
                    }
                }
                
                if (recentTransactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_transactions),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(recentTransactions) { transaction ->
                            TransactionItem(
                                transaction = transaction,
                                onRechargeClick = { viewModel.manualRecharge(transaction.id) },
                                onVerifyClick = { viewModel.manualVerifyPayment(transaction.id) },
                                onCancelClick = { viewModel.cancelTransaction(transaction.id) },
                                expanded = true
                            )
                        }
                    }
                }
            }
        }
        
        // Action Buttons
        Button(
            onClick = { viewModel.showManualRechargeDialog() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = stringResource(R.string.manual_recharge),
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        
        // Additional Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // UI Elements Management Button
            Button(
                onClick = { navigateToUiElements() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(
                    text = "UI Elements",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            
            // Connection Status Button
            Button(
                onClick = { navigateToConnectionStatus() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Text(
                    text = "Connection Status",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
