package com.beluga.e2ee.qr

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Utility for exporting and sharing encrypted QR codes via Android Sharesheet and FileProvider.
 *
 * Resolves Issue #9: allows users to export and share encrypted QR images securely
 * across messenger or email apps without disabling WindowManager.LayoutParams.FLAG_SECURE.
 */
object QrImageSharer {

    private const val SHARED_DIR_NAME = "shared_qr"

    /**
     * Cleans up previously shared temporary QR images to save disk space.
     */
    fun clearOldSharedFiles(context: Context) {
        try {
            val dir = File(context.cacheDir, SHARED_DIR_NAME)
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
        } catch (_: Exception) {}
    }

    /**
     * Saves a single QR bitmap to cache and triggers the system share chooser.
     */
    suspend fun shareSingleQr(
        context: Context,
        bitmap: Bitmap,
        title: String = "Share Encrypted Beluga QR"
    ) = withContext(Dispatchers.IO) {
        clearOldSharedFiles(context)
        val dir = File(context.cacheDir, SHARED_DIR_NAME).apply { mkdirs() }
        val file = File(dir, "beluga_qr_${System.currentTimeMillis()}.png")

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, file)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(chooser)
    }

    /**
     * Saves multiple QR bitmaps to cache and triggers the system share chooser for multi-image sharing.
     */
    suspend fun shareMultipleQrs(
        context: Context,
        bitmaps: List<Bitmap>,
        title: String = "Share Encrypted Beluga QRs"
    ) = withContext(Dispatchers.IO) {
        if (bitmaps.isEmpty()) return@withContext
        if (bitmaps.size == 1) {
            shareSingleQr(context, bitmaps.first(), title)
            return@withContext
        }

        clearOldSharedFiles(context)
        val dir = File(context.cacheDir, SHARED_DIR_NAME).apply { mkdirs() }
        val authority = "${context.packageName}.fileprovider"
        val uris = ArrayList<Uri>(bitmaps.size)

        val timestamp = System.currentTimeMillis()
        bitmaps.forEachIndexed { index, bmp ->
            val file = File(dir, "beluga_qr_${timestamp}_part_${index + 1}_of_${bitmaps.size}.png")
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            uris.add(FileProvider.getUriForFile(context, authority, file))
        }

        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(chooser)
    }
}
