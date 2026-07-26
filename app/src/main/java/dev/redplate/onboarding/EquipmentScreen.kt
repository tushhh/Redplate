package dev.redplate.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.data.EquipmentCategory
import dev.redplate.data.EquipmentEntity
import dev.redplate.data.LoadingScheme
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

@Composable
fun EquipmentScreen(
    equipment: List<EquipmentEntity>,
    selectedIds: Set<String>,
    selectedCount: Int,
    equipmentFilter: EquipmentFilter,
    dumbbellStep: DumbbellStep,
    onToggleEquipment: (String) -> Unit,
    onSetFilter: (EquipmentFilter) -> Unit,
    onSetDumbbellStep: (DumbbellStep) -> Unit,
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

        Column(
            modifier = Modifier.padding(horizontal = 22.dp),
        ) {
            Text(
                text = "What can you actually get to?",
                style = RedplateType.headline.copy(fontSize = 32.sp),
                color = colors.ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Anything you don\u2019t tick is hidden.",
                style = RedplateType.body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = colors.inkMuted,
            )
        }
        Spacer(Modifier.height(12.dp))

        // Category filter tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EquipmentFilter.entries.forEach { filter ->
                val label = if (filter == EquipmentFilter.ALL) "ALL ${equipment.size}" else filter.label
                FilterChip(
                    label = label,
                    isSelected = equipmentFilter == filter,
                    onClick = { onSetFilter(filter) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // Equipment list
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            equipment.forEach { eq ->
                val isSelected = eq.id in selectedIds
                val hasDumbbellSubQuestion = isSelected &&
                    eq.category == EquipmentCategory.DUMBBELL &&
                    eq.loadingScheme == LoadingScheme.FIXED_INCREMENT

                EquipmentRow(
                    name = eq.displayName,
                    subtitle = buildEquipmentSubtitle(eq),
                    isSelected = isSelected,
                    showDumbbellStep = hasDumbbellSubQuestion,
                    dumbbellStep = dumbbellStep,
                    onClick = { onToggleEquipment(eq.id) },
                    onSetDumbbellStep = onSetDumbbellStep,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        PrimaryBar(
            label = if (selectedCount > 0) "Next \u00B7 $selectedCount ticked" else "Next",
            onClick = onNext,
            enabled = selectedCount > 0,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

private fun buildEquipmentSubtitle(eq: EquipmentEntity): String {
    val parts = mutableListOf<String>()
    if (eq.barWeightKg != null) parts.add("${eq.barWeightKg.toInt()} KG BAR")
    if (eq.platePairs.isNotEmpty()) {
        val min = eq.platePairs.keys.minOrNull() ?: 0.0
        val max = eq.platePairs.keys.maxOrNull() ?: 0.0
        parts.add("${formatKg(min)} \u2192 ${formatKg(max)} KG PLATES")
    }
    if (eq.loadingScheme == LoadingScheme.PIN_STACK) {
        val step = eq.availableLoads.zipWithNext { a, b -> b - a }.minOrNull() ?: 5.0
        parts.add("${step.toInt()} KG PINS")
    }
    return parts.joinToString(" \u00B7 ")
}

private fun formatKg(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString()

@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) colors.ink else colors.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = RedplateType.mono.copy(
                fontSize = 11.sp,
                letterSpacing = 0.06.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            ),
            color = if (isSelected) colors.inkOnLight else colors.inkMuted,
        )
    }
}

@Composable
private fun EquipmentRow(
    name: String,
    subtitle: String,
    isSelected: Boolean,
    showDumbbellStep: Boolean,
    dumbbellStep: DumbbellStep,
    onClick: () -> Unit,
    onSetDumbbellStep: (DumbbellStep) -> Unit,
) {
    val colors = RedplateTheme.colors
    val bg = if (showDumbbellStep) colors.surfaceRaised else colors.surface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 11.dp)
                .height(42.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = RedplateType.body.copy(fontSize = 15.sp),
                    color = colors.ink,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = if (showDumbbellStep) "ONE QUESTION \u2014 WHICH ONES?" else subtitle,
                        style = RedplateType.mono.copy(fontSize = 10.5.sp),
                        color = if (showDumbbellStep) colors.live else colors.inkMuted,
                    )
                }
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(colors.live),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "\u2713",
                        style = RedplateType.body.copy(fontSize = 14.sp),
                        color = colors.inkOnLight,
                    )
                }
            }
        }

        if (showDumbbellStep) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp),
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
                            .clickable { onSetDumbbellStep(step) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = step.label,
                            style = RedplateType.title.copy(fontSize = 16.sp),
                            color = if (selected) colors.inkOnLight else colors.inkMuted,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Text(
                            text = if (step == DumbbellStep.CUSTOM) "each" else "steps",
                            style = RedplateType.body.copy(fontSize = 9.5.sp),
                            color = if (selected) colors.inkOnLight.copy(alpha = 0.6f) else colors.inkMuted,
                        )
                    }
                }
            }
            Text(
                text = "Up to 40 kg, in ${dumbbellStep.label} steps. Progressions will only ever suggest weights on that list.",
                style = RedplateType.body.copy(fontSize = 12.5.sp, lineHeight = 19.sp),
                color = colors.inkMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Preview(widthDp = 384, heightDp = 824, backgroundColor = 0xFF101317, showBackground = true)
@Composable
private fun EquipmentScreenPreview() {
    RedplateTheme {
        EquipmentScreen(
            equipment = listOf(
                EquipmentEntity(
                    id = "barbell", displayName = "Barbell + plates",
                    category = EquipmentCategory.BARBELL, loadingScheme = LoadingScheme.PLATE_LOADED,
                    barWeightKg = 20.0, platePairs = mapOf(25.0 to 2, 20.0 to 2, 1.25 to 2),
                ),
                EquipmentEntity(
                    id = "dumbbell", displayName = "Dumbbells",
                    category = EquipmentCategory.DUMBBELL, loadingScheme = LoadingScheme.FIXED_INCREMENT,
                ),
                EquipmentEntity(
                    id = "cable", displayName = "Cable stack",
                    category = EquipmentCategory.CABLE, loadingScheme = LoadingScheme.PIN_STACK,
                    availableLoads = (1..20).map { it * 5.0 },
                ),
            ),
            selectedIds = setOf("barbell", "dumbbell", "cable"),
            selectedCount = 3,
            equipmentFilter = EquipmentFilter.ALL,
            dumbbellStep = DumbbellStep.TWO_POINT_FIVE,
            onToggleEquipment = {},
            onSetFilter = {},
            onSetDumbbellStep = {},
            onNext = {},
        )
    }
}
