package dev.redplate.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import dev.redplate.ui.theme.PlexCondensed
import dev.redplate.ui.theme.RedplateType

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.redplate.ui.theme.RedplateTheme

@Composable
fun IntakeProgressBar(
    currentStep: Int,
    totalSteps: Int = 5,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    val onBack = LocalIntakeBack.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = if (onBack != null) 6.dp else 22.dp, end = 22.dp)
            .padding(vertical = if (onBack != null) 0.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onBack)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Back to the previous question"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "‹",
                    style = RedplateType.title.copy(fontFamily = PlexCondensed, fontSize = 32.sp),
                    color = colors.inkMuted,
                )
            }
            Spacer(Modifier.width(6.dp))
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(totalSteps) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index < currentStep) colors.live else colors.surfaceRaised),
                )
            }
        }
    }
}
