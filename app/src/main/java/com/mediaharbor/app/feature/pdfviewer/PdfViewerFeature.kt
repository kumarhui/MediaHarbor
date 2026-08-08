package com.mediaharbor.app.feature.pdfviewer

import android.app.Activity
import android.app.RecoverableSecurityException
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mediaharbor.app.core.pdf.PdfRendererManager
import com.mediaharbor.app.core.pdf.PdfSession
import com.mediaharbor.app.domain.model.MediaItem
import com.mediaharbor.app.domain.usecase.ConvertPdfToImagesUseCase
import com.mediaharbor.app.feature.sharing.PrintHelper
import com.mediaharbor.app.feature.sharing.ShareHelper
import com.mediaharbor.app.feature.tags.components.TagPickerDialog
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

@Composable
fun PdfPageRenderCard(
    pdfManager: PdfRendererManager,
    pdfSession: PdfSession?,
    mediaItem: MediaItem,
    pageIndex: Int,
    onTap: () -> Unit
) {
    var bitmap by remember(mediaItem.uri, pageIndex) {
        mutableStateOf<Bitmap?>(pdfManager.getCachedPage(mediaItem.uri, pageIndex))
    }

    LaunchedEffect(pdfSession, pageIndex) {
        if (bitmap == null && pdfSession != null) {
            bitmap = pdfManager.renderPage(pdfSession, mediaItem.uri, pageIndex)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val currentBitmap = bitmap
            if (currentBitmap != null) {
                Image(
                    bitmap = currentBitmap.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(media: MediaItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pdfManager = remember { PdfRendererManager(context) }
    val listState = rememberLazyListState()

    var pdfSession by remember { mutableStateOf<PdfSession?>(null) }
    var isPasswordRequired by remember { mutableStateOf(false) }
    var isUnlocked by remember { mutableStateOf(true) }
    var passwordInput by remember { mutableStateOf("") }
    var pageCount by remember { mutableIntStateOf(0) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showTagPickerDialog by remember { mutableStateOf(false) }
    var isChromeVisible by remember { mutableStateOf(true) }

    // Shared document-level viewport state for whole-PDF zoom & pan
    var docScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val currentPage by remember {
        derivedStateOf {
            if (pageCount == 0) 0 else (listState.firstVisibleItemIndex + 1).coerceAtMost(pageCount)
        }
    }

    LaunchedEffect(media.uri) {
        val session = pdfManager.openSession(media.uri)
        if (session != null) {
            pdfSession = session
            val count = pdfManager.getPageCount(session)
            if (count > 0) {
                pageCount = count
                isUnlocked = true
                isPasswordRequired = false
            } else {
                isPasswordRequired = true
                isUnlocked = false
            }
        } else {
            isPasswordRequired = true
            isUnlocked = false
        }
    }

    DisposableEffect(media.uri) {
        onDispose {
            pdfSession?.close()
            pdfSession = null
        }
    }

    LaunchedEffect(currentPage, pdfSession, pageCount) {
        val session = pdfSession
        if (session != null && pageCount > 0) {
            pdfManager.preloadNearbyPages(session, media.uri, currentPage - 1, pageCount)
        }
    }

    var pendingDeleteMedia by remember { mutableStateOf<MediaItem?>(null) }

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "Document deleted", Toast.LENGTH_SHORT).show()
            onDismiss()
        } else {
            Toast.makeText(context, "Deletion cancelled", Toast.LENGTH_SHORT).show()
        }
        pendingDeleteMedia = null
    }

    fun executeDelete(item: MediaItem) {
        if (item.uri.scheme == "file") {
            try {
                val file = File(item.uri.path ?: "")
                if (file.exists() && file.delete()) {
                    Toast.makeText(context, "Document deleted", Toast.LENGTH_SHORT).show()
                    onDismiss()
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            val rows = context.contentResolver.delete(item.uri, null, null)
            if (rows > 0) {
                Toast.makeText(context, "Document deleted", Toast.LENGTH_SHORT).show()
                onDismiss()
                return
            }
        } catch (secEx: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(item.uri))
                    pendingDeleteMedia = item
                    deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    return
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not delete document", Toast.LENGTH_SHORT).show()
                    return
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && secEx is RecoverableSecurityException) {
                pendingDeleteMedia = item
                deleteLauncher.launch(IntentSenderRequest.Builder(secEx.userAction.actionIntent.intentSender).build())
                return
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not delete document", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(item.uri))
                pendingDeleteMedia = item
                deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } catch (e: Exception) {
                Toast.makeText(context, "Could not delete document", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Could not delete document", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        if (!isUnlocked) {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Password Protected PDF") },
                text = {
                    Column {
                        Text("Please enter password to view file:")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        coroutineScope.launch {
                            val count = pdfManager.getPageCount(media.uri)
                            if (count > 0) {
                                pageCount = count
                                isUnlocked = true
                            } else {
                                Toast.makeText(context, "Incorrect Password", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Text("Unlock")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = isChromeVisible,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary)
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    media.displayName,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1
                                )
                                if (pageCount > 0) {
                                    Text(
                                        "Page $currentPage / $pageCount",
                                        color = Color.White.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            var overflowExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { overflowExpanded = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
                                }
                                DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Convert to Images") },
                                        onClick = {
                                            overflowExpanded = false
                                            coroutineScope.launch {
                                                ConvertPdfToImagesUseCase(context)(media.uri, media.displayName.removeSuffix(".pdf")) { _, _ -> }
                                                Toast.makeText(context, "Converted PDF to Images in Pictures/MediaHarbor", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            overflowExpanded = false
                                            showDeleteConfirmDialog = true
                                        }
                                    )
                                }
                            }
                        }

                        // Horizontal Reading Progress Bar
                        if (pageCount > 0) {
                            val readingProgress = (currentPage.toFloat() / pageCount.toFloat()).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { readingProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }
                }

                if (pageCount > 0) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { isChromeVisible = !isChromeVisible },
                                    onDoubleTap = {
                                        if (docScale > 1.05f) {
                                            docScale = 1f
                                            panOffset = Offset.Zero
                                        } else {
                                            docScale = 2.5f
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
                                            docScale = (docScale * zoomChange).coerceIn(1f, 5f)
                                            if (docScale <= 1f) panOffset = Offset.Zero
                                            event.changes.forEach { it.consume() }
                                        } else if (docScale > 1.05f && panChange != Offset.Zero) {
                                            val maxPanX = (docScale - 1f) * 500f
                                            panOffset = Offset(
                                                x = (panOffset.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                                                y = 0f
                                            )
                                            if (abs(panChange.x) > abs(panChange.y)) {
                                                event.changes.forEach { it.consume() }
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        LazyColumn(
                            state = listState,
                            userScrollEnabled = true,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = docScale
                                    scaleY = docScale
                                    translationX = panOffset.x
                                    translationY = panOffset.y
                                },
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(pageCount) { index ->
                                PdfPageRenderCard(
                                    pdfManager = pdfManager,
                                    pdfSession = pdfSession,
                                    mediaItem = media,
                                    pageIndex = index,
                                    onTap = { isChromeVisible = !isChromeVisible }
                                )
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = isChromeVisible,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .windowInsetsPadding(WindowInsets.systemBars)
                                .padding(end = 16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                FloatingActionButton(
                                    onClick = { docScale = (docScale + 0.5f).coerceAtMost(5f) },
                                    containerColor = Color.Black.copy(alpha = 0.6f),
                                    contentColor = Color.White,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
                                }

                                FloatingActionButton(
                                    onClick = {
                                        docScale = (docScale - 0.5f).coerceAtLeast(1f)
                                        if (docScale == 1f) panOffset = Offset.Zero
                                    },
                                    containerColor = Color.Black.copy(alpha = 0.6f),
                                    contentColor = Color.White,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                AnimatedVisibility(
                    visible = isChromeVisible,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.85f))
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            enabled = currentPage > 1,
                            onClick = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem((currentPage - 2).coerceAtLeast(0))
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.NavigateBefore,
                                contentDescription = "Previous Page",
                                tint = if (currentPage > 1) Color.White else Color.Gray
                            )
                        }

                        IconButton(onClick = { ShareHelper.shareViaWhatsApp(context, media.uri, media.mimeType) }) {
                            Icon(Icons.Default.Send, contentDescription = "WhatsApp Share", tint = Color(0xFF25D366))
                        }

                        IconButton(onClick = { PrintHelper.printPdf(context, media.uri, media.displayName) }) {
                            Icon(Icons.Default.Print, contentDescription = "Print", tint = Color.White)
                        }

                        IconButton(onClick = { showTagPickerDialog = true }) {
                            Icon(Icons.Default.Label, contentDescription = "Tag", tint = Color.White)
                        }

                        IconButton(onClick = { showInfoDialog = true }) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
                        }

                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                        }

                        IconButton(
                            enabled = currentPage < pageCount,
                            onClick = {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(currentPage.coerceAtMost(pageCount - 1))
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.NavigateNext,
                                contentDescription = "Next Page",
                                tint = if (currentPage < pageCount) Color.White else Color.Gray
                            )
                        }
                    }
                }
            }
        }

        if (showTagPickerDialog) {
            TagPickerDialog(
                mediaUri = media.uri.toString(),
                onDismiss = { showTagPickerDialog = false }
            )
        }

        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("Delete PDF Document?") },
                text = { Text("Are you sure you want to delete '${media.displayName}'?") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            showDeleteConfirmDialog = false
                            executeDelete(media)
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { Text("PDF Document Info") },
                text = {
                    Column {
                        Text("Name: ${media.displayName}")
                        Text("Pages: $pageCount")
                        Text("Path: ${media.relativePath}")
                        Text("Size: ${media.size / 1024} KB")
                    }
                },
                confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("OK") } }
            )
        }
    }
}