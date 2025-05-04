package com.timoautorecharge.assistant.ui.uielements

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.timoautorecharge.assistant.data.model.UiElementInfo
import com.timoautorecharge.assistant.data.model.UiElementRegistry
import com.timoautorecharge.assistant.ui.viewmodel.UiElementsViewModel
import org.koin.androidx.compose.getViewModel

/**
 * Screen for managing UI elements used for automation.
 * Allows users to view, add, edit, and delete UI elements.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UiElementsScreen(
    navigateBack: () -> Unit,
    viewModel: UiElementsViewModel = getViewModel()
) {
    val elements by viewModel.uiElements.collectAsState(initial = emptyList())
    val groupedElements = elements.groupBy { it.appName }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedElement by remember { mutableStateOf<UiElementRegistry?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UI Elements") },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Element"
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            groupedElements.forEach { (appName, appElements) ->
                item {
                    Text(
                        text = appName.ifEmpty { "Ungrouped Elements" },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                items(appElements) { element ->
                    UiElementItem(
                        element = element,
                        onSelect = {
                            selectedElement = element
                            showAddDialog = true
                        },
                        onCapture = { viewModel.captureElement(element.keyName, element.elementInfo.elementType) },
                        onDelete = { viewModel.deleteElement(element.keyName) }
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            
            // If no elements, show empty state
            if (elements.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No UI elements registered yet. Add one using the + button.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
    
    if (showAddDialog) {
        UiElementDialog(
            existingElement = selectedElement,
            onDismiss = {
                showAddDialog = false
                selectedElement = null
            },
            onConfirm = { key, type, appName, screenName, description ->
                if (selectedElement == null) {
                    viewModel.captureElement(key, type, appName, screenName, description)
                } else {
                    viewModel.updateElement(
                        selectedElement!!.copy(
                            keyName = key,
                            appName = appName,
                            screenName = screenName,
                            description = description
                        )
                    )
                }
                showAddDialog = false
                selectedElement = null
            }
        )
    }
}

/**
 * Composable for displaying a single UI element in the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UiElementItem(
    element: UiElementRegistry,
    onSelect: () -> Unit,
    onCapture: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = element.keyName,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = element.description.ifEmpty { "No description" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${element.elementInfo.elementType} (${element.elementInfo.elementId})",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (element.screenName.isNotEmpty()) {
                        Text(
                            text = "Screen: ${element.screenName}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                Row {
                    IconButton(onClick = onCapture) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Capture Again"
                        )
                    }
                    IconButton(onClick = onSelect) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit"
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete"
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog for adding or editing a UI element.
 */
@Composable
fun UiElementDialog(
    existingElement: UiElementRegistry? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, UiElementInfo.ElementType, String, String, String) -> Unit
) {
    var key by remember { mutableStateOf(existingElement?.keyName ?: "") }
    var appName by remember { mutableStateOf(existingElement?.appName ?: "") }
    var screenName by remember { mutableStateOf(existingElement?.screenName ?: "") }
    var description by remember { mutableStateOf(existingElement?.description ?: "") }
    var selectedType by remember { 
        mutableStateOf(existingElement?.elementInfo?.elementType ?: UiElementInfo.ElementType.BUTTON) 
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (existingElement != null) "Edit Element" else "Add New Element") },
        text = {
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Element Key") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = existingElement == null
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("App Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = screenName,
                    onValueChange = { screenName = it },
                    label = { Text("Screen Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Element Type")
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    UiElementInfo.ElementType.values().forEach { type ->
                        @OptIn(ExperimentalMaterial3Api::class)
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (key.isNotBlank()) {
                        onConfirm(key, selectedType, appName, screenName, description)
                    }
                },
                enabled = key.isNotBlank()
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
