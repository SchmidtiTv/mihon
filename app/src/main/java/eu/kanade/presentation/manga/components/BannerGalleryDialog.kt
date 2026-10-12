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
fun BannerGalleryDialog(
    state: ArtworkGalleryState,
    hasBanner: Boolean,
    onUseBanner: (ArtworkImage) -> Unit,
    onDeleteBanner: (ArtworkImage) -> Unit,
    onSaveBanner: (ArtworkImage) -> Unit,
    onLabelBanner: (ArtworkImage, String) -> Unit,
    onImportBanner: () -> Unit,
    onRemoveBanner: () -> Unit,
    onDismissRequest: () -> Unit,
    onRetry: () -> Unit,
) {
    var showRemoveConfirmation by rememberSaveable { mutableStateOf(false) }

    ArtworkGalleryDialog(
        state = state,
        title = stringResource(MR.strings.action_browse_banners),
        emptyText = stringResource(MR.strings.no_banners_found),
        errorText = stringResource(MR.strings.error_loading_banners),
        useLabel = stringResource(MR.strings.action_use_this_banner),
        removeTitle = stringResource(MR.strings.action_remove_banner),
        removeConfirmation = stringResource(MR.strings.remove_banner_confirmation),
        thumbnail = MangaCover.Banner,
        // The smallest width whose 3:1 thumbnail clears the 48.dp minimum touch target
        thumbnailWidth = 144.dp,
        onUseImage = onUseBanner,
        onDeleteImage = onDeleteBanner,
        onSaveImage = onSaveBanner,
        onDismissRequest = onDismissRequest,
        onRetry = onRetry,
        onLabelImage = onLabelBanner,
        emptyActions = listOf(
            EmptyScreenAction(
                stringRes = MR.strings.action_import_banner,
                icon = MaterialSymbols.Rounded.Add,
                onClick = onImportBanner,
            ),
        ),
        actions = {
            IconButton(onClick = onImportBanner) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Add,
                    contentDescription = stringResource(MR.strings.action_import_banner),
                    tint = Color.White,
                )
            }
            if (hasBanner) {
                IconButton(onClick = { showRemoveConfirmation = true }) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Delete,
                        contentDescription = stringResource(MR.strings.action_clear_banner),
                        tint = Color.White,
                    )
                }
            }
        },
    )

    if (showRemoveConfirmation) {
        ArtworkConfirmDialog(
            title = stringResource(MR.strings.action_clear_banner),
            text = stringResource(MR.strings.clear_banner_confirmation),
            onConfirm = onRemoveBanner,
            onDismissRequest = { showRemoveConfirmation = false },
        )
    }
}
