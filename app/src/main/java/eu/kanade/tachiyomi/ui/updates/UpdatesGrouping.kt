package eu.kanade.tachiyomi.ui.updates

import eu.kanade.core.util.insertSeparators
import eu.kanade.presentation.updates.UpdatesUiModel
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.datetime.LocalDate

/**
 * Identifies one manga's chapters fetched on the same day: (date, mangaId).
 */
typealias UpdatesGroupKey = Pair<LocalDate, Long>

/**
 * Reorders items sorted newest first so each manga's chapters of a day sit together at its newest chapter,
 * matching the grouped list. Selection ranges use indices of this list, so they follow what's on screen.
 */
internal fun List<UpdatesItem>.inDisplayOrder(groupByEntry: Boolean): List<UpdatesItem> {
    if (!groupByEntry) return this
    return groupBy { it.update.dateFetch.toLocalDate() }
        .values
        .flatMap { dayItems -> dayItems.groupBy { it.update.mangaId }.values.flatten() }
}

/**
 * Builds the list shown on the Updates screen from items sorted newest first.
 *
 * With [groupByEntry], 2+ chapters of a manga on the same day collapse into one [UpdatesUiModel.Group]
 * placed at its newest chapter. Expanded groups are followed by their chapters as nested items. A partly
 * selected group is always expanded, so a selection never hides in a collapsed row.
 */
internal fun List<UpdatesItem>.toUpdatesUiModels(
    groupByEntry: Boolean,
    expandedGroups: Set<UpdatesGroupKey>,
): List<UpdatesUiModel> {
    if (!groupByEntry) {
        return map { UpdatesUiModel.Item(it) }
            .insertSeparators { before, after ->
                val beforeDate = before?.item?.update?.dateFetch?.toLocalDate()
                val afterDate = after?.item?.update?.dateFetch?.toLocalDate()
                when {
                    beforeDate != afterDate && afterDate != null -> UpdatesUiModel.Header(afterDate)
                    // Return null to avoid adding a separator between two items.
                    else -> null
                }
            }
    }

    val uiModels = mutableListOf<UpdatesUiModel>()
    // groupBy keeps first-occurrence order, so each day and each manga stay at their newest chapter
    groupBy { it.update.dateFetch.toLocalDate() }.forEach { (date, dayItems) ->
        uiModels += UpdatesUiModel.Header(date)
        dayItems.groupBy { it.update.mangaId }.forEach { (mangaId, mangaItems) ->
            if (mangaItems.size == 1) {
                uiModels += UpdatesUiModel.Item(mangaItems.single())
                return@forEach
            }
            val partlySelected = mangaItems.any { it.selected } && !mangaItems.all { it.selected }
            val expanded = partlySelected || (date to mangaId) in expandedGroups
            uiModels += UpdatesUiModel.Group(date, mangaId, mangaItems, expanded)
            if (expanded) {
                mangaItems.mapTo(uiModels) { UpdatesUiModel.Item(it, nested = true) }
            }
        }
    }
    return uiModels
}

/**
 * A long-press always selects the group; a tap selects it unless every chapter already is.
 */
internal fun Set<Long>.toggleGroup(chapterIds: List<Long>, fromLongPress: Boolean): Set<Long> {
    val allSelected = chapterIds.all { it in this }
    return if (fromLongPress || !allSelected) this + chapterIds else this - chapterIds.toSet()
}

/**
 * Range selection for a group long-press, on indices of the flat item list.
 *
 * Returns the indices to also select and the new first/last selected positions. Only the gap between the
 * previous selection and the group is filled, never the group's own span, which can hold other entries.
 */
internal fun groupLongPressRange(
    positions: Pair<Int, Int>,
    groupFirst: Int,
    groupLast: Int,
    firstSelection: Boolean,
): Pair<IntRange, Pair<Int, Int>> {
    val (start, end) = positions
    return when {
        firstSelection -> IntRange.EMPTY to (groupFirst to groupLast)
        groupFirst > end -> (end + 1)..<groupFirst to (start to groupLast)
        groupLast < start -> (groupLast + 1)..<start to (groupFirst to end)
        else -> IntRange.EMPTY to (minOf(start, groupFirst) to maxOf(end, groupLast))
    }
}
