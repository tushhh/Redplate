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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.data.Goal
import dev.redplate.ui.components.MonoLabel
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

private data class GoalOption(
    val goal: Goal,
    val title: String,
    val description: String,
)

private val goalOptions = listOf(
    GoalOption(Goal.STRENGTH, "Get stronger", "Heavy compounds, 3–6 reps, long rests"),
    GoalOption(Goal.HYPERTROPHY, "Build muscle", "6–15 reps near failure, volume climbs weekly"),
    GoalOption(Goal.GENERAL, "Lean out, keep muscle", "Same lifting, shorter rests, optional finisher"),
    GoalOption(Goal.GENERAL, "Just be fit and healthy", "Full body, moderate reps, nothing brutal"),
)

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
                .padding(horizontal = 22.dp),
        ) {
            MonoLabel(text = "1 OF 5 \u00B7 ABOUT A MINUTE")
            Spacer(Modifier.height(12.dp))

            Text(
                text = "What are you training for?",
                style = RedplateType.headline.copy(fontSize = 36.sp),
                color = colors.ink,
            )
            Spacer(Modifier.height(8.dp))

            Text(
                text = "This sets your rep ranges, rest times and how volume is spread across the week. You can change it later.",
                style = RedplateType.body.copy(fontSize = 14.5.sp, lineHeight = 22.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                goalOptions.forEach { option ->
                    GoalCard(
                        title = option.title,
                        description = option.description,
                        isSelected = selectedGoal == option.goal,
                        onClick = { onSelectGoal(option.goal) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "i",
                    style = RedplateType.mono,
                    color = colors.live,
                )
                Text(
                    text = "No weight targets, no calories, no BMI. This app measures load, reps and turning up.",
                    style = RedplateType.body.copy(fontSize = 12.5.sp, lineHeight = 19.sp),
                    color = colors.inkMuted,
                )
            }
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

@Composable
private fun GoalCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = RedplateTheme.colors
    val bg = if (isSelected) colors.ink else colors.surface
    val titleColor = if (isSelected) colors.inkOnLight else colors.ink
    val descColor = if (isSelected) colors.inkOnLight.copy(alpha = 0.6f) else colors.inkMuted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = RedplateType.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = titleColor,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = description,
                style = RedplateType.body.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                color = descColor,
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(colors.inkOnLight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "\u2713",
                    style = RedplateType.body.copy(fontSize = 14.sp),
                    color = colors.ink,
                )
            }
        }
    }
}

@Preview(widthDp = 384, heightDp = 824, backgroundColor = 0xFF101317, showBackground = true)
@Composable
private fun GoalScreenPreview() {
    RedplateTheme {
        GoalScreen(
            selectedGoal = Goal.HYPERTROPHY,
            onSelectGoal = {},
            onNext = {},
        )
    }
}
