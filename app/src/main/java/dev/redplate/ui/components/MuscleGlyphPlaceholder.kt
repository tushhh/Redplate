package dev.redplate.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import dev.redplate.data.MuscleGroup
import dev.redplate.ui.theme.RedplateType

/** Two-letter abbreviation, used when there is no artwork to show. */
val MuscleGroup.glyph: String
    get() = when (this) {
        MuscleGroup.CHEST        -> "CH"
        MuscleGroup.UPPER_BACK   -> "UB"
        MuscleGroup.LATS         -> "LA"
        MuscleGroup.LOWER_BACK   -> "LB"
        MuscleGroup.FRONT_DELTS  -> "FD"
        MuscleGroup.SIDE_DELTS   -> "SD"
        MuscleGroup.REAR_DELTS   -> "RD"
        MuscleGroup.BICEPS       -> "BI"
        MuscleGroup.TRICEPS      -> "TR"
        MuscleGroup.FOREARMS     -> "FA"
        MuscleGroup.QUADS        -> "QU"
        MuscleGroup.HAMSTRINGS   -> "HA"
        MuscleGroup.GLUTES       -> "GL"
        MuscleGroup.ADDUCTORS    -> "AD"
        MuscleGroup.CALVES       -> "CA"
        MuscleGroup.ABS          -> "AB"
        MuscleGroup.OBLIQUES     -> "OB"
        MuscleGroup.TRAPS        -> "TP"
        MuscleGroup.NECK         -> "NK"
    }

/**
 * Hatched paper with a muscle-group glyph, shown wherever a still is missing.
 *
 * Deliberately not an error state: media is optional in this app, so a missing file is
 * normal and gets an honest placeholder rather than a broken-image icon.
 */
@Composable
fun MuscleGlyphPlaceholder(
    muscle: MuscleGroup,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.background(PLATE_PAPER),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val spacing = 12f
            val lineColor = Color(0xFF3A414B).copy(alpha = 0.18f)
            val totalSteps = ((size.width + size.height) / spacing).toInt() + 2
            for (i in 0..totalSteps) {
                val offset = i * spacing
                val startX = (offset - size.height).coerceAtLeast(0f)
                val startY = (size.height - offset).coerceAtLeast(0f)
                val endX = offset.coerceAtMost(size.width)
                val endY = (offset - size.width + size.height)
                    .coerceAtLeast(0f)
                    .coerceAtMost(size.height)
                drawLine(
                    color = lineColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 1f,
                )
            }
        }
        Text(
            text = muscle.glyph,
            style = RedplateType.figure.copy(fontSize = 26.sp, color = Color(0xFFB0B5BA)),
        )
    }
}
