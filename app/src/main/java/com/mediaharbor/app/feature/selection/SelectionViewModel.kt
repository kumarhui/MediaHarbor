package com.mediaharbor.app.feature.selection

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import coil3.compose.AsyncImage
import com.mediaharbor.app.MediaHarborApp
import com.mediaharbor.app.core.common.FileUtils
import com.mediaharbor.app.core.pdf.PdfRendererManager
import com.mediaharbor.app.data.local.entity.MediaTagCrossRef
import com.mediaharbor.app.domain.model.MediaItem
import com.mediaharbor.app.domain.model.MediaType
import com.mediaharbor.app.domain.usecase.ConvertImagesToIdCardPdfUseCase
import com.mediaharbor.app.domain.usecase.ConvertMultipleImagesToPdfUseCase
import com.mediaharbor.app.feature.pdf.PdfThumbnailView
import com.mediaharbor.app.feature.sharing.PrintHelper
import com.mediaharbor.app.feature.sharing.ShareHelper
import kotlinx.coroutines.launch
import kotlin.math.ceil

class SelectionViewModel : ViewModel() {
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

    fun removeSelection(item: MediaItem) {
        selectedItems.removeAll { it.id == item.id }
        if (anchorItem?.id == item.id) {
            anchorItem = selectedItems.lastOrNull()
        }
        if (selectedItems.isEmpty()) {
            isSelectionMode = false
            anchorItem = null
        }
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val selectedCount = selectionViewModel.selectedItems.size
    val imageItems = remember(selectionViewModel.selectedItems.toList()) {
        selectionViewModel.selectedItems.filter { it.mediaType == MediaType.IMAGE }
    }

    var showSelectedFilesPanel by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    var showConvertToPdfDialog by remember { mutableStateOf(false) }
    var showIdCardLayoutDialog by remember { mutableStateOf(false) }
    var showBatchTagDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { selectionViewModel.clearSelection() }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel Selection"
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            AnimatedContent(
                targetState = selectedCount,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                modifier = Modifier.weight(1f),
                label = "selection_count_anim"
            ) { count ->
                Text(
                    text = if (count == 1) "1 item" else "$count items",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Selected Files Panel Icon
                BadgedBox(
                    badge = {
                        if (selectedCount > 0) {
                            Badge { Text("$selectedCount") }
                        }
                    }
                ) {
                    IconButton(onClick = { showSelectedFilesPanel = true }) {
                        Icon(
                            imageVector = Icons.Default.CollectionsBookmark,
                            contentDescription = "Selected Files Panel"
                        )
                    }
                }

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

                // Overflow Menu
                Box {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                    }

                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Print With") },
                            leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                PrintHelper.printMultiple(context, selectionViewModel.selectedItems)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Open With") },
                            leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                ShareHelper.openWithMultiple(context, selectionViewModel.selectedItems)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Share With...") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                ShareHelper.shareMultiple(context, selectionViewModel.selectedItems)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Share to WhatsApp") },
                            leadingIcon = { Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF25D366)) },
                            onClick = {
                                showOverflowMenu = false
                                ShareHelper.shareToWhatsAppMultiple(context, selectionViewModel.selectedItems)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Print With NokoPrint") },
                            leadingIcon = { Icon(Icons.Default.LocalPrintshop, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                PrintHelper.printWithNokoPrint(context, selectionViewModel.selectedItems)
                            }
                        )

                        Divider()

                        DropdownMenuItem(
                            text = { Text("Convert to PDF (${imageItems.size} images)") },
                            enabled = imageItems.isNotEmpty(),
                            leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                showConvertToPdfDialog = true
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Convert to ID Card Layout (2/A4)") },
                            enabled = imageItems.isNotEmpty(),
                            leadingIcon = { Icon(Icons.Default.ContactPage, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                showIdCardLayoutDialog = true
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Add Tags") },
                            leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                            onClick = {
                                showOverflowMenu = false
                                showBatchTagDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSelectedFilesPanel) {
        SelectedFilesPanelModal(
            selectedItems = selectionViewModel.selectedItems,
            onRemove = { selectionViewModel.removeSelection(it) },
            onClearAll = {
                selectionViewModel.clearSelection()
                showSelectedFilesPanel = false
            },
            onDismiss = { showSelectedFilesPanel = false }
        )
    }

    if (showConvertToPdfDialog && imageItems.isNotEmpty()) {
        ConvertToPdfConfirmationDialog(
            imageItems = imageItems,
            onDismiss = { showConvertToPdfDialog = false },
            onConfirm = { outputName ->
                showConvertToPdfDialog = false
                coroutineScope.launch {
                    val result = ConvertMultipleImagesToPdfUseCase(context)(
                        imageUris = imageItems.map { it.uri },
                        outputFileName = outputName,
                        onProgress = { _, _ -> }
                    )
                    if (result.isSuccess) {
                        Toast.makeText(context, "Created PDF: Documents/MediaHarbor/$outputName.pdf", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to create PDF", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showIdCardLayoutDialog && imageItems.isNotEmpty()) {
        IdCardLayoutPreviewDialog(
            imageItems = imageItems,
            onDismiss = { showIdCardLayoutDialog = false },
            onConfirm = { outputName ->
                showIdCardLayoutDialog = false
                coroutineScope.launch {
                    val result = ConvertImagesToIdCardPdfUseCase(context).createPdf(
                        imageUris = imageItems.map { it.uri },
                        outputFileName = outputName,
                        onProgress = { _, _ -> }
                    )
                    if (result.isSuccess) {
                        Toast.makeText(context, "Created ID Card PDF: Documents/MediaHarbor/$outputName.pdf", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to create ID Card PDF", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showBatchTagDialog) {
        BatchTagPickerDialog(
            selectedItems = selectionViewModel.selectedItems,
            onDismiss = { showBatchTagDialog = false }
        )
    }
}

@Composable
fun SelectedFilesPanelModal(
    selectedItems: List<MediaItem>,
    onRemove: (MediaItem) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pdfManager = remember { PdfRendererManager(context) }
    var filterTab by remember { mutableStateOf("All") }

    val displayedItems = remember(selectedItems, filterTab) {
        when (filterTab) {
            "Photos" -> selectedItems.filter { it.mediaType == MediaType.IMAGE }
            "PDFs" -> selectedItems.filter { it.mediaType == MediaType.PDF }
            else -> selectedItems
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Selected Files (${selectedItems.size})")
                TextButton(onClick = onClearAll) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterTab == "All",
                        onClick = { filterTab = "All" },
                        label = { Text("All (${selectedItems.size})") }
                    )
                    FilterChip(
                        selected = filterTab == "Photos",
                        onClick = { filterTab = "Photos" },
                        label = { Text("Photos (${selectedItems.count { it.mediaType == MediaType.IMAGE }})") }
                    )
                    FilterChip(
                        selected = filterTab == "PDFs",
                        onClick = { filterTab = "PDFs" },
                        label = { Text("PDFs (${selectedItems.count { it.mediaType == MediaType.PDF }})") }
                    )
                }

                if (displayedItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No items selected in this view", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayedItems, key = { it.id }) { item ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Gray.copy(alpha = 0.2f))
                                    ) {
                                        if (item.mediaType == MediaType.IMAGE) {
                                            AsyncImage(
                                                model = item.uri,
                                                contentDescription = item.displayName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            PdfThumbnailView(
                                                pdfManager = pdfManager,
                                                pdf = item,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            item.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (item.mediaType == MediaType.IMAGE)
                                                    MaterialTheme.colorScheme.primaryContainer
                                                else
                                                    MaterialTheme.colorScheme.tertiaryContainer
                                            ) {
                                                Text(
                                                    if (item.mediaType == MediaType.IMAGE) "IMAGE" else "PDF",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                FileUtils.formatFileSize(item.size),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    IconButton(onClick = { onRemove(item) }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun ConvertToPdfConfirmationDialog(
    imageItems: List<MediaItem>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var fileNameInput by remember { mutableStateOf("Combined_Images_${System.currentTimeMillis() / 1000}") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Combine Images into Single PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Selected ${imageItems.size} image(s) will be merged into a single PDF document in selection order.")
                Text("Destination: Documents/MediaHarbor/", style = MaterialTheme.typography.labelMedium, color = Color.Gray)

                OutlinedTextField(
                    value = fileNameInput,
                    onValueChange = { fileNameInput = it },
                    label = { Text("Output PDF Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = fileNameInput.isNotBlank(),
                onClick = { onConfirm(fileNameInput.trim()) }
            ) {
                Text("Combine & Convert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun IdCardLayoutPreviewDialog(
    imageItems: List<MediaItem>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val context = LocalContext.current
    var fileNameInput by remember { mutableStateOf("ID_Cards_${System.currentTimeMillis() / 1000}") }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    val totalPages = remember(imageItems) { ceil(imageItems.size / 2.0).toInt().coerceAtLeast(1) }

    var previewBitmap by remember(currentPageIndex, imageItems) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(currentPageIndex, imageItems) {
        previewBitmap = ConvertImagesToIdCardPdfUseCase(context).generatePreviewBitmap(
            imageItems.map { it.uri },
            currentPageIndex
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ID Card A4 Layout Preview") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Arranges 2 ID-card images per A4 page proportionally. (${imageItems.size} images = $totalPages page(s))",
                    style = MaterialTheme.typography.bodySmall
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    val currentBmp = previewBitmap
                    if (currentBmp != null) {
                        Image(
                            bitmap = currentBmp.asImageBitmap(),
                            contentDescription = "A4 Page Preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(color = Color.White)
                    }
                }

                if (totalPages > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            enabled = currentPageIndex > 0,
                            onClick = { currentPageIndex-- }
                        ) {
                            Text("Previous")
                        }
                        Text("Page ${currentPageIndex + 1} of $totalPages", style = MaterialTheme.typography.labelMedium)
                        TextButton(
                            enabled = currentPageIndex < totalPages - 1,
                            onClick = { currentPageIndex++ }
                        ) {
                            Text("Next")
                        }
                    }
                }

                OutlinedTextField(
                    value = fileNameInput,
                    onValueChange = { fileNameInput = it },
                    label = { Text("PDF Document Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = fileNameInput.isNotBlank(),
                onClick = { onConfirm(fileNameInput.trim()) }
            ) {
                Text("Create PDF")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BatchTagPickerDialog(
    selectedItems: List<MediaItem>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as MediaHarborApp
    val coroutineScope = rememberCoroutineScope()

    val allTagsRaw by app.database.tagDao().getAllTags().collectAsState(initial = emptyList())
    val allTags = remember(allTagsRaw) { allTagsRaw.distinctBy { it.name } }

    var checkedTagIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apply Tags to ${selectedItems.size} Selected Items") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (allTags.isEmpty()) {
                    Text("No tags available in database", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                    ) {
                        items(allTags, key = { it.id }) { tag ->
                            val isChecked = checkedTagIds.contains(tag.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        checkedTagIds = if (isChecked) checkedTagIds - tag.id else checkedTagIds + tag.id
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        checkedTagIds = if (checked) checkedTagIds + tag.id else checkedTagIds - tag.id
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(
                                            try { Color(android.graphics.Color.parseColor(tag.colorHex)) }
                                            catch (e: Exception) { Color.Gray }
                                        )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(tag.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = checkedTagIds.isNotEmpty(),
                onClick = {
                    coroutineScope.launch {
                        selectedItems.forEach { item ->
                            checkedTagIds.forEach { tagId ->
                                app.database.tagDao().addTagToMedia(MediaTagCrossRef(item.uri.toString(), tagId))
                            }
                        }
                        Toast.makeText(context, "Applied tags to ${selectedItems.size} files", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                }
            ) {
                Text("Apply Tags")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}