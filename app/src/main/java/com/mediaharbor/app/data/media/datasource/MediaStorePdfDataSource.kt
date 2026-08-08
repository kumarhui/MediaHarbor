package com.mediaharbor.app.data.media.datasource

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.mediaharbor.app.domain.model.MediaItem
import com.mediaharbor.app.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

class MediaStorePdfDataSource(private val context: Context) {

    fun fetchPdfs(): Flow<List<MediaItem>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(queryPdfs())
            }
        }

        val collectionUri = MediaStore.Files.getContentUri("external")
        try {
            context.contentResolver.registerContentObserver(collectionUri, true, observer)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Emit initial query
        trySend(queryPdfs())

        awaitClose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun queryPdfs(): List<MediaItem> {
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

        val selection = "(${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR ${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?)"
        val selectionArgs = arrayOf("application/pdf", "application/x-pdf", "%.pdf", "%.PDF")
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        val collectionUri = MediaStore.Files.getContentUri("external")

        try {
            context.contentResolver.query(
                collectionUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val addedCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
                val modCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val pathCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                val bIdCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
                val bNameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    if (idCol != -1) {
                        val id = cursor.getLong(idCol)
                        val displayName = if (nameCol != -1) cursor.getString(nameCol) ?: "Document.pdf" else "Document.pdf"
                        val mimeType = if (mimeCol != -1) cursor.getString(mimeCol) ?: "application/pdf" else "application/pdf"
                        val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                        val dateAdded = if (addedCol != -1) cursor.getLong(addedCol) else 0L
                        val dateModified = if (modCol != -1) cursor.getLong(modCol) else 0L
                        val relativePath = if (pathCol != -1) cursor.getString(pathCol) ?: "" else ""
                        val bucketId = if (bIdCol != -1) cursor.getString(bIdCol) ?: "" else ""
                        val bucketName = if (bNameCol != -1) cursor.getString(bNameCol) ?: "" else ""

                        list.add(
                            MediaItem(
                                id = id,
                                uri = ContentUris.withAppendedId(collectionUri, id),
                                displayName = displayName,
                                mimeType = mimeType,
                                size = size,
                                dateAdded = dateAdded,
                                dateModified = dateModified,
                                relativePath = relativePath,
                                bucketId = bucketId,
                                bucketDisplayName = bucketName,
                                width = 0,
                                height = 0,
                                mediaType = MediaType.PDF
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}