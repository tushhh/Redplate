package dev.redplate.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.data.Goal
import dev.redplate.ui.components.InfoNote
import dev.redplate.ui.components.MonoLabel
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

private data class GoalOption(
    val goal: Goal,
    val title: String,
    val description: String,
)

/**
 * Four answers, four distinct prescriptions. Two of these used to map to the same enum
 * value, so picking one highlighted both — [Goal.LEAN] now exists precisely because the
 * intake offers it as a separate answer.
 */
private val goalOptions = listOf(
    GoalOption(Goal.STRENGTH, "Get stronger", "Heavy compounds, 3–6 reps, long rests"),
    GoalOption(Goal.HYPERTROPHY, "Build muscle", "6–15 reps near failure, volume climbs weekly"),
    GoalOption(Goal.LEAN, "Lean out, keep muscle", "Same lifting, shorter rests, optional finisher"),
    GoalOption(Goal.GENERAL, "Just be fit and healthy", "Full body, moderate reps, nothing brutal"),
)

/**
 * Intake, question one — design 2c.
 *
 * One question per screen, no keyboard, and every answer states its consequence so you
 * know why it is being asked.
 */
@Composable
fun GoalScreen(
    selectedGoal: Goal?,
    onSelectGoal: (Goal) -> Unit,
    onNext: () -> Unit,
) {
    val colors = RedplateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
    ) {
        IntakeProgressBar(currentStep = 1)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 8.dp),
        ) {
            MonoLabel(text = "1 of 5 · about a minute")
            Spacer(Modifier.height(12.dp))
            Text(
                text = "What are you training for?",
                style = RedplateType.headline.copy(fontSize = 36.sp, lineHeight = 40.sp),
                color = colors.ink,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "This sets your rep ranges, rest times and how volume is spread " +
                    "across the week. You can change it later.",
                style = RedplateType.body.copy(fontSize = 14.5.sp, lineHeight = 21.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                goalOptions.forEach { option ->
                    ChoiceCard(
                        title = option.title,
                        description = option.description,
                        selected = option.goal == selectedGoal,
                        onClick = { onSelectGoal(option.goal) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            InfoNote(
                text = "No weight targets, no calories, no BMI. This app measures load, " +
                    "reps and turning up.",
            )
            Spacer(Modifier.height(16.dp))
        }

        PrimaryBar(
            label = "Next",
            onClick = onNext,
            enabled = selectedGoal != null,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/**
 * The intake's answer card. Selected inverts to ink, which is the design's way of saying
 * "this is the live one" without spending the warm accent on it.
 *
 * Shared with the plan fork (3a), which passes a larger [titleStyle] because it only has
 * two answers to fit on the screen.
 */
@Composable
fun ChoiceCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tags: List<String> = emptyList(),
    titleStyle: TextStyle = RedplateType.body.copy(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
    ),
) {
    val colors = RedplateTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) colors.ink else colors.surface)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = titleStyle,
                    color = if (selected) colors.inkOnLight else colors.ink,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = description,
                    style = RedplateType.body.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                    color = if (selected) colors.inkOnLightMuted else colors.inkMuted,
                )
            }
            Spacer(Modifier.size(14.dp))
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(colors.inkOnLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        style = RedplateType.body.copy(fontSize = 14.sp),
                        color = colors.ink,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, colors.handle, CircleShape),
                )
            }
        }

        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                tags.forEach { tag ->
                    Text(
                        text = tag,
                        style = RedplateType.mono.copy(fontSize = 9.5.sp),
                        color = if (selected) colors.inkOnLight else colors.inkSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (selected) {
                                    colors.inkOnLight.copy(alpha = 0.08f)
                                } else {
                                    colors.surfaceRaised
                                },
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Preview(name = "2c · goal", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun GoalScreenPreview() {
    RedplateTheme {
        GoalScreen(selectedGoal = Goal.HYPERTROPHY, onSelectGoal = {}, onNext = {})
    }
}

@Preview(name = "2c · nothing picked", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun GoalScreenEmptyPreview() {
    RedplateTheme {
        GoalScreen(selectedGoal = null, onSelectGoal = {}, onNext = {})
    }
}
