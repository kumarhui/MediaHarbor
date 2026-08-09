package com.mediaharbor.app.feature.selection

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.mediaharbor.app.domain.model.MediaItem
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

@Composable
fun DragSelectContainer(
    gridState: LazyGridState,
    items: List<MediaItem>,
    selectionViewModel: SelectionViewModel,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var dragInitialIndex by remember { mutableStateOf<Int?>(null) }
    var currentDragIndex by remember { mutableStateOf<Int?>(null) }
    var currentPointerY by remember { mutableFloatStateOf(0f) }
    var isAutoScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(isAutoScrolling, currentPointerY) {
        if (isAutoScrolling && dragInitialIndex != null) {
            val scrollThreshold = 150f
            val maxScrollSpeed = 30f

            while (isAutoScrolling) {
                val gridHeight = gridState.layoutInfo.viewportSize.height.toFloat()
                val scrollDelta = when {
                    currentPointerY < scrollThreshold -> {
                        val factor = ((scrollThreshold - currentPointerY) / scrollThreshold).coerceIn(0f, 1f)
                        -maxScrollSpeed * factor
                    }
                    currentPointerY > gridHeight - scrollThreshold -> {
                        val factor = ((currentPointerY - (gridHeight - scrollThreshold)) / scrollThreshold).coerceIn(0f, 1f)
                        maxScrollSpeed * factor
                    }
                    else -> 0f
                }

                if (scrollDelta != 0f) {
                    gridState.scrollBy(scrollDelta)
                    val hitItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                        val top = item.offset.y.toFloat()
                        val bottom = top + item.size.height.toFloat()
                        currentPointerY >= top && currentPointerY <= bottom
                    }
                    hitItem?.let { hit ->
                        val mediaIndex = items.indexOfFirst { it.id == hit.key }
                        if (mediaIndex != -1) {
                            currentDragIndex = mediaIndex
                            dragInitialIndex?.let { start ->
                                val range = min(start, mediaIndex)..max(start, mediaIndex)
                                items.forEachIndexed { idx, item ->
                                    if (idx in range) {
                                        if (!selectionViewModel.isSelected(item)) {
                                            selectionViewModel.startSelection(item)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                delay(16)
            }
        }
    }

    Box(
        modifier = modifier.pointerInput(items, selectionViewModel.isSelectionMode) {
            val processDragAt = { position: Offset ->
                currentPointerY = position.y
                val gridHeight = gridState.layoutInfo.viewportSize.height.toFloat()
                val scrollThreshold = 150f
                isAutoScrolling = currentPointerY < scrollThreshold || currentPointerY > gridHeight - scrollThreshold

                val hitItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                    position.x >= item.offset.x &&
                            position.x <= item.offset.x + item.size.width &&
                            position.y >= item.offset.y &&
                            position.y <= item.offset.y + item.size.height
                }

                hitItem?.let { hit ->
                    // Map grid layout item key strictly to MediaItem list index
                    val mediaIndex = items.indexOfFirst { it.id == hit.key }
                    if (mediaIndex != -1) {
                        val start = dragInitialIndex ?: mediaIndex
                        if (dragInitialIndex == null) dragInitialIndex = mediaIndex
                        currentDragIndex = mediaIndex

                        val range = min(start, mediaIndex)..max(start, mediaIndex)
                        items.forEachIndexed { idx, item ->
                            if (idx in range) {
                                if (!selectionViewModel.isSelected(item)) {
                                    selectionViewModel.startSelection(item)
                                }
                            }
                        }
                    }
                }
            }

            if (selectionViewModel.isSelectionMode) {
                detectDragGestures(
                    onDragStart = { offset -> processDragAt(offset) },
                    onDrag = { change, _ ->
                        change.consume()
                        processDragAt(change.position)
                    },
                    onDragEnd = {
                        dragInitialIndex = null
                        currentDragIndex = null
                        isAutoScrolling = false
                    },
                    onDragCancel = {
                        dragInitialIndex = null
                        currentDragIndex = null
                        isAutoScrolling = false
                    }
                )
            } else {
                // Press & Hold -> enters selection mode and continues dragging in one fluid motion
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset -> processDragAt(offset) },
                    onDrag = { change, _ ->
                        change.consume()
                        processDragAt(change.position)
                    },
                    onDragEnd = {
                        dragInitialIndex = null
                        currentDragIndex = null
                        isAutoScrolling = false
                    },
                    onDragCancel = {
                        dragInitialIndex = null
                        currentDragIndex = null
                        isAutoScrolling = false
                    }
                )
            }
        }
    ) {
        content()
    }
}