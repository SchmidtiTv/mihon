package eu.kanade.tachiyomi.data.cover

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Per manga images the user imported. Unlike [eu.kanade.tachiyomi.data.cache.CoverCache], these are only candidates,
 * kept until the user deletes them.
 */
abstract class ArtworkLibrary(private val context: Context, private val dirName: String) {

    private val libraryDir by lazy { createLibraryDir() }

    /**
     * Returns the images imported for the manga, oldest first.
     */
    fun getImages(mangaId: Long): List<File> {
        return getMangaDir(mangaId).listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    fun getLabel(file: File): String? {
        return file.name.substringAfter(LABEL_SEPARATOR, "").takeIf { it.isNotEmpty() }
    }

    /**
     * Renames the image so it carries the label, or none when it's blank.
     *
     * @return the renamed file, or null if it couldn't be renamed.
     */
    fun setLabel(mangaId: Long, file: File, label: String): File? {
        if (file.parentFile != getMangaDir(mangaId) || !file.exists()) return null
        val target = File(file.parentFile, file.name.substringBefore(LABEL_SEPARATOR) + label.toSuffix())
        if (target == file) return file
        return if (file.renameTo(target)) target else null
    }

    @Throws(IOException::class)
    fun addImage(mangaId: Long, inputStream: InputStream, label: String? = null): File {
        val dir = getMangaDir(mangaId).also { it.mkdirs() }
        val suffix = label.orEmpty().toSuffix()
        val timestamp = System.currentTimeMillis()
        var file = File(dir, "$timestamp$suffix")
        var attempt = 1
        while (file.exists()) {
            file = File(dir, "${timestamp}_$attempt$suffix")
            attempt++
        }
        try {
            file.outputStream().use { inputStream.copyTo(it) }
        } catch (e: IOException) {
            file.delete()
            throw e
        }
        return file
    }

    /**
     * Adds the image unless the manga already holds it.
     *
     * @return the stored file, or the existing one holding the same image.
     */
    @Throws(IOException::class)
    fun addImageIfAbsent(mangaId: Long, image: ByteArray, label: String? = null): File {
        return getImages(mangaId).firstOrNull { it.hasContent(image) }
            ?: addImage(mangaId, image.inputStream(), label)
    }

    fun deleteImage(mangaId: Long, file: File): Boolean {
        if (file.parentFile != getMangaDir(mangaId)) return false
        return file.exists() && file.delete()
    }

    open fun deleteImages(mangaId: Long): Boolean {
        val dir = getMangaDir(mangaId)
        return dir.exists() && dir.deleteRecursively()
    }

    private fun getMangaDir(mangaId: Long) = File(libraryDir, mangaId.toString())

    private fun String.toSuffix(): String {
        val label = trim().replace(INVALID_LABEL_CHARS, "").take(MAX_LABEL_LENGTH)
        return if (label.isEmpty()) "" else "$LABEL_SEPARATOR$label"
    }

    private fun createLibraryDir(): File {
        return context.getExternalFilesDir(dirName)
            ?: File(context.filesDir, dirName).also { it.mkdirs() }
    }
}

// Separates the identifier of an image from its label in its file name
private const val LABEL_SEPARATOR = '#'

private const val MAX_LABEL_LENGTH = 64

private val INVALID_LABEL_CHARS = Regex("""[\\/#\p{Cntrl}]""")

fun File.hasContent(bytes: ByteArray): Boolean {
    return length() == bytes.size.toLong() && readBytes().contentEquals(bytes)
}
