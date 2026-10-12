package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.presentation.manga.ArtworkImage
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.cover.CoverLibrary
import eu.kanade.tachiyomi.data.cover.hasContent
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.track.CoverProvidingTracker
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.ui.manga.artwork.GetTrackerArtwork
import eu.kanade.tachiyomi.ui.manga.artwork.artworkStream
import eu.kanade.tachiyomi.ui.manga.artwork.importedArtworkImage
import eu.kanade.tachiyomi.ui.manga.artwork.keepLocalCover
import eu.kanade.tachiyomi.ui.manga.artwork.readCover
import eu.kanade.tachiyomi.ui.manga.artwork.saveArtworkImage
import eu.kanade.tachiyomi.util.editCover
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.MR
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.isLocal
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class MangaCoverViewModel(
    @Assisted private val mangaId: Long,
    private val getManga: GetManga,
    private val imageSaver: ImageSaver,
    private val coverCache: CoverCache,
    private val updateManga: UpdateManga,
    private val coverManager: LocalCoverManager,
    private val coverLibrary: CoverLibrary,
    private val getTrackerArtwork: GetTrackerArtwork,
    private val networkHelper: NetworkHelper,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(mangaId: Long): MangaCoverViewModel
    }

    val state: StateFlow<Manga?> = getManga.subscribe(mangaId)
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), null)

    // Only once it's in the library, or for a local entry
    val canEditCover: StateFlow<Boolean> = state
        .map { manga -> manga != null && (manga.favorite || manga.isLocal()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), false)

    private val _galleryState = MutableStateFlow<ArtworkGalleryState?>(null)

    // Null while the gallery is closed
    val galleryState: StateFlow<ArtworkGalleryState?> = _galleryState.asStateFlow()

    fun saveCover(context: Context) {
        saveToPictures(context, state.value ?: return, MR.strings.cover_saved, MR.strings.error_saving_cover)
    }

    fun saveCover(context: Context, cover: ArtworkImage) {
        saveToPictures(context, cover.data, MR.strings.picture_saved, MR.strings.error_saving_picture)
    }

    private fun saveToPictures(context: Context, data: Any, saved: StringResource, failed: StringResource) {
        val manga = state.value ?: return
        viewModelScope.launch {
            try {
                saveArtworkImage(context, imageSaver, data, manga.title)
                notify(context, saved)
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                notify(context, failed)
            }
        }
    }

    fun shareCover(context: Context) {
        val manga = state.value ?: return
        viewModelScope.launch {
            try {
                val uri = saveArtworkImage(context, imageSaver, manga, manga.title, temp = true) ?: return@launch
                withContext(Dispatchers.Main) {
                    context.startActivity(uri.toShareIntent(context))
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                notify(context, MR.strings.error_sharing_cover)
            }
        }
    }

    fun importCover(context: Context, data: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(data)?.use { it.readBytes() } ?: return@launch
                val file = coverLibrary.addImageIfAbsent(mangaId, bytes)
                val image = importedArtworkImage(context, file, coverLibrary.getLabel(file))
                updateImages { images -> if (images.any { it.url == image.url }) images else listOf(image) + images }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                notify(context, MR.strings.error_importing_covers)
            }
        }
    }

    fun openCoverGallery(context: Context) {
        val manga = state.value ?: return
        _galleryState.value = ArtworkGalleryState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isLocal = manga.isLocal()
                val hasCustomCover = manga.hasCustomCover(coverCache)
                val appliedCover = if (isLocal) {
                    coverManager.readCover(manga)
                } else {
                    coverCache.getCustomCoverFile(manga.id).takeIf { hasCustomCover }?.readBytes()
                }

                val libraryCovers = coverLibrary.getImages(mangaId).map { file ->
                    importedArtworkImage(context, file, coverLibrary.getLabel(file)).copy(
                        isCurrent = appliedCover != null && file.hasContent(appliedCover),
                    )
                }

                val appliedTrackerUrl = coverLibrary.getAppliedUrl(mangaId, appliedCover)
                val trackerCovers = getTrackerArtwork.await(mangaId) { tracker, track ->
                    (tracker as? CoverProvidingTracker)?.getCovers(track).orEmpty()
                }
                    .map { it.copy(isCurrent = appliedTrackerUrl != null && it.url == appliedTrackerUrl) }

                val isOtherCoverApplied = libraryCovers.any { it.isCurrent } || trackerCovers.any { it.isCurrent }

                // A local entry's own cover is whatever was applied last, so it would show that candidate twice
                val sourceCover = manga.thumbnailUrl
                    ?.takeIf { it.isNotBlank() && !(isLocal && isOtherCoverApplied) }
                    ?.let {
                        ArtworkImage(
                            url = it,
                            providerName = context.stringResource(MR.strings.artwork_provider_source),
                            providerLogo = null,
                            isCurrent = isLocal || !hasCustomCover,
                            cover = if (isLocal) manga.asMangaCover() else null,
                        )
                    }

                _galleryState.value = ArtworkGalleryState.Ready(
                    images = (listOfNotNull(sourceCover) + libraryCovers + trackerCovers).distinctBy { it.url },
                )
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                _galleryState.value = ArtworkGalleryState.Error
            }
        }
    }

    fun closeCoverGallery() {
        _galleryState.value = null
    }

    fun setCover(context: Context, cover: ArtworkImage) {
        val manga = state.value ?: return
        // Picking the entry's own cover drops the custom one instead of storing a copy of it
        if (cover.importedFile == null && cover.url == manga.thumbnailUrl) {
            closeCoverGallery()
            deleteCustomCover(context)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = cover.importedFile?.inputStream()
                    ?: networkHelper.client.newCall(GET(cover.url)).awaitSuccess().artworkStream()
                val bytes = stream.use { it.readBytes() }
                coverLibrary.keepLocalCover(context, manga, coverManager)
                manga.editCover(coverManager, bytes.inputStream(), updateManga, coverCache)
                // Only a downloaded cover has a url worth recognizing it by
                coverLibrary.setAppliedUrl(mangaId, cover.url.takeIf { cover.importedFile == null }, bytes)
                closeCoverGallery()
                notifyCoverUpdated(context)
            } catch (e: Exception) {
                notifyFailedCoverUpdate(context, e)
            }
        }
    }

    fun labelCover(cover: ArtworkImage, label: String) {
        val file = cover.importedFile ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val renamed = coverLibrary.setLabel(mangaId, file, label) ?: return@launch
            updateImages { images ->
                images.map { image ->
                    if (image.url != cover.url) {
                        image
                    } else {
                        image.copy(
                            url = renamed.absolutePath,
                            importedFile = renamed,
                            label = coverLibrary.getLabel(renamed),
                        )
                    }
                }
            }
        }
    }

    fun deleteImportedCover(context: Context, cover: ArtworkImage) {
        val file = cover.importedFile ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (!coverLibrary.deleteImage(mangaId, file)) return@launch
            updateImages { images -> images.filterNot { it.url == cover.url } }
            notify(context, MR.strings.cover_removed)
        }
    }

    fun deleteCustomCover(context: Context) {
        closeCoverGallery()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                coverCache.deleteCustomCover(mangaId)
                updateManga.awaitUpdateCoverLastModified(mangaId)
                notifyCoverUpdated(context)
            } catch (e: Exception) {
                notifyFailedCoverUpdate(context, e)
            }
        }
    }

    private fun notifyCoverUpdated(context: Context) {
        viewModelScope.launch { notify(context, MR.strings.cover_updated) }
    }

    private fun notifyFailedCoverUpdate(context: Context, e: Throwable) {
        logcat(LogPriority.ERROR, e)
        viewModelScope.launch { notify(context, MR.strings.notification_cover_update_failed) }
    }

    private suspend fun notify(context: Context, message: StringResource) {
        withContext(Dispatchers.Main) { context.toast(message) }
    }

    private fun updateImages(block: (List<ArtworkImage>) -> List<ArtworkImage>) {
        _galleryState.update { current ->
            if (current !is ArtworkGalleryState.Ready) current else current.copy(images = block(current.images))
        }
    }
}
