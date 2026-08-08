package com.mediaharbor.app.domain.model

import android.net.Uri

enum class MediaType { IMAGE, PDF }

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val relativePath: String,
    val bucketId: String,
    val bucketDisplayName: String,
    val width: Int,
    val height: Int,
    val mediaType: MediaType
)

data class Tag(
    val id: Long = 0,
    val name: String,
    val colorHex: String,
    val mediaCount: Int = 0
)

data class PdfReadingState(
    val mediaUri: String,
    val lastReadPage: Int,
    val totalPages: Int,
    val lastViewedTimestamp: Long
)