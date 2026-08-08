package com.mediaharbor.app.feature.selection

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.mediaharbor.app.domain.model.MediaItem

class SelectionViewModel : ViewModel() {
    val selectedItems = mutableStateListOf<MediaItem>()
    var isSelectionMode by mutableStateOf(false)
        private set

    fun toggleSelection(item: MediaItem) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item)
            if (selectedItems.isEmpty()) isSelectionMode = false
        } else {
            selectedItems.add(item)
            isSelectionMode = true
        }
    }

    fun startSelection(item: MediaItem) {
        if (!selectedItems.contains(item)) {
            selectedItems.add(item)
        }
        isSelectionMode = true
    }

    fun selectAll(items: List<MediaItem>) {
        selectedItems.clear()
        selectedItems.addAll(items)
        if (items.isNotEmpty()) isSelectionMode = true
    }

    fun clearSelection() {
        selectedItems.clear()
        isSelectionMode = false
    }

    fun isSelected(item: MediaItem): Boolean = selectedItems.contains(item)
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
                                slideOutVertically { height -> height } + fadeOut()
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