package tachiyomi.source.local.image

import android.content.ContentResolver
import android.content.Context
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.source.local.io.LocalSourceFileSystem
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private const val DEFAULT_COVER_NAME = "cover.jpg"

@Inject
@SingleIn(AppScope::class)
class LocalCoverManager(
    private val context: Context,
    private val fileSystem: LocalSourceFileSystem,
) {

    fun find(mangaUrl: String): UniFile? {
        return fileSystem.getFilesInMangaDirectory(mangaUrl)
            // Get all file whose names start with "cover"
            .filter { it.isFile && it.nameWithoutExtension.equals("cover", ignoreCase = true) }
            // Get the first actual image
            .firstOrNull { ImageUtil.isImage(it.name) { it.openInputStream() } }
    }

    fun update(
        manga: SManga,
        inputStream: InputStream,
    ): UniFile? {
        val directory = fileSystem.getMangaDirectory(manga.url)
        if (directory == null) {
            inputStream.close()
            return null
        }

        val targetFile = find(manga.url) ?: directory.createFile(DEFAULT_COVER_NAME)!!

        inputStream.use { input ->
            targetFile.openTruncatedOutputStream().use { output ->
                input.copyTo(output)
            }
        }

        DiskUtil.createNoMediaFile(directory, context)

        manga.thumbnail_url = targetFile.uri.toString()
        return targetFile
    }

    // UniFile's "w" mode isn't truncated by every documents provider, so a smaller cover would keep the tail of the
    // one it replaced. The file is kept rather than recreated, as its uri is the manga's thumbnail
    private fun UniFile.openTruncatedOutputStream(): OutputStream {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return openOutputStream()
        return context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Couldn't open $uri for writing")
    }
}
