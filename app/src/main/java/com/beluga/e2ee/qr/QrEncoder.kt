package com.beluga.e2ee.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-performance QR code bitmap encoder powered by ZXing.
 *
 * Implements Issue #18 fix: uses a single flat IntArray allocation and batch bitmap.setPixels
 * for zero GC thrashing and minimal memory footprint.
 */
object QrEncoder {

    private val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 2,
        EncodeHintType.CHARACTER_SET to "UTF-8"
    )

    /**
     * Synchronously encodes content into a square QR code Bitmap.
     *
     * @param content Raw string data to encode.
     * @param sizePx Target width and height in pixels.
     * @return ARGB_8888 Bitmap of the QR code.
     */
    fun encodeSync(content: String, sizePx: Int = 800): Bitmap {
        require(sizePx > 0) { "Size must be > 0" }
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Asynchronously generates a QR code Bitmap on [Dispatchers.Default].
     */
    suspend fun encode(content: String, sizePx: Int = 800): Bitmap = withContext(Dispatchers.Default) {
        encodeSync(content, sizePx)
    }
}
