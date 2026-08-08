package com.mediaharbor.app.feature.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import com.mediaharbor.app.feature.backup.BackupRestoreView

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var isDarkModeEnabled by remember { mutableStateOf(false) }
    var gridColumnsCount by remember { mutableFloatStateOf(3f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        SettingsCategoryHeader("General & Appearance")
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.DarkMode, contentDescription = "Dark Theme")
            Spacer(modifier = Modifier.width(16.dp))
            Text("Dark Mode Theme", modifier = Modifier.weight(1f))
            Switch(checked = isDarkModeEnabled, onCheckedChange = { isDarkModeEnabled = it })
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsCategoryHeader("Gallery Layout")
        Text("Default Grid Column Count: ${gridColumnsCount.toInt()}")
        Slider(
            value = gridColumnsCount,
            onValueChange = { gridColumnsCount = it },
            valueRange = 2f..5f,
            steps = 2
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsCategoryHeader("Storage & Cache")
        SettingsItemRow(
            icon = Icons.Default.CleaningServices,
            title = "Clear Thumbnail Cache",
            subtitle = "Frees up disk cache without deleting media files",
            onClick = {
                val loader = context.imageLoader
                loader.diskCache?.clear()
                loader.memoryCache?.clear()
                Toast.makeText(context, "Thumbnail Cache Cleared!", Toast.LENGTH_SHORT).show()
            }
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))
        BackupRestoreView()

        Divider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsCategoryHeader("About")
        SettingsItemRow(
            icon = Icons.Default.Info,
            title = "MediaHarbor Clean Architecture",
            subtitle = "Version 1.0.0 (Build 1) • Jetpack Compose & Room 2.7.1",
            onClick = {}
        )
    }
}

@Composable
fun SettingsCategoryHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun SettingsItemRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}