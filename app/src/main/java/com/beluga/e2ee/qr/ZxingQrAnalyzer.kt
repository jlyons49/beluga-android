package com.beluga.e2ee.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * High-performance 100% FOSS QR analyzer for CameraX and Gallery screenshots using ZXing.
 *
 * Implements:
 *   - Direct Y-plane extraction from CameraX YUV_420_888 images (Issue #18 fix: zero ML Kit memory leaks).
 *   - Inverted luminance fallback for dark-mode QR codes.
 *   - Offline gallery bitmap and URI decoders.
 */
class ZxingQrAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val yPlane = imageProxy.planes.firstOrNull() ?: return
            val yBuffer = yPlane.buffer
            val width = imageProxy.width
            val height = imageProxy.height
            val rowStride = yPlane.rowStride

            val yBytes = if (rowStride == width) {
                val bytes = ByteArray(yBuffer.remaining())
                yBuffer.get(bytes)
                bytes
            } else {
                val bytes = ByteArray(width * height)
                val pos = yBuffer.position()
                for (row in 0 until height) {
                    yBuffer.position(pos + row * rowStride)
                    yBuffer.get(bytes, row * width, width)
                }
                bytes
            }

            val source = PlanarYUVLuminanceSource(
                yBytes, width, height, 0, 0, width, height, false
            )

            val text = decodeLuminance(source)
            if (text != null && text.isNotBlank()) {
                onQrCodeScanned(text)
            }
        } catch (_: Exception) {
            // Drop unreadable frames silently
        } finally {
            imageProxy.close()
        }
    }

    companion object {
        private val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )

        /**
         * Attempts standard and inverted decoding for high resilience across themes and lighting.
         */
        fun decodeLuminance(source: LuminanceSource): String? {
            val reader = QRCodeReader()

            // Pass 1: Standard normal luminance
            try {
                val bitmap = BinaryBitmap(HybridBinarizer(source))
                val result = reader.decode(bitmap, hints)
                return result.text
            } catch (_: Exception) {}

            // Pass 2: Inverted luminance (for dark mode QR codes)
            try {
                reader.reset()
                val invertedBitmap = BinaryBitmap(HybridBinarizer(source.invert()))
                val result = reader.decode(invertedBitmap, hints)
                return result.text
            } catch (_: Exception) {}

            return null
        }

        /**
         * Decodes a QR code payload from an in-memory Android [Bitmap].
         */
        fun decodeFromBitmap(bitmap: Bitmap): String? {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            return decodeLuminance(source)
        }

        /**
         * Reads an image file from a content [Uri] and decodes any QR code present.
         */
        fun decodeFromUri(context: Context, uri: Uri): Result<String> = runCatching {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: throw IllegalArgumentException("Failed to decode image from selected URI")

            decodeFromBitmap(bitmap)
                ?: throw IllegalArgumentException("No QR code detected in the selected image")
        }
    }
}
