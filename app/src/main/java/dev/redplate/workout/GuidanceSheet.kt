package dev.redplate.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.data.MuscleGroup
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * State for the guidance bottom sheet (design 8b).
 */
data class GuidanceState(
    val exerciseName: String,
    val muscleTags: List<String>,
    val instructions: List<String>,
    val primaryMuscle: MuscleGroup,
    val imageUri: String? = null,
    /** Equipment-valid alternatives, best overlap first. One tap swaps (COACHING.md §4). */
    val substitutes: List<SubstituteOption> = emptyList(),
)

/** An exercise the user could do instead, with the kit it needs. */
data class SubstituteOption(
    val exerciseId: String,
    val name: String,
    val equipmentLabel: String,
)

/**
 * Guidance sheet shown over the set logging screen (design 8b).
 * Drag handle, exercise name, muscle tags, movement window (placeholder),
 * numbered instruction steps, swap row, primary bar "Got it — first set".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GuidanceSheet(
    state: GuidanceState,
    onDismiss: () -> Unit,
    onSwap: (exerciseId: String) -> Unit,
    onGotIt: () -> Unit,
) {
    val colors = RedplateTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 12.dp)
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF3E454E)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // Exercise name
            Text(
                text = state.exerciseName,
                style = RedplateType.title.copy(fontSize = 25.sp),
                color = colors.ink,
            )
            Spacer(Modifier.height(9.dp))

            // Muscle tags
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                state.muscleTags.forEachIndexed { index, tag ->
                    Text(
                        text = tag.uppercase(),
                        style = RedplateType.mono.copy(fontSize = 10.sp, letterSpacing = 0.06.sp),
                        color = if (index == 0) colors.inkBright else colors.inkMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(colors.surfaceRaised)
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // Movement window placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(360f / 186f)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                ExerciseImage(
                    imageUri = state.imageUri,
                    muscle = state.primaryMuscle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(186.dp),
                    contentDescription = state.exerciseName,
                )
                // Attribution badge. The bundled stills come from free-exercise-db,
                // not wger \u2014 crediting the wrong project is a licensing problem, not a
                // cosmetic one.
                Text(
                    text = "free-exercise-db \u00B7 public domain",
                    style = RedplateType.mono.copy(fontSize = 9.5.sp, letterSpacing = 0.06.sp),
                    color = colors.inkOnLight,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp, 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF5F5F0).copy(alpha = 0.82f))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))

            // Numbered instructions
            state.instructions.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier.padding(vertical = 5.dp),
                ) {
                    Text(
                        text = "${index + 1}",
                        style = RedplateType.body.copy(fontSize = 14.sp),
                        color = colors.inkMuted,
                        modifier = Modifier.width(24.dp),
                    )
                    Text(
                        text = step,
                        style = RedplateType.body.copy(fontSize = 14.sp, lineHeight = 22.sp),
                        color = colors.inkBright,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // Substitutes. This was a single "Swap for something else" row wired to an
            // empty handler; a rack being occupied is the most common reason to open
            // this sheet, so the alternatives are listed here and swap in one tap.
            if (state.substitutes.isNotEmpty()) {
                Text(
                    text = "RACK BUSY? TRY",
                    style = RedplateType.mono.copy(fontSize = 10.sp, letterSpacing = 0.1.sp),
                    color = colors.inkMuted,
                )
                Spacer(Modifier.height(8.dp))

                state.substitutes.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .height(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.surfaceRaised)
                            .clickable { onSwap(option.exerciseId) }
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = option.name,
                                style = RedplateType.body.copy(fontSize = 14.5.sp),
                                color = colors.inkBright,
                            )
                            Text(
                                text = option.equipmentLabel,
                                style = RedplateType.mono.copy(fontSize = 10.sp),
                                color = colors.inkMuted,
                            )
                        }
                        Text(
                            text = "\u203A",
                            style = RedplateType.title.copy(fontSize = 22.sp),
                            color = colors.inkMuted,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            PrimaryBar(
                label = "Got it \u2014 first set",
                onClick = onGotIt,
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Preview
@Composable
private fun GuidanceSheetPreview() {
    RedplateTheme {
        // Preview can't show ModalBottomSheet; just show the content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1E24))
                .padding(20.dp),
        ) {
            Text(
                text = "Incline Dumbbell Press",
                style = RedplateType.title.copy(fontSize = 25.sp),
                color = Color(0xFFF5F5F0),
            )
        }
    }
}
