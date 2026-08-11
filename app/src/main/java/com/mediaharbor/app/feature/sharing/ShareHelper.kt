package com.mediaharbor.app.feature.sharing

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.mediaharbor.app.domain.model.MediaItem
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object ShareHelper {

    fun getShareableUri(context: Context, uri: Uri): Uri {
        return if (uri.scheme == "file") {
            try {
                val file = File(uri.path ?: "")
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } catch (e: Exception) {
                Log.e("SHARE_DEBUG", "Failed to convert file URI to FileProvider URI", e)
                uri
            }
        } else {
            uri
        }
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun shareViaWhatsApp(context: Context, uri: Uri, mimeType: String) {
        val shareUri = getShareableUri(context, uri)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.whatsapp")
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "WhatsApp not installed. Opening Sharesheet...", Toast.LENGTH_SHORT).show()
            shareGeneral(context, uri, mimeType)
        }
    }

    fun shareToWhatsAppMultiple(context: Context, items: List<MediaItem>) {
        if (items.isEmpty()) return
        val isInstalled = isPackageInstalled(context, "com.whatsapp") || isPackageInstalled(context, "com.whatsapp.w4b")

        if (!isInstalled) {
            Toast.makeText(context, "WhatsApp is not installed. Opening general share sheet...", Toast.LENGTH_SHORT).show()
            shareMultiple(context, items)
            return
        }

        val shareableUris = ArrayList<Uri>(items.map { getShareableUri(context, it.uri) })
        val commonMime = if (items.all { it.mimeType.startsWith("image/") }) "image/*"
        else if (items.all { it.mimeType == "application/pdf" }) "application/pdf"
        else "*/*"

        val intent = if (shareableUris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = items.first().mimeType
                putExtra(Intent.EXTRA_STREAM, shareableUris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage("com.whatsapp")
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = commonMime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareableUris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage("com.whatsapp")
            }
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Opening WhatsApp failed. Launching share sheet...", Toast.LENGTH_SHORT).show()
            shareMultiple(context, items)
        }
    }

    fun shareGeneral(context: Context, uri: Uri, mimeType: String) {
        val shareUri = getShareableUri(context, uri)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Share Media"))
        } catch (e: Exception) {
            Toast.makeText(context, "No app available to share file", Toast.LENGTH_SHORT).show()
        }
    }

    fun openWith(context: Context, uri: Uri, mimeType: String) {
        val shareUri = getShareableUri(context, uri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(shareUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Open With"))
        } catch (e: Exception) {
            Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    fun openWithMultiple(context: Context, items: List<MediaItem>) {
        if (items.isEmpty()) return
        if (items.size == 1) {
            val first = items.first()
            openWith(context, first.uri, first.mimeType)
            return
        }

        val first = items.first()
        val shareUri = getShareableUri(context, first.uri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(shareUri, first.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Open With (${items.size} items selected)"))
        } catch (e: Exception) {
            Toast.makeText(context, "No compatible application installed", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareMultiple(context: Context, items: List<MediaItem>) {
        if (items.isEmpty()) return
        if (items.size == 1) {
            val first = items[0]
            shareGeneral(context, first.uri, first.mimeType)
            return
        }

        val shareableUris = ArrayList<Uri>()
        items.forEach { item ->
            shareableUris.add(getShareableUri(context, item.uri))
        }

        val commonMimeType = if (items.all { it.mimeType.startsWith("image/") }) {
            "image/*"
        } else if (items.all { it.mimeType == "application/pdf" }) {
            "application/pdf"
        } else {
            "*/*"
        }

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = commonMimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareableUris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Selected (${items.size})"))
    }
}

object PrintHelper {

    fun printMultiple(context: Context, items: List<MediaItem>) {
        if (items.isEmpty()) return
        val pdfs = items.filter { it.mimeType == "application/pdf" }
        if (pdfs.isNotEmpty()) {
            val firstPdf = pdfs.first()
            printPdf(context, firstPdf.uri, firstPdf.displayName)
        } else {
            val first = items.first()
            ShareHelper.openWith(context, first.uri, first.mimeType)
        }
    }

    fun printWithNokoPrint(context: Context, items: List<MediaItem>) {
        if (items.isEmpty()) return
        val nokoprintInstalled = ShareHelper.isPackageInstalled(context, "com.nokoprint")

        if (!nokoprintInstalled) {
            Toast.makeText(context, "NokoPrint is not installed. Opening system print handler...", Toast.LENGTH_SHORT).show()
            printMultiple(context, items)
            return
        }

        val first = items.first()
        val shareableUri = ShareHelper.getShareableUri(context, first.uri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(shareableUri, first.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.nokoprint")
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "NokoPrint failed to launch. Opening system print handler...", Toast.LENGTH_SHORT).show()
            printMultiple(context, items)
        }
    }

    fun printPdf(context: Context, uri: Uri, documentName: String = "Document") {
        val shareableUri = ShareHelper.getShareableUri(context, uri)

        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager != null) {
                val jobName = "MediaHarbor_$documentName"

                val printAdapter = object : PrintDocumentAdapter() {
                    override fun onLayout(
                        oldAttributes: PrintAttributes?,
                        newAttributes: PrintAttributes?,
                        cancellationSignal: CancellationSignal?,
                        callback: LayoutResultCallback?,
                        extras: Bundle?
                    ) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onLayoutCancelled()
                            return
                        }

                        val info = PrintDocumentInfo.Builder(jobName)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .build()

                        callback?.onLayoutFinished(info, newAttributes != oldAttributes)
                    }

                    override fun onWrite(
                        pages: Array<out PageRange>?,
                        destination: ParcelFileDescriptor?,
                        cancellationSignal: CancellationSignal?,
                        callback: WriteResultCallback?
                    ) {
                        var input: FileInputStream? = null
                        var output: FileOutputStream? = null
                        try {
                            val pfd = context.contentResolver.openFileDescriptor(shareableUri, "r")
                            if (pfd == null) {
                                callback?.onWriteFailed("Cannot open document descriptor")
                                return
                            }
                            input = FileInputStream(pfd.fileDescriptor)
                            output = FileOutputStream(destination?.fileDescriptor)

                            val buf = ByteArray(16384)
                            var bytesRead: Int
                            while (input.read(buf).also { bytesRead = it } >= 0) {
                                if (cancellationSignal?.isCanceled == true) {
                                    callback?.onWriteCancelled()
                                    return
                                }
                                output.write(buf, 0, bytesRead)
                            }

                            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                            pfd.close()
                        } catch (e: Exception) {
                            Log.e("PRINT_DEBUG", "Error writing document for printing", e)
                            callback?.onWriteFailed(e.message)
                        } finally {
                            try { input?.close() } catch (_: Exception) {}
                            try { output?.close() } catch (_: Exception) {}
                        }
                    }
                }

                printManager.print(jobName, printAdapter, null)
                return
            }
        } catch (e: Exception) {
            Log.e("PRINT_DEBUG", "Native PrintManager failed, trying intent fallback", e)
        }

        // Fallback: Open general Intent chooser for print/view
        ShareHelper.openWith(context, uri, "application/pdf")
    }
}