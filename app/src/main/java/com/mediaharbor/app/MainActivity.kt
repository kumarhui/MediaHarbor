package com.mediaharbor.app

import android.app.Activity
import android.app.RecoverableSecurityException
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mediaharbor.app.core.common.PermissionUtils
import com.mediaharbor.app.data.local.entity.TagEntity
import com.mediaharbor.app.data.media.datasource.MediaStoreImageDataSource
import com.mediaharbor.app.data.media.datasource.MediaStorePdfDataSource
import com.mediaharbor.app.domain.model.MediaItem
import com.mediaharbor.app.domain.model.MediaType
import com.mediaharbor.app.feature.gallery.GalleryScreen
import com.mediaharbor.app.feature.imageviewer.ImageViewerScreen
import com.mediaharbor.app.feature.pdf.PdfScreen
import com.mediaharbor.app.feature.pdfviewer.PdfViewerScreen
import com.mediaharbor.app.feature.selection.SelectionTopBar
import com.mediaharbor.app.feature.selection.SelectionViewModel
import com.mediaharbor.app.feature.settings.SettingsScreen
import com.mediaharbor.app.feature.tags.TagCollectionScreen
import com.mediaharbor.app.feature.tags.TagsScreen
import com.mediaharbor.app.feature.sharing.ShareHelper
import com.mediaharbor.app.navigation.Screen
import kotlinx.coroutines.flow.combine

class MainActivity : ComponentActivity() {
    private var lastBackPressTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainAppStructure(
                    onDoubleBackExit = {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastBackPressTime < 2000) {
                            finish()
                        } else {
                            lastBackPressTime = currentTime
                            Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppStructure(onDoubleBackExit: () -> Unit) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf<Screen>(Screen.Photos) }
    var activeViewerMedia by remember { mutableStateOf<MediaItem?>(null) }
    var activeTagCollection by remember { mutableStateOf<TagEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val selectionViewModel: SelectionViewModel = viewModel()

    var allMediaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        val photosFlow = MediaStoreImageDataSource(context).fetchImages()
        val pdfsFlow = MediaStorePdfDataSource(context).fetchPdfs()

        combine(photosFlow, pdfsFlow) { photos, pdfs ->
            photos + pdfs
        }.collect { combinedList ->
            allMediaList = combinedList
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        if (!PermissionUtils.hasStoragePermissions(context)) {
            permissionLauncher.launch(PermissionUtils.getRequiredStoragePermissions())
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

    BackHandler(enabled = !selectionViewModel.isSelectionMode && activeViewerMedia != null) {
        activeViewerMedia = null
    }

    BackHandler(enabled = !selectionViewModel.isSelectionMode && activeViewerMedia == null && activeTagCollection != null) {
        activeTagCollection = null
    }

    BackHandler(enabled = !selectionViewModel.isSelectionMode && activeViewerMedia == null && activeTagCollection == null) {
        onDoubleBackExit()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (activeTagCollection != null) {
            TagCollectionScreen(
                tag = activeTagCollection!!,
                allMediaItems = allMediaList,
                onBack = { activeTagCollection = null },
                onMediaClick = { activeViewerMedia = it }
            )
        } else {
            Scaffold(
                topBar = {
                    if (activeViewerMedia == null) {
                        if (selectionViewModel.isSelectionMode) {
                            SelectionTopBar(
                                selectionViewModel = selectionViewModel,
                                totalAvailableItems = allMediaList,
                                onShareSelected = {
                                    val first = selectionViewModel.selectedItems.firstOrNull()
                                    if (first != null) {
                                        ShareHelper.shareGeneral(context, first.uri, first.mimeType)
                                    }
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
                                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            try {
                                                var count = 0
                                                var firstEx: RecoverableSecurityException? = null
                                                itemsToDelete.forEach { item ->
                                                    try {
                                                        if (context.contentResolver.delete(item.uri, null, null) > 0) count++
                                                    } catch (e: RecoverableSecurityException) {
                                                        if (firstEx == null) firstEx = e
                                                    } catch (_: Exception) {}
                                                }
                                                if (firstEx != null) {
                                                    pendingBatchDeleteItems = itemsToDelete
                                                    batchDeleteLauncher.launch(IntentSenderRequest.Builder(firstEx!!.userAction.actionIntent.intentSender).build())
                                                } else {
                                                    Toast.makeText(context, "Deleted $count items", Toast.LENGTH_SHORT).show()
                                                    selectionViewModel.clearSelection()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Could not delete selected items", Toast.LENGTH_SHORT).show()
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
                                title = {
                                    if (isSearchActive) {
                                        TextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            placeholder = { Text("Search title, path, folder...") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else {
                                        Text(currentTab.title)
                                    }
                                },
                                actions = {
                                    IconButton(onClick = {
                                        isSearchActive = !isSearchActive
                                        if (!isSearchActive) searchQuery = ""
                                    }) {
                                        Icon(if (isSearchActive) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search")
                                    }
                                }
                            )
                        }
                    }
                },
                bottomBar = {
                    if (activeViewerMedia == null && !selectionViewModel.isSelectionMode) {
                        NavigationBar {
                            val tabs = listOf(Screen.Photos, Screen.PDFs, Screen.Tags, Screen.Settings)
                            tabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentTab == tab,
                                    onClick = { currentTab = tab },
                                    icon = tab.icon,
                                    label = { Text(tab.title) }
                                )
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    when (currentTab) {
                        Screen.Photos -> GalleryScreen(
                            searchQuery = searchQuery,
                            selectionViewModel = selectionViewModel,
                            onMediaClick = { activeViewerMedia = it }
                        )
                        Screen.PDFs -> PdfScreen(
                            searchQuery = searchQuery,
                            selectionViewModel = selectionViewModel,
                            onMediaClick = { activeViewerMedia = it }
                        )
                        Screen.Tags -> TagsScreen { tag -> activeTagCollection = tag }
                        Screen.Settings -> SettingsScreen()
                    }
                }
            }
        }

        activeViewerMedia?.let { media ->
            if (media.mediaType == MediaType.IMAGE) {
                val photoList = remember(allMediaList) { allMediaList.filter { it.mediaType == MediaType.IMAGE } }
                val selectedIdx = remember(photoList, media) { photoList.indexOfFirst { it.id == media.id }.coerceAtLeast(0) }

                ImageViewerScreen(
                    mediaList = if (photoList.isNotEmpty()) photoList else listOf(media),
                    initialIndex = selectedIdx,
                    onDismiss = { activeViewerMedia = null }
                )
            } else {
                PdfViewerScreen(media = media, onDismiss = { activeViewerMedia = null })
            }
        }
    }
}