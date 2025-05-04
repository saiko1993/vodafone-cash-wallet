package com.timoautorecharge.assistant.ui.transactions

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
import com.timoautorecharge.assistant.ui.components.TransactionItem
import com.timoautorecharge.assistant.ui.viewmodel.MainViewModel

@Composable
fun TransactionsScreen(viewModel: MainViewModel) {
    val allTransactions by viewModel.recentTransactions.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.transactions),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        LazyColumn {
            items(allTransactions) { transaction ->
                TransactionItem(
                    transaction = transaction,
                    onRechargeClick = { viewModel.manualRecharge(transaction.id) }
                )
            }
        }
    }
}
