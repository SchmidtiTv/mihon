package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.ContentCopy
import mihon.icons.materialsymbols.rounded.Photo
import mihon.icons.materialsymbols.rounded.Save
import mihon.icons.materialsymbols.rounded.Share
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ActionButton
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReaderPageActionsDialog(
    onDismissRequest: () -> Unit,
    onAddToCoverLibrary: (setAsCover: Boolean) -> Unit,
    onShare: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    var showAddCoverDialog by rememberSaveable { mutableStateOf(false) }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Row(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            ActionButton(
                modifier = Modifier.weight(1f),
                title = stringResource(MR.strings.action_add_to_cover_library),
                icon = MaterialSymbols.Rounded.Photo,
                onClick = { showAddCoverDialog = true },
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                title = stringResource(MR.strings.action_copy_to_clipboard),
                icon = MaterialSymbols.Rounded.ContentCopy,
                onClick = {
                    onShare(true)
                    onDismissRequest()
                },
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                title = stringResource(MR.strings.action_share),
                icon = MaterialSymbols.Rounded.Share,
                onClick = {
                    onShare(false)
                    onDismissRequest()
                },
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                title = stringResource(MR.strings.action_save),
                icon = MaterialSymbols.Rounded.Save,
                onClick = {
                    onSave()
                    onDismissRequest()
                },
            )
        }
    }

    if (showAddCoverDialog) {
        AddCoverDialog(
            onConfirm = { setAsCover ->
                onAddToCoverLibrary(setAsCover)
                showAddCoverDialog = false
            },
            onDismiss = { showAddCoverDialog = false },
        )
    }
}

@Composable
private fun AddCoverDialog(
    onConfirm: (setAsCover: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var setAsCover by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Text(stringResource(MR.strings.confirm_add_image_to_cover_library))

                LabeledCheckbox(
                    label = stringResource(MR.strings.set_as_cover),
                    checked = setAsCover,
                    onCheckedChange = { setAsCover = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(setAsCover) }) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        onDismissRequest = onDismiss,
    )
}
