package dev.redplate.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.redplate.data.PlateMath
import dev.redplate.ui.theme.PlateColor
import dev.redplate.ui.theme.RedplateTheme

// ── Dimensions ──────────────────────────────────────────────────────
// Calibrated for S24 Ultra (384 dp wide). Competition bumpers share
// a height (like the real 450 mm diameter); change plates scale down.

/**
 * Competition bumpers share a height (like the real 450 mm diameter); change plates
 * scale down from it. [PlateStack] takes the full height as a parameter because the set
 * screen and the guidance sheet give the stack very different amounts of room — 66 dp
 * beside a movement window (design 8a), 88 dp when the stack is the hero (design 1b).
 */
val PLATE_HEIGHT_FULL = 120.dp
val PLATE_HEIGHT_COMPACT = 66.dp

private val BAR_HEIGHT = 7.dp
private val BAR_END_WIDTH = 6.dp
private val COLLAR_WIDTH = 11.dp
private val PLATE_GAP = 2.dp
private val PLATE_CORNER = 3.dp
private val OUTLINE_WIDTH = 1.5.dp

private val BAR_COLOR = Color(0xFF2A2F36)
private val COLLAR_COLOR = Color(0xFF555555)

/** Change plates read as smaller discs, in the same proportions as real ones. */
private fun plateHeight(kg: Double, full: Dp): Dp = when {
    kg >= 10.0 -> full
    kg == 5.0 -> full * 0.80f
    kg == 2.5 -> full * 0.60f
    else -> full * 0.47f
}

private fun plateWidth(kg: Double): Dp = when {
    kg >= 25.0 -> 18.dp
    kg >= 20.0 -> 15.dp
    kg >= 15.0 -> 13.dp
    kg >= 10.0 -> 11.dp
    kg >= 5.0  -> 9.dp
    else       -> 7.dp
}

// ── Public API ──────────────────────────────────────────────────────

@Composable
fun PlateStack(
    plateLoad: PlateMath.PlateLoad,
    modifier: Modifier = Modifier,
    plateHeight: Dp = PLATE_HEIGHT_FULL,
) {
    val outlineColor = RedplateTheme.colors.line

    Box(
        modifier = modifier.heightIn(min = plateHeight),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .clip(RoundedCornerShape(BAR_HEIGHT / 2))
                .background(BAR_COLOR)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            BarEnd(plateHeight)
            Spacer(Modifier.weight(1f))

            PlateRun(
                plates = plateLoad.perSide.asReversed(),
                slideDirection = -1,
                outlineColor = outlineColor,
                fullHeight = plateHeight,
            )

            Collar(plateHeight)

            PlateRun(
                plates = plateLoad.perSide,
                slideDirection = 1,
                outlineColor = outlineColor,
                fullHeight = plateHeight,
            )

            Spacer(Modifier.weight(1f))
            BarEnd(plateHeight)
        }
    }
}

// ── One side of the bar (animated) ──────────────────────────────────

@Composable
private fun PlateRun(
    plates: List<Double>,
    slideDirection: Int,
    outlineColor: Color,
    fullHeight: Dp,
) {
    AnimatedContent(
        targetState = plates,
        transitionSpec = {
            val growing = targetState.size >= initialState.size

            val enter = slideInHorizontally(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) { w -> if (growing) w * slideDirection / 3 else 0 } +
                fadeIn(tween(250))

            val exit = slideOutHorizontally(
                animationSpec = tween(200),
            ) { w -> if (!growing) w * slideDirection / 3 else 0 } +
                fadeOut(tween(150))

            enter togetherWith exit using SizeTransform(clip = false) { _, _ ->
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                )
            }
        },
        contentAlignment = Alignment.CenterStart,
        label = "plates",
    ) { currentPlates ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PLATE_GAP),
        ) {
            currentPlates.forEach { kg ->
                PlateDisc(kg = kg, outlineColor = outlineColor, fullHeight = fullHeight)
            }
        }
    }
}

// ── Single disc ─────────────────────────────────────────────────────

@Composable
private fun PlateDisc(kg: Double, outlineColor: Color, fullHeight: Dp) {
    val color = PlateColor.forPlate(kg)
    val outlined = PlateColor.needsOutline(kg)
    val shape = RoundedCornerShape(PLATE_CORNER)

    Box(
        Modifier
            .width(plateWidth(kg))
            .height(plateHeight(kg, fullHeight))
            .clip(shape)
            .then(if (outlined) Modifier.border(OUTLINE_WIDTH, outlineColor, shape) else Modifier)
            .background(color)
    )
}

// ── Collar + bar end ────────────────────────────────────────────────

@Composable
private fun Collar(fullHeight: Dp) {
    Box(
        Modifier
            .width(COLLAR_WIDTH)
            .height(fullHeight * 0.36f)
            .clip(RoundedCornerShape(2.dp))
            .background(COLLAR_COLOR)
    )
}

@Composable
private fun BarEnd(fullHeight: Dp) {
    Box(
        Modifier
            .width(BAR_END_WIDTH)
            .height(fullHeight * 0.24f)
            .clip(RoundedCornerShape(2.dp))
            .background(BAR_COLOR)
    )
}

// ── Previews ────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 384)
@Composable
private fun PlateStackHeavyPreview() {
    RedplateTheme {
        PlateStack(
            plateLoad = PlateMath.PlateLoad(
                totalKg = 140.0,
                perSide = listOf(25.0, 20.0, 10.0, 5.0),
                exact = true,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 384)
@Composable
private fun PlateStackMediumPreview() {
    RedplateTheme {
        PlateStack(
            plateLoad = PlateMath.PlateLoad(
                totalKg = 100.0,
                perSide = listOf(25.0, 10.0, 5.0),
                exact = true,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 384)
@Composable
private fun PlateStackEmptyBarPreview() {
    RedplateTheme {
        PlateStack(
            plateLoad = PlateMath.PlateLoad(
                totalKg = 20.0,
                perSide = emptyList(),
                exact = true,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 384)
@Composable
private fun PlateStackOutlinePlatesPreview() {
    RedplateTheme {
        PlateStack(
            plateLoad = PlateMath.PlateLoad(
                totalKg = 47.5,
                perSide = listOf(10.0, 2.5, 1.25),
                exact = true,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 384)
@Composable
private fun PlateStackMaxLoadPreview() {
    RedplateTheme {
        PlateStack(
            plateLoad = PlateMath.PlateLoad(
                totalKg = 182.5,
                perSide = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25),
                exact = false,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        )
    }
}
