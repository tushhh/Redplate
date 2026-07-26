package dev.redplate.plan

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import dev.redplate.data.BlockPhase
import dev.redplate.ui.components.SecondaryButton
import dev.redplate.ui.components.VolumeBar
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

@Composable
fun WeekPlanRoute(
    onEditTemplate: (Long) -> Unit = {},
) {
    val viewModel: WeekPlanViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    WeekPlanScreen(state = state, onEditTemplate = onEditTemplate)
}

@Composable
fun WeekPlanScreen(
    state: WeekPlanState,
    onEditTemplate: (Long) -> Unit = {},
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

        // Header: "Week 3 of 5" + ON TRACK badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Week ${state.weekNumber} of ${state.totalWeeks}",
                style = RedplateType.headline,
                color = colors.ink,
            )
            if (state.isOnTrack) {
                Text(
                    text = "ON TRACK",
                    style = RedplateType.mono.copy(fontSize = 10.sp),
                    color = colors.safe,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.safe.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        // Split description
        Text(
            text = state.splitDescription,
            style = RedplateType.body.copy(fontSize = 14.sp),
            color = colors.inkMuted,
        )
        Spacer(Modifier.height(20.dp))

        // Day cards — tapping a training day opens it in the builder
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.days.forEach { day ->
                DayCardRow(day = day, onClick = { day.templateId?.let(onEditTemplate) })
            }
        }
        Spacer(Modifier.height(16.dp))

        // Deload info card (when in deload week)
        if (state.phase == BlockPhase.DELOAD) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Column {
                    Text(
                        text = "DELOAD WEEK",
                        style = RedplateType.mono.copy(fontSize = 10.sp),
                        color = colors.info,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Volume drops to 50%. Same exercises, lighter. Lets fatigue clear so the next block starts fresh.",
                        style = RedplateType.body.copy(fontSize = 14.sp, lineHeight = 22.sp),
                        color = colors.inkSecondary,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Edit today's session, or the first training day if today is a rest day.
        // "Move a day" used to sit beside this with an empty handler; a button that
        // does nothing is worse than no button, so it is gone until it does something.
        val editableTemplateId = state.days.firstOrNull { it.status == DayStatus.TODAY }?.templateId
            ?: state.days.firstNotNullOfOrNull { it.templateId }
        if (editableTemplateId != null) {
            SecondaryButton(
                label = "Edit a session",
                onClick = { onEditTemplate(editableTemplateId) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }

        // Volume section
        if (state.volumeTargets.isNotEmpty()) {
            Text(
                text = "THIS WEEK \u00B7 SETS VS TARGET",
                style = RedplateType.mono.copy(fontSize = 10.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                state.volumeTargets.forEach { vol ->
                    VolumeBar(
                        label = vol.muscleName,
                        current = vol.current,
                        target = vol.target,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // Empty state
        if (state.days.all { it.status == DayStatus.REST }) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(40.dp))
                Text(
                    text = "No program yet",
                    style = RedplateType.title,
                    color = colors.ink,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Set one up from the Today screen.",
                    style = RedplateType.body,
                    color = colors.inkMuted,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DayCardRow(day: DayCard, onClick: () -> Unit) {
    val colors = RedplateTheme.colors
    val isToday = day.status == DayStatus.TODAY
    val isRest = day.status == DayStatus.REST

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (isToday) Modifier.border(1.dp, colors.live, RoundedCornerShape(16.dp))
                else Modifier
            )
            .background(if (isRest) colors.ground else colors.surface)
            .clickable(enabled = !isRest, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Day label
        Text(
            text = day.dayLabel,
            style = RedplateType.mono.copy(fontSize = 10.5.sp),
            color = if (isRest) colors.inkSubtle else colors.inkMuted,
            modifier = Modifier.width(30.dp),
        )

        // Session info
        Column(modifier = Modifier.weight(1f)) {
            if (day.sessionName != null) {
                Text(
                    text = day.sessionName,
                    style = RedplateType.body.copy(fontSize = 15.sp),
                    color = if (day.status == DayStatus.DONE) colors.inkMuted else colors.ink,
                )
                Text(
                    text = "${day.setCount} sets",
                    style = RedplateType.mono.copy(fontSize = 10.sp),
                    color = colors.inkSubtle,
                )
            } else {
                Text(
                    text = "Rest",
                    style = RedplateType.body.copy(fontSize = 15.sp),
                    color = colors.inkSubtle,
                )
            }
        }

        // Status badge or chevron
        val (badgeText, badgeColor) = when (day.status) {
            DayStatus.DONE -> "DONE" to colors.info
            DayStatus.TODAY -> "" to colors.live
            DayStatus.PLANNED -> "" to colors.inkMuted
            DayStatus.REST -> "" to colors.inkSubtle
        }

        if (day.status == DayStatus.TODAY) {
            Text(
                text = "›",
                style = RedplateType.headline.copy(fontSize = 22.sp),
                color = colors.live,
            )
        } else if (badgeText.isNotEmpty()) {
            Text(
                text = badgeText,
                style = RedplateType.mono.copy(fontSize = 9.sp),
                color = badgeColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeColor.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Preview
@Composable
private fun WeekPlanScreenPreview() {
    RedplateTheme {
        WeekPlanScreen(
            state = WeekPlanState(
                weekNumber = 3,
                totalWeeks = 5,
                splitName = "Upper/Lower",
                splitDescription = "4 days \u00B7 Upper A, Lower A, Upper B, Lower B",
                days = listOf(
                    DayCard("MON", "Upper A", 1, 18, DayStatus.DONE),
                    DayCard("TUE", "Lower A", 2, 16, DayStatus.DONE),
                    DayCard("WED", null, null, 0, DayStatus.REST),
                    DayCard("THU", "Upper B", 3, 20, DayStatus.TODAY),
                    DayCard("FRI", "Lower B", 4, 16, DayStatus.PLANNED),
                    DayCard("SAT", null, null, 0, DayStatus.REST),
                    DayCard("SUN", null, null, 0, DayStatus.REST),
                ),
                volumeTargets = listOf(
                    VolumeTarget("Chest", 12, 18),
                    VolumeTarget("Back", 14, 20),
                    VolumeTarget("Quads", 8, 16),
                ),
                isLoading = false,
            ),
        )
    }
}
