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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.redplate.ui.components.BorderedCard
import dev.redplate.ui.components.ConsequenceRow
import dev.redplate.ui.components.ScreenHeader
import dev.redplate.ui.components.SectionLabel
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType
import dev.redplate.ui.theme.StateColor

/**
 * Route. Owns the Storage Access Framework launchers \u2014 the app never picks a location
 * itself, the user always does, so a backup lands somewhere they can find again.
 */
@Composable
fun BackupRoute(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val exportJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportJson) }

    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let(viewModel::exportCsv) }

    val import = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::import) }

    BackupScreen(
        state = state,
        onBack = onBack,
        onExportJson = { exportJson.launch(BackupViewModel.suggestedJsonName()) },
        onExportCsv = { exportCsv.launch(BackupViewModel.suggestedCsvName()) },
        onImport = { import.launch(arrayOf("application/json", "text/plain", "*/*")) },
        onDismissMessage = viewModel::consumeMessage,
    )
}

@Composable
fun BackupScreen(
    state: BackupUiState = BackupUiState(),
    onBack: () -> Unit = {},
    onExportJson: () -> Unit = {},
    onExportCsv: () -> Unit = {},
    onImport: () -> Unit = {},
    onDismissMessage: () -> Unit = {},
) {
    val colors = RedplateTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding(),
    ) {
        ScreenHeader(title = "Backup & export", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 4.dp),
        ) {
            // Stated as a date, never as "enabled". Losing the log is the only failure
            // you cannot undo, so this card says when, how many, and how big.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.surface)
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (state.hasData) colors.safe else colors.inkMuted),
                    )
                    Spacer(Modifier.width(8.dp))
                    SectionLabel(
                        text = if (state.hasData) "On this phone" else "Nothing logged yet",
                        color = if (state.hasData) colors.safe else colors.inkMuted,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = state.headline,
                    style = RedplateType.headline.copy(fontSize = 28.sp, lineHeight = 31.sp),
                    color = colors.ink,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.detail,
                    style = RedplateType.body.copy(fontSize = 14.5.sp, lineHeight = 22.sp),
                    color = colors.inkSecondary,
                )
            }
            Spacer(Modifier.height(12.dp))

            // Success or failure of the last file operation. Silence here is the
            // dangerous outcome — it lets the user believe a backup exists.
            if (state.message != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .clickable(onClick = onDismissMessage)
                        .padding(16.dp),
                ) {
                    Text(
                        text = state.message,
                        style = RedplateType.body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                        color = if (state.isError) colors.live else colors.inkSecondary,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            SectionLabel(text = "Take a copy out")
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ConsequenceRow(
                    label = "Spreadsheet (CSV)",
                    detail = "One row per set. Opens anywhere, restores nothing.",
                    onClick = onExportCsv,
                )
                ConsequenceRow(
                    label = "Full archive (JSON)",
                    detail = "Everything, including PRs and settings. Restores exactly.",
                    onClick = onExportJson,
                )
            }
            Spacer(Modifier.height(20.dp))

            SectionLabel(text = "Bring a copy in")
            Spacer(Modifier.height(8.dp))
            ConsequenceRow(
                label = "Restore from a file",
                detail = "Replaces everything. A file it can't read changes nothing.",
                onClick = onImport,
            )
            Spacer(Modifier.height(16.dp))

            BorderedCard {
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Text(
                        text = "!",
                        style = RedplateType.mono.copy(fontSize = 12.sp),
                        color = StateColor.pr,
                    )
                    Text(
                        text = "Uninstalling clears the device copy. Android Auto Backup may " +
                            "keep one, but it is not guaranteed — export the JSON archive " +
                            "before you uninstall.",
                        style = RedplateType.body.copy(fontSize = 12.5.sp, lineHeight = 19.sp),
                        color = colors.inkMuted,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Preview(name = "9b · backup", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun BackupScreenPreview() {
    RedplateTheme {
        BackupScreen(
            state = BackupUiState(
                sessionCount = 146,
                setCount = 3912,
                headline = "Last session yesterday",
                detail = "146 sessions, 3912 working sets and your equipment setup — " +
                    "about 1.4 MB. Nothing leaves this phone unless you export it.",
                hasData = true,
            ),
        )
    }
}

@Preview(name = "9b · nothing logged yet", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun BackupScreenEmptyPreview() {
    RedplateTheme {
        BackupScreen(state = BackupUiState())
    }
}

@Preview(name = "9b · restore failed", widthDp = 384, heightDp = 824, showBackground = true, backgroundColor = 0xFF101317)
@Composable
private fun BackupScreenErrorPreview() {
    RedplateTheme {
        BackupScreen(
            state = BackupUiState(
                sessionCount = 146,
                setCount = 3912,
                headline = "Last session today",
                detail = "146 sessions, 3912 working sets and your equipment setup — " +
                    "about 1.4 MB. Nothing leaves this phone unless you export it.",
                hasData = true,
                message = "That file isn't a Redplate backup. Pick the .json file written by Export.",
                isError = true,
            ),
        )
    }
}
