package com.mediaharbor.app.feature.tags.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mediaharbor.app.MediaHarborApp
import com.mediaharbor.app.data.local.entity.MediaTagCrossRef
import com.mediaharbor.app.data.local.entity.TagEntity
import com.mediaharbor.app.data.settings.SettingsManager
import com.mediaharbor.app.feature.tags.CreateEditTagDialog
import kotlinx.coroutines.launch

@Composable
fun TagPickerDialog(
    mediaUri: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as MediaHarborApp
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    val defaultTagIds by settingsManager.defaultTagIds.collectAsState()
    val allTagsRaw by app.database.tagDao().getAllTags().collectAsState(initial = emptyList())
    val assignedTags by app.database.tagDao().getTagsForMedia(mediaUri).collectAsState(initial = emptyList())

    val allTags = remember(allTagsRaw) { allTagsRaw.distinctBy { it.name } }
    val assignedTagIds = remember(assignedTags) { assignedTags.map { it.id }.toSet() }

    // Pre-check default tags if the media item has no tags assigned yet
    var hasInitializedDefaults by remember { mutableStateOf(false) }
    LaunchedEffect(allTags, assignedTagIds, defaultTagIds) {
        if (!hasInitializedDefaults && assignedTagIds.isEmpty() && defaultTagIds.isNotEmpty() && allTags.isNotEmpty()) {
            hasInitializedDefaults = true
            defaultTagIds.forEach { tagId ->
                if (allTags.any { it.id == tagId }) {
                    app.database.tagDao().addTagToMedia(MediaTagCrossRef(mediaUri, tagId))
                }
            }
        }
    }

    var showCreateTagDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Tags") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (allTags.isEmpty()) {
                    Text("No tags available. Create one below.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        items(allTags, key = { it.id }) { tag ->
                            val isChecked = assignedTagIds.contains(tag.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch {
                                            if (isChecked) {
                                                app.database.tagDao().removeTagFromMedia(mediaUri, tag.id)
                                            } else {
                                                app.database.tagDao().addTagToMedia(MediaTagCrossRef(mediaUri, tag.id))
                                            }
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        coroutineScope.launch {
                                            if (checked) {
                                                app.database.tagDao().addTagToMedia(MediaTagCrossRef(mediaUri, tag.id))
                                            } else {
                                                app.database.tagDao().removeTagFromMedia(mediaUri, tag.id)
                                            }
                                        }
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

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showCreateTagDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create New Tag")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )

    if (showCreateTagDialog) {
        CreateEditTagDialog(
            title = "Create Tag",
            initialName = "",
            initialColor = "#FF5722",
            onDismiss = { showCreateTagDialog = false },
            onConfirm = { name, color ->
                coroutineScope.launch {
                    val newId = app.database.tagDao().insertTag(TagEntity(name = name, colorHex = color))
                    if (newId > 0) {
                        app.database.tagDao().addTagToMedia(MediaTagCrossRef(mediaUri, newId))
                    }
                }
                showCreateTagDialog = false
            }
        )
    }
}