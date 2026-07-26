package dev.redplate.history

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.redplate.ui.components.SecondaryButton
import dev.redplate.ui.components.SegmentedToggle
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType
import dev.redplate.ui.theme.StateColor

@Composable
fun HistoryRoute() {
    val viewModel: HistoryViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    HistoryScreen(
        state = state,
        onTimeRangeSelected = { viewModel.setTimeRange(it) },
        onExerciseSelected = { viewModel.selectExercise(it) },
    )
}

@Composable
fun HistoryScreen(
    state: HistoryState,
    onTimeRangeSelected: (TimeRange) -> Unit = {},
    onExerciseSelected: (String) -> Unit = {},
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

        // Exercise name header
        Text(
            text = if (state.hasAnyHistory) (state.selectedExercise?.name ?: "History") else "History",
            style = RedplateType.title.copy(fontSize = 28.sp),
            color = colors.ink,
        )
        Spacer(Modifier.height(16.dp))

        if (!state.hasAnyHistory) {
            // Empty state — show available exercises
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Log your first session to see progress here.",
                    style = RedplateType.body.copy(fontSize = 14.5.sp),
                    color = colors.inkMuted,
                )
            }
            Spacer(Modifier.height(20.dp))

            Text(
                text = "YOUR EXERCISES",
                style = RedplateType.mono.copy(fontSize = 10.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.exercises.forEach { exercise ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface)
                            .clickable { onExerciseSelected(exercise.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = exercise.name,
                                style = RedplateType.body.copy(fontSize = 14.5.sp),
                                color = colors.ink,
                            )
                            Text(
                                text = exercise.primaryMuscle.name.replace("_", " "),
                                style = RedplateType.mono.copy(fontSize = 10.sp),
                                color = colors.inkMuted,
                            )
                        }
                        Text(
                            text = "No data",
                            style = RedplateType.mono.copy(fontSize = 10.sp),
                            color = colors.inkMuted,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        } else {
            // Time range toggle
            SegmentedToggle(
                options = TimeRange.entries.map { it.label },
                selectedIndex = TimeRange.entries.indexOf(state.timeRange),
                onOptionSelected = { onTimeRangeSelected(TimeRange.entries[it]) },
            )
            Spacer(Modifier.height(20.dp))

            // E1RM chart
            if (state.e1rmPoints.isNotEmpty()) {
                E1rmChart(
                    points = state.e1rmPoints,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No data yet",
                        style = RedplateType.body,
                        color = colors.inkMuted,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Stat cards: BEST SET / HEAVIEST
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    label = "BEST SET",
                    value = state.bestSetText,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "HEAVIEST",
                    value = state.heaviestText,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(20.dp))

            // Session log
            if (state.sessionLog.isNotEmpty()) {
                Text(
                    text = "EVERY SESSION",
                    style = RedplateType.mono.copy(fontSize = 10.sp),
                    color = colors.inkMuted,
                )
                Spacer(Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.sessionLog.forEach { entry ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = entry.dateLabel,
                                style = RedplateType.data,
                                color = colors.inkMuted,
                                modifier = Modifier.width(60.dp),
                            )
                            if (entry.isPr) {
                                Text(
                                    text = "★",
                                    style = RedplateType.data,
                                    color = StateColor.pr,
                                )
                            }
                            Text(
                                text = entry.setsText,
                                style = RedplateType.data,
                                color = colors.inkBright,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            // Bottom buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    label = "All lifts",
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    label = "Every PR",
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(16.dp),
    ) {
        Text(
            text = label,
            style = RedplateType.mono.copy(fontSize = 10.sp),
            color = colors.inkMuted,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = RedplateType.title,
            color = colors.ink,
        )
    }
}

@Composable
private fun E1rmChart(
    points: List<E1rmPoint>,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    val lineColor = colors.info
    val prColor = StateColor.pr

    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas

        val minE1rm = points.minOf { it.e1rm }
        val maxE1rm = points.maxOf { it.e1rm }
        val range = (maxE1rm - minE1rm).coerceAtLeast(1.0)
        val minTime = points.first().dateMillis.toFloat()
        val maxTime = points.last().dateMillis.toFloat()
        val timeRange = (maxTime - minTime).coerceAtLeast(1f)

        val padX = 8.dp.toPx()
        val padY = 12.dp.toPx()
        val chartW = size.width - padX * 2
        val chartH = size.height - padY * 2

        fun toPoint(p: E1rmPoint): Offset {
            val x = padX + ((p.dateMillis - minTime) / timeRange) * chartW
            val y = padY + (1f - ((p.e1rm - minE1rm) / range).toFloat()) * chartH
            return Offset(x, y)
        }

        // Line path
        val path = Path()
        points.forEachIndexed { i, p ->
            val pt = toPoint(p)
            if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
        }
        drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        // PR markers
        points.filter { it.isPr }.forEach { p ->
            val pt = toPoint(p)
            drawCircle(prColor, radius = 4.dp.toPx(), center = pt)
        }
    }
}

@Preview
@Composable
private fun HistoryScreenPreview() {
    RedplateTheme {
        HistoryScreen(
            state = HistoryState(
                selectedExercise = null,
                bestSetText = "102.5 \u00D7 8",
                heaviestText = "120 kg",
                sessionLog = listOf(
                    SessionLogEntry("25 Jul", "100\u00D78, 100\u00D77, 100\u00D76", isPr = true),
                    SessionLogEntry("21 Jul", "97.5\u00D78, 97.5\u00D78, 97.5\u00D77"),
                ),
                isLoading = false,
            ),
        )
    }
}
