package com.baiel.expressivefiles.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.baiel.expressivefiles.ui.theme.ExpressiveRoundedShape

private val FieldHorizontalPadding = 16.dp

/**
 * Single-line text input styled like the rest of the app's outlined fields.
 *
 * Width-constraint fix (Compose equivalent of layout_width="match_parent"):
 * a single-line [BasicTextField] only scrolls the caret into view when the
 * text OVERFLOWS a finite width. An unconstrained field measures itself with
 * infinite max width - the wrap_content trap - so it grows to fit all text,
 * never overflows, and the caret walks off-screen silently. Here
 * [BoxWithConstraints] resolves the dialog's finite maxWidth once and hands
 * the inner field a fixed [Modifier.width], so long names overflow and the
 * native caret-follows-edge scrolling engages. No horizontalScroll wrapper,
 * no manual scrollState, no onTextLayout caret math - the platform handles
 * it, exactly like a match_parent single-line EditText.
 */
@Composable
fun CursorScrollingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholderText: String,
    focusRequester: FocusRequester,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    initialSelection: TextRange? = null,
    fieldKey: Any? = null,
    modifier: Modifier = Modifier
) {
    // Rename pre-selects the basename (see RenameDialog); anything else
    // starts with the caret at the end of the existing text. Syncs back when
    // the caller resets the value (e.g. dialog reused for another file).
    var fieldValue by remember(fieldKey) {
        mutableStateOf(TextFieldValue(value, initialSelection ?: TextRange(value.length)))
    }
    LaunchedEffect(value, fieldKey) {
        if (value != fieldValue.text) {
            fieldValue = fieldValue.copy(text = value)
        }
    }
    val interactionSource = remember(fieldKey) { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Focus on open so the keyboard appears immediately.
    LaunchedEffect(fieldKey) {
        focusRequester.requestFocus()
    }

    val fieldBg = MaterialTheme.colorScheme.surfaceContainerLowest

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveRoundedShape,
        color = fieldBg,
        border = BorderStroke(
            1.dp,
            if (isFocused) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        // Resolve the finite dialog width here (match_parent equivalent) so
        // the text field below gets a hard width constraint to overflow.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FieldHorizontalPadding, vertical = 14.dp)
        ) {
            val fieldWidth = maxWidth
            Box(modifier = Modifier.fillMaxWidth()) {
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
                    maxLines = 1,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    interactionSource = interactionSource,
                    modifier = Modifier
                        .width(fieldWidth)
                        .focusRequester(focusRequester)
                )
            }
        }
    }
}
