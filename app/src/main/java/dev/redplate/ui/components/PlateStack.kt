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

private val FULL_PLATE_HEIGHT = 120.dp
private val BAR_HEIGHT = 10.dp
private val BAR_END_WIDTH = 6.dp
private val BAR_END_HEIGHT = 22.dp
private val COLLAR_WIDTH = 12.dp
private val COLLAR_HEIGHT = 34.dp
private val PLATE_GAP = 1.5.dp
private val PLATE_CORNER = 3.dp
private val OUTLINE_WIDTH = 1.5.dp

private val BAR_COLOR = Color(0xFF3A3A3A)
private val COLLAR_COLOR = Color(0xFF555555)

private fun plateHeight(kg: Double): Dp = when {
    kg >= 10.0 -> FULL_PLATE_HEIGHT
    kg == 5.0  -> 96.dp
    kg == 2.5  -> 72.dp
    else       -> 56.dp
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
) {
    val outlineColor = RedplateTheme.colors.line

    Box(
        modifier = modifier.heightIn(min = FULL_PLATE_HEIGHT),
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
            BarEnd()
            Spacer(Modifier.weight(1f))

            PlateRun(
                plates = plateLoad.perSide.asReversed(),
                slideDirection = -1,
                outlineColor = outlineColor,
            )

            Collar()

            PlateRun(
                plates = plateLoad.perSide,
                slideDirection = 1,
                outlineColor = outlineColor,
            )

            Spacer(Modifier.weight(1f))
            BarEnd()
        }
    }
}

// ── One side of the bar (animated) ──────────────────────────────────

@Composable
private fun PlateRun(
    plates: List<Double>,
    slideDirection: Int,
    outlineColor: Color,
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
                PlateDisc(kg = kg, outlineColor = outlineColor)
            }
        }
    }
}

// ── Single disc ─────────────────────────────────────────────────────

@Composable
private fun PlateDisc(kg: Double, outlineColor: Color) {
    val color = PlateColor.forPlate(kg)
    val outlined = PlateColor.needsOutline(kg)
    val shape = RoundedCornerShape(PLATE_CORNER)

    Box(
        Modifier
            .width(plateWidth(kg))
            .height(plateHeight(kg))
            .clip(shape)
            .then(if (outlined) Modifier.border(OUTLINE_WIDTH, outlineColor, shape) else Modifier)
            .background(color)
    )
}

// ── Collar + bar end ────────────────────────────────────────────────

@Composable
private fun Collar() {
    Box(
        Modifier
            .width(COLLAR_WIDTH)
            .height(COLLAR_HEIGHT)
            .clip(RoundedCornerShape(2.dp))
            .background(COLLAR_COLOR)
    )
}

@Composable
private fun BarEnd() {
    Box(
        Modifier
            .width(BAR_END_WIDTH)
            .height(BAR_END_HEIGHT)
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
