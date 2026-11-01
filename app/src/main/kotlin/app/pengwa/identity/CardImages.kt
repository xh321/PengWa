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
        try {
            for (quality in listOf(85, 65, 45, 25)) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= CardCodec.MAX_IMAGE_BYTES) return Base64.getEncoder().encodeToString(out.toByteArray())
            }
            error("图片过于复杂，请选择更小的图片")
        } finally { bitmap.recycle() }
    }

    fun decode(value: String): Bitmap? = runCatching {
        val bytes = Base64.getDecoder().decode(value)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..1024 && bounds.outHeight in 1..1024)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
