package dev.redplate.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
 * Bottom tab bar — four words, no icons (designs 9a/10a). 64 dp tall to meet the minimum
 * touch target in CLAUDE.md §4. Active tab = live orange at semibold, inactive = inkMuted.
 * Hidden during full-bleed screens.
 *
 * Each tab fills the bar's full height and an equal share of its width, so a miss with
 * chalky hands still lands on a tab. The icons the bar used to carry were drawn in
 * Canvas at 22 dp and said nothing the words did not; the design dropped them for the
 * larger, more legible label.
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
            .height(TAB_BAR_HEIGHT)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RedplateTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Box(
                contentAlignment = Alignment.Center,
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
                Text(
                    text = tab.label,
                    style = RedplateType.body.copy(
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (selected) colors.live else colors.inkMuted,
                )
            }
        }
    }
}

/** CLAUDE.md §4's 64 dp minimum, and the height the design's text-only bar needs. */
private val TAB_BAR_HEIGHT = 64.dp
