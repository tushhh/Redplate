package dev.redplate.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.redplate.data.ExerciseEntity
import dev.redplate.data.MuscleGroup
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.components.SegmentedToggle
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType
import kotlinx.coroutines.launch

// ── Route (stateful) ──────────────────────────────────────────────────────────

@Composable
fun ExercisePickerRoute(
    onExerciseSelected: (sessionId: Long, exerciseId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExercisePickerViewModel = hiltViewModel(),
) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val isFrontView by viewModel.isFrontView.collectAsStateWithLifecycle()
    val muscleVolume by viewModel.muscleVolume.collectAsStateWithLifecycle()
    val pickedMuscles by viewModel.pickedMuscles.collectAsStateWithLifecycle()
    val pickedCount by viewModel.pickedCount.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val selectedExerciseId by viewModel.selectedExerciseId.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    when (phase) {
        PickerPhase.MUSCLES -> {
            MuscleSelectionScreen(
                isFrontView = isFrontView,
                muscleVolume = muscleVolume,
                pickedMuscles = pickedMuscles,
                pickedCount = pickedCount,
                searchQuery = query,
                onMuscleSelected = viewModel::toggleMuscle,
                onToggleView = viewModel::toggleView,
                onSearchChange = viewModel::search,
                onBuildSession = { viewModel.buildSession() },
                modifier = modifier,
            )
        }
        PickerPhase.EXERCISES -> {
            ExerciseSelectionPhase(
                exercises = exercises,
                selectedExerciseId = selectedExerciseId,
                searchQuery = query,
                onExerciseSelect = viewModel::selectExercise,
                onSearchChange = viewModel::search,
                onBack = viewModel::goBackToMuscles,
                onConfirm = {
                    val id = selectedExerciseId ?: return@ExerciseSelectionPhase
                    scope.launch {
                        val sessionId = viewModel.getOrCreateSession()
                        onExerciseSelected(sessionId, id)
                    }
                },
                modifier = modifier,
            )
        }
    }
}

// ── Screen (stateless, 5a/5b design) ──────────────────────────────────────────

