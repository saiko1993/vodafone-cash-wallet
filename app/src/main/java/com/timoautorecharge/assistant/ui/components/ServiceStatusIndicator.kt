package com.timoautorecharge.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A component that displays the status of a service (active/inactive).
 * 
 * @param title The name of the service
 * @param isActive Whether the service is active
 * @param modifier Modifier for styling
 */
@Composable
fun ServiceStatusIndicator(
    title: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) Color.Green else Color.Red
                        )
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                Text(
                    text = if (isActive) "Active" else "Inactive",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
