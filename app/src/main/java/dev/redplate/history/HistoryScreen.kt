package dev.redplate.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.redplate.ui.components.Chevron
import dev.redplate.ui.components.SecondaryButton
import dev.redplate.ui.components.SectionLabel
import dev.redplate.ui.components.SegmentedToggle
import dev.redplate.ui.components.SheetHandle
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType
import dev.redplate.ui.theme.StateColor

/**
 * History — design 6c. One lift at a time, because "all my data" is a spreadsheet, not
 * a screen. Estimated 1RM as the trend, PRs as facts, the raw log in mono underneath.
 */
@Composable
fun HistoryRoute() {
    val viewModel: HistoryViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryScreen(
        state = state,
        onSelectExercise = viewModel::selectExercise,
        onSetTimeRange = viewModel::setTimeRange,
        onToggleView = viewModel::togglePrView,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryState,
    onSelectExercise: (String) -> Unit = {},
    onSetTimeRange: (TimeRange) -> Unit = {},
    onToggleView: () -> Unit = {},
) {
    val colors = RedplateTheme.colors
    var showLiftPicker by remember { mutableStateOf(false) }

    if (state.isLoading) return
    if (!state.hasAnyHistory) {
        HistoryEmpty()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text(
                text = if (state.showingPrs) "Every PR" else state.selectedExercise?.name.orEmpty(),
                style = RedplateType.headline.copy(fontSize = 28.sp),
                color = colors.ink,
            )
            if (!state.showingPrs) {
                Spacer(Modifier.height(10.dp))
                SegmentedToggle(
                    options = TimeRange.entries.map { it.label },
                    selectedIndex = TimeRange.entries.indexOf(state.timeRange),
                    onOptionSelected = { onSetTimeRange(TimeRange.entries[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            if (state.showingPrs) {
                PrList(prs = state.allPrs)
            } else {
                E1rmCard(state = state)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryStat(
                        label = "Best set",
                        value = state.bestSetText,
                        caption = state.bestSetWhen,
                        modifier = Modifier.weight(1f),
                    )
                    HistoryStat(
                        label = "Heaviest",
                        value = state.heaviestText,
                        caption = state.heaviestWhen,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                EverySessionCard(entries = state.sessionLog)
            }
            Spacer(Modifier.height(24.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SecondaryButton(
                label = "All lifts",
                onClick = { showLiftPicker = true },
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                label = if (state.showingPrs) "Back to the lift" else "Every PR",
                onClick = onToggleView,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showLiftPicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showLiftPicker = false },
            sheetState = sheetState,
            containerColor = colors.surface,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            dragHandle = { SheetHandle(Modifier.padding(vertical = 12.dp)) },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "All lifts",
                    style = RedplateType.title.copy(fontSize = 25.sp),
                    color = colors.ink,
                )
                Spacer(Modifier.height(6.dp))
                state.exercises.forEach { exercise ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (exercise.id == state.selectedExercise?.id) {
                                    colors.surfaceRaised
                                } else {
                                    colors.ground
                                },
                            )
                            .clickable {
                                onSelectExercise(exercise.id)
                                showLiftPicker = false
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = exercise.name,
                            style = RedplateType.body.copy(fontSize = 15.sp),
                            color = colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                        Chevron()
                    }
                }
            }
        }
    }
}

@Composable
private fun E1rmCard(state: HistoryState) {
    val colors = RedplateTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            SectionLabel(text = "Estimated 1RM")
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = state.currentE1rmText,
                    style = RedplateType.title.copy(fontSize = 26.sp, lineHeight = 26.sp),
                    color = colors.ink,
                )
                Text(
                    text = " KG",
                    style = RedplateType.mono.copy(fontSize = 11.sp),
                    color = colors.inkMuted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        E1rmChart(points = state.e1rmPoints)
        if (state.trendLine.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.trendLine,
                style = RedplateType.body.copy(fontSize = 12.5.sp, lineHeight = 19.sp),
                color = colors.inkSecondary,
            )
        }
    }
}

/**
 * Drawn in the palette rather than a chart library's defaults: three gridlines, a blue
 * polyline, a yellow ring on the best point and a live dot on the most recent one.
 */
@Composable
private fun E1rmChart(points: List<E1rmPoint>) {
    val colors = RedplateTheme.colors
    val gridColor = colors.surfaceRaised
    val lineColor = colors.info
    val prColor = StateColor.pr
    val liveColor = colors.live

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        val top = 8f
        val bottom = size.height - 8f
        listOf(top, (top + bottom) / 2f, bottom).forEach { y ->
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }

        if (points.size < 2) return@Canvas

        val min = points.minOf { it.e1rm }
        val max = points.maxOf { it.e1rm }
        val span = (max - min).takeIf { it > 0.5 } ?: 1.0

        fun px(i: Int) = size.width * i / (points.size - 1).toFloat()
        fun py(v: Double) = bottom - ((v - min) / span).toFloat() * (bottom - top)

        val path = Path().apply {
            moveTo(px(0), py(points[0].e1rm))
            for (i in 1 until points.size) lineTo(px(i), py(points[i].e1rm))
        }
        drawPath(path, lineColor, style = Stroke(width = 2.5.dp.toPx()))

        points.indexOfFirst { it.isPr && it.e1rm == max }.takeIf { it >= 0 }?.let { i ->
            drawCircle(
                color = prColor,
                radius = 4.dp.toPx(),
                center = Offset(px(i), py(points[i].e1rm)),
                style = Stroke(width = 2.5.dp.toPx()),
            )
        }
        drawCircle(
            color = liveColor,
            radius = 5.5.dp.toPx(),
            center = Offset(px(points.lastIndex), py(points.last().e1rm)),
        )
    }
}

@Composable
private fun HistoryStat(
    label: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        SectionLabel(text = label)
        Spacer(Modifier.height(5.dp))
        Text(
            text = value,
            style = RedplateType.title.copy(fontSize = 21.sp, lineHeight = 24.sp),
            color = colors.ink,
        )
        if (caption.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = caption,
                style = RedplateType.mono.copy(fontSize = 10.5.sp),
                color = colors.inkSubtle,
            )
        }
    }
}

