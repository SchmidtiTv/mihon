package eu.kanade.tachiyomi.data.backup.restore

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

data class RestoreOptions(
    val libraryEntries: Boolean = true,
    val categories: Boolean = true,
    val artworkLibraries: Boolean = true,
    val appSettings: Boolean = true,
    val extensionStores: Boolean = true,
    val sourceSettings: Boolean = true,
) {

    fun asBooleanArray() = booleanArrayOf(
        libraryEntries,
        categories,
        artworkLibraries,
        appSettings,
        extensionStores,
        sourceSettings,
    )

    fun canRestore() = libraryEntries || categories || appSettings || extensionStores || sourceSettings

    companion object {
        val options = listOf(
            Entry(
                label = MR.strings.label_library,
                getter = RestoreOptions::libraryEntries,
                setter = { options, enabled -> options.copy(libraryEntries = enabled) },
            ),
            Entry(
                label = MR.strings.categories,
                getter = RestoreOptions::categories,
                setter = { options, enabled -> options.copy(categories = enabled) },
            ),
            Entry(
                label = MR.strings.artwork_libraries,
                getter = RestoreOptions::artworkLibraries,
                setter = { options, enabled -> options.copy(artworkLibraries = enabled) },
            ),
            Entry(
                label = MR.strings.app_settings,
                getter = RestoreOptions::appSettings,
                setter = { options, enabled -> options.copy(appSettings = enabled) },
            ),
            Entry(
                label = MR.strings.extensionStores,
                getter = RestoreOptions::extensionStores,
                setter = { options, enabled -> options.copy(extensionStores = enabled) },
            ),
            Entry(
                label = MR.strings.source_settings,
                getter = RestoreOptions::sourceSettings,
                setter = { options, enabled -> options.copy(sourceSettings = enabled) },
            ),
        )

        fun fromBooleanArray(array: BooleanArray): RestoreOptions {
            // An array queued before the artwork library option existed stored one entry less
            val legacy = array.size < 6
            return RestoreOptions(
                libraryEntries = array[0],
                categories = array[1],
                artworkLibraries = legacy || array[2],
                appSettings = if (legacy) array[2] else array[3],
                extensionStores = if (legacy) array[3] else array[4],
                sourceSettings = if (legacy) array[4] else array[5],
            )
        }
    }

    data class Entry(
        val label: StringResource,
        val getter: (RestoreOptions) -> Boolean,
        val setter: (RestoreOptions, Boolean) -> RestoreOptions,
    )
}
