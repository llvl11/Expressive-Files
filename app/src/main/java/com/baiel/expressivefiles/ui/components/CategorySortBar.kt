package com.baiel.expressivefiles.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baiel.expressivefiles.R
import com.baiel.expressivefiles.model.SortMode
import com.baiel.expressivefiles.model.SortOrder
import com.baiel.expressivefiles.ui.theme.ExpressiveAsymmetricShape
import com.baiel.expressivefiles.ui.theme.ExpressiveRoundedShape
import com.baiel.expressivefiles.ui.theme.PillShape

/**
 * Compact per-category ordering control: Name / Size / Date metrics plus
 * an Asc-Desc toggle. Tapping a metric selects it; tapping the ALREADY
 * selected metric clears the choice entirely - no metric is highlighted and
 * the listing falls back to the platform default ordering (name, A-Z), the
 * same order Android's file picker uses. Every choice is remembered per
 * category filter.
 */
@Composable
fun CategorySortBar(
    mode: SortMode,
    order: SortOrder,
    onChange: (SortMode, SortOrder) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding)
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                // Four 48dp targets and three gaps, even in a narrow pane.
                .width(maxWidth.coerceAtLeast(48.dp * 4 + 8.dp * 3)),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val metrics = listOf(
                SortMode.NAME to R.string.sort_metric_name,
                SortMode.SIZE to R.string.sort_metric_size,
                SortMode.DATE to R.string.sort_metric_date
            )
            metrics.forEach { (metric, labelRes) ->
                val selected = mode == metric
                SortMetricButton(
                    text = stringResource(labelRes),
                    selected = selected,
                    modifier = Modifier.weight(1f),
                    shape = when (metric) {
                        SortMode.SIZE -> ExpressiveRoundedShape
                        SortMode.DATE -> ExpressiveAsymmetricShape
                        else -> PillShape
                    },
                    onClick = {
                        if (selected) {
                            // Second tap on the active metric deselects it: the
                            // strip highlights nothing and the ordering returns
                            // to the platform default (name, A-Z).
                            onChange(SortMode.DEFAULT, SortOrder.ASCENDING)
                        } else {
                            onChange(metric, order)
                        }
                    }
                )
            }

            ChunkyIconButton(
                icon = if (order == SortOrder.ASCENDING) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                onClick = { onChange(mode, order.flipped()) },
                contentDescription = if (order == SortOrder.ASCENDING)
                    stringResource(R.string.sort_order_ascending)
                else stringResource(R.string.sort_order_descending),
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

private fun SortOrder.flipped(): SortOrder =
    if (this == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING

@Composable
private fun SortMetricButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = PillShape
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 800f),
        label = "sort_metric_scale"
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        label = "sort_metric_background"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "sort_metric_content"
    )

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { this.selected = selected }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button
            ) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
