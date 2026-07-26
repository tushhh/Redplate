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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.redplate.ui.components.PrimaryBar
import dev.redplate.ui.components.SecondaryButton
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

@Composable
fun BackupScreen(
    onBack: () -> Unit = {},
    onExportJson: () -> Unit = {},
    onExportCsv: () -> Unit = {},
    onImport: () -> Unit = {},
    onBackupNow: () -> Unit = {},
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

            // Status card: SAFE
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surface)
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Green dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colors.safe),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "SAFE",
                            style = RedplateType.mono.copy(fontSize = 11.sp),
                            color = colors.safe,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Last backup: today",
                            style = RedplateType.body.copy(fontSize = 14.sp),
                            color = colors.inkSecondary,
                        )
                        Text(
                            text = "42 sessions \u00B7 1,284 sets",
                            style = RedplateType.mono.copy(fontSize = 10.sp),
                            color = colors.inkMuted,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Back up now button
            SecondaryButton(
                label = "Back up now",
                onClick = onBackupNow,
            )
            Spacer(Modifier.height(24.dp))

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

@Preview
@Composable
private fun BackupScreenPreview() {
    RedplateTheme {
        BackupScreen()
    }
}
