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

    fun shareGeneral(context: Context, uri: Uri, mimeType: String) {
        val shareUri = getShareableUri(context, uri)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Media"))
    }
}

object PrintHelper {

    fun printPdf(context: Context, uri: Uri, documentName: String = "Document") {
        val nokoPackage = "com.nokoprint"
        val shareableUri = ShareHelper.getShareableUri(context, uri)

        // Check whether com.nokoprint is installed on the device
        val isNokoInstalled = try {
            context.packageManager.getPackageInfo(nokoPackage, 0)
            true
        } catch (_: Exception) {
            false
        }

        if (isNokoInstalled) {
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, shareableUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setPackage(nokoPackage)
                }
                context.grantUriPermission(nokoPackage, shareableUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                Log.e("PRINT_DEBUG", "Error opening NokoPrint, falling back to system print", e)
                Toast.makeText(context, "NokoPrint error. Launching system print...", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "NokoPrint not installed. Opening system print...", Toast.LENGTH_SHORT).show()
        }

        // Native Android PrintManager fallback if NokoPrint is unavailable
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                Toast.makeText(context, "Print service unavailable", Toast.LENGTH_SHORT).show()
                return
            }

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
                        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
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
                        Log.e("PRINT_DEBUG", "Error writing PDF for printing", e)
                        callback?.onWriteFailed(e.message)
                    } finally {
                        try { input?.close() } catch (_: Exception) {}
                        try { output?.close() } catch (_: Exception) {}
                    }
                }
            }

            printManager.print(jobName, printAdapter, null)
        } catch (e: Exception) {
            Log.e("PRINT_DEBUG", "Error initiating print job", e)
            Toast.makeText(context, "Unable to print document: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}