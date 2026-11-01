package app.pengwa.identity

import android.content.Context
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import okio.Buffer

/** Distinguishes bounded, embedded card images from URLs and file paths. */
data class CardImageSource(val base64: String)

fun cardImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
    .components {
        add(CardImageKeyer())
        add(CardImageFetcher.Factory())
    }
    .build()

private class CardImageKeyer : Keyer<CardImageSource> {
    override fun key(data: CardImageSource, options: Options): String = "card-image:${data.base64}"
}

private class CardImageFetcher(private val data: CardImageSource, private val options: Options) : Fetcher {
    override suspend fun fetch() = SourceFetchResult(
        source = ImageSource(Buffer().write(CardImages.validatedBytes(data.base64)), options.fileSystem),
        mimeType = null,
        dataSource = DataSource.MEMORY,
    )

    class Factory : Fetcher.Factory<CardImageSource> {
        override fun create(data: CardImageSource, options: Options, imageLoader: ImageLoader): Fetcher =
            CardImageFetcher(data, options)
    }
}
