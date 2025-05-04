package com.timoautorecharge.assistant.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.timoautorecharge.assistant.R
import com.timoautorecharge.assistant.ui.components.StatusCard
import com.timoautorecharge.assistant.ui.components.TransactionItem
import com.timoautorecharge.assistant.ui.viewmodel.MainViewModel

@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val pendingPayments by viewModel.pendingPayments.collectAsState()
    val pendingRecharges by viewModel.pendingRecharges.collectAsState()
    val recentTransactions by viewModel.recentTransactions.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.dashboard),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatusCard(
                title = stringResource(R.string.pending_payments),
                count = pendingPayments.size,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            StatusCard(
                title = stringResource(R.string.pending_recharges),
                count = pendingRecharges.size,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.recent_transactions),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        LazyColumn {
            items(recentTransactions) { transaction ->
                TransactionItem(
                    transaction = transaction,
                    onRechargeClick = { viewModel.manualRecharge(transaction.id) }
                )
            }
        }
    }
}
