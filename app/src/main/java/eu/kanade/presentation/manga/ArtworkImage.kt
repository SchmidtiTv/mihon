package eu.kanade.presentation.manga

import androidx.annotation.DrawableRes
import tachiyomi.domain.manga.model.MangaCover
import java.io.File

/**
 * @param importedFile the file of an image in the entry's own libraries, null for any other candidate.
 * @param cover the cover model of a local entry's own cover. Its file keeps its url when a cover is applied, so
 * loading it through the model keeps the gallery from showing the replaced image.
 */
data class ArtworkImage(
    val url: String,
    val providerName: String,
    @DrawableRes val providerLogo: Int?,
    val importedFile: File? = null,
    val label: String? = null,
    val isCurrent: Boolean = false,
    val cover: MangaCover? = null,
) {

    val data: Any
        get() = importedFile ?: cover ?: url

    val displayName: String
        get() = label?.takeIf { it.isNotBlank() } ?: providerName
}

/**
 * @param lastModified part of its identity, so replacing the banner's file shows the new image instead of the cached
 * one.
 */
data class MangaBanner(
    val file: File,
    val lastModified: Long = file.lastModified(),
)
