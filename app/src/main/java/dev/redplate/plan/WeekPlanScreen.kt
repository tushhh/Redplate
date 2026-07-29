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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.redplate.data.BlockPhase
import dev.redplate.ui.components.Chevron
import dev.redplate.ui.components.FourWeekAverageKey
import dev.redplate.ui.components.InfoNote
import dev.redplate.ui.components.SecondaryButton
import dev.redplate.ui.components.SectionLabel
import dev.redplate.ui.components.VolumeBar
import dev.redplate.ui.theme.PlexCondensed
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * The Plan tab — design 6a, with 10a's full balance chart below the fold.
 *
 * One scroll, two halves: the week list, then all eleven muscle groups in the same row
 * grammar as Today's footer, so the chart reads as that object made complete rather
 * than as a new one. The balance chart is scrolled to, never tabbed to.
 */
@Composable
fun WeekPlanRoute(
    onEditTemplate: (Long) -> Unit = {},
) {
    val viewModel: WeekPlanViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    WeekPlanScreen(
        state = state,
        onEditTemplate = onEditTemplate,
        onAdjustTargets = viewModel::resetTargetsToDefaults,
    )
}

@Composable
fun WeekPlanScreen(
    state: WeekPlanState,
    onEditTemplate: (Long) -> Unit = {},
    onAdjustTargets: () -> Unit = {},
) {
    val colors = RedplateTheme.colors

    if (state.isLoading) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "Week ${state.weekNumber} of ${state.totalWeeks}",
                    style = RedplateType.headline.copy(fontSize = 30.sp),
                    color = colors.ink,
                )
                Text(
                    text = if (state.phase == BlockPhase.DELOAD) "DELOAD" else "ON TRACK",
                    style = RedplateType.mono.copy(fontSize = 10.5.sp, letterSpacing = 0.1.em),
                    color = colors.live,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.splitDescription,
                style = RedplateType.body.copy(fontSize = 13.5.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.days.forEach { day ->
                    DayCardRow(day = day, onClick = { day.templateId?.let(onEditTemplate) })
                }
            }

            if (state.blockNote.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                InfoNote(text = state.blockNote)
            }

            // ── 10a: the same rows, all eleven groups ──
            if (state.balance.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    SectionLabel(text = "Balance · week ${state.weekNumber} so far")
                    FourWeekAverageKey()
                }
                Spacer(Modifier.height(3.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surface)
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        state.balance.forEach { row ->
                            VolumeBar(
                                label = row.muscleName,
                                current = row.current,
                                target = row.target,
                                labelWidth = 74.dp,
                                fourWeekAverage = row.fourWeekAverage,
                            )
                        }
                    }

                    if (state.balanceCoachLine.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.surfaceRaised),
                        )
                        Spacer(Modifier.height(11.dp))
                        Text(
                            text = state.balanceCoachLine,
                            style = RedplateType.body.copy(fontSize = 12.5.sp, lineHeight = 19.sp),
                            color = colors.inkSecondary,
                        )
                    }
                }
            }

            if (state.days.all { it.status == DayStatus.REST }) {
                Spacer(Modifier.height(40.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
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

        val editableTemplateId = state.days.firstOrNull { it.status == DayStatus.TODAY }?.templateId
            ?: state.days.firstNotNullOfOrNull { it.templateId }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SecondaryButton(
                label = "Edit program",
                onClick = { editableTemplateId?.let(onEditTemplate) },
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                label = "Adjust targets",
                onClick = onAdjustTargets,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One weekday. A finished day reports what it cost in sets and tonnage; today is
 * outlined in live and is the only row with a live chevron; a planned day gets a 44dp
 * chevron button so it reads as openable without competing with today.
 */
@Composable
private fun DayCardRow(day: DayCard, onClick: () -> Unit) {
    val colors = RedplateTheme.colors
    val isToday = day.status == DayStatus.TODAY
    val isRest = day.status == DayStatus.REST

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (isToday) {
                    Modifier.border(1.dp, colors.live, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                },
            )
            .background(colors.surface)
            .clickable(enabled = !isRest, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day.dayLabel,
            style = RedplateType.mono.copy(fontSize = 10.5.sp, letterSpacing = 0.08.em),
            color = if (isToday) colors.live else colors.inkMuted,
            modifier = Modifier.width(30.dp),
        )
        Spacer(Modifier.width(13.dp))

        if (isRest) {
            Text(
                text = "Rest",
                style = RedplateType.body.copy(fontSize = 15.sp),
                color = colors.inkSubtle,
                modifier = Modifier.weight(1f),
            )
            return@Row
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = day.sessionName.orEmpty(),
                style = RedplateType.body.copy(
                    fontSize = if (isToday) 16.sp else 15.sp,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (isToday) colors.ink else colors.inkSecondary,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = day.detailLine,
                style = RedplateType.mono.copy(fontSize = 10.5.sp),
                color = if (day.status == DayStatus.DONE) colors.inkSubtle else colors.inkMuted,
            )
        }

        when (day.status) {
            DayStatus.DONE -> Text(
                text = "DONE",
                style = RedplateType.mono.copy(fontSize = 11.sp),
                color = colors.info,
            )

            DayStatus.TODAY -> Text(
                text = "›",
                style = RedplateType.title.copy(fontFamily = PlexCondensed, fontSize = 22.sp),
                color = colors.live,
            )

            else -> Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(colors.surfaceRaised),
                contentAlignment = Alignment.Center,
            ) { Chevron(colors.inkSecondary) }
        }
    }
}

// ── Previews ────────────────────────────────────────────────────────

private val PREVIEW_DAYS = listOf(
    DayCard("MON", "Lower A", 1, 18, DayStatus.DONE, "18 SETS · 9.1 T"),
    DayCard("TUE", null, null, 0, DayStatus.REST, ""),
    DayCard("WED", "Upper B", 2, 20, DayStatus.DONE, "20 SETS · 7.8 T"),
    DayCard("THU", null, null, 0, DayStatus.REST, ""),
    DayCard("FRI", "Upper A", 3, 20, DayStatus.TODAY, "20 SETS · 58 MIN · TODAY"),
    DayCard("SAT", "Lower B", 4, 18, DayStatus.PLANNED, "18 SETS · PLANNED"),
    DayCard("SUN", null, null, 0, DayStatus.REST, ""),
)

private val PREVIEW_BALANCE = listOf(
    VolumeTarget("Chest", 11, 18, 13),
    VolumeTarget("Back", 21, 20, 16),
    VolumeTarget("Shoulders", 13, 24, 15),
    VolumeTarget("Biceps", 9, 14, 8),
    VolumeTarget("Triceps", 12, 16, 11),
    VolumeTarget("Quads", 4, 18, 12),
    VolumeTarget("Hamstrings", 6, 14, 9),
    VolumeTarget("Glutes", 8, 16, 9),
    VolumeTarget("Calves", 2, 10, 3),
    VolumeTarget("Abs", 5, 12, 6),
    VolumeTarget("Forearms", 3, 8, 3),
)

@Preview(name = "6a · the week", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun WeekPlanPreview() {
    RedplateTheme {
        WeekPlanScreen(
            state = WeekPlanState(
                weekNumber = 3,
                totalWeeks = 5,
                splitName = "Upper / Lower",
                splitDescription = "Upper / Lower · building volume · 4 days",
                days = PREVIEW_DAYS,
                blockNote = "Sets climb again next week, then week 5 is a deload — same " +
                    "movements, about 10% lighter, stopping well short. That week is " +
                    "where the growth actually lands.",
                balance = PREVIEW_BALANCE,
                balanceCoachLine = "Back is a set over its cap — Saturday drops a row. " +
                    "Quads are the real gap: 4 of 18 with two days left.",
                isLoading = false,
            ),
        )
    }
}

@Preview(name = "10a · scrolled to balance", widthDp = 384, heightDp = 1400, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun WeekPlanBalancePreview() {
    RedplateTheme {
        WeekPlanScreen(
            state = WeekPlanState(
                weekNumber = 3,
                totalWeeks = 5,
                splitDescription = "Upper / Lower · building volume · 4 days",
                days = PREVIEW_DAYS,
                balance = PREVIEW_BALANCE,
                balanceCoachLine = "Back is a set over its cap — Saturday drops a row. " +
                    "Quads are the real gap: 4 of 18 with two days left.",
                isLoading = false,
            ),
        )
    }
}
