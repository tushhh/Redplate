package dev.redplate.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

data class PresetPlan(
    val id: String,
    val name: String,
    val daysRequired: Int,
    val durationRange: String,
    val volumeDescription: String,
    val description: String,
    val isBestFit: Boolean = false,
    val mismatchWarning: String? = null,
)

@Composable
fun PresetLibraryScreen(
    daysPerWeek: Int,
    sessionMinutes: Int,
    presets: List<PresetPlan>,
    onSelectPreset: (String) -> Unit,
    onConfirm: () -> Unit,
    selectedPresetId: String?,
) {
    val colors = RedplateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            Text(
                text = "Pick a plan",
                style = RedplateType.headline.copy(fontSize = 30.sp),
                color = colors.ink,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "$daysPerWeek days, $sessionMinutes minutes, commercial gym. Sorted by fit.",
                style = RedplateType.body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = colors.inkMuted,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { preset ->
                val isSelected = selectedPresetId == preset.id
                val hasMismatch = preset.mismatchWarning != null

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if (isSelected) Modifier.border(1.dp, colors.live, RoundedCornerShape(20.dp))
                            else Modifier
                        )
                        .background(colors.surface)
                        .clickable { onSelectPreset(preset.id) }
                        .padding(horizontal = 18.dp, vertical = 13.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = preset.name,
                            style = RedplateType.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                            color = colors.ink,
                        )
                        if (preset.isBestFit) {
                            Text(
                                text = "BEST FIT",
                                style = RedplateType.mono.copy(
                                    fontSize = 9.sp,
                                    letterSpacing = 0.08.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                ),
                                color = colors.inkOnLight,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(colors.live)
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                        if (preset.mismatchWarning != null) {
                            Text(
                                text = preset.mismatchWarning,
                                style = RedplateType.mono.copy(fontSize = 9.5.sp),
                                color = colors.inkMuted,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = preset.volumeDescription,
                        style = RedplateType.mono.copy(fontSize = 10.5.sp),
                        color = colors.inkMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = preset.description,
                        style = RedplateType.body.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                        color = if (hasMismatch) colors.inkSubtle else colors.inkSecondary,
                    )
                }
            }

            // Evidence source card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.ground.copy(alpha = 0.5f))
                    .border(1.dp, colors.surface, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Text("i", style = RedplateType.mono, color = colors.live)
                Text(
                    text = "Whichever you pick: around 10 hard sets per muscle per week to grow, heavier work at low reps to get strong, every muscle trained twice a week, big lifts first while you're fresh.",
                    style = RedplateType.body.copy(fontSize = 12.sp, lineHeight = 18.sp),
                    color = colors.inkMuted,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        PrimaryBar(
            label = if (selectedPresetId != null) {
                "Use ${presets.find { it.id == selectedPresetId }?.name ?: "plan"}"
            } else "Select a plan",
            onClick = onConfirm,
            enabled = selectedPresetId != null,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Preview(widthDp = 384, heightDp = 824, backgroundColor = 0xFF101317, showBackground = true)
@Composable
private fun PresetLibraryScreenPreview() {
    RedplateTheme {
        PresetLibraryScreen(
            daysPerWeek = 4,
            sessionMinutes = 60,
            presets = listOf(
                PresetPlan(
                    id = "upper_lower",
                    name = "Upper / Lower",
                    daysRequired = 4,
                    durationRange = "55\u201365 MIN",
                    volumeDescription = "4 DAYS \u00B7 55\u201365 MIN \u00B7 10\u201314 SETS PER MUSCLE / WEEK",
                    description = "Everything trained twice a week with two clear rest days. The most reliable structure at four days.",
                    isBestFit = true,
                ),
                PresetPlan(
                    id = "strength",
                    name = "Strength \u2014 heavy triples",
                    daysRequired = 4,
                    durationRange = "60\u201375 MIN",
                    volumeDescription = "3\u20134 DAYS \u00B7 60\u201375 MIN \u00B7 2\u20133 SETS PER LIFT @ ~80% 1RM",
                    description = "Squat, bench, deadlift, press first while you\u2019re fresh. Long rests, low reps, small weekly jumps.",
                ),
                PresetPlan(
                    id = "ppl",
                    name = "Push / Pull / Legs",
                    daysRequired = 6,
                    durationRange = "50\u201360 MIN",
                    volumeDescription = "6 DAYS \u00B7 50\u201360 MIN \u00B7 14\u201320 SETS PER MUSCLE / WEEK",
                    description = "More volume than you need right now, and only if six days is genuinely realistic.",
                    mismatchWarning = "NEEDS 6 DAYS",
                ),
            ),
            selectedPresetId = "upper_lower",
            onSelectPreset = {},
            onConfirm = {},
        )
    }
}
