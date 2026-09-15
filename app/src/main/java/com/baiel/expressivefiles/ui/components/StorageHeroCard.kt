package com.baiel.expressivefiles.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baiel.expressivefiles.R
import com.baiel.expressivefiles.model.StorageStats
import com.baiel.expressivefiles.ui.theme.ExpressiveCookieShape
import com.baiel.expressivefiles.ui.theme.PillShape

@Composable
fun StorageHeroCard(
    stats: StorageStats,
    onAnalyzeStorageClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCollapsed: Boolean = false,
    onToggleCollapse: () -> Unit = {}
) {
    val usedRatio = if (stats.totalBytes > 0) {
        (stats.usedBytes.toFloat() / stats.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val analysisLabel = stringResource(R.string.analysis_title)

    val animatedUsedRatio by animateFloatAsState(
        targetValue = usedRatio,
        animationSpec = tween(durationMillis = 800),
        label = "storage_ratio"
    )

    Column(
        modifier = modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Vibrant Lilac Storage Hero Box (rounded-[32px] with #EADDFF and #21005D text)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp)),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 20.dp,
                        vertical = 20.dp
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            // Inset from the card edge plus a plain (unclipped)
                            // tap target, so expressive glyph overshoot can
                            // never be cropped by a clipping shape. No ripple:
                            // the whole card already animates on collapse.
                            .padding(start = 8.dp, end = 8.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button
                            ) { onToggleCollapse() }
                    ) {
                        Column {
                            Text(
                                text = formatFileSize(stats.usedBytes),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                letterSpacing = (-0.5).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.hero_used_suffix),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Blob-shaped button to open analytics: an expressive
                        // cookie instead of another plain circle.
                        Surface(
                            shape = ExpressiveCookieShape,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .size(56.dp)
                                .semantics { contentDescription = analysisLabel }
                                .cookieClickable(shape = ExpressiveCookieShape, onClick = onAnalyzeStorageClick)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { animatedUsedRatio },
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                    strokeWidth = 3.dp,
                                    strokeCap = StrokeCap.Round
                                )
                            }
                        }

                        // Collapse / Expand Toggle Button: a stadium pill so the
                        // two header buttons are distinct expressive shapes.
                        Surface(
                            shape = PillShape,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                            modifier = Modifier
                                .size(48.dp)
                                .cookieClickable(shape = PillShape, onClick = onToggleCollapse)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isCollapsed) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = if (isCollapsed) stringResource(R.string.hero_expand) else stringResource(R.string.hero_collapse),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // Expanded Section: Progress Bar & Detailed Free Space
                AnimatedVisibility(
                    visible = !isCollapsed,
                    enter = expandVertically(animationSpec = spring(dampingRatio = 0.65f, stiffness = 350f)) + fadeIn(animationSpec = tween(180)),
                    exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(160))
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(14.dp))

                        // Vibrant Progress Bar
                        SquigglyProgressIndicator(
                            progress = usedRatio,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.hero_remaining_of, formatFileSize(stats.freeBytes), formatFileSize(stats.totalBytes)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}

