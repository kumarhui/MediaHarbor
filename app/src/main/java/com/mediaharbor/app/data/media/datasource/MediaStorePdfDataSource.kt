package com.mediaharbor.app.data.media.datasource

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.mediaharbor.app.domain.model.MediaItem
import com.mediaharbor.app.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

class MediaStorePdfDataSource(private val context: Context) {

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var cachedPdfs: List<MediaItem>? = null

    fun fetchPdfs(forceRefresh: Boolean = false): Flow<List<MediaItem>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                Log.d("PDF_DEBUG", "ContentObserver onChange triggered")
                cachedPdfs = null
                trySend(queryPdfs())
            }
        }

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        try {
            context.contentResolver.registerContentObserver(collectionUri, true, observer)
        } catch (e: Exception) {
            Log.e("PDF_DEBUG", "Failed to register content observer", e)
        }

        if (cachedPdfs != null && !forceRefresh) {
            Log.d("PDF_DEBUG", "Returning cached PDFs (${cachedPdfs?.size} items)")
            trySend(cachedPdfs!!)
        } else {
            trySend(queryPdfs())
        }

        awaitClose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {
                Log.e("PDF_DEBUG", "Failed to unregister content observer", e)
            }
        }
    }.flowOn(Dispatchers.IO)

    fun queryPdfs(): List<MediaItem> {
        _isScanning.value = true
        try {
            val isExternalManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true
            }

            Log.d("PDF_DEBUG", "External storage manager = $isExternalManager")

            val result = if (isExternalManager) {
                val fsPdfs = scanFilesystemPdfs()
                if (fsPdfs.isNotEmpty()) fsPdfs else queryMediaStorePdfs()
            } else {
                queryMediaStorePdfs()
            }

            cachedPdfs = result
            return result
        } finally {
            _isScanning.value = false
        }
    }

    private fun scanFilesystemPdfs(): List<MediaItem> {
        Log.d("PDF_DEBUG", "Starting PDF filesystem scan")
        val list = mutableListOf<MediaItem>()
        val rootDir = Environment.getExternalStorageDirectory()

        fun walk(dir: File) {
            val files = try {
                dir.listFiles()
            } catch (e: Exception) {
                null
            } ?: return

            for (file in files) {
                val name = file.name
                if (file.isDirectory) {
                    val normalizedName = name.lowercase()
                    val path = file.absolutePath.lowercase()
                    if (name.startsWith(".") ||
                        normalizedName == "android" ||
                        path.contains("/android/data") ||
                        path.contains("/android/obb") ||
                        path.contains(".recycle") ||
                        path.contains(".trashed") ||
                        normalizedName == "cache"
                    ) {
                        continue
                    }
                    walk(file)
                } else if (file.isFile) {
                    if (name.endsWith(".pdf", ignoreCase = true) && !name.startsWith(".")) {
                        val path = file.absolutePath
                        val size = file.length()
                        val modified = file.lastModified()
                        val uri = Uri.fromFile(file)

                        Log.d("PDF_DEBUG", "PDF FOUND: path=$path size=$size modified=$modified")

                        val relativePath = path.removePrefix(rootDir.absolutePath).removePrefix("/")
                        val bucketName = file.parentFile?.name ?: ""

                        list.add(
                            MediaItem(
                                id = path.hashCode().toLong(),
                                uri = uri,
                                displayName = name,
                                mimeType = "application/pdf",
                                size = size,
                                dateAdded = modified / 1000,
                                dateModified = modified / 1000,
                                relativePath = relativePath,
                                bucketId = bucketName,
                                bucketDisplayName = bucketName,
                                width = 0,
                                height = 0,
                                mediaType = MediaType.PDF
                            )
                        )
                    }
                }
            }
        }

        if (rootDir != null && rootDir.exists()) {
            walk(rootDir)
        }

        Log.d("PDF_DEBUG", "PDF scan completed. Total PDFs found: ${list.size}")
        return list
    }

    private fun queryMediaStorePdfs(): List<MediaItem> {
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

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

        val list = mutableListOf<MediaItem>()

        try {
            context.contentResolver.query(
                collectionUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                Log.d("PDF_DEBUG", "MediaStore PDF cursor count: ${cursor.count}")

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

                        val normalizedPath = relativePath.lowercase()
                        val isTrashed = normalizedPath.contains(".recycle") || normalizedPath.contains(".trashed") || displayName.startsWith(".")

                        if (!isTrashed) {
                            val contentUri = ContentUris.withAppendedId(collectionUri, id)
                            val mediaItem = MediaItem(
                                id = id,
                                uri = contentUri,
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
                            list.add(mediaItem)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PDF_DEBUG", "Error querying MediaStore for PDFs", e)
        }

        Log.d("PDF_DEBUG", "MediaStore PDF scan completed. Total valid PDFs found: ${list.size}")
        return list
    }
}