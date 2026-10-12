package eu.kanade.tachiyomi.ui.manga.artwork

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.manga.ArtworkKind
import eu.kanade.presentation.manga.components.BaseMangaListItem
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * @param uris the shared images, as strings so the screen survives configuration changes.
 */
data class ArtworkImportScreen(
    private val uris: List<String>,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = metroViewModel<ArtworkImportViewModel>()

        val state by viewModel.state.collectAsStateWithLifecycle()

        Scaffold(
            topBar = { scrollBehavior ->
                SearchToolbar(
                    titleContent = {
                        AppBarTitle(
                            title = stringResource(MR.strings.action_import_artwork),
                            subtitle = stringResource(MR.strings.import_artwork_subtitle),
                        )
                    },
                    searchQuery = state.searchQuery,
                    onChangeSearchQuery = viewModel::search,
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            if (state.isLoading) {
                LoadingScreen(Modifier.padding(contentPadding))
                return@Scaffold
            }
            if (state.isEmpty) {
                EmptyScreen(
                    stringRes = if (state.searchQuery.isNullOrEmpty()) {
                        MR.strings.information_empty_library
                    } else {
                        MR.strings.no_results_found
                    },
                    modifier = Modifier.padding(contentPadding),
                )
                return@Scaffold
            }

            Column(modifier = Modifier.padding(contentPadding)) {
                KindSelector(selected = state.kind, onSelect = viewModel::setKind)
                if (state.kind == ArtworkKind.COVER) {
                    LabeledCheckbox(
                        label = stringResource(MR.strings.action_add_source_cover),
                        checked = state.includeSourceCover,
                        onCheckedChange = { viewModel.toggleSourceCover() },
                        modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                    )
                }
                SharedImages(uris = uris, kind = state.kind)
                HorizontalDivider()
                FastScrollLazyColumn {
                    items(state.titles, key = { it.id }) { manga ->
                        BaseMangaListItem(
                            manga = manga,
                            onClickItem = {
                                viewModel.importArtwork(context, manga, uris.map { it.toUri() })
                            },
                        )
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            viewModel.events.collectLatest { event ->
                when (event) {
                    is ArtworkImportEvent.Imported -> {
                        context.toast(
                            context.pluralStringResource(
                                when (event.kind) {
                                    ArtworkKind.COVER -> MR.plurals.covers_added_to_library
                                    ArtworkKind.BANNER -> MR.plurals.banners_added_to_library
                                },
                                event.count,
                                event.count,
                                event.title,
                            ),
                        )
                        navigator.pop()
                    }
                    is ArtworkImportEvent.FailedImport -> context.toast(
                        when (event.kind) {
                            ArtworkKind.COVER -> MR.strings.error_importing_covers
                            ArtworkKind.BANNER -> MR.strings.error_importing_banners
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun KindSelector(selected: ArtworkKind, onSelect: (ArtworkKind) -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.padding.medium,
                    end = MaterialTheme.padding.medium,
                    top = MaterialTheme.padding.medium,
                ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            ArtworkKind.entries.forEach { kind ->
                FilterChip(
                    selected = kind == selected,
                    onClick = { onSelect(kind) },
                    label = {
                        Text(
                            text = stringResource(
                                when (kind) {
                                    ArtworkKind.COVER -> MR.strings.manga_cover
                                    ArtworkKind.BANNER -> MR.strings.manga_banner
                                },
                            ),
                        )
                    },
                )
            }
        }
    }

    @Composable
    private fun SharedImages(uris: List<String>, kind: ArtworkKind) {
        val models = remember(uris) { uris.map { it.toUri() } }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            val shape = when (kind) {
                ArtworkKind.COVER -> MangaCover.Book
                ArtworkKind.BANNER -> MangaCover.Banner
            }
            items(models) { model ->
                shape(
                    data = model,
                    modifier = Modifier.height(96.dp),
                )
            }
        }
    }
}
