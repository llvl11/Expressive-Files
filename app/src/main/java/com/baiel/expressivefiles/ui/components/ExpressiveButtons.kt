package com.baiel.expressivefiles.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.res.stringResource
import com.baiel.expressivefiles.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baiel.expressivefiles.model.OsType
import com.baiel.expressivefiles.ui.theme.ChunkyTileShape
import com.baiel.expressivefiles.ui.theme.LocalOsType
import com.baiel.expressivefiles.ui.theme.PillShape
import com.baiel.expressivefiles.ui.theme.osIconButtonShape
import kotlinx.coroutines.launch

/**
 * Shared plumbing for the press-scale "chunky" touch targets: standard
 * [clickable] with the ripple suppressed (visuals are the flat background +
 * spring scale), TalkBack role, and haptics on commit.
 */
@Composable
private fun rememberPressScale(
    enabled: Boolean,
    onClick: () -> Unit
): Pair<Modifier, Float> {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val currentOnClick by rememberUpdatedState(onClick)
    val view = LocalView.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = 500f),
        label = "chunky_button_scale"
    )

    val tapModifier = Modifier.clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        role = Role.Button
    ) {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        currentOnClick()
    }
    return tapModifier to scale
}

@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    shape: Shape? = null,
    height: Dp = Dp.Unspecified,
    enabled: Boolean = true
) {
    val (tapModifier, scale) = rememberPressScale(enabled, onClick)
    // MagicOS: capsule buttons, semibold type, slightly shorter; Pixel keeps
    // the chunky rounded-rectangle with ExtraBold type.
    val magic = LocalOsType.current == OsType.MAGIC_OS
    val resolvedShape = shape ?: if (magic) PillShape else ChunkyTileShape
    val resolvedHeight = if (height.value.isNaN()) (if (magic) 48.dp else 58.dp) else height.coerceAtLeast(48.dp)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = resolvedHeight)
            .clip(resolvedShape)
            .background(if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.4f))
            .then(tapModifier)
            .padding(horizontal = if (magic) 24.dp else 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontWeight = if (magic) FontWeight.SemiBold else FontWeight.ExtraBold,
                fontSize = 15.sp,
                letterSpacing = if (magic) 0.sp else 0.2.sp
            )
        }
    }
}

/**
 * Standard pill close (X) button used by dialog/sheet headers. One shared
 * implementation so every surface shows the identical visual and haptics.
 */
@Composable
fun CloseIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ChunkyIconButton(
        icon = Icons.Rounded.Close,
        onClick = onClick,
        modifier = modifier,
        contentDescription = stringResource(R.string.fab_close_actions),
        size = 36.dp,
        shape = PillShape,
        backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )
}

@Composable
fun ChunkyIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = 48.dp,
    shape: Shape? = null,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val currentOnClick by rememberUpdatedState(onClick)
    val view = LocalView.current
    // MagicOS icon buttons are perfect circles; Pixel keeps rounded squares.
    val resolvedShape = shape ?: osIconButtonShape()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 800f),
        label = "chunky_icon_button_scale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(size.coerceAtLeast(48.dp))
            .clip(resolvedShape)
            .background(if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.4f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button
            ) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                currentOnClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size((size.value * 0.5f).dp)
        )
    }
}

@Composable
fun ChunkyChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    badgeText: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val currentOnClick by rememberUpdatedState(onClick)
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // One-shot bounce fired on every tap so filter clicks feel tactile even
    // when the selection state does not change (e.g. re-tapping the same chip).
    val chipPulse = remember { Animatable(1f) }
    // MagicOS filter chips run a touch shorter than the Pixel ones.
    val chipHeight = 48.dp

    // Elastic physics: press dips with a bouncy spring, selection pops with a
    // visible overshoot instead of the default critically-damped spring.
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else if (selected) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "chunky_chip_scale"
    )

    // Unselected chips sit on a container lighter than the page background
    // (M3 expressive "elevated item" look); selected = solid accent.
    val isDarkBg = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
                      else if (isDarkBg) MaterialTheme.colorScheme.surfaceContainerHigh
                      else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "chip_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "chip_content"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale * chipPulse.value
                scaleY = scale * chipPulse.value
            }
            .height(chipHeight)
            .semantics { this.selected = selected }
            .clip(PillShape)
            .background(bg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button
            ) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                scope.launch {
                    chipPulse.snapTo(0.82f)
                    chipPulse.animateTo(1f, spring(dampingRatio = 0.28f, stiffness = 460f))
                }
                currentOnClick()
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            if (badgeText != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
