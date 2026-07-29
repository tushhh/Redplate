package dev.redplate.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Bottom tab bar, 64 dp tall to meet the minimum touch target in CLAUDE.md §4.
 * Active tab = live orange, inactive = inkMuted. Hidden during full-bleed screens.
 *
 * Each tab fills the bar's full height. It previously wrapped its label, so the
 * tappable area was the ~16 dp of text rather than the bar — a miss with chalky
 * hands landed on nothing at all.
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
            .height(TAB_BAR_HEIGHT),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RedplateTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onTabSelected(tab) },
                    ),
            ) {
                val tint = if (selected) colors.live else colors.inkMuted
                when (tab) {
                    RedplateTab.Today -> TodayIcon(tint)
                    RedplateTab.Plan -> PlanIcon(tint)
                    RedplateTab.History -> HistoryIcon(tint)
                    RedplateTab.You -> YouIcon(tint)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = tab.label,
                    style = RedplateType.label.copy(
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = tint,
                )
            }
        }
    }
}

/** Still the 64 dp minimum target of CLAUDE.md §4, with room for icon over label. */
private val TAB_BAR_HEIGHT = 68.dp
