package eu.kanade.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import eu.kanade.presentation.manga.ArtworkImage
import eu.kanade.tachiyomi.ui.manga.ArtworkGalleryState
import kotlinx.coroutines.launch
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Check
import mihon.icons.materialsymbols.rounded.Close
import mihon.icons.materialsymbols.rounded.Delete
import mihon.icons.materialsymbols.rounded.Edit
import mihon.icons.materialsymbols.rounded.Error
import mihon.icons.materialsymbols.rounded.Refresh
import mihon.icons.materialsymbols.rounded.Save
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import androidx.compose.foundation.Image as ComposeImage

@Composable
fun ArtworkGalleryDialog(
    state: ArtworkGalleryState,
    title: String,
    emptyText: String,
    errorText: String,
    useLabel: String,
    removeTitle: String,
    removeConfirmation: String,
    thumbnail: MangaCover,
    thumbnailWidth: Dp,
    onUseImage: (ArtworkImage) -> Unit,
    onDeleteImage: (ArtworkImage) -> Unit,
    onSaveImage: (ArtworkImage) -> Unit,
    onDismissRequest: () -> Unit,
    onRetry: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    emptyActions: List<EmptyScreenAction> = emptyList(),
    onLabelImage: ((ArtworkImage, String) -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Opaque, since it opens on top of the cover dialog
                .background(MaterialTheme.colorScheme.scrim),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Close,
                        contentDescription = stringResource(MR.strings.action_close),
                        tint = Color.White,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(modifier = Modifier.weight(1f))
                actions()
            }

            when (state) {
                ArtworkGalleryState.Loading -> LoadingScreen(Modifier.fillMaxSize())
                ArtworkGalleryState.Error -> {
                    CompositionLocalProvider(LocalContentColor provides Color.White) {
                        EmptyScreen(
                            message = errorText,
                            modifier = Modifier.fillMaxSize(),
                            actions = listOf(
                                EmptyScreenAction(
                                    stringRes = MR.strings.action_retry,
                                    icon = MaterialSymbols.Rounded.Refresh,
                                    onClick = onRetry,
                                ),
                            ),
                        )
                    }
                }
                is ArtworkGalleryState.Ready -> if (state.images.isEmpty()) {
                    CompositionLocalProvider(LocalContentColor provides Color.White) {
                        EmptyScreen(
                            message = emptyText,
                            modifier = Modifier.fillMaxSize(),
                            actions = emptyActions,
                        )
                    }
                } else {
                    ArtworkGalleryContent(
                        images = state.images,
                        useLabel = useLabel,
                        removeTitle = removeTitle,
                        removeConfirmation = removeConfirmation,
                        thumbnail = thumbnail,
                        thumbnailWidth = thumbnailWidth,
                        onUseImage = onUseImage,
                        onDeleteImage = onDeleteImage,
                        onSaveImage = onSaveImage,
                        onLabelImage = onLabelImage,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ArtworkGalleryContent(
    images: List<ArtworkImage>,
    useLabel: String,
    removeTitle: String,
    removeConfirmation: String,
    thumbnail: MangaCover,
    thumbnailWidth: Dp,
    onUseImage: (ArtworkImage) -> Unit,
    onDeleteImage: (ArtworkImage) -> Unit,
    onSaveImage: (ArtworkImage) -> Unit,
    onLabelImage: ((ArtworkImage, String) -> Unit)?,
) {
    // Opens on the applied image
    val pagerState = rememberPagerState(
        initialPage = images.indexOfFirst { it.isCurrent }.coerceAtLeast(0),
    ) { images.size }
    val scope = rememberCoroutineScope()

    // Follow the selected image rather than its position, as importing an image puts it first
    val currentImages by rememberUpdatedState(images)
    var selectedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var knownUrls by remember { mutableStateOf(images.mapTo(HashSet()) { it.url }) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { page -> selectedUrl = currentImages.getOrNull(page)?.url }
    }
    LaunchedEffect(images) {
        // A new url is an imported or renamed image, which the pager moves to
        val addedUrl = images.firstOrNull { it.url !in knownUrls }?.url
        knownUrls = images.mapTo(HashSet()) { it.url }
        val page = images.indexOfFirst { it.url == (addedUrl ?: selectedUrl) }
        if (page >= 0 && page != pagerState.currentPage) pagerState.scrollToPage(page)
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        pageSpacing = 16.dp,
    ) { page ->
        ArtworkPage(image = images[page])
    }

    // The pager can still be on a page that a deletion took away.
    val current = images[pagerState.currentPage.coerceAtMost(images.lastIndex)]
    // By url, so the dialogs act on the image they were opened for even if the pager moves meanwhile
    var deleteUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var labelUrl by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            itemsIndexed(images, key = { _, image -> image.url }) { index, image ->
                val selected = index == pagerState.currentPage
                Box(modifier = Modifier.width(thumbnailWidth)) {
                    thumbnail(
                        data = image.data,
                        contentDescription = image.displayName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = MaterialTheme.shapes.extraSmall,
                            )
                            .clickable { scope.launch { pagerState.animateScrollToPage(index) } },
                    )
                    if (image.isCurrent) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(2.dp)
                                .size(12.dp),
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            current.providerLogo?.let { logo ->
                ComposeImage(
                    painter = painterResource(logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                )
            }
            Text(
                text = current.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
            if (current.isCurrent) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Check,
                    contentDescription = stringResource(MR.strings.artwork_current),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (onLabelImage != null && current.importedFile != null) {
                IconButton(onClick = { labelUrl = current.url }) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Edit,
                        contentDescription = stringResource(MR.strings.action_edit_label),
                        tint = Color.White,
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onUseImage(current) },
                enabled = !current.isCurrent,
            ) {
                Text(text = useLabel)
            }
            FilledTonalIconButton(onClick = { onSaveImage(current) }) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Save,
                    contentDescription = stringResource(MR.strings.action_save),
                )
            }
            if (current.importedFile != null) {
                FilledTonalIconButton(onClick = { deleteUrl = current.url }) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Delete,
                        contentDescription = removeTitle,
                    )
                }
            }
        }
    }

    val labelImage = images.find { it.url == labelUrl }
    if (labelImage != null && onLabelImage != null) {
        LabelDialog(
            label = labelImage.label.orEmpty(),
            onConfirm = { label ->
                labelUrl = null
                onLabelImage(labelImage, label)
            },
            onDismissRequest = { labelUrl = null },
        )
    }

    val deleteImage = images.find { it.url == deleteUrl }
    if (deleteImage != null) {
        ArtworkConfirmDialog(
            title = removeTitle,
            text = removeConfirmation,
            onConfirm = { onDeleteImage(deleteImage) },
            onDismissRequest = { deleteUrl = null },
        )
    }
}

@Composable
private fun ArtworkPage(image: ArtworkImage) {
    var imageState by remember(image.data) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = image.data,
            contentDescription = image.displayName,
            contentScale = ContentScale.Fit,
            onState = { imageState = it },
            modifier = Modifier.fillMaxSize(),
        )
        when (imageState) {
            is AsyncImagePainter.State.Loading -> CircularProgressIndicator()
            is AsyncImagePainter.State.Error -> Icon(
                imageVector = MaterialSymbols.Rounded.Error,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp),
            )
            else -> {}
        }
    }
}

@Composable
fun ArtworkConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        title = { Text(text = title) },
        text = { Text(text = text) },
    )
}

@Composable
private fun LabelDialog(
    label: String,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(label) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.action_edit_label)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(text = stringResource(MR.strings.artwork_label)) },
                singleLine = true,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}
