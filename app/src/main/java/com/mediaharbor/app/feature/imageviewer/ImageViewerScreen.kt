package com.mediaharbor.app.feature.imageviewer

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mediaharbor.app.MediaHarborApp
import com.mediaharbor.app.data.local.entity.MediaTagCrossRef
import com.mediaharbor.app.domain.model.MediaItem
import com.mediaharbor.app.domain.usecase.ConvertImageToPdfUseCase
import com.mediaharbor.app.feature.sharing.PrintHelper
import com.mediaharbor.app.feature.sharing.ShareHelper
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewerScreen(
    mediaList: List<MediaItem>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    if (mediaList.isEmpty()) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val initialPage = remember(initialIndex, mediaList.size) { initialIndex.coerceIn(0, mediaList.size - 1) }
    val pagerState = rememberPagerState(initialPage = initialPage) { mediaList.size }

    var isControlsVisible by remember { mutableStateOf(true) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showInfoDialog by remember { mutableStateOf(false) }

    val currentMedia = mediaList.getOrNull(pagerState.currentPage) ?: mediaList[0]

    val app = context.applicationContext as MediaHarborApp
    val tagsState by app.database.tagDao().getTagsForMedia(currentMedia.uri.toString()).collectAsState(initial = emptyList())

    // Zoom and pan state for the current image
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Reset zoom state when changing pages
    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        panOffset = Offset.Zero
    }

    val isZoomed = scale > 1.05f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = ((1f - (offsetY / 1000f)).coerceIn(0.2f, 1f))))
            .pointerInput(isZoomed) {
                if (!isZoomed) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
                        },
                        onDragEnd = {
                            if (offsetY > 300f) onDismiss() else offsetY = 0f
                        }
                    )
                }
            }
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isZoomed, // Enable pager scrolling when unzoomed
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, offsetY.roundToInt()) }
        ) { page ->
            val item = mediaList[page]

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { isControlsVisible = !isControlsVisible },
                            onDoubleTap = {
                                if (scale > 1.05f) {
                                    scale = 1f
                                    panOffset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()

                                if (zoomChange != 1f) {
                                    scale = (scale * zoomChange).coerceIn(1f, 5f)
                                    if (scale <= 1f) panOffset = Offset.Zero
                                    event.changes.forEach { it.consume() }
                                } else if (scale > 1.05f && panChange != Offset.Zero) {
                                    // Only consume drag events when zoomed in
                                    val maxPanX = (scale - 1f) * 500f
                                    val maxPanY = (scale - 1f) * 500f
                                    panOffset = Offset(
                                        x = (panOffset.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                                        y = (panOffset.y + panChange.y).coerceIn(-maxPanY, maxPanY)
                                    )
                                    event.changes.forEach { it.consume() }
                                }
                                // When scale <= 1.05f and unzoomed, events pass to HorizontalPager
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = panOffset.x
                            translationY = panOffset.y
                        }
                )
            }
        }

        // On-screen Zoom In / Zoom Out Controls
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(
                    onClick = { scale = (scale + 0.5f).coerceAtMost(5f) },
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
                }

                FloatingActionButton(
                    onClick = {
                        scale = (scale - 0.5f).coerceAtLeast(1f)
                        if (scale == 1f) panOffset = Offset.Zero
                    },
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
                }
            }
        }

        // Top Bar
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    currentMedia.displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                var overflowExpanded by remember { mutableStateOf(false) }
                IconButton(onClick = { overflowExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
                }
                DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Convert to PDF") },
                        onClick = {
                            overflowExpanded = false
                            coroutineScope.launch {
                                ConvertImageToPdfUseCase(context)(currentMedia.uri, currentMedia.displayName.removeSuffix(".jpg"))
                                Toast.makeText(context, "Converted to PDF in Documents/MediaHarbor", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share With...") },
                        onClick = {
                            overflowExpanded = false
                            ShareHelper.shareGeneral(context, currentMedia.uri, currentMedia.mimeType)
                        }
                    )
                }
            }
        }

        // Bottom Bar & Tag Area
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                if (tagsState.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tagsState.forEach { tag ->
                            Surface(
                                shape = CircleShape,
                                color = try { Color(android.graphics.Color.parseColor(tag.colorHex)) } catch (e: Exception) { Color.Gray }
                            ) {
                                Text(
                                    tag.name,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // EXACTLY 5 ICONS (No labels, no overflow)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { ShareHelper.shareViaWhatsApp(context, currentMedia.uri, currentMedia.mimeType) }) {
                        Icon(Icons.Default.Share, contentDescription = "WhatsApp Share", tint = Color.Green)
                    }

                    IconButton(onClick = { PrintHelper.printMedia(context, currentMedia.uri) }) {
                        Icon(Icons.Default.Print, contentDescription = "Print", tint = Color.White)
                    }

                    var showTagPicker by remember { mutableStateOf(false) }
                    IconButton(onClick = { showTagPicker = true }) {
                        Icon(Icons.Default.Label, contentDescription = "Tag", tint = Color.White)
                    }
                    if (showTagPicker) {
                        TagPickerModal(mediaUri = currentMedia.uri.toString(), onDismiss = { showTagPicker = false })
                    }

                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
                    }

                    IconButton(onClick = { Toast.makeText(context, "Delete action triggered", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                    }
                }
            }
        }

        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { Text("File Information") },
                text = {
                    Column {
                        Text("Name: ${currentMedia.displayName}")
                        Text("Path: ${currentMedia.relativePath}")
                        Text("Size: ${currentMedia.size / 1024} KB")
                        Text("Resolution: ${currentMedia.width} x ${currentMedia.height}")
                    }
                },
                confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("OK") } }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagPickerModal(mediaUri: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MediaHarborApp
    val coroutineScope = rememberCoroutineScope()
    val allTags by app.database.tagDao().getAllTags().collectAsState(initial = emptyList())
    val assignedTags by app.database.tagDao().getTagsForMedia(mediaUri).collectAsState(initial = emptyList())

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Assign Tags", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn {
                items(allTags.distinctBy { it.name }) { tag ->
                    val isAssigned = assignedTags.any { it.id == tag.id }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    if (isAssigned) {
                                        app.database.tagDao().removeTagFromMedia(mediaUri, tag.id)
                                    } else {
                                        app.database.tagDao().addTagToMedia(MediaTagCrossRef(mediaUri, tag.id))
                                    }
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isAssigned, onCheckedChange = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tag.name)
                    }
                }
            }
        }
    }
}