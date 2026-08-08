package com.mediaharbor.app.feature.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mediaharbor.app.MediaHarborApp
import com.mediaharbor.app.data.local.entity.TagEntity
import com.mediaharbor.app.domain.model.MediaItem
import com.mediaharbor.app.domain.model.MediaType
import com.mediaharbor.app.feature.selection.DragSelectContainer
import com.mediaharbor.app.feature.selection.SelectionViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(onTagClick: (TagEntity) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MediaHarborApp
    val coroutineScope = rememberCoroutineScope()
    val rawTags by app.database.tagDao().getAllTags().collectAsState(initial = emptyList())

    val tags = remember(rawTags) { rawTags.distinctBy { it.name } }

    var showCreateDialog by remember { mutableStateOf(false) }
    var tagToEdit by remember { mutableStateOf<TagEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (tags.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No tags created yet", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tags, key = { it.id }) { tag ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTagClick(tag) }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        try { Color(android.graphics.Color.parseColor(tag.colorHex)) }
                                        catch (e: Exception) { Color.Gray }
                                    )
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                tag.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { tagToEdit = tag }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Tag")
                            }
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    app.database.tagDao().deleteTag(tag.id)
                                }
                            }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete Tag",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create Tag")
        }
    }

    if (showCreateDialog) {
        CreateEditTagDialog(
            title = "Create Tag",
            initialName = "",
            initialColor = "#FF5722",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, color ->
                coroutineScope.launch {
                    app.database.tagDao().insertTag(TagEntity(name = name, colorHex = color))
                }
                showCreateDialog = false
            }
        )
    }

    tagToEdit?.let { tag ->
        CreateEditTagDialog(
            title = "Edit Tag",
            initialName = tag.name,
            initialColor = tag.colorHex,
            onDismiss = { tagToEdit = null },
            onConfirm = { name, color ->
                coroutineScope.launch {
                    app.database.tagDao().updateTag(tag.id, name, color)
                }
                tagToEdit = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagCollectionScreen(
    tag: TagEntity,
    allMediaItems: List<MediaItem>,
    onBack: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    selectionViewModel: SelectionViewModel = remember { SelectionViewModel() }
) {
    val context = LocalContext.current
    val app = context.applicationContext as MediaHarborApp
    val assignedUris by app.database.tagDao().getMediaUrisForTag(tag.id).collectAsState(initial = emptyList())
    val gridState = rememberLazyGridState()

    var selectedFilter by remember { mutableStateOf("All") }

    val filteredMedia = remember(assignedUris, allMediaItems, selectedFilter) {
        val matching = allMediaItems.filter { item -> assignedUris.contains(item.uri.toString()) }
        when (selectedFilter) {
            "Photos" -> matching.filter { it.mediaType == MediaType.IMAGE }
            "PDFs" -> matching.filter { it.mediaType == MediaType.PDF }
            else -> matching
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${tag.name} (${filteredMedia.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Photos", "PDFs").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) }
                    )
                }
            }

            if (filteredMedia.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No media assigned to this tag", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                DragSelectContainer(
                    gridState = gridState,
                    items = filteredMedia,
                    selectionViewModel = selectionViewModel,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredMedia, key = { it.id }) { item ->
                            val isSelected = selectionViewModel.isSelected(item)
                            Card(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clickable {
                                        if (selectionViewModel.isSelectionMode) {
                                            selectionViewModel.toggleSelection(item)
                                        } else {
                                            onMediaClick(item)
                                        }
                                    }
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (item.mediaType == MediaType.IMAGE) {
                                        AsyncImage(
                                            model = item.uri,
                                            contentDescription = item.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.PictureAsPdf,
                                                contentDescription = "PDF",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(48.dp)
                                            )
                                        }
                                    }

                                    if (selectionViewModel.isSelectionMode) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(if (isSelected) Color.Black.copy(alpha = 0.4f) else Color.Transparent)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateEditTagDialog(
    title: String,
    initialName: String,
    initialColor: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, colorHex: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableStateOf(initialColor) }

    val presetColors = listOf(
        "#FF5722", "#3F51B5", "#4CAF50", "#009688",
        "#9C27B0", "#FF9800", "#607D8B", "#E91E63", "#F44336"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tag Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Select Tag Color", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    presetColors.take(5).forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .clickable { selectedColor = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name, selectedColor) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}