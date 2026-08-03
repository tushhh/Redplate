package dev.redplate.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.data.EquipmentCategory
import dev.redplate.data.EquipmentEntity
import dev.redplate.data.LoadingScheme
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.components.SearchField
import dev.redplate.ui.theme.PlexCondensed
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * Intake, question three — design 2e.
 *
 * The hardest form in the app, so it is the one that gets a category filter and a search
 * field. Ticking a fixed-increment item opens the one follow-up that stops the engine
 * prescribing a 6 kg dumbbell nobody owns; every other row stays a single tap.
 */
@Composable
fun EquipmentScreen(
    equipment: List<EquipmentEntity>,
    totalCount: Int,
    selectedIds: Set<String>,
    selectedCount: Int,
    equipmentFilter: EquipmentFilter,
    dumbbellStep: DumbbellStep,
    searchQuery: String,
    onToggleEquipment: (String) -> Unit,
    onSetFilter: (EquipmentFilter) -> Unit,
    onSetDumbbellStep: (DumbbellStep) -> Unit,
    onSearchChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    val colors = RedplateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
    ) {
        IntakeProgressBar(currentStep = 3)

        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
            Text(
                text = "What can you actually get to?",
                style = RedplateType.headline.copy(fontSize = 32.sp, lineHeight = 36.sp),
                color = colors.ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Anything you don’t tick is hidden.",
                style = RedplateType.body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = colors.inkMuted,
            )
        }
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EquipmentFilter.entries.forEach { filter ->
                CategoryChip(
                    label = if (filter == EquipmentFilter.ALL) "ALL $totalCount" else filter.label,
                    selected = equipmentFilter == filter,
                    onClick = { onSetFilter(filter) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (equipment.isEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = if (searchQuery.isBlank()) {
                        "Nothing in this category. Try ALL."
                    } else {
                        "No kit matches “$searchQuery”. Clear the search to see everything."
                    },
                    style = RedplateType.body.copy(fontSize = 14.5.sp, lineHeight = 22.sp),
                    color = colors.inkMuted,
                )
            }

            equipment.forEach { eq ->
                val ticked = eq.id in selectedIds
                EquipmentRow(
                    equipment = eq,
                    ticked = ticked,
                    // Only fixed-increment kit has a rack to describe. Plate-loaded gear
                    // already states its own increment on the row.
                    askLoading = ticked &&
                        eq.category == EquipmentCategory.DUMBBELL &&
                        eq.loadingScheme == LoadingScheme.FIXED_INCREMENT,
                    dumbbellStep = dumbbellStep,
                    onToggle = { onToggleEquipment(eq.id) },
                    onSetDumbbellStep = onSetDumbbellStep,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        SearchField(
            query = searchQuery,
            onQueryChange = onSearchChange,
            placeholder = "Search equipment",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        )

        PrimaryBar(
            label = if (selectedCount > 0) "Next · $selectedCount ticked" else "Tick what you have",
            onClick = onNext,
            enabled = selectedCount > 0,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun CategoryChip(
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
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
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
        )
    }
}

/**
 * One inventory row. It raises to the surfaceRaised tone while its follow-up is open, so
 * the sub-question reads as part of the same answer rather than a new section.
 */
@Composable
private fun EquipmentRow(
    equipment: EquipmentEntity,
    ticked: Boolean,
    askLoading: Boolean,
    dumbbellStep: DumbbellStep,
    onToggle: () -> Unit,
    onSetDumbbellStep: (DumbbellStep) -> Unit,
) {
    val colors = RedplateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (askLoading) colors.surfaceRaised else colors.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .toggleable(value = ticked, role = Role.Checkbox) { onToggle() }
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = equipment.displayName,
                    style = RedplateType.body.copy(fontSize = 15.sp),
                    color = colors.ink,
                )
                val detail = if (askLoading) {
                    "ONE QUESTION — WHICH ONES?"
                } else {
                    describeLoading(equipment)
                }
                if (detail.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = detail,
                        style = RedplateType.mono.copy(fontSize = 10.5.sp),
                        color = if (askLoading) colors.live else colors.inkMuted,
                    )
                }
            }
            Spacer(Modifier.size(14.dp))
            Tick(ticked)
        }

        if (askLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                DumbbellStep.entries.forEach { step ->
                    val selected = dumbbellStep == step
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) colors.ink else colors.surface)
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                            ) { onSetDumbbellStep(step) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = step.label,
                            style = RedplateType.body.copy(
                                fontFamily = PlexCondensed,
                                fontSize = 16.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            color = if (selected) colors.inkOnLight else colors.inkMuted,
                        )
                        Text(
                            text = step.caption,
                            style = RedplateType.body.copy(fontSize = 9.5.sp),
                            color = if (selected) colors.inkOnLightMuted else colors.inkMuted,
                        )
                    }
                }
            }
            Text(
                text = describeLadder(dumbbellStep.ladder(equipment.availableLoads)),
                style = RedplateType.body.copy(fontSize = 12.5.sp, lineHeight = 19.sp),
                color = colors.inkMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun Tick(ticked: Boolean) {
    val colors = RedplateTheme.colors
    if (ticked) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(colors.live),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✓",
                style = RedplateType.body.copy(fontSize = 14.sp),
                color = colors.inkOnLight,
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .border(1.5.dp, colors.handle, CircleShape),
        )
    }
}

