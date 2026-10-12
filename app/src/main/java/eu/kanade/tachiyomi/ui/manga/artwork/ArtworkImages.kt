package eu.kanade.tachiyomi.ui.manga.artwork

import android.content.Context
import android.net.Uri
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import eu.kanade.presentation.manga.ArtworkImage
import eu.kanade.tachiyomi.data.cover.CoverLibrary
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Response
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.isLocal
import java.io.File
import java.io.IOException
import java.io.InputStream

fun importedArtworkImage(context: Context, file: File, label: String? = null): ArtworkImage {
    return ArtworkImage(
        url = file.absolutePath,
        providerName = context.stringResource(MR.strings.artwork_provider_imported),
        providerLogo = null,
        importedFile = file,
        label = label,
    )
}

// A local entry has no custom cover next to the source's: applying a cover overwrites the file in its folder
fun LocalCoverManager.readCover(manga: Manga): ByteArray? {
    return find(manga.url)?.openInputStream()?.use { it.readBytes() }
}

/**
 * Keeps a local entry's cover in its cover library before it's overwritten, so applying another cover never loses it.
 */
fun CoverLibrary.keepLocalCover(context: Context, manga: Manga, coverManager: LocalCoverManager) {
    if (!manga.isLocal()) return
    val cover = coverManager.readCover(manga) ?: return
    // A tracker's cover can be downloaded again, and storing it would list it a second time as the source's
    if (getAppliedUrl(manga.id, cover) != null) return
    addImageIfAbsent(manga.id, cover, context.stringResource(MR.strings.artwork_provider_source))
}

/**
 * Saves the artwork to the pictures or, when [temp] is set, to the temporary share directory.
 *
 * @return the uri of the saved file, or null when the image couldn't be loaded.
 */
suspend fun saveArtworkImage(
    context: Context,
    imageSaver: ImageSaver,
    data: Any,
    name: String,
    temp: Boolean = false,
): Uri? {
    val request = ImageRequest.Builder(context)
        .data(data)
        .size(Size.ORIGINAL)
        .build()

    return withContext(Dispatchers.IO) {
        // TODO: Handle animated cover
        val bitmap = context.imageLoader.execute(request).image
            ?.asDrawable(context.resources)
            ?.getBitmapOrNull()
            ?: return@withContext null
        imageSaver.save(
            Image.Cover(
                bitmap = bitmap,
                name = name,
                location = if (temp) Location.Cache else Location.Pictures.create(),
            ),
        )
    }
}

private const val MAX_ARTWORK_BYTES = 25L * 1024 * 1024

// Rejects a response that isn't an image or is larger than any real cover or banner, before it's written to disk
fun Response.artworkStream(): InputStream {
    val contentType = body.contentType()
    if (contentType != null && contentType.type != "image") {
        close()
        throw IOException("Refusing to save non-image artwork (content-type: $contentType)")
    }

    val contentLength = body.contentLength()
    if (contentLength > MAX_ARTWORK_BYTES) {
        close()
        throw IOException("Refusing to save oversized artwork ($contentLength bytes)")
    }

    return SizeLimitedInputStream(body.byteStream(), MAX_ARTWORK_BYTES)
}

// For a response with a missing or untruthful Content-Length
private class SizeLimitedInputStream(private val delegate: InputStream, private val limit: Long) : InputStream() {
    private var bytesRead = 0L

    override fun read(): Int {
        val result = delegate.read()
        if (result != -1) checkLimit(1)
        return result
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read = delegate.read(b, off, len)
        if (read != -1) checkLimit(read.toLong())
        return read
    }

    private fun checkLimit(read: Long) {
        bytesRead += read
        if (bytesRead > limit) throw IOException("Artwork exceeded the $limit byte limit while downloading")
    }

    override fun close() = delegate.close()
}
