package eu.kanade.tachiyomi.ui.manga.artwork

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.presentation.manga.ArtworkKind
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.cover.BannerLibrary
import eu.kanade.tachiyomi.data.cover.CoverLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.model.Manga
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.isLocal
import kotlin.time.Duration.Companion.seconds

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class ArtworkImportViewModel(
    private val getFavorites: GetFavorites,
    private val coverLibrary: CoverLibrary,
    private val bannerLibrary: BannerLibrary,
    private val coverCache: CoverCache,
    private val coverManager: LocalCoverManager,
) : ViewModel() {

    private val _events: Channel<ArtworkImportEvent> = Channel()
    val events: Flow<ArtworkImportEvent> = _events.receiveAsFlow()

    private val favorites = MutableStateFlow<List<Manga>?>(null)

    private val searchQuery = MutableStateFlow<String?>(null)

    private val kind = MutableStateFlow(ArtworkKind.COVER)

    private val includeSourceCover = MutableStateFlow(false)

    private var importJob: Job? = null

    val state: StateFlow<State> = combine(
        favorites,
        searchQuery,
        kind,
        includeSourceCover,
    ) { favorites, query, kind, includeSourceCover ->
        State(
            searchQuery = query,
            kind = kind,
            includeSourceCover = includeSourceCover,
            titleList = favorites?.filter { query.isNullOrBlank() || it.title.contains(query, ignoreCase = true) },
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), State())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            favorites.value = getFavorites.await()
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        }
    }

    fun search(query: String?) {
        searchQuery.value = query
    }

    fun setKind(kind: ArtworkKind) {
        this.kind.value = kind
    }

    fun toggleSourceCover() {
        includeSourceCover.value = !includeSourceCover.value
    }

    // Ignored while an import is still running, so the images aren't stored twice
    fun importArtwork(context: Context, manga: Manga, uris: List<Uri>) {
        if (importJob?.isActive == true) return
        val kind = kind.value
        val withSourceCover = kind == ArtworkKind.COVER && includeSourceCover.value
        importJob = viewModelScope.launch(Dispatchers.IO) {
            var imported = uris.count { uri ->
                try {
                    val image = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@count false
                    when (kind) {
                        ArtworkKind.COVER -> coverLibrary.addImageIfAbsent(manga.id, image)
                        ArtworkKind.BANNER -> bannerLibrary.addImageIfAbsent(manga.id, image)
                    }
                    true
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e)
                    false
                }
            }

            if (withSourceCover && addSourceCover(manga)) imported++
            _events.send(
                if (imported > 0) {
                    ArtworkImportEvent.Imported(manga.title, imported, kind)
                } else {
                    ArtworkImportEvent.FailedImport(kind)
                },
            )
        }
    }

    private fun addSourceCover(manga: Manga): Boolean {
        return try {
            // A local entry's cover lives in its folder rather than in the cover cache
            val cover = if (manga.isLocal()) {
                coverManager.readCover(manga)
            } else {
                coverCache.getCoverFile(manga.thumbnailUrl)?.takeIf { it.exists() }?.readBytes()
            }
            cover?.let { coverLibrary.addImageIfAbsent(manga.id, it) } != null
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    @Immutable
    data class State(
        val searchQuery: String? = null,
        val kind: ArtworkKind = ArtworkKind.COVER,
        val includeSourceCover: Boolean = false,
        private val titleList: List<Manga>? = null,
    ) {

        val titles: List<Manga>
            get() = titleList.orEmpty()

        val isLoading: Boolean
            get() = titleList == null

        val isEmpty: Boolean
            get() = titles.isEmpty()
    }
}

sealed interface ArtworkImportEvent {
    data class Imported(val title: String, val count: Int, val kind: ArtworkKind) : ArtworkImportEvent
    data class FailedImport(val kind: ArtworkKind) : ArtworkImportEvent
}
