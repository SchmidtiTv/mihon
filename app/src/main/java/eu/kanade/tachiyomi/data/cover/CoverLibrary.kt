package eu.kanade.tachiyomi.data.cover

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File
import java.security.MessageDigest

@Inject
@SingleIn(AppScope::class)
class CoverLibrary(private val context: Context) : ArtworkLibrary(context, COVER_LIBRARY_DIR) {

    private val appliedUrlDir by lazy {
        context.getExternalFilesDir(APPLIED_URL_DIR) ?: File(context.filesDir, APPLIED_URL_DIR).also { it.mkdirs() }
    }

    /**
     * Remembers the tracker url the applied cover was downloaded from, so it can be recognized without downloading
     * it again.
     *
     * @param url the url the cover was downloaded from, null for any other image.
     */
    fun setAppliedUrl(mangaId: Long, url: String?, cover: ByteArray) {
        val file = getAppliedUrlFile(mangaId)
        if (url == null) {
            file.delete()
            return
        }
        file.parentFile?.mkdirs()
        file.writeText("${cover.sha256()}\n$url")
    }

    /**
     * Returns the tracker url the applied cover was downloaded from. The cover can also be replaced elsewhere, like
     * from the reader, so the url only counts while its hash still matches [appliedCover].
     */
    fun getAppliedUrl(mangaId: Long, appliedCover: ByteArray?): String? {
        if (appliedCover == null) return null
        val file = getAppliedUrlFile(mangaId).takeIf { it.exists() } ?: return null
        val (hash, url) = file.readText().split('\n', limit = 2).takeIf { it.size == 2 } ?: return null
        return url.takeIf { hash == appliedCover.sha256() && it.isNotBlank() }
    }

    override fun deleteImages(mangaId: Long): Boolean {
        getAppliedUrlFile(mangaId).delete()
        return super.deleteImages(mangaId)
    }

    private fun getAppliedUrlFile(mangaId: Long) = File(appliedUrlDir, mangaId.toString())

    private fun ByteArray.sha256(): String {
        return MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
    }
}

private const val COVER_LIBRARY_DIR = "covers/library"

// Kept apart from the library itself, whose directories only hold imported images
private const val APPLIED_URL_DIR = "covers/applied"
