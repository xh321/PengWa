package app.pengwa.identity

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A thumbnail for a 512-byte GATT attribute, independent of the full card image. */
object NearbyAvatarImages {
    const val MAX_EDGE = 48
    const val MAX_BYTES = 495

    @Suppress("DEPRECATION")
    fun encode(value: String): ByteArray {
        if (value.isEmpty()) return byteArrayOf()
        return runCatching {
            val bytes = CardImages.validatedBytes(value)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
                inJustDecodeBounds = false
                while (maxOf(outWidth, outHeight) / (inSampleSize.coerceAtLeast(1) * 2) >= MAX_EDGE) {
                    inSampleSize = inSampleSize.coerceAtLeast(1) * 2
                }
            }
            val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return byteArrayOf()
            try {
                for (edge in listOf(48, 40, 32, 24, 16)) {
                    val bitmap = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
                    bitmap.setHasAlpha(false)
                    try {
                        val side = minOf(original.width, original.height)
                        val left = (original.width - side) / 2
                        val top = (original.height - side) / 2
                        Canvas(bitmap).drawBitmap(original, Rect(left, top, left + side, top + side),
                            Rect(0, 0, edge, edge), Paint(Paint.FILTER_BITMAP_FLAG))
                        for (quality in listOf(50, 30, 15)) {
                            val out = ByteArrayOutputStream()
                            val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                            if (bitmap.compress(format, quality, out)) {
                                val image = withoutMetadata(out.toByteArray())
                                if (image.size <= MAX_BYTES) return image
                            }
                        }
                    } finally { bitmap.recycle() }
                }
                byteArrayOf()
            } finally { original.recycle() }
        }.getOrElse {
            android.util.Log.w("NearbyAvatarImages", "Unable to encode public thumbnail", it)
            byteArrayOf()
        }
    }

    /** Android may attach a 456-byte ICC profile even to a tiny sRGB bitmap. Keep only VP8 pixels. */
    private fun withoutMetadata(encoded: ByteArray): ByteArray {
        val data = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN)
        var offset = 12
        while (offset + 8 <= encoded.size) {
            val size = data.getInt(offset + 4)
            require(size >= 0 && size <= encoded.size - offset - 8)
            val padded = size + (size and 1)
            if (encoded.copyOfRange(offset, offset + 4).contentEquals("VP8 ".toByteArray(Charsets.US_ASCII))) {
                return ByteBuffer.allocate(20 + padded).order(ByteOrder.LITTLE_ENDIAN)
                    .put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(12 + padded)
                    .put("WEBP".toByteArray(Charsets.US_ASCII)).put(encoded, offset, 8 + padded).array()
            }
            offset += 8 + padded
        }
        error("Missing lossy WebP frame")
    }
}
