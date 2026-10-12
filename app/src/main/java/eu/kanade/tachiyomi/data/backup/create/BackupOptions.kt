package eu.kanade.tachiyomi.data.backup.create

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

data class BackupOptions(
    val libraryEntries: Boolean = true,
    val categories: Boolean = true,
    val chapters: Boolean = true,
    val tracking: Boolean = true,
    val history: Boolean = true,
    val readEntries: Boolean = true,
    val artworkLibraries: Boolean = false,
    val appSettings: Boolean = true,
    val extensionStores: Boolean = true,
    val sourceSettings: Boolean = true,
    val privateSettings: Boolean = false,
) {

    fun asBooleanArray() = booleanArrayOf(
        libraryEntries,
        categories,
        chapters,
        tracking,
        history,
        readEntries,
        artworkLibraries,
        appSettings,
        extensionStores,
        sourceSettings,
        privateSettings,
    )

    fun canCreate() = libraryEntries || categories || appSettings || extensionStores || sourceSettings

    companion object {
        val libraryOptions = listOf(
            Entry(
                label = MR.strings.manga,
                getter = BackupOptions::libraryEntries,
                setter = { options, enabled -> options.copy(libraryEntries = enabled) },
            ),
            Entry(
                label = MR.strings.chapters,
                getter = BackupOptions::chapters,
                setter = { options, enabled -> options.copy(chapters = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.track,
                getter = BackupOptions::tracking,
                setter = { options, enabled -> options.copy(tracking = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.history,
                getter = BackupOptions::history,
                setter = { options, enabled -> options.copy(history = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.categories,
                getter = BackupOptions::categories,
                setter = { options, enabled -> options.copy(categories = enabled) },
            ),
            Entry(
                label = MR.strings.non_library_settings,
                getter = BackupOptions::readEntries,
                setter = { options, enabled -> options.copy(readEntries = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.artwork_libraries,
                getter = BackupOptions::artworkLibraries,
                setter = { options, enabled -> options.copy(artworkLibraries = enabled) },
                enabled = { it.libraryEntries },
            ),
        )

        val settingsOptions = listOf(
            Entry(
                label = MR.strings.app_settings,
                getter = BackupOptions::appSettings,
                setter = { options, enabled -> options.copy(appSettings = enabled) },
            ),
            Entry(
                label = MR.strings.extensionStores,
                getter = BackupOptions::extensionStores,
                setter = { options, enabled -> options.copy(extensionStores = enabled) },
            ),
            Entry(
                label = MR.strings.source_settings,
                getter = BackupOptions::sourceSettings,
                setter = { options, enabled -> options.copy(sourceSettings = enabled) },
            ),
            Entry(
                label = MR.strings.private_settings,
                getter = BackupOptions::privateSettings,
                setter = { options, enabled -> options.copy(privateSettings = enabled) },
                enabled = { it.appSettings || it.sourceSettings },
            ),
        )

        fun fromBooleanArray(array: BooleanArray): BackupOptions {
            // A backup queued before the artwork library option existed stored one entry less
            val legacy = array.size < 11
            return BackupOptions(
                libraryEntries = array[0],
                categories = array[1],
                chapters = array[2],
                tracking = array[3],
                history = array[4],
                readEntries = array[5],
                artworkLibraries = !legacy && array[6],
                appSettings = if (legacy) array[6] else array[7],
                extensionStores = if (legacy) array[7] else array[8],
                sourceSettings = if (legacy) array[8] else array[9],
                privateSettings = if (legacy) array[9] else array[10],
            )
        }
    }

    data class Entry(
        val label: StringResource,
        val getter: (BackupOptions) -> Boolean,
        val setter: (BackupOptions, Boolean) -> BackupOptions,
        val enabled: (BackupOptions) -> Boolean = { true },
    )
}
