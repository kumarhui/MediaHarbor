package com.mediaharbor.app.feature.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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
    TopAppBar(
        title = {
            Text("${selectionViewModel.selectedItems.size} Selected")
        },
        navigationIcon = {
            IconButton(onClick = { selectionViewModel.clearSelection() }) {
                Icon(Icons.Default.Close, contentDescription = "Close Selection")
            }
        },
        actions = {
            IconButton(onClick = {
                if (selectionViewModel.selectedItems.size == totalAvailableItems.size) {
                    selectionViewModel.clearSelection()
                } else {
                    selectionViewModel.selectAll(totalAvailableItems)
                }
            }) {
                Icon(
                    if (selectionViewModel.selectedItems.size == totalAvailableItems.size) Icons.Default.Deselect else Icons.Default.SelectAll,
                    contentDescription = "Select All"
                )
            }
            IconButton(onClick = onShareSelected) {
                Icon(Icons.Default.Share, contentDescription = "Share Selected")
            }
            IconButton(onClick = onDeleteSelected) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}