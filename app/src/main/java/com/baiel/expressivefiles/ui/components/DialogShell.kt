package com.baiel.expressivefiles.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.baiel.expressivefiles.R
import com.baiel.expressivefiles.ui.theme.ChunkyTileShape
import com.baiel.expressivefiles.ui.theme.ExpressiveRoundedShape

/**
 * Entrance motion shared by every dialog surface: a quick scale pop that
 * settles fast, so appearing dialogs read as fast rather than instant.
 * Apply to the dialog Surface (shell, archive, progress).
 */
@Composable
fun Modifier.dialogEnterMotion(scaleFrom: Float = 0.96f): Modifier {
    val enter = remember(scaleFrom) { Animatable(scaleFrom) }
    LaunchedEffect(scaleFrom) { enter.animateTo(1f, tween(180)) }
    return this.graphicsLayer {
        scaleX = enter.value
        scaleY = enter.value
        // Draw-phase read: no per-frame recomposition of the dialog.
        // Guard the span: scaleFrom == 1f would divide by zero and hand a NaN
        // alpha to graphicsLayer (coerceIn does NOT sanitize NaN), rendering the
        // dialog invisible with no error.
        val span = 1f - scaleFrom
        val progress = if (span <= 0f) 1f else ((enter.value - scaleFrom) / span).coerceIn(0f, 1f)
        alpha = 0.5f + 0.5f * progress
    }
}

/**
 * Shared chrome for the app's chunky dialogs (new folder/file, rename, ...):
 * dialog + tile surface, icon header with close button, body slot, and the
 * Cancel/Confirm button row. Callers own double-tap buffering and pass
 * already-guarded [onDismiss]/[onConfirm] lambdas.
 */
@Composable
fun ChunkyDialogShell(
    onDismiss: () -> Unit,
    icon: ImageVector,
    iconBackgroundColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    widthFraction: Float = 0.92f,
    confirmText: String,
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
    confirmBackgroundColor: Color = MaterialTheme.colorScheme.primary,
    confirmContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    confirmWeight: Float = 1.3f,
    cancelText: String = stringResource(R.string.dialog_cancel),
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        // Real drop shadow in light theme only; dark themes carry depth
        // through tonal elevation instead of shadows.
        val dialogShadow = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.dp else 16.dp
        Surface(
            shape = ChunkyTileShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = dialogShadow,
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .dialogEnterMotion()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = if (subtitle != null) Modifier.weight(1f) else Modifier
                    ) {
                        CookieIconContainer(
                            backgroundColor = iconBackgroundColor,
                            size = 44.dp,
                            shape = ExpressiveRoundedShape
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        if (subtitle != null) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    CloseIconButton(onClick = onDismiss)
                }

                Spacer(modifier = Modifier.height(20.dp))

                content()

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChunkyButton(
                        text = cancelText,
                        onClick = onDismiss,
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    ChunkyButton(
                        text = confirmText,
                        onClick = onConfirm,
                        backgroundColor = confirmBackgroundColor,
                        contentColor = confirmContentColor,
                        enabled = confirmEnabled,
                        modifier = Modifier.weight(confirmWeight)
                    )
                }
            }
        }
    }
}