/**
 * Muscle group selection screen — "What are we training?"
 *
 * Full-body anatomical map with tappable muscle regions, volume shading,
 * floating callout labels, and multi-selection. Matches HTML design 5a/5b.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleSelectionScreen(
    isFrontView: Boolean,
    muscleVolume: Map<MuscleGroup, VolumeLevel>,
    pickedMuscles: Set<MuscleGroup>,
    pickedCount: Int,
    searchQuery: String,
    onMuscleSelected: (MuscleGroup) -> Unit,
    onToggleView: () -> Unit,
    onSearchChange: (String) -> Unit,
    onBuildSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
    ) {
        // ── Header: title + FRONT/BACK toggle ────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "What are we\ntraining?",
                style = RedplateType.headline.copy(
                    fontSize = 27.sp,
                    lineHeight = 30.sp,
                    letterSpacing = (-0.015).sp,
                ),
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            SegmentedToggle(
                options = listOf("FRONT", "BACK"),
                selectedIndex = if (isFrontView) 0 else 1,
                onOptionSelected = { onToggleView() },
            )
        }

        // ── Body map (center, fills available space) ─────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            BodyMapCanvas(
                isFrontView = isFrontView,
                volumeMap = muscleVolume,
                onMuscleSelected = onMuscleSelected,
                pickedMuscles = pickedMuscles,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ── Volume legend ────────────────────────────────────────────────────
        VolumeMapLegend(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )

        // ── Search field ─────────────────────────────────────────────────────
        SearchField(
            query = searchQuery,
            onQueryChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        )

        // ── Primary CTA ─────────────────────────────────────────────────────
        PrimaryBar(
            label = if (pickedCount > 0) "Build the session \u00B7 $pickedCount picked"
                    else "Nothing picked yet",
            onClick = onBuildSession,
            enabled = pickedCount > 0,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

// ── Volume map legend (4 states matching HTML 5a) ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VolumeMapLegend(modifier: Modifier = Modifier) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        modifier = modifier,
    ) {
        LegendItem(
            fillColor = null,
            dashed = true,
            label = "UNDER-TRAINED",
            textColor = Color(0xFF8B939E),
        )
        LegendItem(
            fillColor = Color(0xFF2F9BD8),
            dashed = false,
            label = "ON TARGET",
            textColor = Color(0xFF8B939E),
        )
        LegendItem(
            fillColor = null,
            dashed = false,
            label = "NEAR CAP",
            textColor = Color(0xFF8B939E),
            hatched = true,
        )
        LegendItem(
            fillColor = null,
            dashed = false,
            label = "PICKED",
            textColor = Color(0xFFF5F5F0),
            outlined = true,
        )
    }
}

@Composable
private fun LegendItem(
    fillColor: Color?,
    dashed: Boolean,
    label: String,
    textColor: Color,
    hatched: Boolean = false,
    outlined: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .height(12.dp)
                .width(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .then(
                    when {
                        outlined -> Modifier
                            .background(Color(0xFF101317))
                            .border(3.dp, Color(0xFFF5F5F0), RoundedCornerShape(3.dp))
                        fillColor != null -> Modifier.background(fillColor)
                        hatched -> Modifier.background(Color(0xFFFFD100).copy(alpha = 0.6f))
                        dashed -> Modifier
                            .background(Color.Transparent)
                            .border(
                                width = 1.5.dp,
                                color = Color(0xFF6B737D),
                                shape = RoundedCornerShape(3.dp),
                            )
                        else -> Modifier
                    }
                ),
        )
        Text(
            text = label,
            style = RedplateType.mono.copy(fontSize = 12.sp, letterSpacing = 0.04.sp),
            color = textColor,
        )
    }
}

// ── Search field ─────────────────────────────────────────────────────────────

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "\u2315",
                style = RedplateType.data.copy(fontSize = 13.sp),
                color = colors.inkMuted,
            )
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Or search an exercise",
                        style = RedplateType.body.copy(fontSize = 14.5.sp),
                        color = colors.inkMuted,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = RedplateType.body.copy(fontSize = 14.5.sp, color = colors.ink),
                    cursorBrush = SolidColor(colors.live),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── Exercise selection phase (after muscles are picked) ──────────────────────

@Composable
private fun ExerciseSelectionPhase(
    exercises: List<ExerciseEntity>,
    selectedExerciseId: String?,
    searchQuery: String,
    onExerciseSelect: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    val selected = exercises.find { it.id == selectedExerciseId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
    ) {
        // Header with back
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Pick an\nexercise",
                    style = RedplateType.headline.copy(
                        fontSize = 27.sp,
                        lineHeight = 30.sp,
                        letterSpacing = (-0.015).sp,
                    ),
                    color = colors.ink,
                )
            }
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "← BACK",
                    style = RedplateType.mono.copy(fontSize = 12.sp),
                    color = colors.inkMuted,
                )
            }
        }

        // Exercise list
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (exercises.isEmpty()) {
                Spacer(Modifier.height(32.dp))
                Text(
                    text = "No exercises found for these muscles with your equipment.",
                    style = RedplateType.body.copy(fontSize = 14.5.sp),
                    color = colors.inkMuted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            exercises.forEach { exercise ->
                val isSelected = exercise.id == selectedExerciseId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) colors.surface else colors.ground)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) colors.ink else colors.line,
                            shape = RoundedCornerShape(16.dp),
                        )
                        .clickable { onExerciseSelect(exercise.id) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = exercise.name,
                            style = RedplateType.body.copy(
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.ink,
                        )
                        Text(
                            text = exercise.primaryMuscle.name.replace("_", " ") +
                                " · " + exercise.pattern.name.replace("_", " "),
                            style = RedplateType.mono.copy(fontSize = 10.sp),
                            color = colors.inkMuted,
                        )
                    }
                    if (isSelected) {
                        Text("✓", color = colors.ink, fontSize = 18.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Search
        SearchField(
            query = searchQuery,
            onQueryChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        )

        // CTA
        PrimaryBar(
            label = if (selected != null) "Start with ${selected.name}" else "Select an exercise",
            onClick = onConfirm,
            enabled = selectedExerciseId != null,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF101317, widthDp = 384, heightDp = 824)
@Composable
private fun MuscleSelectionPreview() {
    RedplateTheme {
        MuscleSelectionScreen(
            isFrontView = true,
            muscleVolume = mapOf(
                MuscleGroup.CHEST to VolumeLevel.APPROACHING_MRV,
                MuscleGroup.BICEPS to VolumeLevel.BELOW_MEV,
                MuscleGroup.FRONT_DELTS to VolumeLevel.MEV_TO_MAV,
                MuscleGroup.ABS to VolumeLevel.MEV_TO_MAV,
                MuscleGroup.QUADS to VolumeLevel.BELOW_MEV,
            ),
            pickedMuscles = setOf(MuscleGroup.CHEST, MuscleGroup.BICEPS),
            pickedCount = 2,
            searchQuery = "",
            onMuscleSelected = {},
            onToggleView = {},
            onSearchChange = {},
            onBuildSession = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF101317, widthDp = 384, heightDp = 824)
@Composable
private fun MuscleSelectionBackPreview() {
    RedplateTheme {
        MuscleSelectionScreen(
            isFrontView = false,
            muscleVolume = mapOf(
                MuscleGroup.TRAPS to VolumeLevel.APPROACHING_MRV,
                MuscleGroup.LATS to VolumeLevel.MEV_TO_MAV,
                MuscleGroup.GLUTES to VolumeLevel.MEV_TO_MAV,
                MuscleGroup.CALVES to VolumeLevel.MEV_TO_MAV,
            ),
            pickedMuscles = emptySet(),
            pickedCount = 0,
            searchQuery = "",
            onMuscleSelected = {},
            onToggleView = {},
            onSearchChange = {},
            onBuildSession = {},
        )
    }
}
