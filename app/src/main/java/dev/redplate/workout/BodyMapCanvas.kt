package dev.redplate.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.R
import dev.redplate.data.MuscleGroup
import dev.redplate.ui.theme.PlexMono
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType
import kotlin.math.roundToInt

private const val SVG_W = 280f
private const val SVG_H = 560f

/**
 * Interactive anatomical body map with figure background and callout labels.
 *
 * Renders the body figure PNG (body-front-cut / body-back-cut) as background,
 * overlays translucent volume-tinted shapes per muscle, and draws floating
 * callout labels connected by leader lines — matching the HTML 5a/5b design.
 *
 * @param isFrontView  true = front-body; false = back-body
 * @param volumeMap    current-week volume state per muscle; absent keys → NONE
 * @param pickedMuscles  muscles the user has selected for this session
 * @param onMuscleSelected  called with the resolved [MuscleGroup] on tap
 */
@Composable
fun BodyMapCanvas(
    isFrontView: Boolean,
    volumeMap: Map<MuscleGroup, VolumeLevel>,
    onMuscleSelected: (MuscleGroup) -> Unit,
    modifier: Modifier = Modifier,
    pickedMuscles: Set<MuscleGroup> = emptySet(),
) {
    val colors = RedplateTheme.colors
    val visuals = if (isFrontView) frontVisuals else backVisuals
    val zones   = if (isFrontView) frontHitZones else backHitZones
    val callouts = if (isFrontView) frontCallouts else backCallouts

    // Actual pixel size of the Canvas — updated via onSizeChanged before any touch.
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    // Pending cluster popover; cleared on outside tap or chip selection.
    var popover by remember(isFrontView) { mutableStateOf<PopoverTarget?>(null) }

    val textMeasurer = rememberTextMeasurer()

    // The body figure drawable
    val figureRes = if (isFrontView) R.drawable.body_front_cut else R.drawable.body_back_cut

    Box(modifier = modifier) {

        // ── Body figure PNG background ────────────────────────────────────────
        Image(
            painter = painterResource(figureRes),
            contentDescription = if (isFrontView) "Front view of body" else "Back view of body",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        // ── Drawing + touch surface ───────────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                // TalkBack: all 19 muscles as discrete custom actions.
                .semantics {
                    customActions = allMusclesForAccessibility.map { muscle ->
                        CustomAccessibilityAction(
                            label = muscle.displayName,
                            action = { onMuscleSelected(muscle); true },
                        )
                    }
                }
                // Touch: fit-scale SVG coords, then walk zones in priority order.
                .pointerInput(isFrontView) {
                    detectTapGestures { offset ->
                        val cs = canvasSize
                        if (cs == Size.Zero) return@detectTapGestures
                        val scl = minOf(cs.width / SVG_W, cs.height / SVG_H)
                        val ox  = (cs.width  - SVG_W * scl) / 2f
                        val oy  = (cs.height - SVG_H * scl) / 2f
                        val svgX = (offset.x - ox) / scl
                        val svgY = (offset.y - oy) / scl
                        val hit = zones.firstOrNull { it.contains(svgX, svgY) }
                        when (val beh = hit?.behavior) {
                            is HitBehavior.Direct  -> { popover = null; onMuscleSelected(beh.muscle) }
                            is HitBehavior.Cluster -> { popover = PopoverTarget(beh.muscles, offset.x, offset.y) }
                            null                   -> { popover = null }
                        }
                    }
                }
        ) {
            val scl = minOf(size.width / SVG_W, size.height / SVG_H)
            val ox  = (size.width  - SVG_W * scl) / 2f
            val oy  = (size.height - SVG_H * scl) / 2f

            // ── Muscle shape overlays ─────────────────────────────────────────
             for (shape in visuals) {
                 val isPicked = shape.muscle in pickedMuscles
                 val level = if (isPicked) VolumeLevel.PICKED else volumeMap[shape.muscle]
                 val fill = level.toFillColor()
                 val l  = shape.left         * scl + ox
                 val t  = shape.top          * scl + oy
                 val w  = (shape.right  - shape.left)   * scl
                 val h  = (shape.bottom - shape.top)    * scl
                 val r  = shape.cornerRadius * scl
                 val cr = CornerRadius(r, r)

                 if (fill != Color.Transparent) {
                     drawRoundRect(
                         color    = fill,
                         topLeft  = Offset(l, t),
                         size     = Size(w, h),
                         cornerRadius = cr,
                     )
                 }

                 // PICKED state: glowing highlight with thick border
                 if (isPicked) {
                     // Glow effect: larger, more transparent layer
                     drawRoundRect(
                         color    = Color(0xFFF5F5F0).copy(alpha = 0.25f),
                         topLeft  = Offset(l - 3f * scl, t - 3f * scl),
                         size     = Size(w + 6f * scl, h + 6f * scl),
                         cornerRadius = CornerRadius(r + 3f * scl, r + 3f * scl),
                     )
                     // Bright border
                     drawRoundRect(
                         color    = Color(0xFFF5F5F0),
                         topLeft  = Offset(l, t),
                         size     = Size(w, h),
                         cornerRadius = cr,
                         style    = Stroke(width = (4f * scl).coerceAtLeast(2.5f)),
                     )
                 } else if (level == null || level == VolumeLevel.NONE || level == VolumeLevel.BELOW_MEV) {
                     // Dashed outline for under-trained / none
                     drawRoundRect(
                         color    = Color(0xFF6B737D),
                         topLeft  = Offset(l, t),
                         size     = Size(w, h),
                         cornerRadius = cr,
                         style    = Stroke(
                             width = (1.8f * scl).coerceAtLeast(1f),
                             pathEffect = PathEffect.dashPathEffect(
                                 floatArrayOf(6f * scl, 5f * scl), 0f
                             ),
                         ),
                     )
                 } else {
                     // Subtle outline for trained muscles
                     drawRoundRect(
                         color    = Color.Black.copy(alpha = 0.2f),
                         topLeft  = Offset(l, t),
                         size     = Size(w, h),
                         cornerRadius = cr,
                         style    = Stroke(width = (1f * scl).coerceAtLeast(0.5f)),
                     )
                 }
             }

            // ── Callout labels with leader lines ─────────────────────────────
            for (callout in callouts) {
                val level = if (callout.muscle in pickedMuscles) VolumeLevel.PICKED
                            else volumeMap[callout.muscle]
                // Only draw callouts for muscles that have volume data or are picked
                if (level == null || level == VolumeLevel.NONE) continue

                val anchorPx = Offset(callout.anchorX * scl + ox, callout.anchorY * scl + oy)
                val labelText = callout.muscle.displayName.uppercase()
                val volText = volumeMap[callout.muscle]?.let { "" } ?: "" // Placeholder

                // Label box positioning
                val labelW = 66f * scl
                val labelH = 42f * scl
                val lineLen = 40f * scl

                val (lineEndX, boxLeft) = when (callout.side) {
                    CalloutSide.LEFT -> {
                        val endX = anchorPx.x - lineLen
                        val bLeft = endX - labelW
                        Pair(endX, bLeft)
                    }
                    CalloutSide.RIGHT -> {
                        val endX = anchorPx.x + lineLen
                        Pair(endX, endX)
                    }
                }
                val boxTop = anchorPx.y - labelH / 2f

                // Leader line
                drawLine(
                    color = Color(0xFF8B939E),
                    start = anchorPx,
                    end = Offset(lineEndX, anchorPx.y),
                    strokeWidth = (1.6f * scl).coerceAtLeast(1f),
                )

                // Label background box
                drawRoundRect(
                    color = Color(0xFF242A32),
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(labelW, labelH),
                    cornerRadius = CornerRadius(11f * scl, 11f * scl),
                )

                 // Label text
                 val nameColor = when (level) {
                     VolumeLevel.PICKED -> Color(0xFFF5F5F0)
                     VolumeLevel.MEV_TO_MAV -> Color(0xFF2F9BD8)
                     VolumeLevel.APPROACHING_MRV -> Color(0xFFFFD100)
                     VolumeLevel.AT_MRV -> Color(0xFFFF5C1A)
                     else -> Color(0xFFF5F5F0)
                 }

                val nameStyle = TextStyle(
                    fontFamily = PlexMono,
                    fontWeight = FontWeight.Normal,
                    fontSize = (11f * scl).coerceAtLeast(8f).sp,
                    letterSpacing = 0.4.sp,
                    color = nameColor,
                )
                val nameResult = textMeasurer.measure(labelText, nameStyle)
                drawText(
                    nameResult,
                    topLeft = Offset(
                        boxLeft + (labelW - nameResult.size.width) / 2f,
                        boxTop + labelH * 0.18f,
                    ),
                )
            }
        }

        // ── Cluster chip popover ──────────────────────────────────────────────
        val pop = popover
        if (pop != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.absoluteOffset {
                    val halfRowPx = pop.muscles.size * 50.dp.toPx()
                    val rawX = (pop.tapX - halfRowPx).roundToInt()
                    val rawY = (pop.tapY - 52.dp.toPx()).roundToInt()
                    val clampedX = rawX.coerceIn(8.dp.toPx().roundToInt(),
                        (canvasSize.width - halfRowPx * 2 - 8.dp.toPx()).roundToInt()
                            .coerceAtLeast(8.dp.toPx().roundToInt()))
                    val clampedY = rawY.coerceAtLeast(8.dp.toPx().roundToInt())
                    IntOffset(clampedX, clampedY)
                },
            ) {
                pop.muscles.forEach { muscle ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(44.dp)
                            .defaultMinSize(minWidth = 88.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surface)
                            .border(0.5.dp, colors.line, RoundedCornerShape(8.dp))
                            .clickable { popover = null; onMuscleSelected(muscle) }
                            .padding(horizontal = 12.dp),
                    ) {
                        Text(
                            text  = muscle.displayName,
                            style = RedplateType.label,
                            color = colors.ink,
                        )
                    }
                }
            }
        }
    }
}

// ── Private types ─────────────────────────────────────────────────────────────

private data class PopoverTarget(
    val muscles: List<MuscleGroup>,
    val tapX: Float,
    val tapY: Float,
)

// ── Volume → fill colour ──────────────────────────────────────────────────
// Matches the HTML 5a design: 4 visual states, with strong visual distinction.

private fun VolumeLevel?.toFillColor(): Color = when (this) {
    null,
    VolumeLevel.NONE            -> Color(0xFFF5F5F0).copy(alpha = 0.08f)   // very faint (under-trained)
    VolumeLevel.BELOW_MEV       -> Color(0xFF8B939E).copy(alpha = 0.20f)   // gray (under-trained)
    VolumeLevel.MEV_TO_MAV      -> Color(0xFF2F9BD8).copy(alpha = 0.70f)   // bright blue (on target)
    VolumeLevel.APPROACHING_MRV -> Color(0xFFFFD100).copy(alpha = 0.70f)   // bright yellow (near cap)
    VolumeLevel.AT_MRV          -> Color(0xFFFF5C1A).copy(alpha = 0.70f)   // orange (over cap — live accent)
    VolumeLevel.PICKED          -> Color(0xFFF5F5F0).copy(alpha = 0.12f)   // white faint (picked)
}
