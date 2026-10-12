package eu.kanade.tachiyomi.ui.manga.artwork

import dev.zacsweers.metro.Inject
import eu.kanade.presentation.manga.ArtworkImage
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track

@Inject
class GetTrackerArtwork(
    private val getTracks: GetTracks,
    private val trackerManager: TrackerManager,
) {

    // Trackers are asked in parallel, and the ones that fail are left out
    suspend fun await(mangaId: Long, urlsOf: suspend (Tracker, Track) -> List<String>): List<ArtworkImage> {
        return coroutineScope {
            getTracks.await(mangaId)
                .distinctBy { it.trackerId }
                .map { track ->
                    async {
                        val tracker = trackerManager.get(track.trackerId)
                            ?.takeIf { it.isLoggedIn }
                            ?: return@async emptyList()
                        try {
                            urlsOf(tracker, track).map { url ->
                                ArtworkImage(
                                    url = url,
                                    providerName = tracker.name,
                                    providerLogo = tracker.getLogo(),
                                )
                            }
                        } catch (e: Exception) {
                            logcat(LogPriority.ERROR, e)
                            emptyList()
                        }
                    }
                }
                .awaitAll()
                .flatten()
        }
    }
}
