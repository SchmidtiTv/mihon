package eu.kanade.tachiyomi.data.cover

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path

class BannerCacheTest {

    @TempDir
    lateinit var filesDir: Path

    private fun createCache(): BannerCache {
        val context = mockk<Context> {
            every { getExternalFilesDir("banners/current") } returns this@BannerCacheTest.filesDir.toFile()
        }
        return BannerCache(context)
    }

    @Test
    fun `setBanner remembers the url a banner was downloaded from`() {
        val cache = createCache()

        cache.setBanner(1L, byteArrayOf(1).inputStream(), "https://example.com/banner.jpg")

        assertEquals("https://example.com/banner.jpg", cache.getBannerUrl(1L))
    }

    @Test
    fun `setBanner forgets the previous url when the new banner has none`() {
        val cache = createCache()
        cache.setBanner(1L, byteArrayOf(1).inputStream(), "https://example.com/banner.jpg")

        cache.setBanner(1L, byteArrayOf(2).inputStream())

        assertNull(cache.getBannerUrl(1L))
    }

    @Test
    fun `setBanner forgets the previous url when the replacement stream fails`() {
        val cache = createCache()
        cache.setBanner(1L, byteArrayOf(1).inputStream(), "https://example.com/banner.jpg")

        assertThrows(IOException::class.java) {
            cache.setBanner(1L, FailingInputStream(), "https://example.com/other.jpg")
        }

        assertNull(cache.getBannerUrl(1L))
    }

    @Test
    fun `deleteBanner removes the url along with the banner`() {
        val cache = createCache()
        cache.setBanner(1L, byteArrayOf(1).inputStream(), "https://example.com/banner.jpg")

        cache.deleteBanner(1L)
        cache.setBanner(1L, byteArrayOf(2).inputStream())

        assertNull(cache.getBannerUrl(1L))
    }

    @Test
    fun `setBanner preserves the current banner when the replacement stream fails`() {
        val cache = createCache()
        val mangaId = 1L
        val currentBanner = byteArrayOf(1, 2, 3)

        cache.setBanner(mangaId, currentBanner.inputStream())

        assertThrows(IOException::class.java) {
            cache.setBanner(mangaId, FailingInputStream())
        }

        assertArrayEquals(currentBanner, cache.getBanner(mangaId)?.readBytes())
    }

    private class FailingInputStream : InputStream() {
        private var hasReadByte = false

        override fun read(): Int {
            if (!hasReadByte) {
                hasReadByte = true
                return 0
            }
            throw IOException("Connection dropped")
        }
    }
}
