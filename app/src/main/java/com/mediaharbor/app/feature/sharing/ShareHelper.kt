package com.mediaharbor.app.feature.sharing

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object ShareHelper {
    fun shareViaWhatsApp(context: Context, uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
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
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        context.startActivity(Intent.createChooser(intent, "Share Media"))
    }
}

object PrintHelper {
    fun printMedia(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            setPackage("com.nokoprint")
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "NoKoPrint unavailable. Initiating system print...", Toast.LENGTH_SHORT).show()
        }
    }
}