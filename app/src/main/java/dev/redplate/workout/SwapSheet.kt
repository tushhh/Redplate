package dev.redplate.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.data.MuscleGroup
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

data class SwapCandidate(
    val exerciseId: String,
    val name: String,
    val overlapPercent: Int,
    val loadSuggestion: String,
    val primaryMuscle: MuscleGroup,
    val imageUri: String? = null,
)

/**
 * Swap sheet (design 8d). Shows substitute exercises with overlap % and load suggestions.
 * Bottom sheet over dimmed set screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapSheet(
    exerciseName: String,
    setsLogged: Int,
    candidates: List<SwapCandidate>,
    onSelectCandidate: (String) -> Unit,
    onKeep: () -> Unit,
    onDismiss: () -> Unit,
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
            Text(
                text = "Swap the ${exerciseName.lowercase()}",
                style = RedplateType.title.copy(fontSize = 25.sp),
                color = colors.ink,
            )
            Spacer(Modifier.height(6.dp))

            val loggedText = if (setsLogged > 0) {
                "$setsLogged set${if (setsLogged > 1) "s" else ""} already logged \u2014 they stay. Same muscles, kit you have."
            } else {
                "Same muscles, kit you have."
            }
            Text(
                text = loggedText,
                style = RedplateType.body.copy(fontSize = 14.sp, lineHeight = 22.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(12.dp))

            // Candidate rows
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                candidates.forEach { candidate ->
                    SwapCandidateRow(
                        candidate = candidate,
                        onClick = { onSelectCandidate(candidate.exerciseId) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // Keep button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.surfaceRaised)
                    .clickable(onClick = onKeep),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Keep the ${exerciseName.lowercase()}",
                    style = RedplateType.action.copy(fontSize = 19.sp),
                    color = colors.inkSecondary,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SwapCandidateRow(
    candidate: SwapCandidate,
    onClick: () -> Unit,
) {
    val colors = RedplateTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Thumbnail placeholder (76x60)
        Box(
            modifier = Modifier
                .size(76.dp, 60.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            ExerciseImage(
                imageUri = candidate.imageUri,
                muscle = candidate.primaryMuscle,
                modifier = Modifier.size(76.dp, 60.dp),
                contentDescription = candidate.name,
            )
        }

        // Name + overlap/load
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.name,
                style = RedplateType.body.copy(fontSize = 15.sp),
                color = colors.ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${candidate.overlapPercent}% OVERLAP \u00B7 ${candidate.loadSuggestion}",
                style = RedplateType.mono.copy(fontSize = 10.5.sp),
                color = colors.inkMuted,
            )
        }

        // Chevron
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "\u203A",
                style = RedplateType.title.copy(fontSize = 22.sp),
                color = colors.inkSecondary,
            )
        }
    }
}

@Preview
@Composable
private fun SwapCandidateRowPreview() {
    RedplateTheme {
        SwapCandidateRow(
            candidate = SwapCandidate(
                exerciseId = "db_bench",
                name = "Dumbbell Bench Press",
                overlapPercent = 96,
                loadSuggestion = "40 KG PER SIDE",
                primaryMuscle = MuscleGroup.CHEST,
            ),
            onClick = {},
        )
    }
}
