package com.timoautorecharge.assistant.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A card component that displays a status count with a title.
 * Can be made clickable with the onClick parameter.
 * 
 * @param title The title of the status card
 * @param count The count to display
 * @param modifier Modifier for styling
 * @param onClick Optional click handler for the card
 */
@Composable
fun StatusCard(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = when {
                    count > 0 -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                }
            )
        }
    }
}
