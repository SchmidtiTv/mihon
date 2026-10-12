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
import eu.kanade.presentation.manga.ArtworkImage
import eu.kanade.tachiyomi.data.cover.BannerCache
import eu.kanade.tachiyomi.data.cover.BannerLibrary
import eu.kanade.tachiyomi.data.cover.hasContent
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.track.BannerProvidingTracker
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.ui.manga.artwork.GetTrackerArtwork
import eu.kanade.tachiyomi.ui.manga.artwork.artworkStream
import eu.kanade.tachiyomi.ui.manga.artwork.importedArtworkImage
import eu.kanade.tachiyomi.ui.manga.artwork.saveArtworkImage
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.i18n.MR

@AssistedInject
class MangaBannerViewModel(
    @Assisted private val mangaId: Long,
    private val bannerCache: BannerCache,
    private val bannerLibrary: BannerLibrary,
    private val imageSaver: ImageSaver,
    private val getManga: GetManga,
    private val getTrackerArtwork: GetTrackerArtwork,
    private val networkHelper: NetworkHelper,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(mangaId: Long): MangaBannerViewModel
    }

    private val _hasBanner = MutableStateFlow(false)

    val hasBanner: StateFlow<Boolean> = _hasBanner.asStateFlow()

    private val _galleryState = MutableStateFlow<ArtworkGalleryState>(ArtworkGalleryState.Loading)

    val galleryState: StateFlow<ArtworkGalleryState> = _galleryState.asStateFlow()

    private val _dismissRequests = Channel<Unit>()

    val dismissRequests: Flow<Unit> = _dismissRequests.receiveAsFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            bannerCache.changes
                .filter { it == mangaId }
                .onStart { emit(mangaId) }
                .collect { _hasBanner.value = bannerCache.getBanner(mangaId) != null }
        }
    }

    fun openBannerGallery(context: Context) {
        _galleryState.value = ArtworkGalleryState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentBanner = bannerCache.getBanner(mangaId)?.readBytes()
                val currentBannerUrl = bannerCache.getBannerUrl(mangaId)

                val libraryBanners = bannerLibrary.getImages(mangaId).map { file ->
                    importedArtworkImage(context, file, bannerLibrary.getLabel(file)).copy(
                        isCurrent = currentBanner != null && file.hasContent(currentBanner),
                    )
                }

                val trackerBanners = getTrackerArtwork.await(mangaId) { tracker, track ->
                    (tracker as? BannerProvidingTracker)?.getBanners(track).orEmpty()
                }
                    .map { it.copy(isCurrent = currentBannerUrl != null && it.url == currentBannerUrl) }

                _galleryState.value = ArtworkGalleryState.Ready(
                    images = (libraryBanners + trackerBanners).distinctBy { it.url },
                )
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                _galleryState.value = ArtworkGalleryState.Error
            }
        }
    }

    fun setBanner(context: Context, banner: ArtworkImage) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = banner.importedFile?.inputStream()
                    ?: networkHelper.client.newCall(GET(banner.url)).awaitSuccess().artworkStream()
                // Only a downloaded banner has a url worth recognizing it by
                val url = banner.url.takeIf { banner.importedFile == null }
                stream.use { bannerCache.setBanner(mangaId, it, url) }
                notify(context, MR.strings.banner_updated)
                _dismissRequests.trySend(Unit)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                notify(context, MR.strings.error_updating_banner)
            }
        }
    }

    fun importBanner(context: Context, data: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(data)?.use { it.readBytes() } ?: return@launch
                val file = bannerLibrary.addImageIfAbsent(mangaId, bytes)
                val image = importedArtworkImage(context, file, bannerLibrary.getLabel(file))
                updateImages { images -> if (images.any { it.url == image.url }) images else listOf(image) + images }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                notify(context, MR.strings.error_importing_banners)
            }
        }
    }

    fun saveBanner(context: Context, banner: ArtworkImage) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val title = getManga.await(mangaId)?.title.orEmpty()
                saveArtworkImage(context, imageSaver, banner.data, title)
                notify(context, MR.strings.picture_saved)
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                notify(context, MR.strings.error_saving_picture)
            }
        }
    }

    fun labelBanner(banner: ArtworkImage, label: String) {
        val file = banner.importedFile ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val renamed = bannerLibrary.setLabel(mangaId, file, label) ?: return@launch
            updateImages { images ->
                images.map { image ->
                    if (image.url != banner.url) {
                        image
                    } else {
                        image.copy(
                            url = renamed.absolutePath,
                            importedFile = renamed,
                            label = bannerLibrary.getLabel(renamed),
                        )
                    }
                }
            }
        }
    }

    fun deleteImportedBanner(context: Context, banner: ArtworkImage) {
        val file = banner.importedFile ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (!bannerLibrary.deleteImage(mangaId, file)) return@launch
            updateImages { images -> images.filterNot { it.url == banner.url } }
            notify(context, MR.strings.banner_removed)
        }
    }

    fun removeBanner(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!bannerCache.deleteBanner(mangaId)) return@launch
            notify(context, MR.strings.banner_removed)
            _dismissRequests.trySend(Unit)
        }
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
