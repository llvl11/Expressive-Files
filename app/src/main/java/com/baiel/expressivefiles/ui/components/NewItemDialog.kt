package com.baiel.expressivefiles.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baiel.expressivefiles.R
import com.baiel.expressivefiles.model.OsType
import com.baiel.expressivefiles.ui.theme.ChunkyTileShape
import com.baiel.expressivefiles.ui.theme.ExpressiveRoundedShape
import com.baiel.expressivefiles.ui.theme.FolderColor
import com.baiel.expressivefiles.ui.theme.LocalOsType
import com.baiel.expressivefiles.viewmodel.FileViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

enum class DialogActionType {
    NEW_FOLDER,
    NEW_FILE,
    RENAME,
    DELETE_CONFIRM
}

@Composable
fun NewItemDialog(
    viewModel: FileViewModel,
    type: DialogActionType,
    initialValue: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val pendingItems by viewModel.pendingDeleteItems.collectAsStateWithLifecycle()
    var textValue by remember { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    // Standard rename UX: pre-select the name without the extension, so
    // typing immediately replaces it while the extension survives.
    val renameSelection = remember(type, initialValue) {
        if (type != DialogActionType.RENAME) null
        else {
            val dot = initialValue.lastIndexOf('.')
            if (dot > 0) TextRange(0, dot) else TextRange(0, initialValue.length)
        }
    }

    // Buffered actions: the first tap on Cancel/Confirm wins and tears the
    // dialog down immediately; further taps (double-tap, IME Done racing the
    // tap) are ignored so an action can never fire twice.
    var actionHandled by remember { mutableStateOf(false) }
    fun buffered(action: () -> Unit) {
        if (!actionHandled) {
            actionHandled = true
            action()
        }
    }

    val title = when (type) {
        DialogActionType.NEW_FOLDER -> stringResource(R.string.new_folder_title)
        DialogActionType.NEW_FILE -> stringResource(R.string.new_file_title)
        DialogActionType.RENAME -> stringResource(R.string.rename_title)
        DialogActionType.DELETE_CONFIRM -> stringResource(R.string.delete_confirm_title)
    }

    val icon = when (type) {
        DialogActionType.NEW_FOLDER -> Icons.Rounded.CreateNewFolder
        DialogActionType.NEW_FILE -> Icons.AutoMirrored.Rounded.NoteAdd
        DialogActionType.RENAME -> Icons.Rounded.DriveFileRenameOutline
        DialogActionType.DELETE_CONFIRM -> Icons.Rounded.Delete
    }

    val isDelete = type == DialogActionType.DELETE_CONFIRM

    if (!isDelete) {
        LaunchedEffect(Unit) {
            runCatching { focusRequester.requestFocus() }
            // Focus alone doesn't reliably pop the keyboard - ask for it.
            keyboardController?.show()
        }
    }

    Dialog(onDismissRequest = { buffered(onDismiss) }) {
        Surface(
            shape = ChunkyTileShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(if (isDelete) 0.95f else 0.92f)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CookieIconContainer(
                            backgroundColor = if (isDelete) MaterialTheme.colorScheme.error else FolderColor,
                            size = 44.dp,
                            shape = ExpressiveRoundedShape
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isDelete) MaterialTheme.colorScheme.error else FolderColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    CloseIconButton(onClick = { buffered(onDismiss) })
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (isDelete) {
                    val message = when (pendingItems.size) {
                        0 -> pluralStringResource(R.plurals.delete_many_message, 0, 0)
                        1 -> stringResource(R.string.delete_single_message, pendingItems.first().name)
                        else -> pluralStringResource(
                            R.plurals.delete_many_message,
                            pendingItems.size,
                            pendingItems.size
                        )
                    }
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    CursorScrollingTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        initialSelection = renameSelection,
                        placeholderText = when (type) {
                            DialogActionType.NEW_FOLDER -> stringResource(R.string.new_folder_placeholder)
                            DialogActionType.NEW_FILE -> stringResource(R.string.new_file_placeholder)
                            DialogActionType.RENAME -> stringResource(R.string.rename_placeholder)
                            // Unreachable: the delete branch never renders a text field.
                            DialogActionType.DELETE_CONFIRM -> ""
                        },
                        focusRequester = focusRequester,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (textValue.isNotBlank()) {
                                    buffered { onConfirm(textValue.trim()) }
                                }
                            }
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChunkyButton(
                        text = stringResource(R.string.dialog_cancel),
                        onClick = { buffered(onDismiss) },
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    ChunkyButton(
                        text = when {
                            isDelete -> stringResource(R.string.dialog_delete)
                            type == DialogActionType.RENAME -> stringResource(R.string.rename_title)
                            else -> stringResource(R.string.dialog_create)
                        },
                        onClick = {
                            if (isDelete || textValue.isNotBlank()) {
                                buffered { onConfirm(textValue.trim()) }
                            }
                        },
                        backgroundColor = if (isDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        contentColor = if (isDelete) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                        enabled = isDelete || textValue.isNotBlank(),
                        modifier = Modifier.weight(if (isDelete) 1.5f else 1.3f)
                    )
                }
            }
        }
    }
}


private val CursorWidth = 2.dp
private val FieldHorizontalPadding = 16.dp
// Touch edge auto-scroll tuning (mobile rules): a finger-sized threshold
// that also extends past the bounds, speed scaling with the outside
// distance, paced by VSYNC frames instead of a fixed timer.
// Breathing room from the guide's ensureCursorVisible: the caret never parks
// flush against the pixel boundary, so "visible" and "edge-hold" stay apart.
private val RevealPadding = 16.dp
// Edge fade width signalling hidden text in that direction.
private val EdgeFadeWidth = 8.dp
private val EdgeThreshold = 32.dp
private const val EdgeMaxSpeedPerFrame = 22f

/**
 * Single-line text input styled like the rest of the app's outlined fields,
 * which always keeps the caret visible: Compose's internal single-line
 * scrolling only follows the caret while text is being typed, so with a long
 * file/folder name the tail becomes unreachable once the caret parks at the
 * edge of the field. Here the caret position is read from the layout result
 * and the field is scrolled programmatically on every selection change, so
 * keyboard moves, taps and IME actions all bring the caret - and the end of
 * the word - back into view.
 */
@Composable
private fun CursorScrollingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholderText: String,
    focusRequester: FocusRequester,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    initialSelection: TextRange? = null
) {
    // Rename pre-selects the basename (see NewItemDialog); anything else
    // starts with the caret at the end of the existing text.
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, initialSelection ?: TextRange(value.length))) }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    // Finger tracking for edge auto-scroll: position in container
    // coordinates, updated on every move while pressed.
    var fingerDown by remember { mutableStateOf(false) }
    var fingerX by remember { mutableFloatStateOf(-1f) }

    // Edge auto-scroll while the finger is HELD at or past the visible edge:
    // driven by finger position (not the caret), so caret-handle drags work
    // too. Speed scales with how far past the edge the finger sits, paced by
    // VSYNC frames so 90/120Hz screens stay smooth. Stops on release, when
    // the finger comes back inside, or at the scroll bounds.
    LaunchedEffect(fingerDown, containerWidthPx) {
        if (!fingerDown || containerWidthPx <= 0f) return@LaunchedEffect
        val thresholdPx = with(density) { EdgeThreshold.toPx() }
        fun speedFor(x: Float, width: Float): Float {
            if (x < thresholdPx) {
                val factor = ((thresholdPx - x) / thresholdPx).coerceIn(0.2f, 3f)
                return -EdgeMaxSpeedPerFrame * factor
            }
            if (x > width - thresholdPx) {
                val factor = ((x - (width - thresholdPx)) / thresholdPx).coerceIn(0.2f, 3f)
                return EdgeMaxSpeedPerFrame * factor
            }
            return 0f
        }
        // Reads must not subscribe: every move/scroll pixel would restart
        // this effect instead of letting the frame loop run.
        while (isActive) {
            val (x, width) = Snapshot.withoutReadObservation { fingerX to containerWidthPx }
            val speed = speedFor(x, width)
            if (speed == 0f) break
            val before = Snapshot.withoutReadObservation { scrollState.value }
            try {
                scrollState.scroll { scrollBy(speed) }
            } catch (_: CancellationException) {
                // Lost the scroll-mutex race (e.g. a reveal settling at the
                // same moment): real cancellation still propagates via the
                // isActive check, anything else retries next frame instead
                // of letting the whole loop die after one scroll.
                if (!isActive) break
            }
            // Clamped against the bound - nowhere further to go.
            if (Snapshot.withoutReadObservation { scrollState.value } == before) break
            withFrameMillis { }
        }
    }

    // Gated off while the finger is down: the hold loop above owns the
    // viewport then, so the two never tug-of-war over the scroll mutex.
    // When the finger lifts, this re-runs once and settles the caret.
    LaunchedEffect(fieldValue, layoutResult, containerWidthPx, fingerDown) {
        if (fingerDown) return@LaunchedEffect
        val layout = layoutResult ?: return@LaunchedEffect
        if (containerWidthPx <= 0f || !fieldValue.selection.collapsed) return@LaunchedEffect
        // Caret x is in text coordinates; the visible slice of text excludes
        // the field's horizontal padding, so the scroll math must use the
        // padding-corrected visible width, not the whole container width -
        // otherwise every target lands one padding short and the caret
        // ends up just off-screen instead of being revealed.
        val paddingPx = with(density) { FieldHorizontalPadding.toPx() }
        val visibleWidthPx = (containerWidthPx - paddingPx * 2).coerceAtLeast(0f)
        if (visibleWidthPx <= 0f) return@LaunchedEffect
        val caret = fieldValue.selection.end.coerceIn(0, fieldValue.text.length)
        val caretX = layout.getHorizontalPosition(caret, usePrimaryDirection = true)
        val cursorWidthPx = with(density) { CursorWidth.toPx() }
        val revealPaddingPx = with(density) { RevealPadding.toPx() }
        val target = when {
            caretX + cursorWidthPx > scrollState.value + visibleWidthPx ->
                caretX + cursorWidthPx - visibleWidthPx + revealPaddingPx
            caretX < scrollState.value -> caretX - revealPaddingPx
            else -> return@LaunchedEffect
        }
        scrollState.animateScrollTo(target.roundToInt().coerceIn(0, scrollState.maxValue))
    }

    // Shared with the edge fades below so they melt into the field.
    val fieldBg = if (LocalOsType.current == OsType.MAGIC_OS) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
    // Edge fades signal hidden text: left once scrolled, right until the end.
    val showStartFade by remember { derivedStateOf { scrollState.value > 0 } }
    val showEndFade by remember { derivedStateOf { scrollState.value < scrollState.maxValue } }
    val startFade = Brush.horizontalGradient(0f to fieldBg, 1f to Color.Transparent)
    val endFade = Brush.horizontalGradient(0f to Color.Transparent, 1f to fieldBg)

    Surface(
        // Plain rounded field, not a cookie shape.
        shape = ExpressiveRoundedShape,
        // MagicOS: borderless filled field; Pixel: outlined on white.
        color = fieldBg,
        border = if (LocalOsType.current == OsType.MAGIC_OS) null else BorderStroke(
            width = 1.dp,
            color = if (focused) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        // The field hugs its text and grows past the container once the name
        // is long; scrolling (programmatic only - gestures stay with the text
        // field and its selection handles) then reveals the rest.
        Box(
            modifier = Modifier
                .onSizeChanged { containerWidthPx = it.width.toFloat() }
                .horizontalScroll(scrollState, enabled = false)
                // Finger tracking for edge auto-scroll. Observes only and
                // never consumes: taps, caret placement and the caret
                // handle's own drag keep working untouched - including the
                // downs the handle eats (unconsumed=false).
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fingerX = down.position.x
                        fingerDown = true
                        try {
                            var pressed = true
                            while (pressed) {
                                val event = awaitPointerEvent()
                                pressed = event.changes.any { it.pressed }
                                event.changes.firstOrNull()?.let { fingerX = it.position.x }
                            }
                        } finally {
                            fingerDown = false
                        }
                    }
                }
                .padding(horizontal = FieldHorizontalPadding, vertical = 14.dp)
        ) {
            val minFieldWidth = with(density) {
                (containerWidthPx - FieldHorizontalPadding.toPx() * 2)
                    .coerceAtLeast(0f).toDp()
            }
            // Viewport-width wrapper. The edge fades paint in the draw phase
            // (below) so they can never inflate the layout - overlay boxes
            // with fillMaxHeight would blow the field up to dialog height.
            val fadeWidthPx = with(density) { EdgeFadeWidth.toPx() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        drawContent()
                        if (showStartFade) {
                            drawRect(startFade, size = Size(fadeWidthPx, size.height))
                        }
                        if (showEndFade) {
                            drawRect(
                                endFade,
                                topLeft = Offset(size.width - fadeWidthPx, 0f),
                                size = Size(fadeWidthPx, size.height)
                            )
                        }
                    }
            ) {
                Box {
                    if (fieldValue.text.isEmpty()) {
                    Text(
                        text = placeholderText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = fieldValue,
                    onValueChange = {
                        fieldValue = it
                        onValueChange(it.text)
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    interactionSource = interactionSource,
                    onTextLayout = { layoutResult = it },
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .widthIn(min = minFieldWidth)
                )
                }
            }
        }
    }
}

