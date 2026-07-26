package dev.redplate.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

enum class RedplateTab(val label: String) {
    Today("Today"),
    Plan("Plan"),
    History("History"),
    You("You"),
}

/**
 * 62dp bottom tab bar. Active tab = live orange, inactive = inkMuted.
 * Hides during active workout (set logging is full-bleed).
 */
@Composable
fun RedplateTabBar(
    selectedTab: RedplateTab,
    onTabSelected: (RedplateTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.ground)
            .navigationBarsPadding()
            .height(62.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RedplateTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onTabSelected(tab) },
            ) {
                Text(
                    text = tab.label,
                    style = RedplateType.label.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = if (selected) colors.live else colors.inkMuted,
                )
            }
        }
    }
}
