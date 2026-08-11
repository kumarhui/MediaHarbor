package com.mediaharbor.app.feature.gallery

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mediaharbor.app.MediaHarborApp
import com.mediaharbor.app.data.media.datasource.MediaStoreImageDataSource
import com.mediaharbor.app.data.media.datasource.MediaStorePdfDataSource
import com.mediaharbor.app.data.repository.MediaRepositoryImpl
import com.mediaharbor.app.data.settings.SettingsManager
import com.mediaharbor.app.data.settings.SortOption
import com.mediaharbor.app.domain.model.MediaItem
import com.mediaharbor.app.domain.usecase.GetPhotosUseCase
import com.mediaharbor.app.domain.usecase.SearchMediaUseCase
import com.mediaharbor.app.feature.selection.DragSelectContainer
import com.mediaharbor.app.feature.selection.SelectionViewModel
import kotlinx.coroutines.launch
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

    fun filterAndSortPhotos(photos: List<MediaItem>, query: String, sortOption: SortOption): List<MediaItem> {
        val filtered = searchUseCase(photos, query)
        return when (sortOption) {
            SortOption.DATE_MODIFIED_DESC -> filtered.sortedByDescending { it.dateModified }
            SortOption.DATE_MODIFIED_ASC -> filtered.sortedBy { it.dateModified }
            SortOption.DATE_ADDED_DESC -> filtered.sortedByDescending { it.dateAdded }
            SortOption.DATE_ADDED_ASC -> filtered.sortedBy { it.dateAdded }
            SortOption.SIZE_DESC -> filtered.sortedByDescending { it.size }
            SortOption.SIZE_ASC -> filtered.sortedBy { it.size }
            SortOption.NAME_ASC -> filtered.sortedBy { it.displayName.lowercase() }
            SortOption.NAME_DESC -> filtered.sortedByDescending { it.displayName.lowercase() }
        }
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
fun ModernVerticalScrubber(
    gridState: LazyGridState,
    scrollProgress: Float,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var trackHeightPx by remember { mutableFloatStateOf(0f) }

    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) 10.dp else 4.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "thumb_width"
    )
    val thumbHeight by animateDpAsState(
        targetValue = if (isDragging) 52.dp else 36.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "thumb_height"
    )
    val thumbAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0.6f,
        label = "thumb_alpha"
    )
    val trackAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.25f else 0.08f,
        label = "track_alpha"
    )

    Box(
        modifier = modifier
            .padding(end = 2.dp, top = 8.dp, bottom = 80.dp)
            .fillMaxHeight()
            .width(36.dp)
            .onGloballyPositioned { trackHeightPx = it.size.height.toFloat() }
            .pointerInput(gridState, trackHeightPx) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        if (trackHeightPx > 0f) {
                            val targetProgress = (offset.y / trackHeightPx).coerceIn(0f, 1f)
                            val totalItems = gridState.layoutInfo.totalItemsCount
                            if (totalItems > 0) {
                                val targetIndex = (targetProgress * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                                coroutineScope.launch {
                                    gridState.scrollToItem(targetIndex)
                                }
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (trackHeightPx > 0f) {
                            val targetProgress = (change.position.y / trackHeightPx).coerceIn(0f, 1f)
                            val totalItems = gridState.layoutInfo.totalItemsCount
                            if (totalItems > 0) {
                                val targetIndex = (targetProgress * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                                coroutineScope.launch {
                                    gridState.scrollToItem(targetIndex)
                                }
                            }
                        }
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false }
                )
            }
    ) {
        // Minimal track line
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 5.dp)
                .width(2.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = trackAlpha))
        )

        // Animated scrubber thumb
        Box(
            modifier = Modifier
                .align(
                    BiasAlignment(
                        horizontalBias = 1f,
                        verticalBias = (scrollProgress * 2f) - 1f
                    )
                )
                .padding(end = 1.dp)
                .width(thumbWidth)
                .height(thumbHeight)
                .shadow(
                    elevation = if (isDragging) 6.dp else 0.dp,
                    shape = CircleShape
                )
                .clip(CircleShape)
                .background(
                    if (isDragging) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primary.copy(alpha = thumbAlpha)
                )
        )
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
    val photoSortOrder by settingsManager.photoSortOrder.collectAsState()

    val viewModel = remember { GalleryViewModel(context) }
    val initialPhotos = remember { MediaStoreImageDataSource.getCachedImages() ?: emptyList() }
    val photos by viewModel.photosFlow.collectAsState(initial = initialPhotos)
    val sortedAndFiltered = remember(photos, searchQuery, photoSortOrder) {
        viewModel.filterAndSortPhotos(photos, searchQuery, photoSortOrder)
    }
    val gridState = rememberLazyGridState()

    val tagCountsList by (app?.database?.tagDao()?.getMediaTagCounts()?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) })
    val tagCountMap = remember(tagCountsList) { tagCountsList.associate { it.mediaUri to it.count } }

    val groupedPhotos = remember(sortedAndFiltered, groupByDate) {
        if (groupByDate) {
            sortedAndFiltered.groupBy { formatDateHeader(it.dateModified) }
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

    if (isInitialLoading && sortedAndFiltered.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (sortedAndFiltered.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No Photos Found", style = MaterialTheme.typography.bodyLarge)
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
                items = sortedAndFiltered,
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
                                            selectionViewModel.selectRange(item, sortedAndFiltered)
                                        } else {
                                            selectionViewModel.startSelection(item)
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        items(sortedAndFiltered, key = { it.id }) { item ->
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
                                        selectionViewModel.selectRange(item, sortedAndFiltered)
                                    } else {
                                        selectionViewModel.startSelection(item)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (sortedAndFiltered.size > 20) {
                ModernVerticalScrubber(
                    gridState = gridState,
                    scrollProgress = scrollProgress,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
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
    val context = LocalContext.current
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    val placeholderPainter = remember(placeholderColor) { ColorPainter(placeholderColor) }
    val imageRequest = remember(item.uri) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .crossfade(true)
            .build()
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(placeholderColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            placeholder = placeholderPainter,
            error = placeholderPainter,
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