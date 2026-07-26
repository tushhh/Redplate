package dev.redplate.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.ui.components.MonoLabel
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.theme.PlexCondensed
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * Intake, question two — design 2d.
 *
 * Both answers on one screen because neither means anything alone: four days at thirty
 * minutes is a different programme from four days at ninety. The split they produce is
 * shown live underneath, so the consequence arrives before the commitment does.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen(
    daysPerWeek: Int,
    sessionMinutes: Int,
    consequence: ScheduleConsequence,
    onSelectDays: (Int) -> Unit,
    onSelectMinutes: (Int) -> Unit,
    onNext: () -> Unit,
) {
    val colors = RedplateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
    ) {
        IntakeProgressBar(currentStep = 2)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 8.dp),
        ) {
            MonoLabel(text = "2 OF 5")
            Spacer(Modifier.height(12.dp))

            Text(
                text = "How often, and for how long?",
                style = RedplateType.headline.copy(fontSize = 36.sp, lineHeight = 40.sp),
                color = colors.ink,
            )
            Spacer(Modifier.height(22.dp))

            Text(
                text = "Days a week you can realistically train",
                style = RedplateType.body.copy(fontSize = 14.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DAY_CHOICES.forEach { day ->
                    NumberChip(
                        value = day.toString(),
                        suffix = null,
                        selected = daysPerWeek == day,
                        onClick = { onSelectDays(day) },
                        contentDescription = "$day days a week",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            Text(
                text = "And how long is a session?",
                style = RedplateType.body.copy(fontSize = 14.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(10.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 3,
            ) {
                MINUTE_CHOICES.forEach { minutes ->
                    val isCeiling = minutes == MINUTE_CHOICES.last()
                    NumberChip(
                        value = minutes.toString(),
                        suffix = if (isCeiling) "min+" else "min",
                        selected = sessionMinutes == minutes,
                        onClick = { onSelectMinutes(minutes) },
                        contentDescription = if (isCeiling) {
                            "$minutes minutes or more"
                        } else {
                            "$minutes minutes"
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            ConsequencePanel(consequence)
            Spacer(Modifier.height(16.dp))
        }

        PrimaryBar(
            label = "Next",
            onClick = onNext,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/**
 * "THAT MEANS" — the split the two answers just produced, recomputed on every tap.
 * The sets phrase carries full-strength ink because it is the number that decides
 * whether the session fits the time.
 */
@Composable
private fun ConsequencePanel(consequence: ScheduleConsequence) {
    val colors = RedplateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            text = "THAT MEANS",
            style = RedplateType.mono.copy(fontSize = 9.5.sp, letterSpacing = 0.14.sp),
            color = colors.live,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = buildAnnotatedString {
                append(consequence.split)
                append(". Around ")
                withStyle(SpanStyle(color = colors.ink)) { append(consequence.setsPhrase) }
                append(", ")
                append(consequence.tail)
            },
            style = RedplateType.body.copy(fontSize = 15.sp, lineHeight = 24.sp),
            color = colors.inkBright,
        )
    }
}

/**
 * A 64 dp answer chip. Selected inverts to ink — the intake never spends the warm accent
 * on a choice, only on the action that commits it.
 */
@Composable
private fun NumberChip(
    value: String,
    suffix: String?,
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors

    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.ink else colors.surface)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = value,
                style = RedplateType.body.copy(
                    fontFamily = PlexCondensed,
                    fontSize = if (suffix == null) 24.sp else 20.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (selected) colors.inkOnLight else colors.inkMuted,
            )
            if (suffix != null) {
                Text(
                    text = suffix,
                    style = RedplateType.body.copy(fontSize = 12.sp),
                    color = if (selected) colors.inkOnLightMuted else colors.inkMuted,
                    modifier = Modifier.padding(bottom = 1.dp),
                )
            }
        }
    }
}

private val DAY_CHOICES = 2..6
private val MINUTE_CHOICES = listOf(30, 45, 60, 75, 90)

@Preview(name = "2d · schedule", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun ScheduleScreenPreview() {
    RedplateTheme {
        ScheduleScreen(
            daysPerWeek = 4,
            sessionMinutes = 60,
            consequence = ScheduleConsequence(
                split = "Upper / Lower, twice each",
                setsPhrase = "18–22 sets a session",
                tail = "every muscle hit twice a week — which is the point where " +
                    "progress actually shows up.",
            ),
            onSelectDays = {},
            onSelectMinutes = {},
            onNext = {},
        )
    }
}

@Preview(name = "2d · two short days", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun ScheduleScreenMinimalPreview() {
    RedplateTheme {
        ScheduleScreen(
            daysPerWeek = 2,
            sessionMinutes = 30,
            consequence = ScheduleConsequence(
                split = "Full body, both days",
                setsPhrase = "10–12 sets a session",
                tail = "every muscle hit once or twice a week — enough to grow, with " +
                    "room to add a day later.",
            ),
            onSelectDays = {},
            onSelectMinutes = {},
            onNext = {},
        )
    }
}
