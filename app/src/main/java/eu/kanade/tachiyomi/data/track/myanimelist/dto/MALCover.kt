package eu.kanade.tachiyomi.data.track.myanimelist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MALMangaCoverResult(
    @SerialName("main_picture")
    val mainPicture: MALMangaCovers? = null,
)

// Jikan exposes the picture list MyAnimeList's own API doesn't
@Serializable
data class JikanPicturesResult(
    val data: List<JikanPicture> = emptyList(),
)

@Serializable
data class JikanPicture(
    val jpg: JikanPictureUrls? = null,
)

@Serializable
data class JikanPictureUrls(
    @SerialName("large_image_url")
    val largeImageUrl: String? = null,
    @SerialName("image_url")
    val imageUrl: String? = null,
)
