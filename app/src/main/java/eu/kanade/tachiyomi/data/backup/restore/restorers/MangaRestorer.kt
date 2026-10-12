package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupArtwork
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.cover.ArtworkLibrary
import eu.kanade.tachiyomi.data.cover.BannerCache
import eu.kanade.tachiyomi.data.cover.BannerLibrary
import eu.kanade.tachiyomi.data.cover.CoverLibrary
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tachiyomi.domain.backup.model.RestoredHistory
import tachiyomi.domain.backup.model.RestoredManga
import tachiyomi.domain.backup.repository.RestoreRepository
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import kotlin.time.Clock

@Inject
class MangaRestorer(
    private val restoreRepository: RestoreRepository,
    private val getCategories: GetCategories,
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId,
    private val coverLibrary: CoverLibrary,
    private val bannerLibrary: BannerLibrary,
    private val bannerCache: BannerCache,
    private val fetchInterval: FetchInterval,
) {

    private val timeZone = TimeZone.currentSystemDefault()
    private val now = Clock.System.now().toLocalDateTime(timeZone)
    private val currentFetchWindow = fetchInterval.getWindow(now.date, timeZone)

    suspend fun sortByNew(backupMangas: List<BackupManga>): List<BackupManga> {
        val urlsBySource = restoreRepository.getMangaUrlsBySourceId()

        return backupMangas
            .sortedBy { it.url in urlsBySource[it.source].orEmpty() }
    }

    /**
     * Restores [backupMangas] all together, so either every one of them is restored or none is.
     *
     * Their artwork is only restored once the database side has been committed, as it wouldn't be rolled back.
     */
    suspend fun restore(
        backupMangas: List<BackupManga>,
        backupCategories: List<BackupCategory>,
        restoreArtwork: Boolean = true,
    ) {
        val dbCategoriesByName = getCategories.await().associateBy { it.name }
        val backupCategoriesByOrder = backupCategories.associateBy { it.order }

        val entries = backupMangas.map { backupManga ->
            RestoredManga(
                manga = backupManga.getMangaImpl(),
                chapters = backupManga.chapters.map { it.toChapterImpl() },
                categoryIds = backupManga.categories.mapNotNull { backupCategoryOrder ->
                    backupCategoriesByOrder[backupCategoryOrder]?.let { backupCategory ->
                        dbCategoriesByName[backupCategory.name]?.id
                    }
                },
                history = backupManga.history.map {
                    val history = it.getHistoryImpl()
                    RestoredHistory(it.url, history.readAt, history.readDuration)
                },
                tracks = backupManga.tracking.map { it.getTrackImpl() },
                excludedScanlators = backupManga.excludedScanlators,
            )
        }

        restoreRepository.restoreManga(entries) { manga ->
            fetchInterval.withFetchInterval(manga, now, timeZone, currentFetchWindow).apply {
                // A next update still ahead was worked out with the chapters the backup came from
                manga.nextUpdate
                    ?.takeIf { it >= currentFetchWindow.start }
                    ?.let { nextUpdate = it }
            }
        }

        if (restoreArtwork) {
            backupMangas
                .filter { it.coverLibrary.isNotEmpty() || it.bannerLibrary.isNotEmpty() || it.banner != null }
                .forEach { backupManga ->
                    val mangaId = getMangaByUrlAndSourceId.await(backupManga.url, backupManga.source)?.id
                        ?: return@forEach
                    restoreArtwork(mangaId, backupManga)
                }
        }
    }

    private fun restoreArtwork(mangaId: Long, backupManga: BackupManga) {
        restoreArtworkLibrary(coverLibrary, mangaId, backupManga.coverLibrary)
        restoreArtworkLibrary(bannerLibrary, mangaId, backupManga.bannerLibrary)
        restoreBanner(mangaId, backupManga.banner)
    }

    private fun restoreArtworkLibrary(library: ArtworkLibrary, mangaId: Long, images: List<BackupArtwork>) {
        images.forEach { library.addImageIfAbsent(mangaId, it.image, it.label) }
    }

    // An entry that already has a banner keeps it, as the backed up one may be an older pick
    private fun restoreBanner(mangaId: Long, banner: ByteArray?) {
        if (banner == null || banner.isEmpty()) return
        if (bannerCache.getBanner(mangaId) != null) return

        bannerCache.setBanner(mangaId, banner.inputStream())
    }
}
