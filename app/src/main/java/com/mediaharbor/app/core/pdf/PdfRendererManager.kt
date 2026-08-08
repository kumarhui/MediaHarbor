package com.mediaharbor.app.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class PdfSession(
    val pfd: ParcelFileDescriptor,
    val renderer: PdfRenderer
) {
    val mutex = Mutex()
    @Volatile var isClosed = false

    fun close() {
        if (!isClosed) {
            isClosed = true
            try { renderer.close() } catch (_: Exception) {}
            try { pfd.close() } catch (_: Exception) {}
        }
    }
}

class PdfRendererManager(private val context: Context) {

    companion object {
        private const val TAG = "PDF_CACHE"

        // Shared static LRU memory cache across instances
        private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        private val cacheSize = (maxMemory / 4).coerceIn(32768, 131072) // KB

        private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }

            override fun entryRemoved(
                evicted: Boolean,
                key: String,
                oldValue: Bitmap,
                newValue: Bitmap?
            ) {
                if (evicted) {
                    Log.d(TAG, "EVICT page key=$key")
                }
            }
        }

        // De-duplication map for in-flight thumbnail generation tasks
        private val inFlightThumbnails = ConcurrentHashMap<String, Deferred<Bitmap?>>()
    }

    private val diskCacheDir: File by lazy {
        File(context.cacheDir, "pdf_thumbnails").apply {
            if (!exists()) mkdirs()
        }
    }

    private fun generateCacheKey(pdfUri: Uri, lastModified: Long, size: Long): String {
        val uriHash = pdfUri.toString().hashCode()
        return "thumb_${uriHash}_${lastModified}_$size"
    }

    suspend fun openSession(pdfUri: Uri): PdfSession? = withContext(Dispatchers.IO) {
        try {
            val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r") ?: return@withContext null
            val renderer = PdfRenderer(pfd)
            PdfSession(pfd, renderer)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getPageCount(session: PdfSession): Int = withContext(Dispatchers.IO) {
        if (session.isClosed) return@withContext 0
        session.mutex.withLock {
            if (session.isClosed) 0 else session.renderer.pageCount
        }
    }

    suspend fun getPageCount(pdfUri: Uri): Int = withContext(Dispatchers.IO) {
        val session = openSession(pdfUri) ?: return@withContext 0
        val count = session.renderer.pageCount
        session.close()
        count
    }

    fun getCachedPage(pdfUri: Uri, pageIndex: Int): Bitmap? {
        val cacheKey = "${pdfUri}_$pageIndex"
        return memoryCache.get(cacheKey)
    }

    fun getCachedThumbnail(pdfUri: Uri, lastModified: Long = 0L, size: Long = 0L): Bitmap? {
        val cacheKey = generateCacheKey(pdfUri, lastModified, size)

        // 1. Check memory cache
        memoryCache.get(cacheKey)?.let { return it }

        // 2. Check disk cache
        val diskFile = File(diskCacheDir, "$cacheKey.jpg")
        if (diskFile.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bitmap != null) {
                    memoryCache.put(cacheKey, bitmap)
                    return bitmap
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading disk cache", e)
            }
        }
        return null
    }

    suspend fun renderThumbnail(
        pdfUri: Uri,
        lastModified: Long = 0L,
        size: Long = 0L
    ): Bitmap? = coroutineScope {
        val cacheKey = generateCacheKey(pdfUri, lastModified, size)

        // 1. Fast Memory & Disk check
        getCachedThumbnail(pdfUri, lastModified, size)?.let { return@coroutineScope it }

        // 2. De-duplicate in-flight requests for the same thumbnail
        val existingJob = inFlightThumbnails[cacheKey]
        if (existingJob != null) {
            return@coroutineScope existingJob.await()
        }

        val task = async(Dispatchers.IO) {
            try {
                // Double-check cache before rendering
                getCachedThumbnail(pdfUri, lastModified, size)?.let { return@async it }

                Log.d(TAG, "RENDER THUMBNAIL START key=$cacheKey")
                val session = openSession(pdfUri) ?: return@async null
                val bitmap = try {
                    session.mutex.withLock {
                        if (session.isClosed || session.renderer.pageCount == 0) return@withLock null
                        val page = session.renderer.openPage(0)
                        val targetWidth = 320
                        val aspectRatio = page.height.toFloat() / page.width.toFloat()
                        val targetHeight = (targetWidth * aspectRatio).toInt().coerceAtLeast(1)

                        val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        bmp
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed rendering first page for thumbnail", e)
                    null
                } finally {
                    session.close()
                }

                if (bitmap != null) {
                    memoryCache.put(cacheKey, bitmap)
                    try {
                        val diskFile = File(diskCacheDir, "$cacheKey.jpg")
                        FileOutputStream(diskFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed writing thumbnail to disk cache", e)
                    }
                    Log.d(TAG, "RENDER THUMBNAIL COMPLETE key=$cacheKey")
                }
                bitmap
            } finally {
                inFlightThumbnails.remove(cacheKey)
            }
        }

        inFlightThumbnails[cacheKey] = task
        task.await()
    }

    suspend fun renderPage(
        session: PdfSession,
        pdfUri: Uri,
        pageIndex: Int,
        renderScale: Float = 1.35f
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${pdfUri}_$pageIndex"
        memoryCache.get(cacheKey)?.let {
            Log.d(TAG, "CACHE HIT page=$pageIndex")
            return@withContext it
        }

        if (session.isClosed) return@withContext null

        session.mutex.withLock {
            if (session.isClosed) return@withLock null
            try {
                if (pageIndex < 0 || pageIndex >= session.renderer.pageCount) return@withLock null

                Log.d(TAG, "RENDER START page=$pageIndex")
                val page = session.renderer.openPage(pageIndex)
                val width = (page.width * renderScale).toInt().coerceAtLeast(1)
                val height = (page.height * renderScale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                memoryCache.put(cacheKey, bitmap)
                Log.d(TAG, "RENDER COMPLETE page=$pageIndex")
                bitmap
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun preloadNearbyPages(
        session: PdfSession,
        pdfUri: Uri,
        centerPageIndex: Int,
        pageCount: Int,
        range: Int = 2
    ) = withContext(Dispatchers.IO) {
        if (session.isClosed) return@withContext
        val start = (centerPageIndex - range).coerceAtLeast(0)
        val end = (centerPageIndex + range).coerceAtMost(pageCount - 1)

        for (idx in start..end) {
            val cacheKey = "${pdfUri}_$idx"
            if (memoryCache.get(cacheKey) == null) {
                renderPage(session, pdfUri, idx)
            }
        }
    }

    fun clearCache() {
        memoryCache.evictAll()
    }
}