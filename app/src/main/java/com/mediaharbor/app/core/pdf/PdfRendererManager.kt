package com.mediaharbor.app.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

    // Bounded LRU Bitmap Cache (up to 25% of app memory or 64MB)
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 4).coerceIn(16384, 65536) // KB

    private val pageCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
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
        return pageCache.get(cacheKey)
    }

    suspend fun renderPage(
        session: PdfSession,
        pdfUri: Uri,
        pageIndex: Int,
        renderScale: Float = 2.0f
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${pdfUri}_$pageIndex"
        pageCache.get(cacheKey)?.let { return@withContext it }

        if (session.isClosed) return@withContext null

        session.mutex.withLock {
            if (session.isClosed) return@withContext null
            try {
                if (pageIndex < 0 || pageIndex >= session.renderer.pageCount) return@withContext null
                val page = session.renderer.openPage(pageIndex)
                val width = (page.width * renderScale).toInt().coerceAtLeast(1)
                val height = (page.height * renderScale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                pageCache.put(cacheKey, bitmap)
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
            if (pageCache.get(cacheKey) == null) {
                renderPage(session, pdfUri, idx)
            }
        }
    }

    fun clearCache() {
        pageCache.evictAll()
    }
}