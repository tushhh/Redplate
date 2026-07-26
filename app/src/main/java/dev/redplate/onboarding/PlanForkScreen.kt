package dev.redplate.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.ui.components.InfoNote
import dev.redplate.ui.components.MonoLabel
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.theme.PlexCondensed
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

private data class PlanForkOption(
    val choice: PlanChoice,
    val title: String,
    val description: String,
    val tags: List<String>,
)

private val planOptions = listOf(
    PlanForkOption(
        PlanChoice.GIVE_ME_A_PLAN,
        "Give me a plan",
        "A full week, built from your equipment and days. Weights go up on their own as you log sets.",
        listOf("EVERY MUSCLE 2× / WEEK", "AUTO PROGRESSION", "DELOADS PLANNED"),
    ),
    PlanForkOption(
        PlanChoice.I_CHOOSE,
        "I’ll choose each day",
        "Tap the muscles you feel like training and get a session built around them, on the spot.",
        listOf("BODY MAP", "STILL TRACKS VOLUME", "WARNS IF YOU SKEW"),
    ),
)

/**
 * The fork — design 3a.
 *
 * Asked once at the end of intake and always reachable from Plan. Neither path is the
 * "advanced" one; the only difference is who picks the exercises, which is why both
 * cards carry the same weight and the same three-tag summary.
 */
@Composable
fun PlanForkScreen(
    selectedChoice: PlanChoice,
    onSelectChoice: (PlanChoice) -> Unit,
    onFinish: () -> Unit,
) {
    val colors = RedplateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
    ) {
        IntakeProgressBar(currentStep = 5)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 4.dp),
        ) {
            MonoLabel(text = "5 OF 5 · LAST ONE")
            Spacer(Modifier.height(10.dp))

            Text(
                text = "Who picks the exercises?",
                style = RedplateType.headline.copy(fontSize = 33.sp, lineHeight = 37.sp),
                color = colors.ink,
            )
            Spacer(Modifier.height(6.dp))

            Text(
                text = "You can switch any time, and mixing is fine — follow the plan on " +
                    "Monday, freestyle on Saturday.",
                style = RedplateType.body.copy(fontSize = 14.5.sp, lineHeight = 22.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                planOptions.forEach { option ->
                    ChoiceCard(
                        title = option.title,
                        description = option.description,
                        selected = selectedChoice == option.choice,
                        onClick = { onSelectChoice(option.choice) },
                        tags = option.tags,
                        // The fork is the one intake question with only two answers, so
                        // each card can afford the display face.
                        titleStyle = RedplateType.body.copy(
                            fontFamily = PlexCondensed,
                            fontSize = 24.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            InfoNote(
                text = "Either way the app tracks sets per muscle per week and tells you " +
                    "when something is being neglected.",
            )
            Spacer(Modifier.height(16.dp))
        }

        PrimaryBar(
            label = if (selectedChoice == PlanChoice.GIVE_ME_A_PLAN) {
                "Build my plan"
            } else {
                "Start picking"
            },
            onClick = onFinish,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Preview(name = "3a · the fork", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun PlanForkScreenPreview() {
    RedplateTheme {
        PlanForkScreen(
            selectedChoice = PlanChoice.GIVE_ME_A_PLAN,
            onSelectChoice = {},
            onFinish = {},
        )
    }
}

@Preview(name = "3a · I choose", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun PlanForkScreenChoosePreview() {
    RedplateTheme {
        PlanForkScreen(
            selectedChoice = PlanChoice.I_CHOOSE,
            onSelectChoice = {},
            onFinish = {},
        )
    }
}
