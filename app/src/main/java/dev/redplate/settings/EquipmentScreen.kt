package dev.redplate.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.redplate.data.EquipmentCategory
import dev.redplate.data.EquipmentEntity
import dev.redplate.data.LoadingScheme
import dev.redplate.ui.components.InfoNote
import dev.redplate.ui.components.PillToggle
import dev.redplate.ui.components.ScreenHeader
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

@Composable
fun EquipmentRoute(
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.equipmentState.collectAsStateWithLifecycle()
    EquipmentScreen(
        equipment = state.equipment,
        onToggle = viewModel::toggleEquipment,
        onBack = onBack,
    )
}

/**
 * The inventory after intake — the same list 2e collects, kept editable.
 *
 * Rows state what each item can physically load, because that is what the toggle
 * actually controls: untick the cable stack and every cable exercise leaves the browser
 * and the generator both.
 */
@Composable
fun EquipmentScreen(
    equipment: List<EquipmentEntity>,
    onToggle: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = RedplateTheme.colors
    val availableCount = equipment.count { it.isAvailable }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ScreenHeader(
            title = "My equipment",
            subtitle = if (equipment.isEmpty()) {
                null
            } else {
                "$availableCount OF ${equipment.size} TICKED"
            },
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Untick anything you can’t get to. Exercises that need it disappear " +
                    "from the browser and stop being prescribed.",
                style = RedplateType.body.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(4.dp))

            if (equipment.isEmpty()) {
                InfoNote(
                    text = "No equipment on file yet. Finish the intake, or restore a " +
                        "backup from You → Backup, and the list appears here.",
                )
            }

            equipment.forEach { eq ->
                EquipmentRow(equipment = eq, onToggle = { onToggle(eq.id) })
            }

            if (availableCount == 0 && equipment.isNotEmpty()) {
                InfoNote(
                    text = "Nothing is ticked, so nothing can be prescribed. Tick at least " +
                        "one item — bodyweight counts.",
                    markerColor = colors.live,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EquipmentRow(
    equipment: EquipmentEntity,
    onToggle: () -> Unit,
) {
    val colors = RedplateTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .toggleable(value = equipment.isAvailable, role = Role.Switch) { onToggle() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = equipment.displayName,
                style = RedplateType.body.copy(fontSize = 15.sp),
                color = if (equipment.isAvailable) colors.ink else colors.inkSubtle,
            )
            val detail = describeLoading(equipment)
            if (detail.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = RedplateType.mono.copy(fontSize = 10.5.sp),
                    color = colors.inkMuted,
                )
            }
        }
        Spacer(Modifier.size(14.dp))
        // The whole 64 dp row is the switch, so the pill is the readout for it rather
        // than a second control — announcing two switches per item is worse than one.
        Box(Modifier.clearAndSetSemantics {}) {
            PillToggle(
                checked = equipment.isAvailable,
                onCheckedChange = { onToggle() },
                contentDescription = equipment.displayName,
            )
        }
    }
}

/** What this equipment can physically make. Mirrors the same line on intake 2e. */
private fun describeLoading(eq: EquipmentEntity): String {
    val parts = mutableListOf<String>()
    eq.barWeightKg?.let { parts += "${formatKg(it)} KG BAR" }
    if (eq.platePairs.isNotEmpty()) {
        val min = eq.platePairs.keys.minOrNull()
        val max = eq.platePairs.keys.maxOrNull()
        if (min != null && max != null) parts += "${formatKg(min)} → ${formatKg(max)} KG PLATES"
    }
    when (eq.loadingScheme) {
        LoadingScheme.PIN_STACK ->
            stepOf(eq.availableLoads)?.let { parts += "${formatKg(it)} KG PINS" }

        LoadingScheme.FIXED_INCREMENT -> {
            val ceiling = eq.availableLoads.maxOrNull()
            val step = stepOf(eq.availableLoads)
            if (ceiling != null && step != null) {
                parts += "UP TO ${formatKg(ceiling)} KG · ${formatKg(step)} KG STEPS"
            } else if (ceiling != null) {
                parts += "UP TO ${formatKg(ceiling)} KG"
            }
        }

        LoadingScheme.BODYWEIGHT -> parts += "BODYWEIGHT"
        LoadingScheme.BANDED -> parts += "BANDS"
        LoadingScheme.PLATE_LOADED -> Unit
    }
    if (parts.isEmpty()) parts += eq.category.name.replace('_', ' ')
    return parts.joinToString(" · ")
}

private fun stepOf(loads: List<Double>): Double? =
    loads.sorted().zipWithNext { a, b -> b - a }.minOrNull()

private fun formatKg(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toInt().toString() else "%.2f".format(kg).trimEnd('0').trimEnd('.')

private val previewEquipment = listOf(
    EquipmentEntity(
        id = "barbell", displayName = "Barbell",
        category = EquipmentCategory.BARBELL, loadingScheme = LoadingScheme.PLATE_LOADED,
        barWeightKg = 20.0, platePairs = mapOf(25.0 to 2, 20.0 to 4, 1.25 to 1),
    ),
    EquipmentEntity(
        id = "dumbbells", displayName = "Dumbbells",
        category = EquipmentCategory.DUMBBELL, loadingScheme = LoadingScheme.FIXED_INCREMENT,
        availableLoads = generateSequence(10.0) { it + 2.5 }.takeWhile { it <= 40.0 }.toList(),
    ),
    EquipmentEntity(
        id = "cable", displayName = "Cable stack",
        category = EquipmentCategory.CABLE, loadingScheme = LoadingScheme.PIN_STACK,
        availableLoads = (1..20).map { it * 5.0 },
    ),
    EquipmentEntity(
        id = "rox_bands", displayName = "Resistance Bands (Rox Zone)",
        category = EquipmentCategory.BAND, loadingScheme = LoadingScheme.BANDED,
        isAvailable = false,
    ),
)

@Preview(name = "Equipment", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun EquipmentScreenPreview() {
    RedplateTheme {
        EquipmentScreen(equipment = previewEquipment, onToggle = {}, onBack = {})
    }
}

@Preview(name = "Equipment · nothing ticked", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun EquipmentScreenNoneAvailablePreview() {
    RedplateTheme {
        EquipmentScreen(
            equipment = previewEquipment.map { it.copy(isAvailable = false) },
            onToggle = {},
            onBack = {},
        )
    }
}

@Preview(name = "Equipment · empty", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun EquipmentScreenEmptyPreview() {
    RedplateTheme {
        EquipmentScreen(equipment = emptyList(), onToggle = {}, onBack = {})
    }
}
