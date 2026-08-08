package com.mediaharbor.app.data.media.datasource

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.mediaharbor.app.domain.model.MediaItem
import com.mediaharbor.app.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class MediaStorePdfDataSource(private val context: Context) {
    fun fetchPdfs(): Flow<List<MediaItem>> = flow {
        val list = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME
        )

        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
        val selectionArgs = arrayOf("application/pdf")
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        val collectionUri = MediaStore.Files.getContentUri("external")

        context.contentResolver.query(
            collectionUri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val modCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
            val bIdCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
            val bNameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                list.add(
                    MediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(collectionUri, id),
                        displayName = cursor.getString(nameCol) ?: "Document.pdf",
                        mimeType = cursor.getString(mimeCol) ?: "application/pdf",
                        size = cursor.getLong(sizeCol),
                        dateAdded = cursor.getLong(addedCol),
                        dateModified = cursor.getLong(modCol),
                        relativePath = cursor.getString(pathCol) ?: "",
                        bucketId = cursor.getString(bIdCol) ?: "",
                        bucketDisplayName = cursor.getString(bNameCol) ?: "",
                        width = 0,
                        height = 0,
                        mediaType = MediaType.PDF
                    )
                )
            }
        }
        emit(list)
    }.flowOn(Dispatchers.IO)
}