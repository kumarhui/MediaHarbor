package com.mediaharbor.app.feature.selection

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.mediaharbor.app.domain.model.MediaItem
import kotlin.math.max
import kotlin.math.min

class SelectionViewModel : ViewModel() {
    // Selection order preserved in exact sequence chosen by user
    val selectedItems = mutableStateListOf<MediaItem>()

    var anchorItem by mutableStateOf<MediaItem?>(null)
        private set

    var isSelectionMode by mutableStateOf(false)
        private set

    fun toggleSelection(item: MediaItem) {
        val existingIndex = selectedItems.indexOfFirst { it.id == item.id }
        if (existingIndex != -1) {
            selectedItems.removeAt(existingIndex)
            if (anchorItem?.id == item.id) {
                anchorItem = selectedItems.lastOrNull()
            }
            if (selectedItems.isEmpty()) {
                isSelectionMode = false
                anchorItem = null
            }
        } else {
            selectedItems.add(item)
            anchorItem = item
            isSelectionMode = true
        }
    }

    fun startSelection(item: MediaItem) {
        if (selectedItems.none { it.id == item.id }) {
            selectedItems.add(item)
        }
        anchorItem = item
        isSelectionMode = true
    }

    fun selectRange(targetItem: MediaItem, displayedList: List<MediaItem>) {
        if (!isSelectionMode || anchorItem == null) {
            startSelection(targetItem)
            return
        }

        val anchorIndex = displayedList.indexOfFirst { it.id == anchorItem?.id }
        val targetIndex = displayedList.indexOfFirst { it.id == targetItem.id }

        if (anchorIndex == -1 || targetIndex == -1) {
            toggleSelection(targetItem)
            return
        }

        if (anchorIndex == targetIndex) {
            startSelection(targetItem)
            return
        }

        val rangeIndices = if (anchorIndex < targetIndex) {
            (anchorIndex + 1)..targetIndex
        } else {
            (anchorIndex - 1) downTo targetIndex
        }

        for (idx in rangeIndices) {
            val item = displayedList[idx]
            if (selectedItems.none { it.id == item.id }) {
                selectedItems.add(item)
            }
        }

        anchorItem = targetItem
        isSelectionMode = true
    }

    fun selectAll(items: List<MediaItem>) {
        selectedItems.clear()
        selectedItems.addAll(items)
        anchorItem = items.lastOrNull()
        if (items.isNotEmpty()) isSelectionMode = true
    }

    fun clearSelection() {
        selectedItems.clear()
        anchorItem = null
        isSelectionMode = false
    }

    fun isSelected(item: MediaItem): Boolean = selectedItems.any { it.id == item.id }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectionViewModel: SelectionViewModel,
    totalAvailableItems: List<MediaItem>,
    onShareSelected: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    val selectedCount = selectionViewModel.selectedItems.size

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                modifier = Modifier.size(40.dp)
            ) {
                IconButton(onClick = { selectionViewModel.clearSelection() }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Selection",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            AnimatedContent(
                targetState = selectedCount,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInVertically { height -> height } + fadeIn() togetherWith
                                slideOutVertically { height -> -height } + fadeOut()
                    } else {
                        slideInVertically { height -> -height } + fadeIn() togetherWith
                                slideOutVertically { height -> -height } + fadeOut()
                    }
                },
                modifier = Modifier.weight(1f),
                label = "selection_count_anim"
            ) { count ->
                Text(
                    text = if (count == 1) "1 item selected" else "$count items selected",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = {
                    if (selectedCount == totalAvailableItems.size) {
                        selectionViewModel.clearSelection()
                    } else {
                        selectionViewModel.selectAll(totalAvailableItems)
                    }
                }) {
                    Icon(
                        imageVector = if (selectedCount == totalAvailableItems.size)
                            Icons.Default.Deselect
                        else
                            Icons.Default.SelectAll,
                        contentDescription = "Select All"
                    )
                }

                IconButton(
                    onClick = onShareSelected,
                    enabled = selectedCount > 0
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Selected"
                    )
                }

                IconButton(
                    onClick = onDeleteSelected,
                    enabled = selectedCount > 0
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Selected",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}