package app.pengwa.identity

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.util.Base64

object CardImages {
    /** Decode at bounded dimensions before allocating pixels; re-encode to strip metadata. */
    fun import(context: Context, uri: Uri, avatar: Boolean): String {
        val edge = if (avatar) 192 else 384
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val scale = minOf(1f, edge.toFloat() / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val preserveAlpha = !avatar && bitmap.hasAlpha()
        var current = bitmap
        try {
            while (true) {
                val qualities = if (preserveAlpha) listOf(100) else listOf(85, 65, 45, 25)
                for (quality in qualities) {
                    val out = ByteArrayOutputStream()
                    current.compress(if (preserveAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, quality, out)
                    if (out.size() <= CardCodec.MAX_IMAGE_BYTES) return Base64.getEncoder().encodeToString(out.toByteArray())
                }
                if (current.width == 1 && current.height == 1) error("图片无法压缩，请选择其他图片")
                // Recompute both sides from the original aspect ratio, including very narrow images.
                val longest = (maxOf(current.width, current.height) * 0.75f).toInt().coerceAtLeast(1)
                val scale = longest.toFloat() / maxOf(bitmap.width, bitmap.height)
                val smaller = Bitmap.createScaledBitmap(bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1), true)
                if (current !== bitmap) current.recycle()
                current = smaller
            }
        } finally {
            if (current !== bitmap) current.recycle()
            bitmap.recycle()
        }
    }

    fun decode(value: String): Bitmap? = runCatching {
        val bytes = Base64.getDecoder().decode(value)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..1024 && bounds.outHeight in 1..1024)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
