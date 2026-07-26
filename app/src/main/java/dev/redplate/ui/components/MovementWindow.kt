package dev.redplate.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.redplate.data.MuscleGroup
import dev.redplate.ui.theme.RedplateType

/**
 * The movement window: start and end position cross-fading on a loop.
 *
 * Two stills carry more information than either alone — enough to read the movement
 * without bundling video. When only one image exists the window simply holds it still;
 * the design has to survive that case and does (8c's Chest Press).
 *
 * Degrades to the muscle-group placeholder when nothing is on disk. Never a broken
 * image, never a spinner that resolves to nothing.
 */
@Composable
fun MovementWindow(
    startImageUri: String?,
    endImageUri: String?,
    muscle: MuscleGroup,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    /** Staggers the loop so a grid of cards doesn't pulse in unison (8c). */
    phaseOffsetMillis: Int = 0,
    attribution: String? = MEDIA_ATTRIBUTION,
) {
    val primary = startImageUri ?: endImageUri

    Box(
        modifier = modifier
            .background(PLATE_PAPER)
            .semantics { contentDescription?.let { this.contentDescription = it } },
    ) {
        if (primary == null) {
            MuscleGlyphPlaceholder(muscle = muscle, modifier = Modifier.fillMaxSize())
            return@Box
        }

        AsyncImage(
            model = primary,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        // The second frame fades in over the first. Only worth animating when the pair
        // is genuinely different, and never in a @Preview, where an endless animation
        // would keep the frame from ever settling.
        if (startImageUri != null && endImageUri != null && !LocalInspectionMode.current) {
            val transition = rememberInfiniteTransition(label = "movement")
            val alpha by transition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = LOOP_MILLIS
                        0f at 0
                        0f at (LOOP_MILLIS * 38 / 100)
                        1f at (LOOP_MILLIS * 52 / 100)
                        1f at (LOOP_MILLIS * 92 / 100)
                        0f at LOOP_MILLIS
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(
                        phaseOffsetMillis,
                    ),
                ),
                label = "frame",
            )
            AsyncImage(
                model = endImageUri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(alpha),
            )
        }

        if (attribution != null) {
            Text(
                text = attribution,
                style = RedplateType.mono.copy(fontSize = 9.5.sp),
                color = Color(0xFF0C0E11),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp, 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF5F5F0).copy(alpha = 0.82f))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Credit for the bundled stills. The design mocked these up against wger/Everkinetic
 * artwork; the images that actually ship come from free-exercise-db, so the badge names
 * that instead — crediting the wrong project is a licensing problem, not a cosmetic one.
 */
const val MEDIA_ATTRIBUTION = "free-exercise-db · public domain"

/** The paper-white the artwork is drawn on. Keeps the window bright against the ground. */
val PLATE_PAPER = Color(0xFFEDEEE9)

private const val LOOP_MILLIS = 2600
