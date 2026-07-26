package dev.redplate.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.data.MuscleGroup
import dev.redplate.ui.components.MovementWindow
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.components.SearchField
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

// ── State ────────────────────────────────────────────────────────────────────

/** One card in the browser grid. */
data class BrowserExercise(
    val id: String,
    val name: String,
    /** "DUMBBELL · 34 SETS" — kit first, then how much you have actually done it. */
    val tag: String,
    val primaryMuscle: MuscleGroup,
    val startImageUri: String? = null,
    val endImageUri: String? = null,
    /** False when the required kit is unticked. Shown dimmed rather than hidden. */
    val isAvailable: Boolean = true,
)

/** A titled run of cards: "IN THIS SESSION", "YOU TRAIN THESE MOST", "EVERYTHING ELSE". */
data class BrowserSection(val label: String, val items: List<BrowserExercise>)

/** The three bottom filters. [MUSCLE] carries the name of the muscle in play. */
enum class BrowseFilter { MY_KIT, MUSCLE, COMPOUND }

data class BrowserState(
    val title: String = "Add an exercise",
    val subtitle: String = "",
    val sections: List<BrowserSection> = emptyList(),
    val muscleFilterLabel: String = "MUSCLE",
    val activeFilters: Set<BrowseFilter> = emptySet(),
    val archiveSize: Int = 0,
    val selectedId: String? = null,
    val selectedName: String? = null,
    /** "Add" when appending, "Swap in" when replacing, "Start with" when freestyling. */
    val confirmVerb: String = "Add",
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = sections.all { it.items.isEmpty() }
}

// ── Screen ───────────────────────────────────────────────────────────────────

/**
 * The exercise browser — design 8c.
 *
 * Tiered rather than alphabetical: what is already in this session, then what you
 * actually train, then the archive. The cards animate their start and end positions on
 * staggered loops, which is what makes a grid scannable without reading a single name —
 * and a lift with only one still simply sits there, which the layout has to survive.
 *
 * Filters and search sit at the bottom with the primary action, inside the thumb arc.
 */
