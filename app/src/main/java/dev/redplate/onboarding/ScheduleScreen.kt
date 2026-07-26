package dev.redplate.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.ui.components.MonoLabel
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen(
    daysPerWeek: Int,
    sessionMinutes: Int,
    splitDescription: String,
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
                .padding(horizontal = 22.dp),
        ) {
            MonoLabel(text = "2 OF 5")
            Spacer(Modifier.height(12.dp))

            Text(
                text = "How often, and for how long?",
                style = RedplateType.headline.copy(fontSize = 36.sp),
                color = colors.ink,
            )
            Spacer(Modifier.height(22.dp))

            Text(
                text = "Days a week you can realistically train",
                style = RedplateType.body.copy(fontSize = 14.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(10.dp))

            // Days picker: 2–6
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (2..6).forEach { day ->
                    ScheduleChip(
                        label = day.toString(),
                        isSelected = daysPerWeek == day,
                        onClick = { onSelectDays(day) },
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

            // Session length: 30/45/60/75/90+
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 3,
            ) {
                listOf(30, 45, 60, 75, 90).forEach { mins ->
                    val label = if (mins == 90) "90+" else mins.toString()
                    val suffix = "min"
                    SessionLengthChip(
                        value = label,
                        suffix = suffix,
                        isSelected = sessionMinutes == mins,
                        onClick = { onSelectMinutes(mins) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            // "THAT MEANS" consequence card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "THAT MEANS",
                    style = RedplateType.mono.copy(fontSize = 9.5.sp),
                    color = colors.live,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = splitDescription,
                    style = RedplateType.body.copy(fontSize = 15.sp, lineHeight = 24.sp),
                    color = colors.inkBright,
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        PrimaryBar(
            label = "Next",
            onClick = onNext,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun ScheduleChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) colors.ink else colors.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = RedplateType.title,
            color = if (isSelected) colors.inkOnLight else colors.inkMuted,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SessionLengthChip(
    value: String,
    suffix: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) colors.ink else colors.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = value,
                style = RedplateType.exerciseName,
                color = if (isSelected) colors.inkOnLight else colors.inkMuted,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = suffix,
                style = RedplateType.body.copy(fontSize = 12.sp),
                color = if (isSelected) colors.inkOnLight.copy(alpha = 0.6f) else colors.inkMuted,
            )
        }
    }
}

@Preview(widthDp = 384, heightDp = 824, backgroundColor = 0xFF101317, showBackground = true)
@Composable
private fun ScheduleScreenPreview() {
    RedplateTheme {
        ScheduleScreen(
            daysPerWeek = 4,
            sessionMinutes = 60,
            splitDescription = "Upper / Lower, twice each. Around 20 sets a session, every muscle hit twice a week \u2014 which is the point where progress actually shows up.",
            onSelectDays = {},
            onSelectMinutes = {},
            onNext = {},
        )
    }
}
