package com.mediaharbor.app.feature.backup

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mediaharbor.app.domain.usecase.BackupDataUseCase
import com.mediaharbor.app.domain.usecase.RestoreDataUseCase
import kotlinx.coroutines.launch

@Composable
fun BackupRestoreView() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showRestoreDialog by remember { mutableStateOf(false) }
    var jsonPasteText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Backup & Metadata", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            enabled = !isProcessing,
            onClick = {
                coroutineScope.launch {
                    isProcessing = true
                    val result = BackupDataUseCase(context)()
                    isProcessing = false
                    if (result.isSuccess) {
                        Toast.makeText(context, "Backup Created: ${result.getOrNull()}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Backup Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Export Application Backup JSON")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            enabled = !isProcessing,
            onClick = { showRestoreDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Restore Metadata from JSON")
        }

        if (isProcessing) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Application Metadata") },
            text = {
                Column {
                    Text("Paste backup JSON content below:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = jsonPasteText,
                        onValueChange = { jsonPasteText = it },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        placeholder = { Text("{ \"tags\": [...] }") }
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = jsonPasteText.isNotBlank(),
                        onClick = {
                            coroutineScope.launch {
                                isProcessing = true
                                val result = RestoreDataUseCase(context)(jsonPasteText, replace = false)
                                isProcessing = false
                                if (result.isSuccess) {
                                    Toast.makeText(context, "Metadata MERGED successfully!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Invalid JSON structure", Toast.LENGTH_SHORT).show()
                                }
                            }
                            showRestoreDialog = false
                        }
                    ) {
                        Text("MERGE")
                    }

                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        enabled = jsonPasteText.isNotBlank(),
                        onClick = {
                            coroutineScope.launch {
                                isProcessing = true
                                val result = RestoreDataUseCase(context)(jsonPasteText, replace = true)
                                isProcessing = false
                                if (result.isSuccess) {
                                    Toast.makeText(context, "Metadata REPLACED successfully!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Invalid JSON structure", Toast.LENGTH_SHORT).show()
                                }
                            }
                            showRestoreDialog = false
                        }
                    ) {
                        Text("REPLACE")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel") }
            }
        )
    }
}