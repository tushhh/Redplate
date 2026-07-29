package dev.redplate.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.redplate.ui.components.ConsequenceRow
import dev.redplate.ui.components.MonoLabel
import dev.redplate.ui.components.PillToggle
import dev.redplate.ui.components.SectionLabel
import dev.redplate.ui.components.SegmentedToggle
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * The You tab — design 9a.
 *
 * Two groups, named for what they do rather than what they are: the settings that get
 * the numbers wrong if they're wrong, then the ones that decide how loud the coach is.
 */
@Composable
fun SettingsRoute(
    onNavigateToBackup: () -> Unit = {},
    onNavigateToEquipment: () -> Unit = {},
    onNavigateToPlan: () -> Unit = {},
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onToggleUnits = viewModel::toggleUnits,
        onSetDeloadPrompts = viewModel::setDeloadPrompts,
        onNavigateToBackup = onNavigateToBackup,
        onNavigateToEquipment = onNavigateToEquipment,
        onNavigateToPlan = onNavigateToPlan,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsState,
    onToggleUnits: () -> Unit = {},
    onSetDeloadPrompts: (Boolean) -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToEquipment: () -> Unit = {},
    onNavigateToPlan: () -> Unit = {},
) {
    val colors = RedplateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(22.dp))
        MonoLabel(text = state.sinceLabel)
        Spacer(Modifier.height(10.dp))
        Text(
            text = state.headline,
            style = RedplateType.headline.copy(fontSize = 30.sp, lineHeight = 33.sp),
            color = colors.ink,
        )
        Spacer(Modifier.height(14.dp))

        // Three numbers instead of an avatar with an initial in it. This page is about
        // what has been logged and what the engine is working from, not about a profile.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Sessions", state.sessionCountLabel, Modifier.weight(1f))
            StatTile("PRs this block", state.prCountLabel, Modifier.weight(1f))
            StatTile("Bodyweight", state.bodyweightLabel, Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))

        SectionLabel(text = "Your plan")
        Spacer(Modifier.height(8.dp))
        ConsequenceRow(
            label = "Goal, days and session length",
            detail = "The answers your program is built from",
            value = state.planSummary,
            onClick = onNavigateToPlan,
        )
        Spacer(Modifier.height(24.dp))

        SectionLabel(text = "Gets the numbers wrong if wrong")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // One row, not two. "Plates in your gym" and "Equipment" both opened the same
            // screen and described the same inventory from two directions.
            ConsequenceRow(
                label = "Weights and equipment",
                detail = state.plateSummary,
                value = state.equipmentSummary,
                onClick = onNavigateToEquipment,
            )
            ConsequenceRow(
                label = "Units",
                detail = "Converts history, never re-rounds it",
                onClick = null,
                trailing = {
                    SegmentedToggle(
                        options = listOf("KG", "LB"),
                        selectedIndex = if (state.useMetric) 0 else 1,
                        onOptionSelected = { onToggleUnits() },
                        segmentSize = 44.dp,
                    )
                },
            )
        }
        Spacer(Modifier.height(24.dp))

        SectionLabel(text = "How loud the coach is")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConsequenceRow(
                label = "Rest timer",
                detail = "Auto-starts, buzzes at zero",
                value = state.restSummary,
                onClick = null,
            )
            ConsequenceRow(
                label = "Deload prompts",
                detail = "Flags a stall after 3 flat weeks",
                onClick = null,
                trailing = {
                    PillToggle(
                        checked = state.deloadPromptsEnabled,
                        onCheckedChange = onSetDeloadPrompts,
                        contentDescription = "Deload prompts",
                    )
                },
            )
            ConsequenceRow(
                label = "Backup",
                detail = state.backupSummary,
                onClick = onNavigateToBackup,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** One number and what it is. Reads as instrumentation, which is what this page is. */
@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = RedplateTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = value,
            style = RedplateType.figure.copy(fontSize = 24.sp, lineHeight = 26.sp),
            color = colors.ink,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label.uppercase(),
            style = RedplateType.mono.copy(fontSize = 9.sp),
            color = colors.inkMuted,
        )
    }
}

@Preview(name = "9a · You", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun SettingsPreview() {
    RedplateTheme {
        SettingsScreen(
            state = SettingsState(
                sinceLabel = "TRAINING SINCE MARCH 2026",
                headline = "146 sessions and counting.",
                sessionCountLabel = "146",
                prCountLabel = "4",
                bodyweightLabel = "82.4 kg",
                planSummary = "Build muscle · 4 days · 60 min",
                plateSummary = "25·20·15·10·5·2.5·1.25",
                useMetric = true,
                equipmentSummary = "18 items",
                restSummary = "Set by your plan",
                deloadPromptsEnabled = true,
                backupSummary = "146 sessions on this phone",
                isLoading = false,
            ),
        )
    }
}

@Preview(name = "9a · nothing logged yet", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun SettingsEmptyPreview() {
    RedplateTheme {
        SettingsScreen(
            state = SettingsState(
                sinceLabel = "YOU",
                plateSummary = "25·20·15·10·5·2.5·1.25",
                equipmentSummary = "18 items",
                restSummary = "Set by your plan",
                backupSummary = "Nothing logged yet",
                isLoading = false,
            ),
        )
    }
}
