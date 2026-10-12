package eu.kanade.tachiyomi.ui.updates

import eu.kanade.presentation.updates.UpdatesUiModel
import eu.kanade.tachiyomi.data.download.model.Download
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.updates.model.UpdatesWithRelations

class UpdatesGroupingTest {

    private val day1 = LocalDate(2026, 10, 12)
    private val day0 = LocalDate(2026, 10, 11)

    // Items are passed newest first, like the updates query returns them
    private fun item(
        mangaId: Long,
        chapterId: Long,
        date: LocalDate,
        hour: Int,
        selected: Boolean = false,
    ) = UpdatesItem(
        update = UpdatesWithRelations(
            mangaId = mangaId,
            mangaTitle = "Manga $mangaId",
            chapterId = chapterId,
            chapterName = "Chapter $chapterId",
            scanlator = null,
            chapterUrl = "/$chapterId",
            read = false,
            bookmark = false,
            lastPageRead = 0,
            sourceId = 0L,
            dateFetch = LocalDateTime(date.year, date.month, date.day, hour, 0)
                .toInstant(TimeZone.currentSystemDefault()),
            coverData = MangaCover(mangaId, 0L, true, null, null),
        ),
        downloadStateProvider = { Download.State.NOT_DOWNLOADED },
        downloadProgressProvider = { 0 },
        selected = selected,
    )

    @Test
    fun `grouping off matches flat list with date headers`() {
        val a1 = item(1, 11, day1, 12)
        val a2 = item(1, 12, day1, 11)
        val b1 = item(2, 21, day0, 10)

        listOf(a1, a2, b1).toUpdatesUiModels(groupByEntry = false, expandedGroups = emptySet()) shouldBe listOf(
            UpdatesUiModel.Header(day1),
            UpdatesUiModel.Item(a1),
            UpdatesUiModel.Item(a2),
            UpdatesUiModel.Header(day0),
            UpdatesUiModel.Item(b1),
        )
    }

    @Test
    fun `single chapter of a manga on a day is a plain item`() {
        val a1 = item(1, 11, day1, 12)

        listOf(a1).toUpdatesUiModels(groupByEntry = true, expandedGroups = emptySet()) shouldBe listOf(
            UpdatesUiModel.Header(day1),
            UpdatesUiModel.Item(a1),
        )
    }

    @Test
    fun `several chapters of a manga on a day form one collapsed group`() {
        val a1 = item(1, 11, day1, 12)
        val a2 = item(1, 12, day1, 11)
        val b1 = item(2, 21, day1, 10)

        listOf(a1, a2, b1).toUpdatesUiModels(groupByEntry = true, expandedGroups = emptySet()) shouldBe listOf(
            UpdatesUiModel.Header(day1),
            UpdatesUiModel.Group(day1, 1, listOf(a1, a2), expanded = false),
            UpdatesUiModel.Item(b1),
        )
    }

    @Test
    fun `groups non-adjacent chapters of the same manga within a day`() {
        val a1 = item(1, 11, day1, 12)
        val b1 = item(2, 21, day1, 11)
        val a2 = item(1, 12, day1, 10)

        listOf(a1, b1, a2).toUpdatesUiModels(groupByEntry = true, expandedGroups = emptySet()) shouldBe listOf(
            UpdatesUiModel.Header(day1),
            UpdatesUiModel.Group(day1, 1, listOf(a1, a2), expanded = false),
            UpdatesUiModel.Item(b1),
        )
    }

    @Test
    fun `same manga on two days appears under each day`() {
        val a1 = item(1, 11, day1, 12)
        val a2 = item(1, 12, day1, 11)
        val a0 = item(1, 10, day0, 12)

        listOf(a1, a2, a0).toUpdatesUiModels(groupByEntry = true, expandedGroups = emptySet()) shouldBe listOf(
            UpdatesUiModel.Header(day1),
            UpdatesUiModel.Group(day1, 1, listOf(a1, a2), expanded = false),
            UpdatesUiModel.Header(day0),
            UpdatesUiModel.Item(a0),
        )
    }

    @Test
    fun `expanded group is followed by its nested items in order`() {
        val a1 = item(1, 11, day1, 12)
        val a2 = item(1, 12, day1, 11)

        listOf(a1, a2).toUpdatesUiModels(groupByEntry = true, expandedGroups = setOf(day1 to 1L)) shouldBe listOf(
            UpdatesUiModel.Header(day1),
            UpdatesUiModel.Group(day1, 1, listOf(a1, a2), expanded = true),
            UpdatesUiModel.Item(a1, nested = true),
            UpdatesUiModel.Item(a2, nested = true),
        )
    }

    @Test
    fun `stale expanded key for a single chapter yields a plain item`() {
        val a1 = item(1, 11, day1, 12)

        listOf(a1).toUpdatesUiModels(groupByEntry = true, expandedGroups = setOf(day1 to 1L)) shouldBe listOf(
            UpdatesUiModel.Header(day1),
            UpdatesUiModel.Item(a1),
        )
    }

