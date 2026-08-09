package com.mediaharbor.app.feature.pdf

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.ui.BiasAlignment
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

private fun formatDateHeader(timestampSeconds: Long): String {
    val date = Date(timestampSeconds * 1000)
    val now = Calendar.getInstance()
    val itemCal = Calendar.getInstance().apply { time = date }

    return when {
        now.get(Calendar.YEAR) == itemCal.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == itemCal.get(Calendar.DAY_OF_YEAR) -> "Today"

        now.get(Calendar.YEAR) == itemCal.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) - itemCal.get(Calendar.DAY_OF_YEAR) == 1 -> "Yesterday"

        now.get(Calendar.YEAR) == itemCal.get(Calendar.YEAR) ->
            SimpleDateFormat("MMMM d", Locale.getDefault()).format(date)

        else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(date)
    }
}

@Composable
fun PdfThumbnailView(
    pdfManager: PdfRendererManager,
    pdf: MediaItem,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(pdf.uri, pdf.dateModified, pdf.size) {
        mutableStateOf<Bitmap?>(pdfManager.getCachedThumbnail(pdf.uri, pdf.dateModified, pdf.size))
    }

    LaunchedEffect(pdf.uri, pdf.dateModified, pdf.size) {
        if (bitmap == null) {
            withContext(Dispatchers.IO) {
                bitmap = pdfManager.renderThumbnail(pdf.uri, pdf.dateModified, pdf.size)
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
                contentDescription = pdf.displayName,
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
    val groupPdfByDate by settingsManager.groupPdfByDate.collectAsState()

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

    val groupedPdfs = remember(filtered, groupPdfByDate) {
        if (groupPdfByDate && filtered.isNotEmpty()) {
            filtered.groupBy { formatDateHeader(it.dateModified) }
        } else {
            emptyMap()
        }
    }

    // Flatten grouped items so the 0-indexed list sequence matches visual grid layout exactly
    val displayedList = remember(filtered, groupedPdfs, groupPdfByDate) {
        if (groupPdfByDate && groupedPdfs.isNotEmpty()) {
            groupedPdfs.values.flatten()
        } else {
            filtered
        }
    }

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
        val scrollProgress by remember {
            derivedStateOf {
                val total = gridState.layoutInfo.totalItemsCount
                val visible = gridState.layoutInfo.visibleItemsInfo.size
                if (total <= visible || total == 0) 0f
                else {
                    val first = gridState.firstVisibleItemIndex
                    (first.toFloat() / (total - visible).toFloat()).coerceIn(0f, 1f)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            DragSelectContainer(
                gridState = gridState,
                items = displayedList,
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
                    if (groupPdfByDate && groupedPdfs.isNotEmpty()) {
                        groupedPdfs.forEach { (dateHeader, itemsInGroup) ->
                            item(
                                key = "pdf_header_$dateHeader",
                                span = { GridItemSpan(pdfColumns) }
                            ) {
                                Text(
                                    text = dateHeader,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp)
                                )
                            }

                            items(itemsInGroup, key = { it.id }) { pdf ->
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
                                        if (selectionViewModel.isSelectionMode) {
                                            selectionViewModel.selectRange(pdf, displayedList)
                                        } else {
                                            selectionViewModel.startSelection(pdf)
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        items(displayedList, key = { it.id }) { pdf ->
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
                                    if (selectionViewModel.isSelectionMode) {
                                        selectionViewModel.selectRange(pdf, displayedList)
                                    } else {
                                        selectionViewModel.startSelection(pdf)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (filtered.size > 15) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp, top = 8.dp, bottom = 80.dp)
                        .fillMaxHeight()
                        .width(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.15f)
                            .align(
                                BiasAlignment(
                                    horizontalBias = 0f,
                                    verticalBias = (scrollProgress * 2f) - 1f
                                )
                            )
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.85f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            PdfThumbnailView(
                pdfManager = pdfManager,
                pdf = pdf,
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
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