package dev.redplate.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.redplate.R
import dev.redplate.data.MuscleGroup
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.StateColor

/**
 * The body map — design 5a and 5b.
 *
 * The artwork carries the figure; this paints the week's volume on top of it. That is the
 * point of the screen: navigation and the per-muscle dashboard are one object, so you see
 * what is undertrained *while* choosing what to train.
 *
 * Shading follows 5a's legend — dashed outline when untrained, solid blue on target,
 * hatched yellow near the cap, and a thick ink outline once picked.
 */
@Composable
fun BodyMapCanvas(
    isFrontView: Boolean,
    volumeMap: Map<MuscleGroup, VolumeLevel>,
    onMuscleSelected: (MuscleGroup) -> Unit,
    modifier: Modifier = Modifier,
    pickedMuscles: Set<MuscleGroup> = emptySet(),
) {
    val regions = if (isFrontView) frontRegions else backRegions
    val colors = RedplateTheme.colors

    // Parsing is not free and the path set never changes, so do it once per view.
    val parsed = remember(isFrontView) {
        regions.map { it to PathParser().parsePathString(it.pathData).toPath() }
    }
    val muscles = remember(isFrontView) { regions.map { it.muscle }.distinct() }

    Box(
        modifier = modifier.semantics {
            // Every muscle is a discrete TalkBack action however thin its shape is — a
            // screen-reader user must never depend on hit-testing to reach one.
            customActions = muscles.map { muscle ->
                CustomAccessibilityAction(muscle.displayName) {
                    onMuscleSelected(muscle)
                    true
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(
                if (isFrontView) R.drawable.body_front_cut else R.drawable.body_back_cut,
            ),
            contentDescription = if (isFrontView) {
                "Front view of the body. Tap a muscle to train it."
            } else {
                "Back view of the body. Tap a muscle to train it."
            },
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isFrontView, parsed) {
                    detectTapGestures { tap ->
                        hitTest(tap, parsed, size.width.toFloat(), size.height.toFloat())
                            ?.let(onMuscleSelected)
                    }
                },
        ) {
            val scale = fitScale(size.width, size.height)
            val dx = (size.width - BODY_MAP_VIEWPORT_WIDTH * scale) / 2f
            val dy = (size.height - BODY_MAP_VIEWPORT_HEIGHT * scale) / 2f

            translate(dx, dy) {
                parsed.forEach { (region, path) ->
                    val scaled = Path().apply {
                        addPath(path)
                        transform(Matrix().apply { scale(scale, scale) })
                    }
                    val picked = region.muscle in pickedMuscles
                    val level = if (picked) {
                        VolumeLevel.PICKED
                    } else {
                        volumeMap[region.muscle] ?: VolumeLevel.NONE
                    }
                    drawRegion(
                        path = scaled,
                        level = level,
                        picked = picked,
                        inkColor = colors.ink,
                        dashedColor = colors.outlineDashed,
                        infoColor = colors.info,
                    )
                }
            }
        }
    }
}

/** Fill and outline for one region, per the legend in 5a. */
private fun DrawScope.drawRegion(
    path: Path,
    level: VolumeLevel,
    picked: Boolean,
    inkColor: Color,
    dashedColor: Color,
    infoColor: Color,
) {
    when (level) {
        VolumeLevel.NONE, VolumeLevel.BELOW_MEV -> {
            drawPath(path, inkColor.copy(alpha = 0.05f))
            drawPath(
                path,
                dashedColor,
                style = Stroke(
                    width = 1.8.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                    ),
                ),
            )
        }

        VolumeLevel.MEV_TO_MAV -> drawPath(path, infoColor.copy(alpha = 0.55f))

        VolumeLevel.APPROACHING_MRV, VolumeLevel.AT_MRV -> {
            drawPath(path, StateColor.pr.copy(alpha = 0.34f))
            // Clipped to the region, so the hatch reads as texture rather than overlay.
            clipPath(path) {
                val stripe = 3.dp.toPx()
                var x = -size.height
                while (x < size.width + size.height) {
                    drawLine(
                        color = StateColor.pr.copy(alpha = 0.85f),
                        start = Offset(x, size.height),
                        end = Offset(x + size.height, 0f),
                        strokeWidth = stripe,
                    )
                    x += stripe * 2
                }
            }
        }

        VolumeLevel.PICKED -> drawPath(path, inkColor.copy(alpha = 0.18f))
    }

    if (picked) {
        drawPath(path, inkColor, style = Stroke(width = 3.dp.toPx()))
    }
}

/**
 * The artwork draws with ContentScale.Fit, so the overlay letterboxes identically or the
 * regions land off the body.
 */
