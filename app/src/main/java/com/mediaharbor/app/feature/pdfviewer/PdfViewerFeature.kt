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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mediaharbor.app.core.pdf.PdfRendererManager
import com.mediaharbor.app.domain.model.MediaItem
import com.mediaharbor.app.domain.usecase.ConvertPdfToImagesUseCase
import com.mediaharbor.app.feature.sharing.PrintHelper
import com.mediaharbor.app.feature.sharing.ShareHelper
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PdfPageRenderCard(
    pdfManager: PdfRendererManager,
    mediaItem: MediaItem,
    pageIndex: Int
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(mediaItem.uri, pageIndex) {
        bitmap = pdfManager.renderPageToBitmap(mediaItem.uri, pageIndex)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun PdfViewerScreen(media: MediaItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pdfManager = remember { PdfRendererManager(context) }

    var isPasswordRequired by remember { mutableStateOf(false) }
    var isUnlocked by remember { mutableStateOf(true) }
    var passwordInput by remember { mutableStateOf("") }
    var pageCount by remember { mutableIntStateOf(0) }
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
        // Handle direct filesystem file deletion if URI scheme is "file"
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
                    Text(
                        media.displayName,
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
                                    executeDelete(media)
                                }
                            )
                        }
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
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { ShareHelper.shareViaWhatsApp(context, media.uri, media.mimeType) }) {
                        Icon(Icons.Default.Send, contentDescription = "WhatsApp Share", tint = Color(0xFF25D366))
                    }
                    IconButton(onClick = { PrintHelper.printMedia(context, media.uri) }) {
                        Icon(Icons.Default.Print, contentDescription = "Print", tint = Color.White)
                    }
                    IconButton(onClick = { Toast.makeText(context, "Tag action triggered", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Default.Label, contentDescription = "Tag", tint = Color.White)
                    }
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
                    }
                    IconButton(onClick = { executeDelete(media) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                    }
                }
            }
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