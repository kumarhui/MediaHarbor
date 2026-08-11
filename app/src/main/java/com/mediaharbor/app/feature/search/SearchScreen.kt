package com.mediaharbor.app.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mediaharbor.app.MediaHarborApp
import com.mediaharbor.app.core.pdf.PdfRendererManager
import com.mediaharbor.app.data.local.entity.TagEntity
import com.mediaharbor.app.data.settings.SettingsManager
import com.mediaharbor.app.domain.model.MediaItem
import com.mediaharbor.app.domain.model.MediaType
import com.mediaharbor.app.feature.gallery.PhotoTile
import com.mediaharbor.app.feature.pdf.PdfGridCard
import com.mediaharbor.app.feature.selection.SelectionViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    allMediaItems: List<MediaItem>,
    onBack: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onTagClick: (TagEntity) -> Unit,
    onRefresh: (suspend () -> Unit)? = null
) {
    val context = LocalContext.current
    val app = context.applicationContext as? MediaHarborApp
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val photoColumns by settingsManager.photoColumns.collectAsState()
    val pdfManager = remember { PdfRendererManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf("All") }

    val allTagsRaw by (app?.database?.tagDao()?.getAllTags()?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) })
    val allTags = remember(allTagsRaw) { allTagsRaw.distinctBy { it.name } }

    var isRefreshing by remember { mutableStateOf(false) }
    var pullOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(searchQuery) {
        delay(250)
        debouncedQuery = searchQuery.trim()
    }

    BackHandler { onBack() }

    val matchedTags = remember(debouncedQuery, allTags) {
        if (debouncedQuery.isBlank()) emptyList()
        else allTags.filter { it.name.contains(debouncedQuery, ignoreCase = true) }
    }

    val matchedMedia = remember(debouncedQuery, allMediaItems, activeFilter) {
        if (debouncedQuery.isBlank()) emptyList()
        else {
            val list = allMediaItems.filter { item ->
                item.displayName.contains(debouncedQuery, ignoreCase = true) ||
                        item.relativePath.contains(debouncedQuery, ignoreCase = true) ||
                        item.bucketDisplayName.contains(debouncedQuery, ignoreCase = true)
            }
            when (activeFilter) {
                "Photos" -> list.filter { it.mediaType == MediaType.IMAGE }
                "PDFs" -> list.filter { it.mediaType == MediaType.PDF }
                else -> list
            }
        }
    }

    val pullToRefreshConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0 && pullOffsetY > 0f) {
                    val consumed = available.y
                    pullOffsetY = (pullOffsetY + consumed).coerceAtLeast(0f)
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    pullOffsetY += available.y * 0.5f
                    if (pullOffsetY > 160f && !isRefreshing && onRefresh != null) {
                        isRefreshing = true
                        coroutineScope.launch {
                            try {
                                onRefresh()
                            } finally {
                                isRefreshing = false
                                pullOffsetY = 0f
                            }
                        }
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search photos, PDFs, tags...", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(pullToRefreshConnection)
        ) {
            if (isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Photos", "PDFs").forEach { filter ->
                    FilterChip(
                        selected = activeFilter == filter,
                        onClick = { activeFilter = filter },
                        label = { Text(filter) }
                    )
                }
            }

            if (debouncedQuery.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Type a name, folder, or tag to search",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (matchedMedia.isEmpty() && matchedTags.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No matches found for \"$debouncedQuery\"",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(photoColumns),
                    contentPadding = PaddingValues(top = 8.dp, start = 8.dp, end = 8.dp, bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (matchedTags.isNotEmpty() && activeFilter == "All") {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(photoColumns) }) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    "Matching Tags",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    matchedTags.forEach { tag ->
                                        AssistChip(
                                            onClick = { onTagClick(tag) },
                                            label = { Text(tag.name) },
                                            leadingIcon = {
                                                Box(
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            try { Color(android.graphics.Color.parseColor(tag.colorHex)) }
                                                            catch (_: Exception) { Color.Gray }
                                                        )
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(matchedMedia, key = { it.id }) { item ->
                        if (item.mediaType == MediaType.IMAGE) {
                            PhotoTile(
                                item = item,
                                isSelectionMode = false,
                                isSelected = false,
                                tagCount = 0,
                                onClick = { onMediaClick(item) },
                                onLongClick = {}
                            )
                        } else {
                            PdfGridCard(
                                pdf = item,
                                pdfManager = pdfManager,
                                isSelectionMode = false,
                                isSelected = false,
                                tagCount = 0,
                                onClick = { onMediaClick(item) },
                                onLongClick = {}
                            )
                        }
                    }
                }
            }
        }
    }
}