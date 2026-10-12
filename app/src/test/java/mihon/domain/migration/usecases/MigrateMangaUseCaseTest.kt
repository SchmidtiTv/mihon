package mihon.domain.migration.usecases

import android.content.Context
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.cover.BannerCache
import eu.kanade.tachiyomi.data.cover.BannerLibrary
import eu.kanade.tachiyomi.data.cover.CoverLibrary
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.Source
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mihon.domain.migration.models.MigrationFlag
import mihon.domain.source.interactor.UpdateMangaFromRemote
import mihon.domain.source.models.RemoteMangaUpdate
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.UpsertTrack
import java.io.File
import java.nio.file.Path

class MigrateMangaUseCaseTest {

    @TempDir
    lateinit var filesDir: Path

    private lateinit var coverLibrary: CoverLibrary
    private lateinit var bannerLibrary: BannerLibrary
    private lateinit var bannerCache: BannerCache
    private lateinit var migration: MigrateMangaUseCase

    private var flags = setOf(MigrationFlag.ARTWORK)
    private var previousSourceCover: File? = null

    private val current = Manga.create().copy(id = 1L, source = 1L)
    private val target = Manga.create().copy(id = 2L, source = 2L)

    @BeforeEach
    fun setup() {
        val context = mockk<Context> {
            every { getExternalFilesDir(any()) } answers {
                this@MigrateMangaUseCaseTest.filesDir.resolve(firstArg<String>()).toFile()
            }
        }
        coverLibrary = CoverLibrary(context)
        bannerLibrary = BannerLibrary(context)
        bannerCache = BannerCache(context)

        val migrationFlags = mockk<Preference<Set<MigrationFlag>>> {
            every { get() } answers { flags }
        }
        val sourcePreferences = mockk<SourcePreferences> {
            every { this@mockk.migrationFlags } returns migrationFlags
        }
        val trackerManager = mockk<TrackerManager> {
            every { trackers } returns emptyList()
        }
        val source = mockk<Source> {
            every { name } returns "Source"
        }
        val sourceManager = mockk<SourceManager>()
        coEvery { sourceManager.get(any()) } returns source
        val getTracks = mockk<GetTracks> {
            coEvery { await(any()) } returns emptyList()
        }
        val updateManga = mockk<UpdateManga> {
            coEvery { awaitAll(any()) } returns true
        }
        val updateMangaFromRemote = mockk<UpdateMangaFromRemote>()
        coEvery { updateMangaFromRemote(target, fetchChapters = true) } returns
            Result.success(RemoteMangaUpdate(target, emptyList()))

        migration = MigrateMangaUseCase(
            scope = TestScope(),
            sourcePreferences = sourcePreferences,
            trackerManager = trackerManager,
            sourceManager = sourceManager,
            downloadManager = mockk<DownloadManager>(),
            updateManga = updateManga,
            getChaptersByMangaId = mockk<GetChaptersByMangaId>(),
            updateChapter = mockk<UpdateChapter>(),
            getCategories = mockk<GetCategories>(),
            setMangaCategories = mockk<SetMangaCategories>(),
            getTracks = getTracks,
            upsertTrack = mockk<UpsertTrack>(),
            coverCache = mockk<CoverCache> {
                every { getCoverFile(any()) } answers { previousSourceCover }
            },
            coverLibrary = coverLibrary,
            bannerLibrary = bannerLibrary,
            bannerCache = bannerCache,
            updateMangaFromRemote = updateMangaFromRemote,
        )
    }

    @Test
    fun `migration does not duplicate copied artwork`() = runTest {
        coverLibrary.addImage(current.id, byteArrayOf(1).inputStream())
        bannerLibrary.addImage(current.id, byteArrayOf(2).inputStream())

        migration(current, target, replace = false)
        migration(current, target, replace = false)

        assertEquals(1, coverLibrary.getImages(target.id).size)
        assertEquals(1, bannerLibrary.getImages(target.id).size)
    }

    @Test
    fun `migration preserves imported cover labels`() = runTest {
        coverLibrary.addImage(current.id, byteArrayOf(1).inputStream(), label = "Vol 3")

        migration(current, target, replace = false)

        assertEquals("Vol 3", coverLibrary.getImages(target.id).single().let(coverLibrary::getLabel))
    }

    @Test
    fun `migration does not duplicate the previous source cover`() = runTest {
        flags = setOf(MigrationFlag.SOURCE_COVER)
        previousSourceCover = filesDir.resolve("source-cover").toFile().apply { writeBytes(byteArrayOf(3)) }

        migration(current, target, replace = false)
        migration(current, target, replace = false)

        assertArrayEquals(byteArrayOf(3), coverLibrary.getImages(target.id).single().readBytes())
    }

    @Test
    fun `migration keeps the target banner`() = runTest {
        val targetBanner = byteArrayOf(1)
        bannerCache.setBanner(current.id, byteArrayOf(2).inputStream())
        bannerCache.setBanner(target.id, targetBanner.inputStream())

        migration(current, target, replace = false)

        assertArrayEquals(targetBanner, bannerCache.getBanner(target.id)?.readBytes())
    }
}
