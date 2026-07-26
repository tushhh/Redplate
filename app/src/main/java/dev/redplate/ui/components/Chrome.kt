package dev.redplate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.redplate.ui.theme.PlexCondensed
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

/**
 * Screen-level chrome shared by the revamped screens. Each piece exists because the
 * design uses the same shape in three or more places; keeping them here is what stops
 * the app drifting into five slightly different section headings.
 */

/** Mono heading above a group of rows: "GETS THE NUMBERS WRONG IF WRONG". */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = RedplateTheme.colors.inkMuted,
) {
    Text(
        text = text.uppercase(),
        style = RedplateType.mono.copy(fontSize = 9.5.sp, letterSpacing = 0.14.em),
        color = color,
        modifier = modifier,
    )
}

/**
 * Back chevron, centred title, optional trailing action. The chevron is a real 64dp
 * target — the system gesture duplicates it rather than replacing it.
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = RedplateTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clickable(onClick = onBack)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Back"
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "‹",
                style = RedplateType.load.copy(fontSize = 32.sp),
                color = colors.inkMuted,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = RedplateType.exerciseName.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = colors.ink,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = RedplateType.mono.copy(fontSize = 10.sp, letterSpacing = 0.12.em),
                    color = colors.inkMuted,
                )
            }
        }

        if (trailing != null) trailing() else Spacer(Modifier.width(64.dp))
    }
}

/**
 * A settings row that states its consequence.
 *
 * Design 9a: "every row says what it changes about the training". [detail] is that
 * sentence — not a description of the control, but of what gets worse if it's wrong.
 */
@Composable
fun ConsequenceRow(
    label: String,
    detail: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    value: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = RedplateTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = RedplateType.body.copy(fontSize = 14.5.sp),
                color = colors.ink,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = detail,
                style = RedplateType.body.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = colors.inkMuted,
            )
        }

        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                style = RedplateType.mono.copy(fontSize = 11.sp),
                color = colors.inkBright,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        } else if (onClick != null) {
            Spacer(Modifier.width(12.dp))
            Chevron()
        }
    }
}

/** The disclosure chevron used on every row that opens something. */
@Composable
fun Chevron(color: Color = RedplateTheme.colors.inkMuted) {
    Text(
        text = "›",
        style = RedplateType.title.copy(fontFamily = PlexCondensed, fontSize = 22.sp),
        color = color,
    )
}

/** 50×30 pill switch, live when on (9a deload prompts, 9b nightly backup). */
@Composable
fun PillToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = RedplateTheme.colors
    Box(
        modifier = modifier
            .size(width = 50.dp, height = 30.dp)
            .clip(CircleShape)
            .background(if (checked) colors.live else colors.surfaceRaised)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) colors.inkOnLight else colors.inkMuted),
        )
    }
}

/** Mono label over a large condensed figure. Session summary and history stats. */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    valueColor: Color = RedplateTheme.colors.ink,
    suffix: String? = null,
) {
    val colors = RedplateTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        SectionLabel(text = label)
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = RedplateType.title.copy(fontSize = 25.sp, lineHeight = 28.sp),
                color = valueColor,
            )
            if (suffix != null) {
                Text(
                    text = suffix,
                    style = RedplateType.body.copy(fontSize = 14.sp),
                    color = colors.inkMuted,
                )
            }
        }
        if (caption != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = caption,
                style = RedplateType.mono.copy(fontSize = 10.5.sp),
                color = colors.inkSubtle,
            )
        }
    }
}

/**
 * The quiet "i" note. Sits on a sunken panel so it reads as an aside rather than
 * another card competing with the content above it.
 */
@Composable
fun InfoNote(
    text: String,
    modifier: Modifier = Modifier,
    marker: String = "i",
    markerColor: Color = RedplateTheme.colors.live,
    onClick: (() -> Unit)? = null,
    actionLabel: String? = null,
) {
    val colors = RedplateTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceSunken)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = marker,
            style = RedplateType.mono.copy(fontSize = 11.sp),
            color = markerColor,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = text,
                style = RedplateType.body.copy(fontSize = 12.5.sp, lineHeight = 19.sp),
                color = colors.inkSecondary,
            )
            if (actionLabel != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = actionLabel,
                    style = RedplateType.body.copy(fontSize = 12.5.sp),
                    color = colors.inkBright,
                )
            }
        }
        if (onClick != null) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(colors.surface),
                contentAlignment = Alignment.Center,
            ) { Chevron(colors.inkSecondary) }
        }
    }
}

/** Bordered card used for the deload explainer and the uninstall warning. */
@Composable
fun BorderedCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, RedplateTheme.colors.line, RoundedCornerShape(18.dp))
            .padding(horizontal = 15.dp, vertical = 13.dp),
    ) { content() }
}

/** Sheet grab handle. Purely decorative — sheets always have a tappable close too. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(44.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(RedplateTheme.colors.handle),
    )
}

// ── Previews ────────────────────────────────────────────────────────

@Preview(widthDp = 384, backgroundColor = 0xFF101317, showBackground = true)
@Composable
private fun ChromePreview() {
    RedplateTheme {
        Column(
            modifier = Modifier
                .background(RedplateTheme.colors.ground)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScreenHeader(title = "Backup & export", onBack = {})
            SectionLabel("Gets the numbers wrong if wrong")
            ConsequenceRow(
                label = "Plates in your gym",
                detail = "Sets what the stack can round to",
                value = "20·15·10·5·2.5·1.25",
                onClick = {},
            )
            ConsequenceRow(
                label = "Deload prompts",
                detail = "Flags a stall after 3 flat weeks",
                onClick = null,
                trailing = { PillToggle(true, {}, "Deload prompts") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(label = "Sets", value = "20", modifier = Modifier.weight(1f))
                StatCard(
                    label = "Lifted",
                    value = "8.6",
                    suffix = " t",
                    modifier = Modifier.weight(1f),
                )
            }
            InfoNote("Either way the app tracks sets per muscle per week.")
            SheetHandle()
        }
    }
}
