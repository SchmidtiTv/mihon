package eu.kanade.tachiyomi.ui.manga

import eu.kanade.presentation.manga.ArtworkImage

sealed interface ArtworkGalleryState {
    data object Loading : ArtworkGalleryState
    data class Ready(val images: List<ArtworkImage>) : ArtworkGalleryState
    data object Error : ArtworkGalleryState
}
