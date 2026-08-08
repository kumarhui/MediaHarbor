package com.mediaharbor.app.feature.imageviewer

import android.app.Activity
import android.app.RecoverableSecurityException
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    var activeList by remember(mediaList) { mutableStateOf(mediaList.toList()) }

    if (activeList.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val initialPage = remember(initialIndex, activeList.size) { initialIndex.coerceIn(0, activeList.size - 1) }
    val pagerState = rememberPagerState(initialPage = initialPage) { activeList.size }

    var isControlsVisible by remember { mutableStateOf(true) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showInfoDialog by remember { mutableStateOf(false) }

    val safePage = pagerState.currentPage.coerceIn(0, activeList.size - 1)
    val currentMedia = activeList.getOrNull(safePage) ?: activeList[0]

    val app = context.applicationContext as MediaHarborApp
    val tagsState by app.database.tagDao().getTagsForMedia(currentMedia.uri.toString()).collectAsState(initial = emptyList())

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        panOffset = Offset.Zero
    }

    val isZoomed = scale > 1.05f

    var pendingDeleteMedia by remember { mutableStateOf<MediaItem?>(null) }

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingDeleteMedia?.let { deletedItem ->
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    try {
                        context.contentResolver.delete(deletedItem.uri, null, null)
                    } catch (_: Exception) {}
                }
                val newList = activeList.filter { it.id != deletedItem.id }
                Toast.makeText(context, "Image deleted", Toast.LENGTH_SHORT).show()
                if (newList.isEmpty()) {
                    onDismiss()
                } else {
                    activeList = newList
                    val nextTarget = safePage.coerceAtMost(newList.size - 1)
                    coroutineScope.launch {
                        pagerState.scrollToPage(nextTarget)
                    }
                }
            }
        } else {
            Toast.makeText(context, "Deletion cancelled", Toast.LENGTH_SHORT).show()
        }
        pendingDeleteMedia = null
    }

    fun executeDelete(item: MediaItem) {
        try {
            // First attempt direct deletion (succeeds if app owns the file)
            val rows = context.contentResolver.delete(item.uri, null, null)
            if (rows > 0) {
                val newList = activeList.filter { it.id != item.id }
                Toast.makeText(context, "Image deleted", Toast.LENGTH_SHORT).show()
                if (newList.isEmpty()) {
                    onDismiss()
                } else {
                    activeList = newList
                    val nextTarget = safePage.coerceAtMost(newList.size - 1)
                    coroutineScope.launch { pagerState.scrollToPage(nextTarget) }
                }
                return
            }
        } catch (secEx: SecurityException) {
            // Requires system authorization on Android 11+ (API 30+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(item.uri))
                    pendingDeleteMedia = item
                    deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    return
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not delete image", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        } catch (e: RecoverableSecurityException) {
            // Requires system authorization on Android 10 (API 29)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                pendingDeleteMedia = item
                deleteLauncher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
                return
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not delete image", Toast.LENGTH_SHORT).show()
            return
        }

        // Fallback for API 30+ if direct delete returned 0 without exception
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(item.uri))
                pendingDeleteMedia = item
                deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } catch (e: Exception) {
                Toast.makeText(context, "Could not delete image", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Could not delete image", Toast.LENGTH_SHORT).show()
        }
    }

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
            userScrollEnabled = !isZoomed,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, offsetY.roundToInt()) }
        ) { page ->
            val item = activeList[page]

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
                                    val maxPanX = (scale - 1f) * 500f
                                    val maxPanY = (scale - 1f) * 500f
                                    panOffset = Offset(
                                        x = (panOffset.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                                        y = (panOffset.y + panChange.y).coerceIn(-maxPanY, maxPanY)
                                    )
                                    event.changes.forEach { it.consume() }
                                }
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

        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .windowInsetsPadding(WindowInsets.systemBars)
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
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
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
                Box {
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
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                overflowExpanded = false
                                executeDelete(currentMedia)
                            }
                        )
                    }
                }
            }
        }

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
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(vertical = 8.dp)
            ) {
                if (tagsState.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { ShareHelper.shareViaWhatsApp(context, currentMedia.uri, currentMedia.mimeType) }) {
                        Icon(Icons.Default.Send, contentDescription = "WhatsApp Share", tint = Color(0xFF25D366))
                    }

                    IconButton(onClick = { PrintHelper.printMedia(context, currentMedia.uri) }) {
                        Icon(Icons.Default.Print, contentDescription = "Print", tint = Color.White)
                    }

                    IconButton(onClick = { Toast.makeText(context, "Tag action triggered", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Default.Label, contentDescription = "Tag", tint = Color.White)
                    }

                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
                    }

                    IconButton(onClick = { executeDelete(currentMedia) }) {
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