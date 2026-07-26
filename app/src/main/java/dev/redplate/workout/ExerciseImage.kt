package dev.redplate.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.data.MuscleGroup
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * Displays an exercise image if available, otherwise shows a placeholder
 * with the primary muscle group name. Degrades silently when imageUri is null.
 *
 * In a future iteration this can use Coil's AsyncImage to load the file URI.
 * For now it renders a muscle-label card so the layout doesn't break.
 */
@Composable
fun ExerciseImage(
    imageUri: String?,
    muscle: MuscleGroup,
    modifier: Modifier = Modifier,
    contentDescription: String = "",
) {
    val colors = RedplateTheme.colors

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = muscle.name.replace('_', ' '),
            style = RedplateType.mono.copy(fontSize = 12.sp),
            color = colors.inkMuted,
        )
    }
}