private fun fitScale(width: Float, height: Float): Float =
    minOf(width / BODY_MAP_VIEWPORT_WIDTH, height / BODY_MAP_VIEWPORT_HEIGHT)

/**
 * Which muscle a tap landed on.
 *
 * Containing region first, then the nearest one within a thumb's width. That fallback is
 * what makes the thin regions reachable without a two-stage popover: a near miss resolves
 * to the obvious target instead of to nothing.
 */
private fun hitTest(
    tap: Offset,
    regions: List<Pair<BodyRegion, Path>>,
    width: Float,
    height: Float,
): MuscleGroup? {
    val scale = fitScale(width, height)
    if (scale <= 0f) return null
    val dx = (width - BODY_MAP_VIEWPORT_WIDTH * scale) / 2f
    val dy = (height - BODY_MAP_VIEWPORT_HEIGHT * scale) / 2f

    // Back into the design's coordinate space, where the paths are expressed.
    val point = Offset((tap.x - dx) / scale, (tap.y - dy) / scale)

    regions.firstOrNull { (_, path) -> path.containsPoint(point) }?.let { return it.first.muscle }

    val tolerance = TAP_TOLERANCE_VIEWPORT
    return regions
        .map { (region, path) -> region to path.distanceToOutline(point) }
        .filter { it.second <= tolerance }
        .minByOrNull { it.second }
        ?.first
        ?.muscle
}

/** Bounds check, then an intersection probe for the real shape. */
private fun Path.containsPoint(point: Offset): Boolean {
    val bounds = getBounds()
    if (point.x < bounds.left || point.x > bounds.right) return false
    if (point.y < bounds.top || point.y > bounds.bottom) return false

    val probe = Path().apply {
        addRect(Rect(point.x - 0.5f, point.y - 0.5f, point.x + 0.5f, point.y + 0.5f))
    }
    val hit = Path()
    hit.op(this, probe, PathOperation.Intersect)
    return !hit.isEmpty
}

/** Rough distance from a point to the outline, sampled along its length. */
private fun Path.distanceToOutline(point: Offset): Float {
    val measure = PathMeasure().apply { setPath(this@distanceToOutline, false) }
    val length = measure.length
    if (length <= 0f) return Float.MAX_VALUE

    var best = Float.MAX_VALUE
    var travelled = 0f
    val step = (length / OUTLINE_SAMPLES).coerceAtLeast(1f)
    while (travelled <= length) {
        val distance = (measure.getPosition(travelled) - point).getDistance()
        if (distance < best) best = distance
        travelled += step
    }
    return best
}

/** Roughly a thumb's width in the 230×520 viewport. */
private const val TAP_TOLERANCE_VIEWPORT = 14f

private const val OUTLINE_SAMPLES = 48

// ── Previews ────────────────────────────────────────────────────────

@Preview(name = "5a · front", widthDp = 240, heightDp = 520, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun BodyMapFrontPreview() {
    RedplateTheme {
        Box(
            Modifier
                .background(RedplateTheme.colors.ground)
                .padding(8.dp),
        ) {
            BodyMapCanvas(
                isFrontView = true,
                volumeMap = mapOf(
                    MuscleGroup.CHEST to VolumeLevel.APPROACHING_MRV,
                    MuscleGroup.SIDE_DELTS to VolumeLevel.MEV_TO_MAV,
                    MuscleGroup.ABS to VolumeLevel.MEV_TO_MAV,
                    MuscleGroup.QUADS to VolumeLevel.NONE,
                ),
                pickedMuscles = setOf(MuscleGroup.BICEPS),
                onMuscleSelected = {},
                modifier = Modifier
                    .width(198.dp)
                    .height(447.dp),
            )
        }
    }
}

@Preview(name = "5b · back", widthDp = 240, heightDp = 520, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun BodyMapBackPreview() {
    RedplateTheme {
        Box(
            Modifier
                .background(RedplateTheme.colors.ground)
                .padding(8.dp),
        ) {
            BodyMapCanvas(
                isFrontView = false,
                volumeMap = mapOf(
                    MuscleGroup.TRAPS to VolumeLevel.APPROACHING_MRV,
                    MuscleGroup.LATS to VolumeLevel.MEV_TO_MAV,
                    MuscleGroup.GLUTES to VolumeLevel.MEV_TO_MAV,
                    MuscleGroup.CALVES to VolumeLevel.MEV_TO_MAV,
                ),
                onMuscleSelected = {},
                modifier = Modifier
                    .width(198.dp)
                    .height(447.dp),
            )
        }
    }
}
