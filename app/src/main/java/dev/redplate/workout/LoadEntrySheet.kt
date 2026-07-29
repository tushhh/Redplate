package dev.redplate.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.ui.components.MonoLabel
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * Types in the weight — or the resistance level — that was actually used.
 *
 * The steppers walk the increments the app *believes* the equipment has. That is a guess:
 * the seeded plate inventory is an assumption, and a stack marked in numbered levels has
 * no kilogram ladder at all. This is how the user overrules it, and it is why the value it
 * commits is never snapped to anything.
 *
 * Its own keypad rather than the system IME, because CLAUDE.md §4 is not negotiable here:
 * a soft keyboard puts 20 dp keys under a thumb that is out of breath with chalk on it,
 * and it covers the readout it is editing. These keys are 64 dp and sit in the bottom half
 * of the screen, where the hand already is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadEntrySheet(
    entry: String,
    unitLabel: String,
    allowsDecimal: Boolean,
    canCommit: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onCommit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = RedplateTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        LoadEntryPad(
            entry = entry,
            unitLabel = unitLabel,
            allowsDecimal = allowsDecimal,
            canCommit = canCommit,
            onDigit = onDigit,
            onBackspace = onBackspace,
            onCommit = onCommit,
        )
    }
}

/** The sheet's body, separated so it can be previewed — a `ModalBottomSheet` cannot. */
@Composable
private fun LoadEntryPad(
    entry: String,
    unitLabel: String,
    allowsDecimal: Boolean,
    canCommit: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onCommit: () -> Unit,
) {
    val colors = RedplateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
    ) {
        MonoLabel(text = "WHAT DID YOU ACTUALLY USE?")
        Spacer(Modifier.height(10.dp))

        // The value being typed, at readout scale so it stays legible from the rack.
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
                .semantics {
                    contentDescription = "Entering ${entry.ifEmpty { "no value" }} $unitLabel"
                },
        ) {
            Text(
                text = entry.ifEmpty { "—" },
                style = RedplateType.load.copy(fontSize = 56.sp, lineHeight = 56.sp),
                color = if (entry.isEmpty()) colors.inkMuted else colors.ink,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = unitLabel,
                style = RedplateType.mono.copy(fontSize = 14.sp),
                color = colors.inkMuted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("789", "456", "123").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { digit -> Key(label = digit.toString(), onClick = { onDigit(digit) }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (allowsDecimal) {
                    Key(label = ".", onClick = { onDigit('.') })
                } else {
                    // Levels are whole numbers; the gap keeps 0 under the same finger.
                    Spacer(Modifier.weight(1f))
                }
                Key(label = "0", onClick = { onDigit('0') })
                Key(label = "⌫", onClick = onBackspace, description = "Delete last digit")
            }
        }

        Spacer(Modifier.height(12.dp))
        PrimaryBar(label = "Use this", onClick = onCommit, enabled = canCommit)
    }
}

/** 64 dp minimum per CLAUDE.md §4, not Material's 48. */
@Composable
private fun RowScope.Key(
    label: String,
    onClick: () -> Unit,
    description: String = label,
) {
    val colors = RedplateTheme.colors
    Box(
        modifier = Modifier
            .weight(1f)
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceRaised)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = RedplateType.body.copy(fontSize = 22.sp),
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "Load entry · kilograms", widthDp = 384, showBackground = true, backgroundColor = 0xFF1A1E24)
@Composable
private fun LoadEntryKgPreview() {
    RedplateTheme {
        LoadEntryPad(
            entry = "102.5",
            unitLabel = "KG",
            allowsDecimal = true,
            canCommit = true,
            onDigit = {},
            onBackspace = {},
            onCommit = {},
        )
    }
}

@Preview(name = "Load entry · resistance level", widthDp = 384, showBackground = true, backgroundColor = 0xFF1A1E24)
@Composable
private fun LoadEntryLevelPreview() {
    RedplateTheme {
        LoadEntryPad(
            entry = "7",
            unitLabel = "LEVEL",
            allowsDecimal = false,
            canCommit = true,
            onDigit = {},
            onBackspace = {},
            onCommit = {},
        )
    }
}

@Preview(name = "Load entry · empty", widthDp = 384, showBackground = true, backgroundColor = 0xFF1A1E24)
@Composable
private fun LoadEntryEmptyPreview() {
    RedplateTheme {
        LoadEntryPad(
            entry = "",
            unitLabel = "KG",
            allowsDecimal = true,
            canCommit = false,
            onDigit = {},
            onBackspace = {},
            onCommit = {},
        )
    }
}
