package dev.redplate.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.redplate.data.ExerciseEntity
import dev.redplate.data.MuscleGroup
import dev.redplate.ui.components.MuscleGlyphPlaceholder
import dev.redplate.ui.theme.RedplateTheme
import dev.redplate.ui.theme.RedplateType

// ── Exercise image with placeholder fallback ─────────────────────────────────

/**
 * Loads an exercise image via Coil. Falls back to the hatched
 * muscle-glyph placeholder when no image URI is available.
 *
 * @param imageUri  Asset URI from [MediaResolver] (e.g. "file:///android_asset/..."), or null.
 * @param muscle    Used for the fallback placeholder glyph.
 */
@Composable
fun ExerciseImage(
    imageUri: String?,
    muscle: MuscleGroup,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    if (imageUri != null) {
        AsyncImage(
            model = imageUri,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.background(Color(0xFFEDEEE9)),
        )
    } else {
        MuscleGlyphPlaceholder(muscle = muscle, modifier = modifier)
    }
}

// ── Exercise card (browser style, 8c design) ─────────────────────────────────

/**
 * Exercise card for the 2-column browser grid. Matches design 8c:
 * - Image area (131px height) with EDEEE9 background
 * - Name below in 13.5sp
 * - Equipment tag in mono 10sp
 * - Selected: white border + checkmark overlay
 * - Unavailable (no equipment): dimmed text
 */
@Composable
fun ExerciseCard(
    exercise: ExerciseEntity,
    isSelected: Boolean = false,
    isAvailable: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageUri: String? = null,
) {
    val colors = RedplateTheme.colors
    val cardBg = if (isAvailable) colors.surface else Color(0xFF171B21)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (isSelected) Modifier.border(3.dp, colors.ink, RoundedCornerShape(16.dp))
                else Modifier
            )
            .background(cardBg)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = buildString {
                    append(exercise.name)
                    if (isSelected) append(", selected")
                    if (!isAvailable) append(", no equipment")
                }
                role = Role.Button
            },
    ) {
        Column {
            // Image area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(131f / 160f),
            ) {
                ExerciseImage(
                    imageUri = imageUri,
                    muscle = exercise.primaryMuscle,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = exercise.name,
                )
            }

            // Name + equipment tag
            Column(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = exercise.name,
                    style = RedplateType.body.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 18.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (isAvailable) colors.ink else colors.inkSubtle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = exercise.primaryMuscle.displayName.uppercase(),
                    style = RedplateType.mono.copy(fontSize = 10.sp),
                    color = colors.inkMuted,
                )
            }
        }

        // Selected checkmark overlay
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(colors.ink),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "\u2713",
                    style = RedplateType.body.copy(fontSize = 14.sp),
                    color = colors.inkOnLight,
                )
            }
        }
    }
}

// displayName is defined in BodyMapData.kt as a file-level extension on MuscleGroup.
