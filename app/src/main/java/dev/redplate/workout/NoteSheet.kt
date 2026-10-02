package dev.redplate.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.ui.components.MonoLabel
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * A free-text note on a finished session (design 7d's "Add a note").
 *
 * The one place the app uses the system keyboard: a note is words, and it is written
 * after the session, sitting down — not mid-set with chalk on your hands, which is the
 * case the 64 dp keypad exists for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSheet(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = RedplateTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by rememberSaveable { mutableStateOf(initial) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 12.dp),
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                MonoLabel(text = "A NOTE ON THIS SESSION")
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceRaised)
                        .padding(14.dp),
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = "Slept badly, left shoulder tight, new gym shoes…",
                            style = RedplateType.body.copy(fontSize = 15.sp),
                            color = colors.inkMuted,
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { if (it.length <= MAX_NOTE_LENGTH) text = it },
                        textStyle = RedplateType.body.copy(fontSize = 15.sp, color = colors.ink),
                        cursorBrush = SolidColor(colors.live),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            PrimaryBar(
                label = if (text.isBlank() && initial.isNotBlank()) "Remove the note" else "Save the note",
                onClick = { onSave(text) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

private const val MAX_NOTE_LENGTH = 500
