package app.pengwa.identity

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NearbyAvatarImagesTest {
    private fun source(kind: String): String {
        val width = if (kind == "wide") 384 else 192
        val bitmap = Bitmap.createBitmap(width, 192, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            if (kind == "noise") {
                val random = Random(42)
                bitmap.setPixels(IntArray(width * 192) { 0xff000000.toInt() or random.nextInt(0xffffff) }, 0, width, 0, 0, width, 192)
            } else {
                if (kind != "alpha") canvas.drawColor(0xfff5cfd5.toInt())
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffaa3953.toInt() }
                canvas.drawCircle(width / 2f, 96f, 72f, paint)
                paint.color = -1
                canvas.drawCircle(width / 2f - 25, 80f, 12f, paint)
                canvas.drawCircle(width / 2f + 25, 80f, 12f, paint)
            }
            val output = ByteArrayOutputStream()
            bitmap.compress(if (kind == "alpha") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 25, output)
            assertTrue(output.size() <= CardCodec.MAX_IMAGE_BYTES)
            return Base64.getEncoder().encodeToString(output.toByteArray())
        } finally { bitmap.recycle() }
    }

    @Test fun thumbnailsAreBoundedAndBenchmarkEncodingAndDecoding() {
        for (kind in listOf("face", "wide", "alpha", "noise")) {
            val input = source(kind)
            repeat(3) { NearbyAvatarImages.encode(input) }
            var image = byteArrayOf()
            val encode = List(30) { measureNanoTime { image = NearbyAvatarImages.encode(input) } / 1_000_000.0 }.sorted()
            assertTrue(image.size in 1..495, "$kind: ${image.size}")
            var allocation = 0
            var edge = 0
            val decode = List(30) {
                measureNanoTime {
                    val bitmap = assertNotNull(BitmapFactory.decodeByteArray(image, 0, image.size))
                    try {
                        assertTrue(bitmap.width in 1..48 && bitmap.height in 1..48)
                        allocation = bitmap.allocationByteCount
                        edge = bitmap.width
                    } finally { bitmap.recycle() }
                } / 1_000_000.0
            }.sorted()
            Log.i("NearbyAvatarBench", "case=$kind sourceBytes=${Base64.getDecoder().decode(input).size} imageBytes=${image.size} packetBytes=${image.size + 17} edge=$edge bitmapBytes=$allocation encodeP50Ms=${encode[15]} encodeP95Ms=${encode[28]} decodeP50Ms=${decode[15]} decodeP95Ms=${decode[28]}")
        }
    }

    @Test fun emptyAndInvalidImagesFallBackWithoutThrowing() {
        for (value in listOf("", "broken", "A".repeat(20_000))) assertTrue(NearbyAvatarImages.encode(value).isEmpty())
    }

    @Test fun thumbnailUsesTheExistingCoilCache() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loader = cardImageLoader(context)
        try {
            val value = Base64.getEncoder().encodeToString(NearbyAvatarImages.encode(source("face")))
            fun request() = ImageRequest.Builder(context).data(CardImageSource(value)).build()
            assertIs<SuccessResult>(loader.execute(request()))
            assertEquals(coil3.decode.DataSource.MEMORY_CACHE, assertIs<SuccessResult>(loader.execute(request())).dataSource)
        } finally { loader.shutdown() }
    }
}
