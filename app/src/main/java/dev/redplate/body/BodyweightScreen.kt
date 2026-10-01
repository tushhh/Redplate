package dev.redplate.body

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.redplate.data.BodyweightPoint
import dev.redplate.ui.components.InfoNote
import dev.redplate.ui.components.MonoLabel
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.components.ScreenHeader
import dev.redplate.ui.components.SecondaryButton
import dev.redplate.ui.components.SectionLabel
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType
import dev.redplate.workout.LoadEntrySheet

@Composable
fun BodyweightRoute(onBack: () -> Unit) {
    val viewModel: BodyweightViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    BodyweightScreen(
        state = state,
        onBack = onBack,
        onLogWeight = viewModel::startWeightEntry,
        onAddWaist = viewModel::startWaistEntry,
        onDelete = viewModel::delete,
        onUndoDelete = viewModel::undoDelete,
    )

    state.entryField?.let { field ->
        LoadEntrySheet(
            entry = state.entry,
            unitLabel = if (field == BodyEntryField.WEIGHT) "KG" else "CM",
            allowsDecimal = true,
            canCommit = state.canCommit,
            onDigit = viewModel::appendDigit,
            onBackspace = viewModel::backspace,
            onCommit = viewModel::commitEntry,
            onDismiss = viewModel::cancelEntry,
            title = if (field == BodyEntryField.WEIGHT) "THIS MORNING'S WEIGHT" else "WAIST, AT THE NAVEL",
        )
    }
}

/**
 * Bodyweight, as a trend beside the strength trend.
 *
 * The scale and the bar are read together because neither means much alone: the plan is
 * working when the average line drifts down and the lifts keep climbing. Daily weigh-ins
 * are faint dots; the seven-day average is the line, because that is the number to trust.
 */
