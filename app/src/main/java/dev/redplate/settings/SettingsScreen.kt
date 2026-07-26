package dev.redplate.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
    onNavigateToPlates: () -> Unit = {},
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    SettingsScreen(
        state = state,
        onToggleUnits = viewModel::toggleUnits,
        onSetDeloadPrompts = viewModel::setDeloadPrompts,
        onNavigateToBackup = onNavigateToBackup,
        onNavigateToEquipment = onNavigateToEquipment,
        onNavigateToPlates = onNavigateToPlates,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsState,
    onToggleUnits: () -> Unit = {},
    onSetDeloadPrompts: (Boolean) -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToEquipment: () -> Unit = {},
    onNavigateToPlates: () -> Unit = {},
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.initial,
                    style = RedplateType.title.copy(fontSize = 24.sp),
                    color = colors.inkSecondary,
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.name,
                    style = RedplateType.headline.copy(fontSize = 26.sp, lineHeight = 29.sp),
                    color = colors.ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = state.statsLine,
                    style = RedplateType.mono.copy(fontSize = 10.5.sp),
                    color = colors.inkMuted,
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        SectionLabel(text = "Gets the numbers wrong if wrong")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConsequenceRow(
                label = "Plates in your gym",
                detail = "Sets what the stack can round to",
                value = state.plateSummary,
                onClick = onNavigateToPlates,
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
            ConsequenceRow(
                label = "Equipment",
                detail = "Filters every suggestion and swap",
                value = state.equipmentSummary,
                onClick = onNavigateToEquipment,
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

@Preview(name = "9a · You", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun SettingsPreview() {
    RedplateTheme {
        SettingsScreen(
            state = SettingsState(
                sinceLabel = "YOU · SINCE MARCH 2026",
                statsLine = "82.4 KG · 146 SESSIONS · 4 PRS THIS BLOCK",
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
                statsLine = "80 KG · 0 SESSIONS",
                plateSummary = "25·20·15·10·5·2.5·1.25",
                equipmentSummary = "18 items",
                restSummary = "Set by your plan",
                backupSummary = "Nothing logged yet",
                isLoading = false,
            ),
        )
    }
}
