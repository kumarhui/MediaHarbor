package com.mediaharbor.app.feature.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import com.mediaharbor.app.MediaHarborApp
import com.mediaharbor.app.data.media.datasource.MediaStoreImageDataSource
import com.mediaharbor.app.data.media.datasource.MediaStorePdfDataSource
import com.mediaharbor.app.data.settings.SettingsManager
import com.mediaharbor.app.domain.usecase.BackupDataUseCase
import com.mediaharbor.app.domain.usecase.RestoreDataUseCase
import com.mediaharbor.app.feature.backup.BackupRestoreView
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as MediaHarborApp
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val isDarkMode by settingsManager.isDarkMode.collectAsState()
    val language by settingsManager.language.collectAsState()
    val photoColumns by settingsManager.photoColumns.collectAsState()
    val pdfColumns by settingsManager.pdfColumns.collectAsState()

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showExportModal by remember { mutableStateOf(false) }
    var showRestoreModal by remember { mutableStateOf(false) }
    var showRemoveTagsMediaTypeModal by remember { mutableStateOf(false) }
    var removeTagsTargetType by remember { mutableStateOf<String?>(null) } // "IMAGE" or "PDF"
    var isProcessing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        SettingsCategoryHeader("Appearance")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.DarkMode, contentDescription = "Dark Theme")
            Spacer(modifier = Modifier.width(16.dp))
            Text("Dark Mode", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = isDarkMode,
                onCheckedChange = { settingsManager.setDarkMode(it) }
            )
        }

        SettingsItemRow(
            icon = Icons.Default.Language,
            title = "Language",
            subtitle = if (language == "hi") "Hindi (हिंदी)" else "English",
            onClick = { showLanguageDialog = true }
        )

        Divider(modifier = Modifier.padding(vertical = 12.dp))

        SettingsCategoryHeader("Gallery Layout")

        Text("Photos per row: $photoColumns", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = photoColumns.toFloat(),
            onValueChange = { settingsManager.setPhotoColumns(it.toInt()) },
            valueRange = 2f..5f,
            steps = 2,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text("PDFs per row: $pdfColumns", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = pdfColumns.toFloat(),
            onValueChange = { settingsManager.setPdfColumns(it.toInt()) },
            valueRange = 2f..5f,
            steps = 2,
            modifier = Modifier.fillMaxWidth()
        )

        Divider(modifier = Modifier.padding(vertical = 12.dp))

        SettingsCategoryHeader("Data & Backup")

        SettingsItemRow(
            icon = Icons.Default.FileDownload,
            title = "Export Application Backup JSON",
            subtitle = "Saves tags and metadata into JSON file",
            onClick = { showExportModal = true }
        )

        SettingsItemRow(
            icon = Icons.Default.FileUpload,
            title = "Restore Metadata from JSON",
            subtitle = "Imports tags and media associations from backup",
            onClick = { showRestoreModal = true }
        )

        SettingsItemRow(
            icon = Icons.Default.CleaningServices,
            title = "Clear Thumbnail Cache",
            subtitle = "Frees up memory cache without deleting files",
            onClick = {
                val loader = context.imageLoader
                loader.diskCache?.clear()
                loader.memoryCache?.clear()
                Toast.makeText(context, "Thumbnail cache cleared", Toast.LENGTH_SHORT).show()
            }
        )

        Divider(modifier = Modifier.padding(vertical = 12.dp))

        SettingsCategoryHeader("Tags")

        SettingsItemRow(
            icon = Icons.Default.LabelOff,
            title = "Remove tags from all",
            subtitle = "Clears tag associations from Images or PDFs",
            onClick = { showRemoveTagsMediaTypeModal = true }
        )

        Divider(modifier = Modifier.padding(vertical = 12.dp))

        SettingsCategoryHeader("About")
        SettingsItemRow(
            icon = Icons.Default.Info,
            title = "MediaHarbor",
            subtitle = "Version 1.0.0 (Build 1) • Material 3 & Jetpack Compose",
            onClick = {}
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Select Language") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                settingsManager.setLanguage("en")
                                showLanguageDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = language == "en", onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("English", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                settingsManager.setLanguage("hi")
                                showLanguageDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = language == "hi", onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Hindi (हिंदी)", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showExportModal) {
        AlertDialog(
            onDismissRequest = { showExportModal = false },
            title = { Text("Export Application Backup") },
            text = { Text("This will export your Media Harbor metadata and tag definitions to a JSON file in your Documents directory.") },
            confirmButton = {
                Button(onClick = {
                    showExportModal = false
                    coroutineScope.launch {
                        val result = BackupDataUseCase(context)()
                        if (result.isSuccess) {
                            Toast.makeText(context, "Exported: ${result.getOrNull()}", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text("Export")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportModal = false }) { Text("Cancel") }
            }
        )
    }

    if (showRestoreModal) {
        AlertDialog(
            onDismissRequest = { showRestoreModal = false },
            title = { Text("Restore Metadata") },
            text = { Text("Restoring metadata will import tag definitions and relationships. Proceed to backup restore view?") },
            confirmButton = {
                Button(onClick = {
                    showRestoreModal = false
                }) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreModal = false }) { Text("Cancel") }
            }
        )
    }

    if (showRemoveTagsMediaTypeModal) {
        AlertDialog(
            onDismissRequest = { showRemoveTagsMediaTypeModal = false },
            title = { Text("Remove Tags From All") },
            text = { Text("Choose which media type to remove tags from:") },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showRemoveTagsMediaTypeModal = false
                            removeTagsTargetType = "IMAGE"
                        }
                    ) {
                        Text("Images")
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showRemoveTagsMediaTypeModal = false
                            removeTagsTargetType = "PDF"
                        }
                    ) {
                        Text("PDFs")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveTagsMediaTypeModal = false }) { Text("Cancel") }
            }
        )
    }

    removeTagsTargetType?.let { mediaType ->
        val typeName = if (mediaType == "IMAGE") "Images" else "PDFs"
        AlertDialog(
            onDismissRequest = { removeTagsTargetType = null },
            title = { Text("Remove Tags From All $typeName?") },
            text = { Text("All tags will be removed from all $typeName. This action cannot be undone.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        val target = removeTagsTargetType
                        removeTagsTargetType = null
                        coroutineScope.launch {
                            val items = if (target == "IMAGE") {
                                MediaStoreImageDataSource(context).fetchImages().collect { list ->
                                    app.database.tagDao().removeTagsForMediaUris(list.map { it.uri.toString() })
                                }
                            } else {
                                MediaStorePdfDataSource(context).fetchPdfs().collect { list ->
                                    app.database.tagDao().removeTagsForMediaUris(list.map { it.uri.toString() })
                                }
                            }
                            Toast.makeText(context, "Removed tags from all $typeName", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Remove Tags")
                }
            },
            dismissButton = {
                TextButton(onClick = { removeTagsTargetType = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SettingsCategoryHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun SettingsItemRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}