package dev.redplate.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType
import dev.redplate.ui.theme.StateColor

/**
 * Where a muscle stands against its weekly set target.
 *
 * Three states, settled in design 10b. The important one is the third: going past a
 * weekly cap is drawn in yellow, never red, because one heavy week is a scheduling fact
 * rather than a mistake. The count only changes colour in that state — under the cap it
 * stays grey, so the row reads as status and not as a warning.
 */
enum class VolumeBarState {
    /** Under 75% of cap. Solid blue, count in grey. */
    BUILDING,

    /** 75–100%. Hatched — the last few sets. The target working, not a warning. */
    NEAR_CAP,

    /** Over cap. Track fills solid, count turns yellow. */
    OVER_CAP;

    companion object {
        fun of(current: Int, target: Int): VolumeBarState {
            if (target <= 0) return BUILDING
            val fraction = current.toFloat() / target
            return when {
                fraction > 1f -> OVER_CAP
                fraction >= 0.75f -> NEAR_CAP
                else -> BUILDING
            }
        }
    }
}

/**
 * One muscle group's week: name, track, count. Used identically on Today's footer, the
 * Plan tab's full chart and the session summary, so the three read as the same object
 * made complete rather than three different charts.
 *
 * [fourWeekAverage] draws the faint tick that makes a single week legible — without a
 * baseline a bar is just a number (design 10a).
 */
@Composable
fun VolumeBar(
    label: String,
    current: Int,
    target: Int,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 62.dp,
    fourWeekAverage: Int? = null,
) {
    val colors = RedplateTheme.colors
    val state = VolumeBarState.of(current, target)
    val fill = if (target > 0) (current.toFloat() / target).coerceIn(0f, 1f) else 0f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "$label, $current of $target sets this week"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = RedplateType.body.copy(fontSize = 12.5.sp),
            color = colors.inkSecondary,
            modifier = Modifier.width(labelWidth),
        )
        Spacer(Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(TRACK_HEIGHT)
                .clip(RoundedCornerShape(TRACK_HEIGHT / 2))
                .background(colors.surfaceRaised),
        ) {
            when (state) {
                VolumeBarState.BUILDING -> Box(
                    Modifier
                        .fillMaxWidth(fill)
                        .fillMaxHeight()
                        .background(colors.info),
                )

                VolumeBarState.NEAR_CAP -> Box(
                    Modifier
                        .fillMaxWidth(fill)
                        .fillMaxHeight(),
                ) { HatchFill() }

                VolumeBarState.OVER_CAP -> Box(
                    Modifier
                        .fillMaxSize()
                        .background(StateColor.pr),
                )
            }

            if (fourWeekAverage != null && target > 0) {
                val position = (fourWeekAverage.toFloat() / target).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth(position)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            // A grey tick vanishes on a solid yellow fill, so over the
                            // cap it switches to the hatch's dark stripe.
                            .background(
                                if (state == VolumeBarState.OVER_CAP) {
                                    StateColor.capHatchDark
                                } else {
                                    colors.tick
                                },
                            ),
                    )
                }
            }
        }

        Spacer(Modifier.width(10.dp))
        Text(
            text = "$current/$target",
            style = RedplateType.mono.copy(fontSize = 11.sp),
            color = if (state == VolumeBarState.OVER_CAP) StateColor.pr else colors.inkMuted,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp),
        )
    }
}

/** 135° stripes at 3dp intervals — the near-cap texture from 10b. */
@Composable
private fun HatchFill() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(StateColor.pr.copy(alpha = 0.34f))
        val stripe = 3.dp.toPx()
        val period = stripe * 2
        // Step far enough left that every 135° line still crosses the track.
        var x = -size.height
        while (x < size.width + size.height) {
            drawLine(
                color = StateColor.pr.copy(alpha = 0.85f),
                start = Offset(x, size.height),
                end = Offset(x + size.height, 0f),
                strokeWidth = stripe,
            )
            x += period
        }
    }
}

/**
 * The same row with a second, live-coloured segment showing what an unsaved edit would
 * add. The program builder's whole point: change a set count and watch the weekly total
 * move against your cap before you commit (design 6b).
 */
@Composable
fun VolumeDeltaBar(
    label: String,
    current: Int,
    added: Int,
    target: Int,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    val currentFill = if (target > 0) (current.toFloat() / target).coerceIn(0f, 1f) else 0f
    val addedFill = if (target > 0) (added.toFloat() / target).coerceIn(0f, 1f - currentFill) else 0f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "$label, $current of $target sets, this edit adds $added"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = RedplateType.body.copy(fontSize = 13.sp),
            color = colors.inkSecondary,
            modifier = Modifier.width(62.dp),
        )
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.surfaceRaised),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(currentFill)
                    .fillMaxHeight()
                    .background(colors.info),
            )
            // fillMaxWidth is relative to the space left after the first segment.
            Box(
                Modifier
                    .fillMaxWidth(if (currentFill < 1f) addedFill / (1f - currentFill) else 0f)
                    .fillMaxHeight()
                    .background(colors.live),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "${current + added}/$target",
            style = RedplateType.mono.copy(fontSize = 11.sp),
            color = colors.ink,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp),
        )
    }
}

/** Legend for the four-week-average tick, sat beside the balance chart's heading. */
@Composable
fun FourWeekAverageKey(modifier: Modifier = Modifier) {
    val colors = RedplateTheme.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(2.dp)
                .height(9.dp)
                .background(colors.tick),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "4-WK AVG",
            style = RedplateType.mono.copy(fontSize = 9.sp),
            color = colors.inkSubtle,
        )
    }
}

private val TRACK_HEIGHT = 6.dp

// ── Previews ────────────────────────────────────────────────────────

@Preview(name = "Volume bar · three states", widthDp = 384, backgroundColor = 0xFF101317, showBackground = true)
@Composable
private fun VolumeBarStatesPreview() {
    RedplateTheme {
        Column(
            modifier = Modifier
                .background(RedplateTheme.colors.ground)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VolumeBar(label = "Chest", current = 11, target = 20)
            VolumeBar(label = "Triceps", current = 16, target = 20)
            VolumeBar(label = "Back", current = 21, target = 20)
            VolumeBar(label = "Quads", current = 4, target = 18, fourWeekAverage = 12)
            VolumeDeltaBar(label = "Chest", current = 11, added = 2, target = 18)
            FourWeekAverageKey()
        }
    }
}
