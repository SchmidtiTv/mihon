package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackupMangaTest {

    @Test
    fun `decodes cover and banner artwork from existing backup fields`() {
        // A labeled image in the cover library (field 114) and an image in the banner library (field 115)
        val existingBackup = byteArrayOf(
            0x08,
            0x01,
            0x12,
            0x01,
            0x78,
            0x92.toByte(),
            0x07,
            0x0A,
            0x0A,
            0x01,
            0x01,
            0x12,
            0x05,
            0x56,
            0x6F,
            0x6C,
            0x20,
            0x33,
            0x9A.toByte(),
            0x07,
            0x03,
            0x0A,
            0x01,
            0x02,
        )

        val manga = ProtoBuf.decodeFromByteArray<BackupManga>(existingBackup)

        assertArrayEquals(byteArrayOf(1), manga.coverLibrary.single().image)
        assertEquals("Vol 3", manga.coverLibrary.single().label)
        assertArrayEquals(byteArrayOf(2), manga.bannerLibrary.single().image)
    }
}
