package dev.redplate.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.data.ExerciseEntity
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.theme.PlexCondensed
import dev.redplate.ui.theme.PlexMono
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * Browser card layout for exercise selection in planned sessions.
 * Matches HTML design 8c: 2-column grid with animated images, filter chips, and search.
 *
 * @param sessionName The session being built (e.g., "Upper A")
 * @param exercisesInSession Already-added exercises (show in first section)
 * @param frequentExercises Frequently-used exercises (show in second section)
 * @param selectedExerciseId Currently selected exercise ID
 * @param onExerciseSelect Callback when an exercise is tapped
 * @param onAddExercise Callback when the CTA is tapped
 * @param modifier Layout modifier
 */
@Composable
fun ExerciseBrowserScreen(
    sessionName: String,
    exercisesInSession: List<ExerciseEntity>,
    frequentExercises: List<ExerciseEntity>,
    selectedExerciseId: String?,
    onExerciseSelect: (String) -> Unit,
    onAddExercise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    val selectedExercise = frequentExercises.find { it.id == selectedExerciseId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground),
    ) {
        // Header
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                text = "Add to $sessionName",
                style = RedplateType.headline.copy(
                    fontFamily = PlexCondensed,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.ink,
            )
            Text(
                text = "Yours first · ${exercisesInSession.size + frequentExercises.size} in the archive",
                style = RedplateType.body.copy(fontSize = 13.sp),
                color = colors.inkMuted,
            )
        }

        // Scrollable content
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // IN THIS SESSION section
            if (exercisesInSession.isNotEmpty()) {
                Text(
                    text = "IN THIS SESSION",
                    style = RedplateType.mono.copy(fontSize = 10.sp),
                    color = colors.inkMuted,
                )
                Spacer(Modifier.height(8.dp))

                ExerciseGrid(
                    exercises = exercisesInSession,
                    selectedId = selectedExerciseId,
                    onSelect = onExerciseSelect,
                )

                Spacer(Modifier.height(12.dp))
            }

            // YOU TRAIN THESE MOST section
            Text(
                text = "YOU TRAIN THESE MOST",
                style = RedplateType.mono.copy(fontSize = 10.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(8.dp))

            ExerciseGrid(
                exercises = frequentExercises,
                selectedId = selectedExerciseId,
                onSelect = onExerciseSelect,
            )

            Spacer(Modifier.height(12.dp))
        }

        // Filter chips and search
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Filter chips row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip("MY KIT", true, Modifier.width(64.dp))
                FilterChip("CHEST", false, Modifier.weight(1f))
                FilterChip("COMPOUND", false, Modifier.weight(1f))
            }

            // Search field
            SearchField(
                placeholder = "Search all 873",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Primary CTA
        PrimaryBar(
            label = if (selectedExercise != null) "Add ${selectedExercise.name}" else "Select an exercise",
            onClick = onAddExercise,
            enabled = selectedExerciseId != null,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun ExerciseGrid(
    exercises: List<ExerciseEntity>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    val numColumns = 2

    Column(modifier = modifier) {
        exercises.chunked(numColumns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                row.forEach { exercise ->
                    val isSelected = exercise.id == selectedId
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) colors.ink else Color.Transparent,
                                shape = RoundedCornerShape(16.dp),
                            )
                            .clickable { onSelect(exercise.id) },
                    ) {
                        Column {
                            // Exercise image area (131dp tall)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(131.dp)
                                    .background(Color(0xFFEDEEE9)),
                                contentAlignment = Alignment.Center,
                            ) {
                                // Placeholder for image; real implementation would use ExerciseImage
                                Text(
                                    text = exercise.name.take(2).uppercase(),
                                    style = RedplateType.figure.copy(fontSize = 18.sp),
                                    color = Color(0xFF8B939E),
                                )
                            }

                            // Checkmark badge for selected
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(colors.ink)
                                        .align(Alignment.End)
                                        .padding(7.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("✓", color = Color(0xFF0C0E11), fontSize = 14.sp)
                                }
                            }

                            // Exercise details
                            Column(
                                modifier = Modifier.padding(9.dp),
                            ) {
                                Text(
                                    text = exercise.name,
                                    style = RedplateType.body.copy(
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = colors.ink,
                                    lineHeight = 18.sp,
                                )
                                Text(
                                    text = exercise.pattern.name.replace("_", " ").uppercase(),
                                    style = RedplateType.mono.copy(fontSize = 10.sp),
                                    color = colors.inkMuted,
                                )
                            }
                        }
                    }
                }

                // Filler for odd-count rows
                if (row.size < numColumns) {
                    Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(9.dp))
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.ink else colors.surface)
            .clickable { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = RedplateType.mono.copy(
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            ),
            color = if (selected) Color(0xFF0C0E11) else colors.inkMuted,
        )
    }
}

@Composable
private fun SearchField(
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "⌕ $placeholder",
            style = RedplateType.body.copy(fontSize = 14.5.sp),
            color = colors.inkMuted,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}


