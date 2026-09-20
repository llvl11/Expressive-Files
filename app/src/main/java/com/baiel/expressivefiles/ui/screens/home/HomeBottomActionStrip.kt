package com.baiel.expressivefiles.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baiel.expressivefiles.R
import com.baiel.expressivefiles.model.ClipboardAction
import com.baiel.expressivefiles.model.ClipboardState
import com.baiel.expressivefiles.ui.components.ChunkyIconButton
import com.baiel.expressivefiles.ui.theme.ChunkyTileShape
import com.baiel.expressivefiles.ui.theme.ExpressiveAsymmetricShape
import com.baiel.expressivefiles.ui.theme.ExpressiveLeafShape
import com.baiel.expressivefiles.ui.theme.ExpressiveRoundedShape
import com.baiel.expressivefiles.ui.theme.PillShape
import com.baiel.expressivefiles.ui.theme.osIconButtonShape

@Composable
fun HomeBottomActionStrip(
    // Collected here so clipboard changes only recompose this strip.
    clipboardFlow: kotlinx.coroutines.flow.StateFlow<ClipboardState?>,
    isSelectionMode: Boolean,
    selectedCount: Int,
    onClearSelected: () -> Unit,
    onPaste: () -> Unit,
    onClearClipboard: () -> Unit,
    onCopySelected: () -> Unit,
    onCutSelected: () -> Unit,
    onCompressSelected: () -> Unit,
    onShareSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard by clipboardFlow.collectAsStateWithLifecycle()

    Box(modifier = modifier) {
        // Clipboard Paste Bar
        AnimatedVisibility(
            visible = clipboard != null && !isSelectionMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            clipboard?.let { clip ->
                Surface(
                    shape = ChunkyTileShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (clip.action == ClipboardAction.COPY) Icons.Rounded.ContentCopy else Icons.Rounded.ContentCut,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(if (clip.action == ClipboardAction.COPY) R.string.clipboard_copy_action else R.string.clipboard_move_action) + " " +
                                        pluralStringResource(R.plurals.clipboard_items_suffix, clip.items.size, clip.items.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.clipboard_paste_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChunkyIconButton(
                                icon = Icons.Rounded.ContentPaste,
                                onClick = onPaste,
                                size = 40.dp,
                                shape = PillShape,
                                backgroundColor = MaterialTheme.colorScheme.primary,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                contentDescription = stringResource(R.string.clipboard_paste)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            ChunkyIconButton(
                                icon = Icons.Rounded.Close,
                                onClick = onClearClipboard,
                                size = 40.dp,
                                shape = PillShape,
                                backgroundColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                contentDescription = stringResource(R.string.clipboard_clear)
                            )
                        }
                    }
                }
            }
        }

        // Selection Mode Action Strip with entrance animation.
        AnimatedVisibility(
            visible = isSelectionMode && selectedCount > 0,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(250)),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        ) {
            Surface(
                shape = ChunkyTileShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Accent-themed actions follow the active color palette,
                    // mirroring the top-bar's lit-sort-toggle styling.
                    val accent = MaterialTheme.colorScheme.primary
                    val accentBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)

                    // Role-based expressive shapes: one stable silhouette
                    // per action.
                    ChunkyIconButton(
                        icon = Icons.Rounded.ContentCopy,
                        onClick = onCopySelected,
                        size = 44.dp,
                        shape = osIconButtonShape(CircleShape),
                        backgroundColor = accentBg,
                        tint = accent,
                        contentDescription = stringResource(R.string.strip_copy)
                    )

                    ChunkyIconButton(
                        icon = Icons.Rounded.ContentCut,
                        onClick = onCutSelected,
                        size = 44.dp,
                        shape = osIconButtonShape(ExpressiveRoundedShape),
                        backgroundColor = accentBg,
                        tint = accent,
                        contentDescription = stringResource(R.string.strip_move)
                    )

                    ChunkyIconButton(
                        icon = Icons.Rounded.Archive,
                        onClick = onCompressSelected,
                        size = 44.dp,
                        shape = osIconButtonShape(ExpressiveAsymmetricShape),
                        backgroundColor = accentBg,
                        tint = accent,
                        contentDescription = stringResource(R.string.strip_compress)
                    )

                    ChunkyIconButton(
                        icon = Icons.Rounded.Share,
                        onClick = onShareSelected,
                        size = 44.dp,
                        shape = osIconButtonShape(PillShape),
                        backgroundColor = accentBg,
                        tint = accent,
                        contentDescription = stringResource(R.string.strip_share)
                    )

                    ChunkyIconButton(
                        icon = Icons.Rounded.Delete,
                        onClick = onDeleteSelected,
                        size = 44.dp,
                        backgroundColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        tint = MaterialTheme.colorScheme.error,
                        contentDescription = stringResource(R.string.strip_delete)
                    )

                    ChunkyIconButton(
                        icon = Icons.Rounded.Close,
                        onClick = onClearSelected,
                        size = 44.dp,
                        shape = osIconButtonShape(ExpressiveLeafShape),
                        backgroundColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        contentDescription = stringResource(R.string.strip_clear_selection)
                    )
                }
            }
        }
    }
}

