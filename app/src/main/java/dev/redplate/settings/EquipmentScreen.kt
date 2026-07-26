package dev.redplate.settings

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.redplate.data.EquipmentEntity
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

@Composable
fun EquipmentRoute(
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.equipmentState.collectAsState()
    EquipmentScreen(
        equipment = state.equipment,
        onToggle = viewModel::toggleEquipment,
        onBack = onBack,
    )
}

@Composable
fun EquipmentScreen(
    equipment: List<EquipmentEntity>,
    onToggle: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = RedplateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "← BACK",
                    style = RedplateType.mono.copy(fontSize = 12.sp),
                    color = colors.inkMuted,
                )
            }
            Spacer(Modifier.weight(1f))
        }

        Text(
            text = "My equipment",
            style = RedplateType.title.copy(fontSize = 28.sp),
            color = colors.ink,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Toggle what's available at your gym. Exercises are filtered to match.",
            style = RedplateType.body.copy(fontSize = 13.sp),
            color = colors.inkMuted,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(16.dp))

        // Equipment list
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            equipment.forEach { eq ->
                EquipmentRow(
                    equipment = eq,
                    onToggle = { onToggle(eq.id) },
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
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = equipment.displayName,
                style = RedplateType.body.copy(fontSize = 15.sp),
                color = colors.ink,
            )
            Text(
                text = equipment.category.name.replace("_", " "),
                style = RedplateType.mono.copy(fontSize = 10.sp),
                color = colors.inkMuted,
            )
        }
        // Toggle indicator
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (equipment.isAvailable) colors.live else colors.line),
            contentAlignment = if (equipment.isAvailable) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(3.dp)
                    .size(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.ink),
            )
        }
    }
}