@Composable
private fun EverySessionCard(entries: List<SessionLogEntry>) {
    val colors = RedplateTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        SectionLabel(text = "Every session")
        Spacer(Modifier.height(8.dp))
        entries.forEach { entry ->
            Row {
                Text(
                    text = "${entry.dateLabel}  ${entry.setsText}",
                    style = RedplateType.data.copy(fontSize = 12.sp, lineHeight = 20.sp),
                    color = colors.inkSecondary,
                    modifier = Modifier.weight(1f),
                )
                if (entry.isPr) {
                    Text(
                        text = "★",
                        style = RedplateType.data.copy(fontSize = 12.sp),
                        color = StateColor.pr,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrList(prs: List<PrEntry>) {
    val colors = RedplateTheme.colors
    if (prs.isEmpty()) {
        Text(
            text = "No PRs yet. The first working set on any lift sets the bar.",
            style = RedplateType.body.copy(fontSize = 15.sp, lineHeight = 23.sp),
            color = colors.inkSecondary,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        prs.forEach { pr ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = pr.exerciseName,
                        style = RedplateType.body.copy(fontSize = 15.sp),
                        color = colors.ink,
                    )
                    Text(
                        text = pr.dateLabel,
                        style = RedplateType.mono.copy(fontSize = 10.5.sp),
                        color = colors.inkMuted,
                    )
                }
                Text(
                    text = pr.setText,
                    style = RedplateType.mono.copy(fontSize = 12.5.sp),
                    color = StateColor.pr,
                )
            }
        }
    }
}

@Composable
private fun HistoryEmpty() {
    val colors = RedplateTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Nothing logged yet.",
            style = RedplateType.headline.copy(fontSize = 30.sp),
            color = colors.ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Log a session and the curve starts here. Trends need three sessions " +
                "before they mean anything.",
            style = RedplateType.body.copy(fontSize = 15.sp, lineHeight = 23.sp),
            color = colors.inkSecondary,
        )
    }
}

// ── Previews ────────────────────────────────────────────────────────

@Preview(name = "6c · one lift", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun HistoryPreview() {
    RedplateTheme {
        HistoryScreen(
            state = HistoryState(
                timeRange = TimeRange.TWELVE_WEEKS,
                currentE1rmText = "128",
                e1rmPoints = listOf(
                    E1rmPoint(0, 108.0, false), E1rmPoint(1, 110.0, false),
                    E1rmPoint(2, 109.0, false), E1rmPoint(3, 114.0, false),
                    E1rmPoint(4, 116.0, false), E1rmPoint(5, 115.0, false),
                    E1rmPoint(6, 119.0, false), E1rmPoint(7, 122.0, false),
                    E1rmPoint(8, 121.0, false), E1rmPoint(9, 124.0, true),
                    E1rmPoint(10, 126.0, false), E1rmPoint(11, 128.0, true),
                ),
                trendLine = "Up 14 kg in twelve weeks. No stall — the ring is your best set.",
                bestSetText = "102.5 × 9",
                bestSetWhen = "TODAY",
                heaviestText = "110 × 3",
                heaviestWhen = "2 WEEKS AGO",
                sessionLog = listOf(
                    SessionLogEntry("24 JUL", "102.5 × 10 · 9 · 9", isPr = true),
                    SessionLogEntry("21 JUL", "100 × 10 · 10 · 9 · 8"),
                    SessionLogEntry("17 JUL", "100 × 9 · 9 · 8 · 8"),
                    SessionLogEntry("14 JUL", "97.5 × 10 · 10 · 9"),
                ),
                hasAnyHistory = true,
                isLoading = false,
            ),
        )
    }
}

@Preview(name = "6c · nothing logged", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun HistoryEmptyPreview() {
    RedplateTheme {
        HistoryScreen(state = HistoryState(isLoading = false, hasAnyHistory = false))
    }
}
