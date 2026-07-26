package dev.redplate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * The app's one search affordance — 64 dp, bottom-anchored, inside the thumb arc.
 *
 * Search sits at the *bottom* of every screen that has it (2e, 5a, 8c). The keyboard
 * comes up from below, so a top-anchored field puts what you are typing behind your own
 * hand; down here the field stays visible above the keyboard.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
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
                text = "⌕",
                style = RedplateType.data.copy(fontSize = 13.sp),
                color = colors.inkMuted,
            )
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = placeholder },
                )
            }
        }
    }
}

@Preview(widthDp = 384, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun SearchFieldPreview() {
    RedplateTheme {
        SearchField(
            query = "",
            onQueryChange = {},
            placeholder = "Search all 873",
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}
