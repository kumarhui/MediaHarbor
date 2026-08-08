package com.mediaharbor.app.feature.pdf

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.mediaharbor.app.core.common.FileUtils
import com.mediaharbor.app.core.common.PermissionUtils
import com.mediaharbor.app.data.media.datasource.MediaStoreImageDataSource
import com.mediaharbor.app.data.media.datasource.MediaStorePdfDataSource
import com.mediaharbor.app.data.repository.MediaRepositoryImpl
import com.mediaharbor.app.domain.model.MediaItem
import com.mediaharbor.app.domain.usecase.GetPdfsUseCase
import com.mediaharbor.app.domain.usecase.SearchMediaUseCase
import com.mediaharbor.app.feature.selection.SelectionViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PdfViewModel(context: android.content.Context) : ViewModel() {
    private val pdfDataSource = MediaStorePdfDataSource(context)
    private val repo = MediaRepositoryImpl(
        MediaStoreImageDataSource(context),
        pdfDataSource
    )
    private val getPdfsUseCase = GetPdfsUseCase(repo)
    private val searchUseCase = SearchMediaUseCase()

    val pdfsFlow = getPdfsUseCase()
    val isScanning: StateFlow<Boolean> = pdfDataSource.isScanning

    fun filterPdfs(pdfs: List<MediaItem>, query: String): List<MediaItem> {
        return searchUseCase(pdfs, query)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfScreen(
    searchQuery: String,
    selectionViewModel: SelectionViewModel,
    onMediaClick: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    val viewModel = remember { PdfViewModel(context) }
    val pdfs by viewModel.pdfsFlow.collectAsState(initial = emptyList())
    val isScanning by viewModel.isScanning.collectAsState()

    var isInitialLoading by remember { mutableStateOf(true) }
    val filtered = remember(pdfs, searchQuery) { viewModel.filterPdfs(pdfs, searchQuery) }

    LaunchedEffect(pdfs, isScanning) {
        if (pdfs.isNotEmpty() || !isScanning) {
            isInitialLoading = false
        }
    }

    var hasPermission by remember { mutableStateOf(PermissionUtils.hasAllFilesPermission()) }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasPermission = PermissionUtils.hasAllFilesPermission()
    }

    LaunchedEffect(filtered.size) {
        Log.d("PDF_DEBUG", "PDF tab count=${filtered.size}")
    }

    if (!hasPermission) {
        // State 1: Permission not granted
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FolderSpecial,
                    contentDescription = "File Access Permission",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Allow file access to discover PDFs",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "MediaHarbor requires broad file access to scan and display PDF documents stored across your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                settingsLauncher.launch(intent)
                            } catch (e: Exception) {
                                val genericIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                settingsLauncher.launch(genericIntent)
                            }
                        } else {
                            hasPermission = true
                        }
                    }
                ) {
                    Text("Grant Access")
                }
            }
        }
    } else if (isInitialLoading && filtered.isEmpty()) {
        // State 2: Active loading
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Scanning PDF documents...", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        }
    } else if (filtered.isEmpty()) {
        // State 3: Permission granted + scan complete + no PDFs
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No PDF documents found", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        // State 4: Permission granted + PDFs found
        LazyColumn(
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered, key = { it.id }) { pdf ->
                val isSelected = selectionViewModel.isSelected(pdf)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = {
                                if (selectionViewModel.isSelectionMode) {
                                    selectionViewModel.toggleSelection(pdf)
                                } else {
                                    onMediaClick(pdf)
                                }
                            },
                            onLongClick = {
                                selectionViewModel.startSelection(pdf)
                            }
                        ),
                    colors = if (isSelected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    else CardDefaults.cardColors()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectionViewModel.isSelectionMode) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                                contentDescription = "Selected",
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "PDF",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pdf.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${FileUtils.formatFileSize(pdf.size)} • ${pdf.relativePath}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}