package com.mediaharbor.app.feature.pdf

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.mediaharbor.app.MediaHarborApp
import com.mediaharbor.app.core.common.FileUtils
import com.mediaharbor.app.core.common.PermissionUtils
import com.mediaharbor.app.core.pdf.PdfRendererManager
import com.mediaharbor.app.data.media.datasource.MediaStoreImageDataSource
import com.mediaharbor.app.data.media.datasource.MediaStorePdfDataSource
import com.mediaharbor.app.data.repository.MediaRepositoryImpl
import com.mediaharbor.app.data.settings.SettingsManager
import com.mediaharbor.app.domain.model.MediaItem
import com.mediaharbor.app.domain.usecase.GetPdfsUseCase
import com.mediaharbor.app.domain.usecase.SearchMediaUseCase
import com.mediaharbor.app.feature.selection.DragSelectContainer
import com.mediaharbor.app.feature.selection.SelectionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class PdfViewModel(context: android.content.Context) : ViewModel() {
    private val pdfDataSource = MediaStorePdfDataSource(context)
    private val repo = MediaRepositoryImpl(
        MediaStoreImageDataSource(context),
        pdfDataSource
    )
    private val getPdfsUseCase = GetPdfsUseCase(repo)
    private val searchUseCase = SearchMediaUseCase()

    val pdfsFlow = getPdfsUseCase()
    val isScanning: StateFlow<Boolean> = MediaStorePdfDataSource.isScanning

    fun filterPdfs(pdfs: List<MediaItem>, query: String): List<MediaItem> {
        return searchUseCase(pdfs, query)
    }
}

@Composable
fun PdfThumbnailView(
    pdfManager: PdfRendererManager,
    uri: Uri,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(pdfManager.getCachedThumbnail(uri)) }

    LaunchedEffect(uri) {
        if (bitmap == null) {
            withContext(Dispatchers.IO) {
                bitmap = pdfManager.renderThumbnail(uri)
            }
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp)
            )
        }
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
    val app = context.applicationContext as? MediaHarborApp
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val pdfColumns by settingsManager.pdfColumns.collectAsState()

    val viewModel = remember { PdfViewModel(context) }
    val pdfManager = remember { PdfRendererManager(context) }
    val initialPdfs = remember { MediaStorePdfDataSource.getCachedPdfs() ?: emptyList() }
    val pdfs by viewModel.pdfsFlow.collectAsState(initial = initialPdfs)
    val isScanning by viewModel.isScanning.collectAsState()

    val tagCountsList by (app?.database?.tagDao()?.getMediaTagCounts()?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) })
    val tagCountMap = remember(tagCountsList) { tagCountsList.associate { it.mediaUri to it.count } }

    val gridState = rememberLazyGridState()

    var isInitialLoading by remember {
        mutableStateOf(MediaStorePdfDataSource.getCachedPdfs() == null && (isScanning || pdfs.isEmpty()))
    }
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

    if (!hasPermission) {
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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No PDF documents found", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        DragSelectContainer(
            gridState = gridState,
            items = filtered,
            selectionViewModel = selectionViewModel,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(pdfColumns),
                contentPadding = PaddingValues(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { pdf ->
                    val isSelected = selectionViewModel.isSelected(pdf)
                    val tagCount = tagCountMap[pdf.uri.toString()] ?: 0

                    PdfGridCard(
                        pdf = pdf,
                        pdfManager = pdfManager,
                        isSelectionMode = selectionViewModel.isSelectionMode,
                        isSelected = isSelected,
                        tagCount = tagCount,
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
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfGridCard(
    pdf: MediaItem,
    pdfManager: PdfRendererManager,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    tagCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = if (isSelected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.85f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            ) {
                PdfThumbnailView(
                    pdfManager = pdfManager,
                    uri = pdf.uri,
                    modifier = Modifier.fillMaxSize()
                )

                if (tagCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        tonalElevation = 2.dp,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Label,
                                contentDescription = "Tags",
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "$tagCount",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isSelected) Color.Black.copy(alpha = 0.4f) else Color.Transparent)
                    )
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = "Selected",
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    )
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = pdf.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = FileUtils.formatFileSize(pdf.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}