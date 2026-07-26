package dev.redplate.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import dev.redplate.ui.components.SecondaryButton
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

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
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(24.dp))

            Text(
                text = "Backup",
                style = RedplateType.headline,
                color = colors.ink,
            )
            Spacer(Modifier.height(20.dp))

            // What is actually in the database. This card used to read
            // "SAFE \u00B7 Last backup: today \u00B7 42 sessions \u00B7 1,284 sets" as hardcoded text,
            // which told the user their data was safe regardless of whether it was.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surface)
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (state.hasData) colors.safe else colors.inkMuted),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "ON THIS PHONE",
                            style = RedplateType.mono.copy(fontSize = 11.sp),
                            color = colors.inkMuted,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.lastSessionLabel,
                            style = RedplateType.body.copy(fontSize = 14.sp),
                            color = colors.inkSecondary,
                        )
                        Text(
                            text = "${state.sessionCount} sessions \u00B7 ${state.setCount} working sets",
                            style = RedplateType.mono.copy(fontSize = 10.sp),
                            color = colors.inkMuted,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Result of the last export or restore, success or failure. Silence here is
            // the dangerous outcome: it lets the user believe a backup exists.
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
                        style = RedplateType.body.copy(fontSize = 14.sp, lineHeight = 22.sp),
                        color = if (state.isError) colors.live else colors.inkSecondary,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(12.dp))

            // Export section
            Text(
                text = "EXPORT",
                style = RedplateType.mono.copy(fontSize = 10.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportCard(
                    format = "JSON",
                    description = "Full fidelity. Can restore everything.",
                    onClick = onExportJson,
                    modifier = Modifier.weight(1f),
                )
                ExportCard(
                    format = "CSV",
                    description = "For spreadsheets. Lossy.",
                    onClick = onExportCsv,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(24.dp))

            // Import section
            Text(
                text = "IMPORT",
                style = RedplateType.mono.copy(fontSize = 10.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(10.dp))

            SecondaryButton(
                label = "Restore from a file",
                onClick = onImport,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Restoring replaces everything currently on this phone. If the file " +
                    "can't be read, nothing changes.",
                style = RedplateType.body.copy(fontSize = 13.sp, lineHeight = 20.sp),
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(20.dp))

            // Warning card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Column {
                    Text(
                        text = "IF YOU UNINSTALL",
                        style = RedplateType.mono.copy(fontSize = 10.sp),
                        color = colors.live,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Android Auto Backup keeps your data for a while, but it's not guaranteed. Export a JSON backup before uninstalling.",
                        style = RedplateType.body.copy(fontSize = 14.sp, lineHeight = 22.sp),
                        color = colors.inkSecondary,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        // Back affordance. The screen had none — it relied entirely on the system
        // gesture, which CLAUDE.md §4 allows only as a duplicate of a visible control.
        SecondaryButton(
            label = "Back",
            onClick = onBack,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun ExportCard(
    format: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(
            text = format,
            style = RedplateType.title.copy(fontSize = 18.sp),
            color = colors.ink,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = description,
            style = RedplateType.body.copy(fontSize = 13.sp, lineHeight = 20.sp),
            color = colors.inkMuted,
        )
    }
}

@Preview(name = "Backup · with history")
@Composable
private fun BackupScreenPreview() {
    RedplateTheme {
        BackupScreen(
            state = BackupUiState(
                sessionCount = 42,
                setCount = 1284,
                lastSessionLabel = "Last session: yesterday",
                hasData = true,
            ),
        )
    }
}

@Preview(name = "Backup · nothing logged yet")
@Composable
private fun BackupScreenEmptyPreview() {
    RedplateTheme {
        BackupScreen(state = BackupUiState())
    }
}

@Preview(name = "Backup · restore failed")
@Composable
private fun BackupScreenErrorPreview() {
    RedplateTheme {
        BackupScreen(
            state = BackupUiState(
                sessionCount = 42,
                setCount = 1284,
                lastSessionLabel = "Last session: today",
                hasData = true,
                message = "That file isn't a Redplate backup. Pick the .json file written by Export.",
                isError = true,
            ),
        )
    }
}