@Composable
fun BodyweightScreen(
    state: BodyweightUiState,
    onBack: () -> Unit,
    onLogWeight: () -> Unit,
    onAddWaist: () -> Unit,
    onDelete: (BodyweightRow) -> Unit,
    onUndoDelete: () -> Unit = {},
) {
    val colors = RedplateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .systemBarsPadding(),
    ) {
        ScreenHeader(title = "Leaner & stronger", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            MonoLabel(text = "BODYWEIGHT · 7-DAY AVERAGE")
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = state.averageLabel,
                    style = RedplateType.load.copy(fontSize = 56.sp, lineHeight = 58.sp),
                    color = colors.ink,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "KG",
                    style = RedplateType.mono.copy(fontSize = 14.sp),
                    color = colors.inkMuted,
                    modifier = Modifier.padding(bottom = 9.dp),
                )
            }
            state.rateLabel?.let {
                Text(
                    text = it,
                    style = RedplateType.mono.copy(fontSize = 12.sp),
                    color = colors.inkBright,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.coachLine,
                style = RedplateType.body.copy(fontSize = 15.sp, lineHeight = 23.sp),
                color = colors.inkSecondary,
            )
            Spacer(Modifier.height(14.dp))

            if (state.points.size >= 2) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.surface)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    SectionLabel(text = "Trend · dots are weigh-ins, the line is the average")
                    Spacer(Modifier.height(12.dp))
                    TrendChart(
                        points = state.points,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .semantics { contentDescription = "Bodyweight trend chart" },
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            // The scoreboard: is the plan doing what it says on the tin?
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface)
                    .padding(horizontal = 17.dp, vertical = 14.dp),
            ) {
                SectionLabel(text = "Last four weeks")
                Spacer(Modifier.height(10.dp))
                ScoreRow("Bodyweight", state.fourWeekWeightLabel ?: "needs 2 weeks")
                ScoreRow("Waist", state.waistLabel ?: "not measured")
                ScoreRow(
                    "Lifts",
                    state.strength?.let { s ->
                        "${s.up} up · ${s.flat} flat · ${s.down} down"
                    } ?: "needs 4 weeks of logging",
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Working when the average comes down and the lifts keep going up. " +
                        "If lifts start dropping while the scale falls fast, ease the pace.",
                    style = RedplateType.body.copy(fontSize = 12.5.sp, lineHeight = 19.sp),
                    color = colors.inkMuted,
                )
            }

            state.justRemoved?.let { gone ->
                Spacer(Modifier.height(12.dp))
                InfoNote(
                    text = "Removed ${gone.dateLabel.lowercase()} · ${gone.weightLabel}.",
                    marker = "−",
                    markerColor = colors.inkMuted,
                    onClick = onUndoDelete,
                    actionLabel = "Undo",
                )
            }

            if (state.rows.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionLabel(text = "Weigh-ins")
                Spacer(Modifier.height(6.dp))
                state.rows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = row.dateLabel,
                            style = RedplateType.data.copy(fontSize = 12.5.sp),
                            color = colors.inkMuted,
                            modifier = Modifier.width(110.dp),
                        )
                        Text(
                            text = row.weightLabel,
                            style = RedplateType.data.copy(fontSize = 13.sp),
                            color = colors.inkBright,
                            modifier = Modifier.weight(1f),
                        )
                        row.waistLabel?.let {
                            Text(
                                text = it,
                                style = RedplateType.data.copy(fontSize = 12.5.sp),
                                color = colors.inkSecondary,
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Box(
                            modifier = Modifier
                                .height(56.dp)
                                .width(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onDelete(row) }
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "Remove the weigh-in from ${row.dateLabel}"
                                    role = Role.Button
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Remove",
                                style = RedplateType.body.copy(fontSize = 12.sp),
                                color = colors.inkMuted,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (state.canAddWaist) {
            SecondaryButton(
                label = "Add today's waist",
                onClick = onAddWaist,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
            )
        }
        PrimaryBar(
            label = if (state.canAddWaist) "Log another weigh-in" else "Log this morning's weight",
            onClick = onLogWeight,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun ScoreRow(label: String, value: String) {
    val colors = RedplateTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = RedplateType.body.copy(fontSize = 14.sp),
            color = colors.inkBright,
        )
        Text(
            text = value,
            style = RedplateType.mono.copy(fontSize = 12.sp),
            color = colors.inkBright,
        )
    }
}

/**
 * Weigh-ins as faint dots, the seven-day average as the line. Drawn by hand rather than
 * by a chart library, in the app's own ink — no library to ship, nothing to fetch.
 */
@Composable
private fun TrendChart(points: List<BodyweightPoint>, modifier: Modifier = Modifier) {
    val colors = RedplateTheme.colors
    val dot = colors.inkSubtle
    val line = colors.ink
    Canvas(modifier = modifier) {
        val minT = points.first().measuredAt
        val maxT = points.last().measuredAt.coerceAtLeast(minT + 1)
        val values = points.flatMap { listOf(it.weightKg, it.averageKg) }
        val lo = values.min() - 0.5
        val hi = values.max() + 0.5
        fun x(t: Long) = ((t - minT).toFloat() / (maxT - minT)) * size.width
        fun y(v: Double) = size.height - ((v - lo) / (hi - lo)).toFloat() * size.height

        points.forEach { drawCircle(dot, radius = 3.dp.toPx(), center = Offset(x(it.measuredAt), y(it.weightKg))) }

        val path = Path()
        points.forEachIndexed { i, p ->
            val o = Offset(x(p.measuredAt), y(p.averageKg))
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        drawPath(
            path,
            color = line,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Preview(name = "Leaner & stronger", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun BodyweightPreview() {
    val day = 24L * 3600 * 1000
    val weights = listOf(86.0, 85.6, 85.9, 85.2, 85.0, 84.8, 85.1, 84.4, 84.2, 84.5, 83.9, 83.7)
    val points = weights.mapIndexed { i, w ->
        BodyweightPoint(i * 3 * day, w, weights.take(i + 1).takeLast(3).average())
    }
    RedplateTheme {
        BodyweightScreen(
            state = BodyweightUiState(
                isLoading = false,
                averageLabel = "83.9",
                rateLabel = "−0.6 KG / WEEK · −0.7%",
                coachLine = "Down 0.6 kg a week. A pace that lets you get leaner and keep the bar moving.",
                points = points,
                fourWeekWeightLabel = "−2.3 kg",
                waistLabel = "88 cm (−2.0)",
                strength = StrengthTrend(up = 5, flat = 2, down = 0),
            ),
            onBack = {},
            onLogWeight = {},
            onAddWaist = {},
            onDelete = {},
        )
    }
}

@Preview(name = "Leaner & stronger · empty", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun BodyweightEmptyPreview() {
    RedplateTheme {
        BodyweightScreen(
            state = BodyweightUiState(
                isLoading = false,
                coachLine = "Weigh in first thing in the morning, a few days a week. The trend is " +
                    "what counts — any single day can be a kilo off.",
            ),
            onBack = {},
            onLogWeight = {},
            onAddWaist = {},
            onDelete = {},
        )
    }
}
