package eu.kanade.tachiyomi.data.cover

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * The banner shown behind the header of an entry. Sources don't provide banners, so it's always a copy of an image
 * the user picked, and an entry without one shows its blurred cover instead.
 */
@Inject
@SingleIn(AppScope::class)
class BannerCache(private val context: Context) {

    private val bannerDir by lazy { createBannerDir() }

    private val _changes = MutableSharedFlow<Long>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Emits the id of an entry whenever its banner is set or deleted.
     */
    val changes: Flow<Long> = _changes.asSharedFlow()

    fun getBanner(mangaId: Long): File? = getBannerFile(mangaId).takeIf { it.exists() }

    /**
     * Returns the url the banner was downloaded from, so a tracker's banner can be recognized as the applied one
     * without downloading it again.
     */
    fun getBannerUrl(mangaId: Long): String? {
        if (getBanner(mangaId) == null) return null
        return getBannerUrlFile(mangaId).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    }

    /**
     * @param url the url the banner was downloaded from, null for any other image.
     */
    @Throws(IOException::class)
    fun setBanner(mangaId: Long, inputStream: InputStream, url: String? = null) {
        val file = getBannerFile(mangaId)
        file.parentFile?.mkdirs()
        // Dropped first, so a failed replacement can't leave the url of another image behind
        getBannerUrlFile(mangaId).delete()
        val temporaryFile = File.createTempFile("banner-$mangaId-", ".tmp", file.parentFile)
        try {
            temporaryFile.outputStream().use { inputStream.copyTo(it) }
            if (!temporaryFile.renameTo(file)) {
                throw IOException("Unable to replace banner")
            }
        } finally {
            temporaryFile.delete()
        }
        url?.let { getBannerUrlFile(mangaId).writeText(it) }
        _changes.tryEmit(mangaId)
    }

    fun deleteBanner(mangaId: Long): Boolean {
        getBannerUrlFile(mangaId).delete()
        val deleted = getBannerFile(mangaId).let { it.exists() && it.delete() }
        if (deleted) {
            _changes.tryEmit(mangaId)
        }
        return deleted
    }

    private fun getBannerFile(mangaId: Long) = File(bannerDir, mangaId.toString())

    private fun getBannerUrlFile(mangaId: Long) = File(bannerDir, "$mangaId$URL_FILE_SUFFIX")

    private fun createBannerDir(): File {
        return context.getExternalFilesDir(BANNERS_DIR)
            ?: File(context.filesDir, BANNERS_DIR).also { it.mkdirs() }
    }
}

private const val BANNERS_DIR = "banners/current"

private const val URL_FILE_SUFFIX = ".url"
