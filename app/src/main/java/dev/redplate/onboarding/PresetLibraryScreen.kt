package dev.redplate.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.ui.components.InfoNote
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

/**
 * The preset library — design 3b.
 *
 * Each template states its dose on the card, and whether it fits the days already given.
 * A plan that needs six days when you said four is still offered — dimmed and labelled,
 * never hidden — because the user is allowed to change their mind about the week.
 */
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
    var reasoningOpen by rememberSaveable { mutableStateOf(false) }
    val selected = remember(presets, selectedPresetId) {
        presets.firstOrNull { it.id == selectedPresetId }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
            Text(
                text = "Pick a plan",
                style = RedplateType.headline.copy(fontSize = 30.sp, lineHeight = 33.sp),
                color = colors.ink,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "$daysPerWeek days, $sessionMinutes minutes, your kit. Sorted by fit.",
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
                PresetCard(
                    preset = preset,
                    selected = selectedPresetId == preset.id,
                    onClick = { onSelectPreset(preset.id) },
                )
            }

            InfoNote(
                text = if (reasoningOpen) REASONING_LONG else REASONING_SHORT,
                actionLabel = if (reasoningOpen) "Show less." else "Read the reasoning.",
                onClick = { reasoningOpen = !reasoningOpen },
            )
            Spacer(Modifier.height(8.dp))
        }

        PrimaryBar(
            label = selected?.let { "Use ${it.name}" } ?: "Pick one to continue",
            onClick = onConfirm,
            enabled = selected != null,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun PresetCard(
    preset: PresetPlan,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = RedplateTheme.colors
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .then(if (selected) Modifier.border(1.dp, colors.live, shape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = preset.name,
                style = RedplateType.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (preset.isBestFit) {
                Text(
                    text = "BEST FIT",
                    style = RedplateType.mono.copy(
                        fontSize = 9.sp,
                        letterSpacing = 0.08.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.inkOnLight,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(colors.live)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            // Stated, not enforced: picking this plan moves the week to suit it.
            preset.mismatchWarning?.let { warning ->
                Text(
                    text = warning,
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
            color = if (preset.mismatchWarning != null) colors.inkSubtle else colors.inkSecondary,
        )
    }
}

private const val REASONING_SHORT =
    "Whichever you pick: around 10 hard sets per muscle per week to grow, heavier work at " +
        "low reps to get strong, every muscle trained twice a week, big lifts first."

private const val REASONING_LONG =
    "Ten to twenty hard sets per muscle per week is the range where growth reliably shows " +
        "up; below about ten it stalls and above twenty the extra fatigue costs more than " +
        "it returns, so the generator targets the middle and only climbs if you are " +
        "clearing your rep ranges. Strength responds to load rather than volume, so the " +
        "strength preset drops to two or three sets near 80% of your best single. Twice a " +
        "week beats once because the growth signal from a session fades in about two days. " +
        "Compounds go first because they are the sets that suffer most when you are already " +
        "tired. Every one of those numbers is visible on the slot it produced, and every " +
        "one of them can be overridden."

private val previewPresets = listOf(
    PresetPlan(
        id = PRESET_UPPER_LOWER,
        name = "Upper / Lower",
        daysRequired = 4,
        durationRange = "55–65 MIN",
        volumeDescription = "4 DAYS · 55–65 MIN · 10–14 SETS PER MUSCLE / WEEK",
        description = "Everything trained twice a week with two clear rest days. The most " +
            "reliable structure at four days.",
        isBestFit = true,
    ),
    PresetPlan(
        id = PRESET_STRENGTH,
        name = "Strength — heavy triples",
        daysRequired = 4,
        durationRange = "60–75 MIN",
        volumeDescription = "3–4 DAYS · 60–75 MIN · 2–3 SETS PER LIFT @ ~80% 1RM",
        description = "Squat, bench, deadlift, press first while you’re fresh. Long rests, " +
            "low reps, small weekly jumps.",
    ),
    PresetPlan(
        id = PRESET_PPL,
        name = "Push / Pull / Legs",
        daysRequired = 6,
        durationRange = "50–60 MIN",
        volumeDescription = "6 DAYS · 50–60 MIN · 14–20 SETS PER MUSCLE / WEEK",
        description = "More volume than you need right now, and only if six days is " +
            "genuinely realistic.",
        mismatchWarning = "NEEDS 6 DAYS",
    ),
)

@Preview(name = "3b · preset library", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun PresetLibraryScreenPreview() {
    RedplateTheme {
        PresetLibraryScreen(
            daysPerWeek = 4,
            sessionMinutes = 60,
            presets = previewPresets,
            selectedPresetId = PRESET_UPPER_LOWER,
            onSelectPreset = {},
            onConfirm = {},
        )
    }
}
