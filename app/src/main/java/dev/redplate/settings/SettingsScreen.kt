package dev.redplate.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import dev.redplate.ui.components.SegmentedToggle
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

@Composable
fun SettingsRoute(
    onNavigateToBackup: () -> Unit = {},
    onNavigateToEquipment: () -> Unit = {},
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    SettingsScreen(
        state = state,
        onToggleUnits = viewModel::toggleUnits,
        onNavigateToBackup = onNavigateToBackup,
        onNavigateToEquipment = onNavigateToEquipment,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsState,
    onToggleUnits: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToEquipment: () -> Unit = {},
) {
    val colors = RedplateTheme.colors

    if (state.isLoading) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        // Profile header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Initial circle
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "R",
                    style = RedplateType.title,
                    color = colors.ink,
                )
            }

            Column {
                Text(
                    text = "Redplate",
                    style = RedplateType.title,
                    color = colors.ink,
                )
                val weight = if (state.useMetric) {
                    "${formatWeight(state.bodyweightKg)} kg"
                } else {
                    "${formatWeight(state.bodyweightKg * 2.20462)} lb"
                }
                val months = state.trainingAgeMonths
                val ageText = if (months >= 12) "${months / 12}y ${months % 12}m" else "${months}m"
                Text(
                    text = "$weight \u00B7 $ageText training \u00B7 ${state.daysPerWeek}×/wk",
                    style = RedplateType.mono.copy(fontSize = 10.sp),
                    color = colors.inkMuted,
                )
            }
        }
        Spacer(Modifier.height(28.dp))

        // Section: GETS THE NUMBERS WRONG IF WRONG
        SectionHeader("GETS THE NUMBERS WRONG IF WRONG")
        Spacer(Modifier.height(10.dp))

        // Units toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Units",
                style = RedplateType.body.copy(fontSize = 15.sp),
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            SegmentedToggle(
                options = listOf("KG", "LB"),
                selectedIndex = if (state.useMetric) 0 else 1,
                onOptionSelected = { onToggleUnits() },
            )
        }
        Spacer(Modifier.height(6.dp))

        // Equipment row
        SettingsRow(
            label = "Equipment",
            detail = "${state.equipmentCount} available",
            onClick = onNavigateToEquipment,
        )
        Spacer(Modifier.height(24.dp))

        // Section: YOUR DATA
        SectionHeader("YOUR DATA")
        Spacer(Modifier.height(10.dp))

        // A "Deload prompts: ON" row used to sit here — a read-only badge with no setting
        // behind it. The backup row's detail was the literal text "Last: today"; the real
        // status lives on the backup screen, where it is read from the database.
        SettingsRow(
            label = "Backup",
            detail = "Export & restore",
            onClick = onNavigateToBackup,
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = RedplateType.mono.copy(fontSize = 10.sp),
        color = RedplateTheme.colors.inkMuted,
    )
}

@Composable
private fun SettingsRow(
    label: String,
    detail: String,
    onClick: () -> Unit,
) {
    val colors = RedplateTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = RedplateType.body.copy(fontSize = 15.sp),
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = detail,
            style = RedplateType.mono.copy(fontSize = 10.sp),
            color = colors.inkMuted,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "\u203A",
            style = RedplateType.title.copy(fontSize = 18.sp),
            color = colors.inkSubtle,
        )
    }
}

private fun formatWeight(kg: Double): String {
    return if (kg == kg.toLong().toDouble()) "${kg.toLong()}" else "%.1f".format(kg)
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    RedplateTheme {
        SettingsScreen(
            state = SettingsState(
                bodyweightKg = 82.0,
                trainingAgeMonths = 36,
                useMetric = true,
                daysPerWeek = 4,
                equipmentCount = 12,
                isLoading = false,
            ),
        )
    }
}
