package eu.kanade.presentation.manga.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.ArtworkImage
import eu.kanade.tachiyomi.ui.manga.ArtworkGalleryState
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Add
import mihon.icons.materialsymbols.rounded.Delete
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreenAction

@Composable
fun CoverGalleryDialog(
    state: ArtworkGalleryState,
    hasCustomCover: Boolean,
    onUseCover: (ArtworkImage) -> Unit,
    onDeleteCover: (ArtworkImage) -> Unit,
    onSaveCover: (ArtworkImage) -> Unit,
    onLabelCover: (ArtworkImage, String) -> Unit,
    onImportCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onDismissRequest: () -> Unit,
    onRetry: () -> Unit,
) {
    var showRemoveConfirmation by rememberSaveable { mutableStateOf(false) }

    ArtworkGalleryDialog(
        state = state,
        title = stringResource(MR.strings.action_browse_covers),
        emptyText = stringResource(MR.strings.no_covers_found),
        errorText = stringResource(MR.strings.error_loading_covers),
        useLabel = stringResource(MR.strings.action_use_this_cover),
        removeTitle = stringResource(MR.strings.action_remove_cover),
        removeConfirmation = stringResource(MR.strings.remove_cover_confirmation),
        thumbnail = MangaCover.Book,
        thumbnailWidth = 48.dp,
        onUseImage = onUseCover,
        onDeleteImage = onDeleteCover,
        onSaveImage = onSaveCover,
        onDismissRequest = onDismissRequest,
        onRetry = onRetry,
        onLabelImage = onLabelCover,
        emptyActions = listOf(
            EmptyScreenAction(
                stringRes = MR.strings.action_import_cover,
                icon = MaterialSymbols.Rounded.Add,
                onClick = onImportCover,
            ),
        ),
        actions = {
            IconButton(onClick = onImportCover) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Add,
                    contentDescription = stringResource(MR.strings.action_import_cover),
                    tint = Color.White,
                )
            }
            if (hasCustomCover) {
                IconButton(onClick = { showRemoveConfirmation = true }) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Delete,
                        contentDescription = stringResource(MR.strings.action_clear_cover),
                        tint = Color.White,
                    )
                }
            }
        },
    )

    if (showRemoveConfirmation) {
        ArtworkConfirmDialog(
            title = stringResource(MR.strings.action_clear_cover),
            text = stringResource(MR.strings.clear_cover_confirmation),
            onConfirm = onRemoveCover,
            onDismissRequest = { showRemoveConfirmation = false },
        )
    }
}