    @Test
    fun `group selected only when every chapter is selected`() {
        UpdatesUiModel.Group(day1, 1, listOf(item(1, 11, day1, 12, true), item(1, 12, day1, 11)), false)
            .selected shouldBe false
        UpdatesUiModel.Group(day1, 1, listOf(item(1, 11, day1, 12, true), item(1, 12, day1, 11, true)), false)
            .selected shouldBe true
    }

    @Test
    fun `tap on unselected group selects all its chapters`() {
        setOf(99L).toggleGroup(listOf(11L, 12L), fromLongPress = false) shouldBe setOf(99L, 11L, 12L)
    }

    @Test
    fun `tap on partly selected group selects the rest`() {
        setOf(11L).toggleGroup(listOf(11L, 12L), fromLongPress = false) shouldBe setOf(11L, 12L)
    }

    @Test
    fun `tap on fully selected group deselects its chapters`() {
        setOf(99L, 11L, 12L).toggleGroup(listOf(11L, 12L), fromLongPress = false) shouldBe setOf(99L)
    }

    @Test
    fun `long press on fully selected group keeps it selected`() {
        setOf(11L, 12L).toggleGroup(listOf(11L, 12L), fromLongPress = true) shouldBe setOf(11L, 12L)
    }

    @Test
    fun `first group long press sets positions to the group span without filling`() {
        groupLongPressRange(-1 to -1, groupFirst = 2, groupLast = 5, firstSelection = true) shouldBe
            (IntRange.EMPTY to (2 to 5))
    }

    @Test
    fun `group long press below the selection fills only the gap before the group`() {
        groupLongPressRange(0 to 1, groupFirst = 4, groupLast = 7, firstSelection = false) shouldBe
            ((2..<4) to (0 to 7))
    }

    @Test
    fun `group long press above the selection fills only the gap after the group`() {
        groupLongPressRange(8 to 9, groupFirst = 2, groupLast = 5, firstSelection = false) shouldBe
            ((6..<8) to (2 to 9))
    }

    @Test
    fun `group long press overlapping the selection only widens positions`() {
        groupLongPressRange(3 to 6, groupFirst = 1, groupLast = 4, firstSelection = false) shouldBe
            (IntRange.EMPTY to (1 to 6))
    }

    @Test
    fun `display order clusters each manga's chapters at its newest chapter within a day`() {
        val a1 = item(1, 11, day1, 12)
        val b1 = item(2, 21, day1, 11)
        val a2 = item(1, 12, day1, 10)
        val c0 = item(3, 31, day0, 12)

        listOf(a1, b1, a2, c0).inDisplayOrder(groupByEntry = true) shouldBe listOf(a1, a2, b1, c0)
        listOf(a1, b1, a2, c0).inDisplayOrder(groupByEntry = false) shouldBe listOf(a1, b1, a2, c0)
    }

    @Test
    fun `range between two plain rows skips chapters of a collapsed group above`() {
        val a1 = item(1, 11, day1, 12)
        val b1 = item(2, 21, day1, 11)
        val c1 = item(3, 31, day1, 10)
        val a2 = item(1, 12, day1, 9)
        val d1 = item(4, 41, day1, 8)
        val items = listOf(a1, b1, c1, a2, d1).inDisplayOrder(groupByEntry = true)

        // Long-press B1, then D1: the existing range fill selects the indices strictly between them
        val between = (items.indexOf(b1) + 1)..<items.indexOf(d1)

        between.map { items[it] } shouldBe listOf(c1)
    }

    @Test
    fun `group long press after a lower selection fills the rows visibly between`() {
        val a1 = item(1, 11, day1, 12)
        val b1 = item(2, 21, day1, 11)
        val a2 = item(1, 12, day1, 10)
        val c1 = item(3, 31, day1, 9)
        val items = listOf(a1, b1, a2, c1).inDisplayOrder(groupByEntry = true)
        val c1Index = items.indexOf(c1)

        val (fill, _) = groupLongPressRange(
            positions = c1Index to c1Index,
            groupFirst = items.indexOf(a1),
            groupLast = items.indexOf(a2),
            firstSelection = false,
        )

        fill.map { items[it] } shouldBe listOf(b1)
    }

    @Test
    fun `partly selected collapsed group is shown expanded`() {
        val a1 = item(1, 11, day1, 12, selected = true)
        val a2 = item(1, 12, day1, 11)

        listOf(a1, a2).toUpdatesUiModels(groupByEntry = true, expandedGroups = emptySet()) shouldBe listOf(
            UpdatesUiModel.Header(day1),
            UpdatesUiModel.Group(day1, 1, listOf(a1, a2), expanded = true),
            UpdatesUiModel.Item(a1, nested = true),
            UpdatesUiModel.Item(a2, nested = true),
        )
    }
}
