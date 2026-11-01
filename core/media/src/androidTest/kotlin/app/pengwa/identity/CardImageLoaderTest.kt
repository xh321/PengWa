package app.pengwa.identity

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.decode.DataSource
import coil3.request.*
import coil3.size.Size
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.*

@RunWith(AndroidJUnit4::class)
class CardImageLoaderTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun png(width: Int, height: Int): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            Base64.getEncoder().encodeToString(bytes)
        } finally { bitmap.recycle() }
    }

    @Test fun decodesEmbeddedImagesAndSharesMemoryCache() = runBlocking {
        val loader = cardImageLoader(context)
        try {
            val value = png(64, 192)
            fun request() = ImageRequest.Builder(context).data(CardImageSource(value)).size(Size.ORIGINAL).build()
            val result = assertIs<SuccessResult>(loader.execute(request()))
            assertEquals(64, result.image.width)
            assertEquals(192, result.image.height)
            assertEquals(DataSource.MEMORY_CACHE, assertIs<SuccessResult>(loader.execute(request())).dataSource)
        } finally { loader.shutdown() }
    }

    @Test fun rejectsMalformedOversizedAndExcessiveDimensionImages() = runBlocking {
        val loader = cardImageLoader(context)
        try {
            for (value in listOf("", "not base64!", "A".repeat(CardCodec.MAX_IMAGE_BYTES * 2), png(1025, 1))) {
                assertIs<ErrorResult>(loader.execute(ImageRequest.Builder(context).data(CardImageSource(value)).build()))
            }
        } finally { loader.shutdown() }
    }
}