@Composable
fun ExerciseBrowserScreen(
    state: BrowserState,
    searchQuery: String,
    onSelect: (String) -> Unit,
    onToggleFilter: (BrowseFilter) -> Unit,
    onSearchChange: (String) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    style = RedplateType.headline.copy(fontSize = 26.sp, lineHeight = 29.sp),
                    color = colors.ink,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = state.subtitle,
                    style = RedplateType.body.copy(fontSize = 13.sp),
                    color = colors.inkMuted,
                )
            }
            // Not drawn in 8c, which leans on the system gesture. CLAUDE.md §4 bans
            // gesture-only actions and explicitly allows a back affordance up here, so
            // it stays: leaving this screen must not require knowing an edge swipe.
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 16.dp)
                    .semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "← BACK",
                    style = RedplateType.mono.copy(fontSize = 12.sp),
                    color = colors.inkMuted,
                )
            }
        }

        if (state.isEmpty && !state.isLoading) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            ) {
                Text(
                    text = emptyMessage(searchQuery, state.activeFilters),
                    style = RedplateType.body.copy(fontSize = 14.5.sp, lineHeight = 22.sp),
                    color = colors.inkMuted,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                state.sections.forEachIndexed { sectionIndex, section ->
                    if (section.items.isEmpty()) return@forEachIndexed

                    item(
                        key = "header-${section.label}",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Text(
                            text = section.label,
                            style = RedplateType.mono.copy(
                                fontSize = 10.sp,
                                letterSpacing = 0.14.sp,
                            ),
                            color = colors.inkMuted,
                            modifier = Modifier.padding(
                                top = if (sectionIndex == 0) 0.dp else 3.dp,
                                bottom = 0.dp,
                            ),
                        )
                    }

                    itemsIndexed(
                        section.items,
                        key = { _, item -> "${section.label}-${item.id}" },
                    ) { index, exercise ->
                        BrowserCard(
                            exercise = exercise,
                            selected = exercise.id == state.selectedId,
                            // Staggered so the grid never pulses in unison.
                            phaseOffsetMillis = (index % STAGGER_STEPS) * STAGGER_MILLIS,
                            onClick = { onSelect(exercise.id) },
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                label = "MY KIT",
                selected = BrowseFilter.MY_KIT in state.activeFilters,
                onClick = { onToggleFilter(BrowseFilter.MY_KIT) },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                label = state.muscleFilterLabel,
                selected = BrowseFilter.MUSCLE in state.activeFilters,
                onClick = { onToggleFilter(BrowseFilter.MUSCLE) },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                label = "COMPOUND",
                selected = BrowseFilter.COMPOUND in state.activeFilters,
                onClick = { onToggleFilter(BrowseFilter.COMPOUND) },
                modifier = Modifier.weight(1f),
            )
        }

        SearchField(
            query = searchQuery,
            onQueryChange = onSearchChange,
            placeholder = "Search all ${state.archiveSize}",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        )

        PrimaryBar(
            label = state.selectedName
                ?.let { "${state.confirmVerb} $it" }
                ?: "Pick one to continue",
            onClick = onConfirm,
            enabled = state.selectedId != null,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

private fun emptyMessage(query: String, filters: Set<BrowseFilter>): String = when {
    query.isNotBlank() -> "Nothing in the archive matches “$query”. Try fewer letters."
    filters.isNotEmpty() -> "No exercise clears every filter. Turn one off to widen it."
    else -> "No exercises yet. Restore a backup from You → Backup to bring the archive back."
}

// ── Card ─────────────────────────────────────────────────────────────────────

/**
 * A browser card. Selected takes a 3 dp ink frame and a tick, which reads at arm's
 * length; unavailable kit sinks a tone rather than disappearing, so the archive stays
 * honest about what exists in the world versus what exists in your gym.
 */
@Composable
private fun BrowserCard(
    exercise: BrowserExercise,
    selected: Boolean,
    phaseOffsetMillis: Int,
    onClick: () -> Unit,
) {
    val colors = RedplateTheme.colors
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (exercise.isAvailable) colors.surface else colors.surfaceSunken)
            .then(if (selected) Modifier.border(3.dp, colors.ink, shape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = buildString {
                    append(exercise.name)
                    append(", ")
                    append(exercise.tag.lowercase())
                    if (!exercise.isAvailable) append(", kit not ticked")
                }
            },
    ) {
        Column {
            MovementWindow(
                startImageUri = exercise.startImageUri,
                endImageUri = exercise.endImageUri,
                muscle = exercise.primaryMuscle,
                phaseOffsetMillis = phaseOffsetMillis,
                // The badge belongs on the big window in guidance, not on a 175 dp card
                // where it would cover the movement it is crediting.
                attribution = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(CARD_IMAGE_ASPECT),
            )
            Column(
                modifier = Modifier.padding(start = 11.dp, end = 11.dp, top = 9.dp, bottom = 11.dp),
            ) {
                Text(
                    text = exercise.name,
                    style = RedplateType.body.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 17.5.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (exercise.isAvailable) colors.ink else colors.inkSubtle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = exercise.tag,
                    style = RedplateType.mono.copy(fontSize = 10.sp),
                    color = colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(colors.ink),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✓",
                    style = RedplateType.body.copy(fontSize = 14.sp),
                    color = colors.inkOnLight,
                )
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.ink else colors.surface)
            .toggleable(value = selected, role = Role.Checkbox) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = RedplateType.mono.copy(
                fontSize = 11.sp,
                letterSpacing = 0.06.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            ),
            color = if (selected) colors.inkOnLight else colors.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val COLUMNS = 2
private const val CARD_IMAGE_ASPECT = 175f / 131f
private const val STAGGER_STEPS = 4
private const val STAGGER_MILLIS = 400

// ── Previews ─────────────────────────────────────────────────────────────────

private val previewState = BrowserState(
    title = "Add to Upper A",
    subtitle = "Yours first · 873 in the archive",
    sections = listOf(
        BrowserSection(
            label = "IN THIS SESSION",
            items = listOf(
                BrowserExercise("bb_bench", "Barbell Bench Press", "BARBELL", MuscleGroup.CHEST),
                BrowserExercise("bb_row", "Barbell Row", "BARBELL", MuscleGroup.UPPER_BACK),
            ),
        ),
        BrowserSection(
            label = "YOU TRAIN THESE MOST",
            items = listOf(
                BrowserExercise(
                    "db_incline_bench", "Incline Dumbbell Press",
                    "DUMBBELL · 34 SETS", MuscleGroup.CHEST,
                ),
                BrowserExercise(
                    "pec_deck", "Pec Deck", "NO KIT TICKED",
                    MuscleGroup.CHEST, isAvailable = false,
                ),
            ),
        ),
    ),
    muscleFilterLabel = "CHEST",
    activeFilters = setOf(BrowseFilter.MY_KIT),
    archiveSize = 873,
    selectedId = "db_incline_bench",
    selectedName = "Incline Dumbbell Press",
    confirmVerb = "Add",
    isLoading = false,
)

@Preview(name = "8c · browser", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun ExerciseBrowserPreview() {
    RedplateTheme {
        ExerciseBrowserScreen(
            state = previewState,
            searchQuery = "",
            onSelect = {},
            onToggleFilter = {},
            onSearchChange = {},
            onBack = {},
            onConfirm = {},
        )
    }
}

@Preview(name = "8c · nothing matches", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun ExerciseBrowserEmptyPreview() {
    RedplateTheme {
        ExerciseBrowserScreen(
            state = previewState.copy(sections = emptyList(), selectedId = null, selectedName = null),
            searchQuery = "zercher",
            onSelect = {},
            onToggleFilter = {},
            onSearchChange = {},
            onBack = {},
            onConfirm = {},
        )
    }
}
