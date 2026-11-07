package dev.dhun.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.dhun.design.DhunColors
import dev.dhun.design.DhunSpacing
import dev.dhun.design.components.DhunButton
import dev.dhun.design.components.DhunConfirmDialog
import dev.dhun.design.components.DhunTextButton
import dev.dhun.design.components.DhunTextField
import dev.dhun.design.components.GlassDialog

/**
 * The two playlist-management dialogs, shared by every place a saved playlist
 * can be renamed or deleted: the playlist detail page and the Library's
 * playlist list.
 *
 * They live here rather than in one screen because the list used to offer no
 * rename/delete at all while the detail page owned the only copies — the same
 * dialog rewritten a second time is how the two drift apart (different blank
 * rule, different failure copy, one closing on the tap and one waiting for the
 * write).
 *
 * Both dialogs are *result-driven*: the caller passes `isSaving`/`isDeleting`
 * from the write it actually started and `errorMessage` from that write's
 * failure, so a dialog can only close after the store returned, never on the
 * tap. `enabled = name.isNotBlank()` is the app's one name rule (the same check
 * `create`/`rename` enforce); a blank name can never be submitted.
 */

/** Rename a saved playlist. Empty names are refused by the Save button. */
@Composable
internal fun PlaylistRenameDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isSaving: Boolean = false,
    errorMessage: String? = null,
) {
    var name by remember(current) { mutableStateOf(current) }
    GlassDialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Text(
            "Rename playlist",
            style = MaterialTheme.typography.titleMedium,
            color = DhunColors.textPrimary,
        )
        Spacer(modifier = Modifier.height(DhunSpacing.md))
        DhunTextField(
            value = name,
            onValueChange = { name = it },
            isError = errorMessage != null,
        )
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(DhunSpacing.xs))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = DhunColors.error,
            )
        }
        Spacer(modifier = Modifier.height(DhunSpacing.md))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            DhunTextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
            Spacer(modifier = Modifier.width(DhunSpacing.sm))
            DhunButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank() && !isSaving,
                loading = isSaving,
            ) { Text(if (errorMessage != null) "Try again" else "Save") }
        }
    }
}

/**
 * Confirm deleting a saved playlist. The title names the playlist so the user
 * can see exactly which one is about to go, and the body states what is *not*
 * deleted (songs, downloads, favorites, history) — the same copy the detail
 * page uses.
 */
@Composable
internal fun PlaylistDeleteDialog(
    playlistName: String,
    isDeleting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DhunConfirmDialog(
        title = "Delete \"$playlistName\"?",
        message = "This removes the playlist from your local library. Songs, downloads, favorites and history are not deleted. Backups or device storage may retain copies; this is not secure erasure.",
        confirmLabel = "Delete",
        retryLabel = "Retry deletion",
        pendingLabel = "Deleting…",
        isPending = isDeleting,
        errorMessage = errorMessage,
        onDismissRequest = onDismiss,
        onConfirm = onConfirm,
    )
}
