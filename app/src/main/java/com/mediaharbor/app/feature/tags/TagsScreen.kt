package com.mediaharbor.app.feature.tags

import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.mediaharbor.app.MediaHarborApp
import com.mediaharbor.app.core.pdf.PdfRendererManager
import com.mediaharbor.app.data.local.entity.TagEntity
import com.mediaharbor.app.data.settings.SettingsManager
import com.mediaharbor.app.domain.model.MediaItem
import com.mediaharbor.app.domain.model.MediaType
import com.mediaharbor.app.feature.gallery.PhotoTile
import com.mediaharbor.app.feature.pdf.PdfGridCard
import com.mediaharbor.app.feature.pdf.PdfThumbnailView
import com.mediaharbor.app.feature.selection.DragSelectContainer
import com.mediaharbor.app.feature.selection.SelectionTopBar
import com.mediaharbor.app.feature.selection.SelectionViewModel
import com.mediaharbor.app.feature.sharing.ShareHelper
import kotlinx.coroutines.launch

private data class TagStats(
    val fileCount: Int = 0,
    val totalSize: Long = 0L,
    val photoCount: Int = 0,
    val pdfCount: Int = 0,
    val representativeMedia: MediaItem? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditTagDialog(
    title: String,
    initialName: String,
    initialColor: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: String) -> Unit
) {
    var tagName by remember { mutableStateOf(initialName) }
    var tagColor by remember { mutableStateOf(initialColor) }

    val presetColors = listOf(
        "#FF5722", "#3F51B5", "#4CAF50", "#009688",
        "#9C27B0", "#FF9800", "#607D8B", "#E91E63",
        "#F44336", "#8BC34A", "#00BCD4", "#673AB7", "#D32F2F"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = tagName,
                    onValueChange = { tagName = it },
                    label = { Text("Tag Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Select Color", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetColors.take(6).forEach { colorHex ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    try { Color(android.graphics.Color.parseColor(colorHex)) }
                                    catch (e: Exception) { Color.Gray }
                                )
                                .clickable { tagColor = colorHex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = tagName.isNotBlank(),
                onClick = { onConfirm(tagName.trim(), tagColor) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TagsScreen(
    allMediaItems: List<MediaItem>,
    onTagClick: (TagEntity) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as MediaHarborApp
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val tagColumns by settingsManager.tagColumns.collectAsState()
    val pdfManager = remember { PdfRendererManager(context) }
    val coroutineScope = rememberCoroutineScope()

    val rawTags by app.database.tagDao().getAllTags().collectAsState(initial = emptyList())
    val allCrossRefs by app.database.tagDao().getAllCrossRefs().collectAsState(initial = emptyList())

    val tags = remember(rawTags) { rawTags.distinctBy { it.name } }

    val tagStatsMap = remember(tags, allCrossRefs, allMediaItems) {
        val mediaByUri = allMediaItems.associateBy { it.uri.toString() }
        tags.associate { tag ->
            val matchedUris = allCrossRefs.filter { it.tagId == tag.id }.map { it.mediaUri }.toSet()
            val matchedItems = matchedUris.mapNotNull { mediaByUri[it] }
            val fileCount = matchedItems.size
            val totalSize = matchedItems.sumOf { it.size }
            val photoCount = matchedItems.count { it.mediaType == MediaType.IMAGE }
            val pdfCount = matchedItems.count { it.mediaType == MediaType.PDF }
            val representativeMedia = matchedItems.firstOrNull { it.mediaType == MediaType.IMAGE }
                ?: matchedItems.firstOrNull()
            tag.id to TagStats(fileCount, totalSize, photoCount, pdfCount, representativeMedia)
        }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var contextMenuTag by remember { mutableStateOf<TagEntity?>(null) }
    var tagToEdit by remember { mutableStateOf<TagEntity?>(null) }
    var tagToDelete by remember { mutableStateOf<TagEntity?>(null) }

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
            LazyVerticalGrid(
                columns = GridCells.Fixed(tagColumns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, start = 12.dp, end = 12.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tags, key = { it.id }) { tag ->
                    val stats = tagStatsMap[tag.id] ?: TagStats()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onTagClick(tag) },
                                onLongClick = { contextMenuTag = tag }
                            )
                    ) {
                        // Top Media Thumbnail with fully rounded corners on all sides
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            val rep = stats.representativeMedia
                            if (rep != null) {
                                if (rep.mediaType == MediaType.IMAGE) {
                                    AsyncImage(
                                        model = rep.uri,
                                        contentDescription = tag.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    PdfThumbnailView(
                                        pdfManager = pdfManager,
                                        pdf = rep,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            try { Color(android.graphics.Color.parseColor(tag.colorHex)) }
                                            catch (e: Exception) { Color.Gray }
                                        )
                                )
                            }
                        }

                        // Info Section sitting directly on the transparent background
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (stats.fileCount == 1) "1 file" else "${stats.fileCount} files",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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

    contextMenuTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { contextMenuTag = null },
            title = { Text(tag.name) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            val t = contextMenuTag
                            contextMenuTag = null
                            tagToEdit = t
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Edit Tag", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    TextButton(
                        onClick = {
                            val t = contextMenuTag
                            contextMenuTag = null
                            tagToDelete = t
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Delete Tag", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { contextMenuTag = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreateDialog) {
        CreateEditTagDialog(
            title = "Create Tag",
            initialName = "",
            initialColor = "#FF5722",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name: String, color: String ->
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
            onConfirm = { name: String, color: String ->
                coroutineScope.launch {
                    app.database.tagDao().updateTag(tag.id, name, color)
                }
                tagToEdit = null
            }
        )
    }

    tagToDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { tagToDelete = null },
            title = { Text("Delete ${tag.name}?") },
            text = { Text("This tag will be removed from all associated files.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        coroutineScope.launch {
                            app.database.tagDao().deleteTag(tag.id)
                        }
                        tagToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { tagToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TagCollectionScreen(
    tag: TagEntity,
    allMediaItems: List<MediaItem>,
    onBack: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    selectionViewModel: SelectionViewModel = viewModel()
) {
    val context = LocalContext.current
    val app = context.applicationContext as? MediaHarborApp
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val photoColumns by settingsManager.photoColumns.collectAsState()
    val pdfManager = remember { PdfRendererManager(context) }

    val assignedUris by (app?.database?.tagDao()?.getMediaUrisForTag(tag.id)?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) })

    val tagCountsList by (app?.database?.tagDao()?.getMediaTagCounts()?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) })
    val tagCountMap = remember(tagCountsList) { tagCountsList.associate { it.mediaUri to it.count } }

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

    var pendingBatchDeleteItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    val batchDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "Deleted ${pendingBatchDeleteItems.size} items", Toast.LENGTH_SHORT).show()
            selectionViewModel.clearSelection()
        } else {
            Toast.makeText(context, "Deletion cancelled", Toast.LENGTH_SHORT).show()
        }
        pendingBatchDeleteItems = emptyList()
    }

    BackHandler(enabled = selectionViewModel.isSelectionMode) {
        selectionViewModel.clearSelection()
    }

    Scaffold(
        topBar = {
            if (selectionViewModel.isSelectionMode) {
                SelectionTopBar(
                    selectionViewModel = selectionViewModel,
                    totalAvailableItems = filteredMedia,
                    onShareSelected = {
                        ShareHelper.shareMultiple(context, selectionViewModel.selectedItems)
                    },
                    onDeleteSelected = {
                        val itemsToDelete = selectionViewModel.selectedItems.toList()
                        if (itemsToDelete.isNotEmpty()) {
                            val uris = itemsToDelete.map { it.uri }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
                                    pendingBatchDeleteItems = itemsToDelete
                                    batchDeleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                                } catch (e: Exception) {
                                    var count = 0
                                    itemsToDelete.forEach { item ->
                                        try {
                                            if (context.contentResolver.delete(item.uri, null, null) > 0) count++
                                        } catch (_: Exception) {}
                                    }
                                    Toast.makeText(context, "Deleted $count items", Toast.LENGTH_SHORT).show()
                                    selectionViewModel.clearSelection()
                                }
                            } else {
                                var count = 0
                                itemsToDelete.forEach { item ->
                                    try {
                                        if (context.contentResolver.delete(item.uri, null, null) > 0) count++
                                    } catch (_: Exception) {}
                                }
                                Toast.makeText(context, "Deleted $count items", Toast.LENGTH_SHORT).show()
                                selectionViewModel.clearSelection()
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                    title = { Text("${tag.name} (${filteredMedia.size})") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
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
                        columns = GridCells.Fixed(photoColumns),
                        contentPadding = PaddingValues(top = 4.dp, start = 4.dp, end = 4.dp, bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredMedia, key = { it.id }) { item ->
                            val isSelected = selectionViewModel.isSelected(item)
                            val tagCount = tagCountMap[item.uri.toString()] ?: 0

                            if (item.mediaType == MediaType.IMAGE) {
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
                                            selectionViewModel.selectRange(item, filteredMedia)
                                        } else {
                                            selectionViewModel.startSelection(item)
                                        }
                                    }
                                )
                            } else {
                                PdfGridCard(
                                    pdf = item,
                                    pdfManager = pdfManager,
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
                                            selectionViewModel.selectRange(item, filteredMedia)
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
    }
}