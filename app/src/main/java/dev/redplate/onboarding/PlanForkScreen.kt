package dev.redplate.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import dev.redplate.ui.components.MonoLabel
import dev.redplate.ui.components.PrimaryBar
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
        listOf("EVERY MUSCLE 2\u00D7 / WEEK", "AUTO PROGRESSION", "DELOADS PLANNED"),
    ),
    PlanForkOption(
        PlanChoice.I_CHOOSE,
        "I\u2019ll choose each day",
        "Tap the muscles you feel like training and get a session built around them, on the spot.",
        listOf("BODY MAP", "STILL TRACKS VOLUME", "WARNS IF YOU SKEW"),
    ),
)

@OptIn(ExperimentalLayoutApi::class)
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
        // No progress bar on screen 3a per design — it says "5 OF 5 · LAST ONE"
        // but there's no visible progress bar segments. Let's include it for consistency.

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 22.dp),
        ) {
            MonoLabel(text = "5 OF 5 \u00B7 LAST ONE")
            Spacer(Modifier.height(10.dp))

            Text(
                text = "Who picks the exercises?",
                style = RedplateType.headline.copy(fontSize = 33.sp),
                color = colors.ink,
            )
            Spacer(Modifier.height(6.dp))

            Text(
                text = "You can switch any time, and mixing is fine \u2014 follow the plan on Monday, freestyle on Saturday.",
                style = RedplateType.body.copy(fontSize = 14.5.sp, lineHeight = 22.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(14.dp))

            planOptions.forEach { option ->
                val isSelected = selectedChoice == option.choice
                val bg = if (isSelected) colors.ink else colors.surface
                val titleColor = if (isSelected) colors.inkOnLight else colors.ink
                val descColor = if (isSelected) colors.inkOnLight.copy(alpha = 0.6f) else colors.inkMuted
                val tagBg = if (isSelected) colors.inkOnLight.copy(alpha = 0.08f) else colors.surfaceRaised
                val tagColor = if (isSelected) colors.inkOnLight else colors.inkSecondary

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(bg)
                        .clickable { onSelectChoice(option.choice) }
                        .padding(18.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.title,
                                style = RedplateType.title,
                                color = titleColor,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = option.description,
                                style = RedplateType.body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                                color = descColor,
                            )
                        }
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(colors.inkOnLight),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("\u2713", style = RedplateType.body.copy(fontSize = 14.sp), color = colors.ink)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, colors.line, CircleShape),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        option.tags.forEach { tag ->
                            Text(
                                text = tag,
                                style = RedplateType.mono.copy(fontSize = 9.5.sp, letterSpacing = 0.06.sp),
                                color = tagColor,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(tagBg)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Row(
                modifier = Modifier.padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text("i", style = RedplateType.mono, color = colors.live)
                Text(
                    text = "Either way the app tracks sets per muscle per week and tells you when something is being neglected.",
                    style = RedplateType.body.copy(fontSize = 12.5.sp, lineHeight = 19.sp),
                    color = colors.inkMuted,
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        PrimaryBar(
            label = if (selectedChoice == PlanChoice.GIVE_ME_A_PLAN) "Build my plan" else "Start picking",
            onClick = onFinish,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Preview(widthDp = 384, heightDp = 824, backgroundColor = 0xFF101317, showBackground = true)
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
