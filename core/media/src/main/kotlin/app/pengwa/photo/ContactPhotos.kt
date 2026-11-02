package app.pengwa.photo

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import java.util.UUID

/** Private originals grouped by stable contact ID; no gallery URI permission is needed after import. */
object ContactPhotos {
    private fun directory(context: Context, contactId: UUID) =
        File(context.filesDir, "contact_photos/$contactId")

    fun list(context: Context, contactId: UUID): List<File> =
        directory(context, contactId).listFiles()?.filter { it.extension == "photo" }
            ?.sortedByDescending { it.name } ?: emptyList()

    /** Copy atomically and validate the image header before publishing it to the list. Call on IO. */
    fun add(context: Context, contactId: UUID, uri: Uri): File {
        val dir = directory(context, contactId).apply { check(mkdirs() || isDirectory) }
        val name = "${System.currentTimeMillis()}_${UUID.randomUUID()}"
        val pending = File(dir, "$name.pending")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open image" }
                pending.outputStream().use { input.copyTo(it) }
            }
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(pending)) { decoder, info, _ ->
                require(info.size.width > 0 && info.size.height > 0)
                decoder.setTargetSize(1, 1)
            }.recycle()
            val target = File(dir, "$name.photo")
            check(pending.renameTo(target))
            return target
        } finally {
            pending.delete()
        }
    }

    fun remove(context: Context, contactId: UUID, file: File): Boolean {
        require(file.parentFile == directory(context, contactId))
        return file.delete()
    }
}
