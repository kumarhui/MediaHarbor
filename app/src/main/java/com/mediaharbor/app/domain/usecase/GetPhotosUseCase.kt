package com.mediaharbor.app.domain.usecase

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.mediaharbor.app.data.local.database.MediaHarborDatabase
import com.mediaharbor.app.data.local.entity.TagEntity
import com.mediaharbor.app.domain.model.MediaItem
import com.mediaharbor.app.domain.model.Tag
import com.mediaharbor.app.domain.repository.MediaRepository
import com.mediaharbor.app.domain.repository.TagRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class GetPhotosUseCase(private val repository: MediaRepository) {
    operator fun invoke(): Flow<List<MediaItem>> = repository.fetchImages()
}

class GetPdfsUseCase(private val repository: MediaRepository) {
    operator fun invoke(): Flow<List<MediaItem>> = repository.fetchPdfs()
}

class SearchMediaUseCase {
    operator fun invoke(items: List<MediaItem>, query: String): List<MediaItem> {
        if (query.isBlank()) return items
        return items.filter { item ->
            item.displayName.contains(query, ignoreCase = true) ||
                    item.relativePath.contains(query, ignoreCase = true)
        }.sortedBy { item ->
            when {
                item.displayName.equals(query, ignoreCase = true) -> 1
                item.displayName.startsWith(query, ignoreCase = true) -> 2
                else -> 3
            }
        }
    }
}

class ConvertImageToPdfUseCase(private val context: Context) {
    suspend operator fun invoke(imageUri: Uri, outputFileName: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: return@withContext Result.failure(Exception("Cannot open image"))
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdfDocument.finishPage(page)

            val name = if (outputFileName.endsWith(".pdf")) outputFileName else "$outputFileName.pdf"
            val values = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, name)
                put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/MediaHarbor")
            }

            val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                ?: return@withContext Result.failure(Exception("Failed to insert PDF MediaStore record"))

            context.contentResolver.openOutputStream(uri)?.use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class ConvertPdfToImagesUseCase(private val context: Context) {
    suspend operator fun invoke(
        pdfUri: Uri,
        folderTitle: String,
        onProgress: (Int, Int) -> Unit
    ): Result<List<Uri>> = withContext(Dispatchers.IO) {
        val uris = mutableListOf<Uri>()
        try {
            val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
                ?: return@withContext Result.failure(Exception("Cannot open PDF file descriptor"))
            val renderer = PdfRenderer(pfd)
            val total = renderer.pageCount

            for (i in 0 until total) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val imageName = "${folderTitle}_Page_${String.format("%03d", i + 1)}.jpg"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, imageName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MediaHarbor/$folderTitle")
                }

                val imageUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (imageUri != null) {
                    context.contentResolver.openOutputStream(imageUri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    uris.add(imageUri)
                }
                onProgress(i + 1, total)
            }
            renderer.close()
            pfd.close()
            Result.success(uris)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class BackupDataUseCase(private val context: Context) {
    suspend operator fun invoke(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val db = MediaHarborDatabase.getDatabase(context)
            val tags = db.tagDao().getAllTags().first()

            val json = JSONObject().apply {
                put("version", 1)
                put("timestamp", System.currentTimeMillis())
                val arr = JSONArray()
                tags.forEach { tag ->
                    arr.put(JSONObject().apply {
                        put("id", tag.id)
                        put("name", tag.name)
                        put("colorHex", tag.colorHex)
                    })
                }
                put("tags", arr)
            }

            val dir = File(context.getExternalFilesDir(null), "backups")
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "mediaharbor_backup_${System.currentTimeMillis()}.json")
            file.writeText(json.toString(2))
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class RestoreDataUseCase(private val context: Context) {
    suspend operator fun invoke(jsonContent: String, replace: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = MediaHarborDatabase.getDatabase(context)
            val json = JSONObject(jsonContent)
            val arr = json.getJSONArray("tags")

            if (replace) {
                val current = db.tagDao().getAllTags().first()
                current.forEach { db.tagDao().deleteTag(it.id) }
            }

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                db.tagDao().insertTag(TagEntity(name = obj.getString("name"), colorHex = obj.getString("colorHex")))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}