/** What this equipment can physically make, stated on the row so ticking is informed. */
private fun describeLoading(eq: EquipmentEntity): String {
    val parts = mutableListOf<String>()
    eq.barWeightKg?.let { parts += "${formatKg(it)} KG BAR" }
    if (eq.platePairs.isNotEmpty()) {
        val min = eq.platePairs.keys.minOrNull()
        val max = eq.platePairs.keys.maxOrNull()
        if (min != null && max != null) parts += "${formatKg(min)} → ${formatKg(max)} KG PLATES"
    }
    when (eq.loadingScheme) {
        LoadingScheme.PIN_STACK -> describeStack(eq.availableLoads)?.let { parts += it }

        LoadingScheme.FIXED_INCREMENT ->
            eq.availableLoads.maxOrNull()?.let { parts += "UP TO ${formatKg(it)} KG" }

        // Numbered levels, no mass printed on the machine — so there is nothing in
        // kilograms to describe here.
        LoadingScheme.RESISTANCE_LEVEL -> parts += "RESISTANCE LEVELS"
        LoadingScheme.BODYWEIGHT -> parts += "BODYWEIGHT"
        LoadingScheme.BANDED -> parts += "BANDS"
        LoadingScheme.PLATE_LOADED -> Unit
    }
    return parts.joinToString(" · ")
}

/** The consequence of the follow-up: the exact ladder progressions may step along. */
private fun describeLadder(ladder: List<Double>): String {
    val floor = ladder.minOrNull()
    val ceiling = ladder.maxOrNull()
    val step = stepOf(ladder)
    if (floor == null || ceiling == null || step == null) {
        return "The rack exactly as it is. Progressions will only ever suggest weights " +
            "that exist on it."
    }
    return "${formatKg(floor)} to ${formatKg(ceiling)} kg, in ${formatKg(step)} kg steps. " +
        "Progressions will only ever suggest weights on that list."
}

private fun stepOf(loads: List<Double>): Double? =
    loads.sorted().zipWithNext { a, b -> b - a }.minOrNull()

/**
 * A stack whose pins are evenly spaced can be described by that spacing. Plenty are not —
 * the multi-gym's row and pulldown go up in 7.5 kg to 50 and 10 kg after that — and calling
 * those "7.5 KG PINS" states a spacing the machine does not have. Those report their range
 * instead, which is true of any stack.
 */
private fun describeStack(loads: List<Double>): String? {
    val sorted = loads.sorted()
    val gaps = sorted.zipWithNext { a, b -> b - a }
    val step = gaps.minOrNull() ?: return null
    val uniform = gaps.all { kotlin.math.abs(it - step) < 1e-6 }
    return if (uniform) "${formatKg(step)} KG PINS"
    else "${formatKg(sorted.first())} → ${formatKg(sorted.last())} KG STACK"
}

private fun formatKg(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toInt().toString() else "%.2f".format(kg).trimEnd('0').trimEnd('.')

private val previewEquipment = listOf(
    EquipmentEntity(
        id = "barbell", displayName = "Barbell + plates",
        category = EquipmentCategory.BARBELL, loadingScheme = LoadingScheme.PLATE_LOADED,
        barWeightKg = 20.0, platePairs = mapOf(25.0 to 2, 20.0 to 2, 1.25 to 2),
    ),
    EquipmentEntity(
        id = "dumbbells", displayName = "Dumbbells",
        category = EquipmentCategory.DUMBBELL, loadingScheme = LoadingScheme.FIXED_INCREMENT,
        availableLoads = generateSequence(10.0) { it + 2.0 }.takeWhile { it <= 40.0 }.toList(),
    ),
    EquipmentEntity(
        id = "cable", displayName = "Cable stack",
        category = EquipmentCategory.CABLE, loadingScheme = LoadingScheme.PIN_STACK,
        availableLoads = (1..20).map { it * 5.0 },
    ),
)

@Preview(name = "2e · equipment", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun EquipmentScreenPreview() {
    RedplateTheme {
        EquipmentScreen(
            equipment = previewEquipment,
            totalCount = 14,
            selectedIds = setOf("barbell", "dumbbells", "cable"),
            selectedCount = 3,
            equipmentFilter = EquipmentFilter.ALL,
            dumbbellStep = DumbbellStep.TWO_POINT_FIVE,
            searchQuery = "",
            onToggleEquipment = {},
            onSetFilter = {},
            onSetDumbbellStep = {},
            onSearchChange = {},
            onNext = {},
        )
    }
}

@Preview(name = "2e · nothing ticked", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun EquipmentScreenEmptyPreview() {
    RedplateTheme {
        EquipmentScreen(
            equipment = previewEquipment,
            totalCount = 14,
            selectedIds = emptySet(),
            selectedCount = 0,
            equipmentFilter = EquipmentFilter.ALL,
            dumbbellStep = DumbbellStep.TWO_POINT_FIVE,
            searchQuery = "",
            onToggleEquipment = {},
            onSetFilter = {},
            onSetDumbbellStep = {},
            onSearchChange = {},
            onNext = {},
        )
    }
}
