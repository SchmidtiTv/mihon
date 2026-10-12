package eu.kanade.tachiyomi.data.cover

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CoverLibraryTest {

    @TempDir
    lateinit var filesDir: Path

    private lateinit var library: CoverLibrary

    @BeforeEach
    fun setup() {
        val context = mockk<Context> {
            every { getExternalFilesDir(any()) } answers {
                this@CoverLibraryTest.filesDir.resolve(firstArg<String>()).toFile()
            }
        }
        library = CoverLibrary(context)
    }

    @Test
    fun `adding an image it already holds keeps the existing one`() {
        val first = library.addImageIfAbsent(MANGA_ID, byteArrayOf(1), label = "Vol 1")
        val second = library.addImageIfAbsent(MANGA_ID, byteArrayOf(1))

        assertEquals(first, second)
        assertEquals(listOf(first), library.getImages(MANGA_ID))
    }

    @Test
    fun `applied url is returned while it describes the applied cover`() {
        library.setAppliedUrl(MANGA_ID, URL, byteArrayOf(1, 2))

        assertEquals(URL, library.getAppliedUrl(MANGA_ID, byteArrayOf(1, 2)))
    }

    @Test
    fun `applied url is ignored once the cover was replaced by other means`() {
        library.setAppliedUrl(MANGA_ID, URL, byteArrayOf(1, 2))

        assertNull(library.getAppliedUrl(MANGA_ID, byteArrayOf(3)))
        assertNull(library.getAppliedUrl(MANGA_ID, null))
    }

    @Test
    fun `applying a cover without a url forgets the previous one`() {
        library.setAppliedUrl(MANGA_ID, URL, byteArrayOf(1, 2))
        library.setAppliedUrl(MANGA_ID, null, byteArrayOf(1, 2))

        assertNull(library.getAppliedUrl(MANGA_ID, byteArrayOf(1, 2)))
    }

    @Test
    fun `deleting the images of an entry forgets its applied url`() {
        library.setAppliedUrl(MANGA_ID, URL, byteArrayOf(1, 2))
        library.deleteImages(MANGA_ID)

        assertNull(library.getAppliedUrl(MANGA_ID, byteArrayOf(1, 2)))
    }

    private companion object {
        const val MANGA_ID = 1L
        const val URL = "https://example.org/cover.jpg"
    }
}
