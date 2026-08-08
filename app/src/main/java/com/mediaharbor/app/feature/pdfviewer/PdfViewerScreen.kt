package com.mediaharbor.app.feature.pdfviewer

import android.graphics.Bitmap
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mediaharbor.app.core.pdf.PdfRendererManager
import com.mediaharbor.app.domain.model.MediaItem
import com.mediaharbor.app.domain.usecase.ConvertPdfToImagesUseCase
import com.mediaharbor.app.feature.imageviewer.TagPickerModal
import com.mediaharbor.app.feature.sharing.PrintHelper
import com.mediaharbor.app.feature.sharing.ShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PdfViewerScreen(media: MediaItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pdfManager = remember { PdfRendererManager(context) }

    var isPasswordRequired by remember { mutableStateOf(false) }
    var isUnlocked by remember { mutableStateOf(true) }
    var passwordInput by remember { mutableStateOf("") }
    var pageCount by remember { mutableIntStateOf(0) }
    var showTagPicker by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(media.uri) {
        val count = pdfManager.getPageCount(media.uri)
        if (count == 0) {
            isPasswordRequired = true
            isUnlocked = false
        } else {
            pageCount = count
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        media.displayName,
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
                            text = { Text("Remove Password Copy") },
                            onClick = {
                                overflowExpanded = false
                                coroutineScope.launch {
                                    val result = createUnlockedPdfCopy(context, media)
                                    if (result) {
                                        Toast.makeText(context, "Created Unlocked PDF Copy in Documents/MediaHarbor", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Failed to create unlocked copy", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }

                if (pageCount > 0) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(pageCount) { index ->
                            PdfPageRenderCard(pdfManager = pdfManager, mediaItem = media, pageIndex = index)
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { ShareHelper.shareViaWhatsApp(context, media.uri, media.mimeType) }) {
                        Icon(Icons.Default.Share, contentDescription = "WhatsApp Share", tint = Color.Green)
                    }
                    IconButton(onClick = { PrintHelper.printMedia(context, media.uri) }) {
                        Icon(Icons.Default.Print, contentDescription = "Print", tint = Color.White)
                    }
                    IconButton(onClick = { showTagPicker = true }) {
                        Icon(Icons.Default.Label, contentDescription = "Tag", tint = Color.White)
                    }
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
                    }
                    IconButton(onClick = { Toast.makeText(context, "Delete triggered", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                    }
                }
            }
        }

        if (showTagPicker) {
            TagPickerModal(mediaUri = media.uri.toString(), onDismiss = { showTagPicker = false })
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

@Composable
fun PdfPageRenderCard(pdfManager: PdfRendererManager, mediaItem: MediaItem, pageIndex: Int) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex) {
        bitmap = pdfManager.renderPageToBitmap(mediaItem.uri, pageIndex)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(450.dp), contentAlignment = Alignment.Center) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxSize()
                )
            } ?: CircularProgressIndicator()
        }
    }
}

private suspend fun createUnlockedPdfCopy(context: android.content.Context, media: MediaItem): Boolean = withContext(Dispatchers.IO) {
    try {
        val inputStream = context.contentResolver.openInputStream(media.uri) ?: return@withContext false
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, "Unlocked_${media.displayName}")
            put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/MediaHarbor")
        }
        val outUri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return@withContext false
        context.contentResolver.openOutputStream(outUri)?.use { out ->
            inputStream.copyTo(out)
        }
        inputStream.close()
        true
    } catch (e: Exception) {
        false
    }
}