package dev.redplate.onboarding

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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.ui.components.MonoLabel
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.components.SectionLabel
import dev.redplate.ui.theme.PlexCondensed
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType
import dev.redplate.workout.formatLoad

/** How long someone has been lifting, as the four answers the engine can tell apart. */
enum class TrainingExperience(val label: String, val months: Int) {
    NEW("Under 6 months", 3),
    SOME("6–12 months", 9),
    STEADY("1–3 years", 24),
    LONG("3+ years", 48),
}

/**
 * Intake, question three: the three things COACHING.md §1 marks required that the intake
 * never asked — how long you have been lifting, what you weigh, and the one-time readiness
 * screen.
 *
 * Every user used to be recorded as a beginner weighing 80 kg, which took a set off every
 * compound for people who did not need it and put a made-up number on the You tab.
 */
@Composable
fun AboutYouScreen(
    experience: TrainingExperience?,
    bodyweightKg: Double?,
    readinessFlagged: Boolean?,
    onSelectExperience: (TrainingExperience) -> Unit,
    onBodyweightDown: () -> Unit,
    onBodyweightUp: () -> Unit,
    onEditBodyweight: () -> Unit,
    onSetReadiness: (Boolean) -> Unit,
    onNext: () -> Unit,
) {
    val colors = RedplateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
    ) {
        IntakeProgressBar(currentStep = 3)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 8.dp),
        ) {
            MonoLabel(text = "3 of 5 · about you")
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Three things the plan is built from.",
                style = RedplateType.headline.copy(fontSize = 32.sp, lineHeight = 36.sp),
                color = colors.ink,
            )
            Spacer(Modifier.height(20.dp))

            SectionLabel(text = "How long have you been lifting?")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Consistently, not counting breaks. Newer lifters start with a set " +
                    "less on the big lifts and a shorter finisher.",
                style = RedplateType.body.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(10.dp))
            TrainingExperience.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { option ->
                        AnswerChip(
                            label = option.label,
                            selected = option == experience,
                            onClick = { onSelectExperience(option) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel(text = "What do you weigh?")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "The first point on your bodyweight trend. Never a target — the app " +
                    "tracks the line, it doesn't set one.",
                style = RedplateType.body.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Stepper("−", "Half a kilo less", onBodyweightDown)
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onEditBodyweight)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = bodyweightKg
                                ?.let { "Bodyweight ${formatLoad(it)} kilograms. Tap to type it." }
                                ?: "Bodyweight not set. Tap to type it."
                        },
                ) {
                    Text(
                        text = bodyweightKg?.let(::formatLoad) ?: "—",
                        style = RedplateType.load.copy(fontSize = 48.sp, lineHeight = 50.sp),
                        color = if (bodyweightKg == null) colors.inkMuted else colors.ink,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "KG",
                        style = RedplateType.mono.copy(fontSize = 13.sp),
                        color = colors.inkMuted,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Stepper("+", "Half a kilo more", onBodyweightUp)
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel(text = "Before you start")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Do any of these apply right now? Chest pain or dizziness when you " +
                    "exercise · a joint injury or recent surgery · pregnancy · a condition " +
                    "a doctor is supervising.",
                style = RedplateType.body.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                color = colors.inkSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnswerChip(
                    label = "None of these",
                    selected = readinessFlagged == false,
                    onClick = { onSetReadiness(false) },
                    modifier = Modifier.weight(1f),
                )
                AnswerChip(
                    label = "One or more",
                    selected = readinessFlagged == true,
                    onClick = { onSetReadiness(true) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (readinessFlagged == true) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "The app still works — it just won't prescribe anything under five " +
                        "reps. Worth getting the all-clear from a doctor or physio before " +
                        "you push hard.",
                    style = RedplateType.body.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                    color = colors.inkMuted,
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        PrimaryBar(
            label = "Next",
            onClick = onNext,
            enabled = experience != null && bodyweightKg != null && readinessFlagged != null,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun AnswerChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    Box(
        modifier = modifier
            .sizeIn(minHeight = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.ink else colors.surface)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = RedplateType.body.copy(
                fontSize = 14.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) colors.inkOnLight else colors.inkBright,
        )
    }
}

@Composable
private fun Stepper(symbol: String, description: String, onClick: () -> Unit) {
    val colors = RedplateTheme.colors
    Box(
        Modifier
            .sizeIn(minWidth = 64.dp, minHeight = 64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = RedplateType.figure.copy(fontFamily = PlexCondensed, fontSize = 26.sp),
            color = colors.ink,
        )
    }
}

@Preview(name = "Intake · about you", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun AboutYouPreview() {
    RedplateTheme {
        AboutYouScreen(
            experience = TrainingExperience.STEADY,
            bodyweightKg = 84.5,
            readinessFlagged = false,
            onSelectExperience = {},
            onBodyweightDown = {},
            onBodyweightUp = {},
            onEditBodyweight = {},
            onSetReadiness = {},
            onNext = {},
        )
    }
}

@Preview(name = "Intake · about you, unanswered", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun AboutYouEmptyPreview() {
    RedplateTheme {
        AboutYouScreen(
            experience = null,
            bodyweightKg = null,
            readinessFlagged = true,
            onSelectExperience = {},
            onBodyweightDown = {},
            onBodyweightUp = {},
            onEditBodyweight = {},
            onSetReadiness = {},
            onNext = {},
        )
    }
}
