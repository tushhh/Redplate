package dev.redplate.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.ui.components.MonoLabel
import dev.redplate.ui.components.SearchField
import dev.redplate.ui.components.SecondaryButton
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * Choose a lift for the program builder: to add to a session, or to replace one in it.
 *
 * A bottom sheet rather than a new screen, because it is always reached *from* a session
 * and should drop back to it. Search sits at the top of the sheet, which on this phone is
 * still in the lower half of the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickSheet(
    state: PickerState,
    onQueryChange: (String) -> Unit,
    onPick: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = RedplateTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
        ) {
            MonoLabel(text = state.title.uppercase())
            Spacer(Modifier.height(10.dp))
            SearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                placeholder = "Search ${state.options.size}",
            )
            Spacer(Modifier.height(8.dp))

            val visible = state.visible
            if (visible.isEmpty()) {
                Text(
                    text = if (state.options.isEmpty()) {
                        "Nothing else your equipment supports for this. Turn more kit on in " +
                            "You → Weights and equipment."
                    } else {
                        "Nothing matches \"${state.query.trim()}\"."
                    },
                    style = RedplateType.body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                    color = colors.inkMuted,
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(visible, key = { it.exerciseId }) { option ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surfaceRaised)
                                .clickable { onPick(option.exerciseId) }
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "Choose ${option.name}"
                                    role = Role.Button
                                }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                        ) {
                            Text(
                                text = option.name,
                                style = RedplateType.body.copy(fontSize = 15.sp),
                                color = colors.ink,
                            )
                            Text(
                                text = option.detail,
                                style = RedplateType.mono.copy(fontSize = 9.5.sp),
                                color = colors.inkMuted,
                            )
                        }
                    }
                }
            }

            if (state.swapSlotId != null) {
                Spacer(Modifier.height(8.dp))
                SecondaryButton(label = "Remove it from this session", onClick = onRemove)
            }
        }
    }
}
