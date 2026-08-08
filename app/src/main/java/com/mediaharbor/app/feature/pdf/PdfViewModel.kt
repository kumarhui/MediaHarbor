package com.mediaharbor.app.feature.pdf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.mediaharbor.app.core.common.FileUtils
import com.mediaharbor.app.data.media.datasource.MediaStoreImageDataSource
import com.mediaharbor.app.data.media.datasource.MediaStorePdfDataSource
import com.mediaharbor.app.data.repository.MediaRepositoryImpl
import com.mediaharbor.app.domain.model.MediaItem
import com.mediaharbor.app.domain.usecase.GetPdfsUseCase
import com.mediaharbor.app.domain.usecase.SearchMediaUseCase
import com.mediaharbor.app.feature.selection.SelectionViewModel

class PdfViewModel(context: android.content.Context) : ViewModel() {
    private val repo = MediaRepositoryImpl(
        MediaStoreImageDataSource(context),
        MediaStorePdfDataSource(context)
    )
    private val getPdfsUseCase = GetPdfsUseCase(repo)
    private val searchUseCase = SearchMediaUseCase()

    val pdfsFlow = getPdfsUseCase()

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
    val filtered = remember(pdfs, searchQuery) { viewModel.filterPdfs(pdfs, searchQuery) }

    if (filtered.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No PDF Documents Found", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
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