package com.mediaharbor.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable

sealed class Screen(val route: String, val title: String, val icon: @Composable () -> Unit) {
    object Photos : Screen("photos", "Photos", { Icon(Icons.Default.Photo, contentDescription = "Photos") })
    object PDFs : Screen("pdfs", "PDFs", { Icon(Icons.Default.PictureAsPdf, contentDescription = "PDFs") })
    object Tags : Screen("tags", "Tags", { Icon(Icons.Default.Label, contentDescription = "Tags") })
    object Settings : Screen("settings", "Settings", { Icon(Icons.Default.Settings, contentDescription = "Settings") })
}