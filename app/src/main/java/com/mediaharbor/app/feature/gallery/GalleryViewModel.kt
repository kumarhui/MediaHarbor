package com.mediaharbor.app.feature.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import coil3.compose.AsyncImage
import com.mediaharbor.app.MediaHarborApp
import com.mediaharbor.app.data.media.datasource.MediaStoreImageDataSource
import com.mediaharbor.app.data.media.datasource.MediaStorePdfDataSource
import com.mediaharbor.app.data.repository.MediaRepositoryImpl
import com.mediaharbor.app.data.settings.SettingsManager
import com.mediaharbor.app.domain.model.MediaItem
import com.mediaharbor.app.domain.usecase.GetPhotosUseCase
import com.mediaharbor.app.domain.usecase.SearchMediaUseCase
import com.mediaharbor.app.feature.selection.DragSelectContainer
import com.mediaharbor.app.feature.selection.SelectionViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class GalleryViewModel(context: android.content.Context) : ViewModel() {
    private val repo = MediaRepositoryImpl(
        MediaStoreImageDataSource(context),
        MediaStorePdfDataSource(context)
    )
    private val getPhotosUseCase = GetPhotosUseCase(repo)
    private val searchUseCase = SearchMediaUseCase()

    val photosFlow = getPhotosUseCase()

    fun filterPhotos(photos: List<MediaItem>, query: String): List<MediaItem> {
        return searchUseCase(photos, query)
    }
}

private fun formatDateHeader(timestampSeconds: Long): String {
    val date = Date(timestampSeconds * 1000)
    val now = Calendar.getInstance()
    val photoCal = Calendar.getInstance().apply { time = date }

    return when {
        now.get(Calendar.YEAR) == photoCal.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == photoCal.get(Calendar.DAY_OF_YEAR) -> "Today"

        now.get(Calendar.YEAR) == photoCal.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) - photoCal.get(Calendar.DAY_OF_YEAR) == 1 -> "Yesterday"

        now.get(Calendar.YEAR) == photoCal.get(Calendar.YEAR) ->
            SimpleDateFormat("MMMM d", Locale.getDefault()).format(date)

        else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(date)
    }
}

@Composable
fun GalleryScreen(
    searchQuery: String,
    selectionViewModel: SelectionViewModel,
    onMediaClick: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as? MediaHarborApp
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val photoColumns by settingsManager.photoColumns.collectAsState()
    val groupByDate by settingsManager.groupByDate.collectAsState()

    val viewModel = remember { GalleryViewModel(context) }
    val initialPhotos = remember { MediaStoreImageDataSource.getCachedImages() ?: emptyList() }
    val photos by viewModel.photosFlow.collectAsState(initial = initialPhotos)
    val filtered = remember(photos, searchQuery) { viewModel.filterPhotos(photos, searchQuery) }
    val gridState = rememberLazyGridState()

    val tagCountsList by (app?.database?.tagDao()?.getMediaTagCounts()?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) })
    val tagCountMap = remember(tagCountsList) { tagCountsList.associate { it.mediaUri to it.count } }

    val groupedPhotos = remember(filtered, groupByDate) {
        if (groupByDate) {
            filtered.groupBy { formatDateHeader(it.dateModified) }
        } else {
            emptyMap()
        }
    }

    var isInitialLoading by remember {
        mutableStateOf(MediaStoreImageDataSource.getCachedImages() == null && photos.isEmpty())
    }

    LaunchedEffect(photos) {
        if (photos.isNotEmpty() || MediaStoreImageDataSource.getCachedImages() != null) {
            isInitialLoading = false
        }
    }

    if (isInitialLoading && filtered.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (filtered.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No Photos Found", style = MaterialTheme.typography.bodyLarge)
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
                columns = GridCells.Fixed(photoColumns),
                contentPadding = PaddingValues(top = 4.dp, start = 4.dp, end = 4.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (groupByDate && groupedPhotos.isNotEmpty()) {
                    groupedPhotos.forEach { (dateHeader, itemsInGroup) ->
                        item(
                            key = "header_$dateHeader",
                            span = { GridItemSpan(photoColumns) }
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

                        items(itemsInGroup, key = { it.id }) { item ->
                            val isSelected = selectionViewModel.isSelected(item)
                            val tagCount = tagCountMap[item.uri.toString()] ?: 0

                            PhotoTile(
                                item = item,
                                isSelectionMode = selectionViewModel.isSelectionMode,
                                isSelected = isSelected,
                                tagCount = tagCount,
                                onClick = {
                                    if (selectionViewModel.isSelectionMode) {
                                        selectionViewModel.toggleSelection(item)
                                    } else {
                                        onMediaClick(item)
                                    }
                                },
                                onLongClick = {
                                    if (selectionViewModel.isSelectionMode) {
                                        selectionViewModel.selectRange(item, filtered)
                                    } else {
                                        selectionViewModel.startSelection(item)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { item ->
                        val isSelected = selectionViewModel.isSelected(item)
                        val tagCount = tagCountMap[item.uri.toString()] ?: 0

                        PhotoTile(
                            item = item,
                            isSelectionMode = selectionViewModel.isSelectionMode,
                            isSelected = isSelected,
                            tagCount = tagCount,
                            onClick = {
                                if (selectionViewModel.isSelectionMode) {
                                    selectionViewModel.toggleSelection(item)
                                } else {
                                    onMediaClick(item)
                                }
                            },
                            onLongClick = {
                                if (selectionViewModel.isSelectionMode) {
                                    selectionViewModel.selectRange(item, filtered)
                                } else {
                                    selectionViewModel.startSelection(item)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoTile(
    item: MediaItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    tagCount: Int = 0,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
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
}