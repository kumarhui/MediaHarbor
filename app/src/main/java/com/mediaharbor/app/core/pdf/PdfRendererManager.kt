package com.mediaharbor.app.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
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
import kotlin.math.min
import kotlin.math.sqrt

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

        // Hard safety limits for Android Canvas bitmap allocation
        private const val MAX_DIMENSION = 2880 // Max single dimension in pixels
        private const val MAX_PIXELS = 8_000_000 // Max total pixels (~32 MB in ARGB_8888)

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

    fun getCachedPage(pdfUri: Uri, pageIndex: Int, scaleBucket: Int = 1): Bitmap? {
        val cacheKey = "${pdfUri}_${pageIndex}_$scaleBucket"
        return memoryCache.get(cacheKey) ?: memoryCache.get("${pdfUri}_$pageIndex")
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

        getCachedThumbnail(pdfUri, lastModified, size)?.let { return@coroutineScope it }

        val existingJob = inFlightThumbnails[cacheKey]
        if (existingJob != null) {
            return@coroutineScope existingJob.await()
        }

        val task = async(Dispatchers.IO) {
            try {
                getCachedThumbnail(pdfUri, lastModified, size)?.let { return@async it }

                val session = openSession(pdfUri) ?: return@async null
                val bitmap = try {
                    session.mutex.withLock {
                        if (session.isClosed || session.renderer.pageCount == 0) return@withLock null
                        val page = session.renderer.openPage(0)
                        val targetWidth = 320
                        val pageW = page.width.coerceAtLeast(1)
                        val pageH = page.height.coerceAtLeast(1)
                        val aspectRatio = pageH.toFloat() / pageW.toFloat()
                        val targetHeight = (targetWidth * aspectRatio).toInt().coerceIn(1, 640)

                        val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        canvas.drawColor(Color.WHITE)

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
        renderScale: Float = 1.5f
    ): Bitmap? = withContext(Dispatchers.IO) {
        val safeScale = renderScale.coerceIn(1.0f, 3.0f)
        val scaleBucket = (safeScale * 10).toInt()
        val cacheKey = "${pdfUri}_${pageIndex}_$scaleBucket"

        memoryCache.get(cacheKey)?.let {
            return@withContext it
        }

        if (session.isClosed) return@withContext null

        session.mutex.withLock {
            if (session.isClosed) return@withLock null
            try {
                if (pageIndex < 0 || pageIndex >= session.renderer.pageCount) return@withLock null

                val page = session.renderer.openPage(pageIndex)
                val rawW = page.width.coerceAtLeast(1)
                val rawH = page.height.coerceAtLeast(1)

                // Normalize base scale to screen display width
                val screenWidth = context.resources.displayMetrics.widthPixels.coerceAtLeast(720)
                val baseScale = screenWidth.toFloat() / rawW.toFloat()

                var targetScale = baseScale * safeScale
                var width = (rawW * targetScale).toInt().coerceAtLeast(1)
                var height = (rawH * targetScale).toInt().coerceAtLeast(1)

                // Enforce strict Max Dimension caps to protect Android Canvas memory limits
                if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    val fitScale = min(MAX_DIMENSION.toFloat() / width, MAX_DIMENSION.toFloat() / height)
                    width = (width * fitScale).toInt().coerceAtLeast(1)
                    height = (height * fitScale).toInt().coerceAtLeast(1)
                }

                // Enforce strict Max Pixel count caps (~32MB RAM max per page bitmap)
                val totalPixels = width.toLong() * height.toLong()
                if (totalPixels > MAX_PIXELS) {
                    val pixelScale = sqrt(MAX_PIXELS.toDouble() / totalPixels.toDouble()).toFloat()
                    width = (width * pixelScale).toInt().coerceAtLeast(1)
                    height = (height * pixelScale).toInt().coerceAtLeast(1)
                }

                val bitmap = try {
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                } catch (oom: OutOfMemoryError) {
                    Log.e(TAG, "OOM creating bitmap ($width x $height), falling back to screen resolution", oom)
                    val fallbackW = (screenWidth * 0.8f).toInt()
                    val fallbackH = (fallbackW * (rawH.toFloat() / rawW.toFloat())).toInt()
                    Bitmap.createBitmap(fallbackW, fallbackH, Bitmap.Config.ARGB_8888)
                }

                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                memoryCache.put(cacheKey, bitmap)
                bitmap
            } catch (e: Exception) {
                Log.e(TAG, "Failed rendering PDF page $pageIndex", e)
                null
            }
        }
    }

    suspend fun preloadNearbyPages(
        session: PdfSession,
        pdfUri: Uri,
        centerPageIndex: Int,
        pageCount: Int,
        range: Int = 1,
        renderScale: Float = 1.5f
    ) = withContext(Dispatchers.IO) {
        if (session.isClosed) return@withContext
        val start = (centerPageIndex - range).coerceAtLeast(0)
        val end = (centerPageIndex + range).coerceAtMost(pageCount - 1)

        for (idx in start..end) {
            renderPage(session, pdfUri, idx, renderScale)
        }
    }

    fun clearCache() {
        memoryCache.evictAll()
    }
}