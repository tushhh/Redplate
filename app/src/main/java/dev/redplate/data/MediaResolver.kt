package dev.redplate.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves exercise stills. Sideloaded media wins over the bundled set.
 *
 * 1. **Sideloaded** — `<externalFilesDir>/exercises/<exerciseId>_start.{jpg,png,webp}`,
 *    pushed with `adb push` and keyed by Redplate's own exercise ids. Swapping the media
 *    set is a file copy rather than a rebuild, per CLAUDE.md §2 and COACHING.md §4.
 * 2. **Bundled fallback** — the trimmed `free-exercise-db` set in `assets/exercises/`,
 *    reached through [ExerciseMediaMap] because those files keep the upstream naming.
 *
 * Missing files are normal and are not an error: the UI degrades to the muscle-group
 * placeholder, never a broken-image icon and never a spinner that resolves to nothing.
 */
@Singleton
class MediaResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Filenames in `assets/exercises/`, listed once — an asset list is a disk walk. */
    private val assetFiles: Set<String> by lazy {
        context.assets.list(ASSET_DIR)?.toHashSet() ?: emptySet()
    }

    /** Where `adb push` puts an overriding media set. Null when no external storage is mounted. */
    private val sideloadDir: File? by lazy {
        context.getExternalFilesDir(null)?.let { File(it, ASSET_DIR) }
    }

    /** Asset or file URI for the start-position still, or null when nothing is on disk. */
    fun startImage(exerciseId: String): String? = resolve(exerciseId, "start")

    /** Asset or file URI for the end-position still, or null when nothing is on disk. */
    fun endImage(exerciseId: String): String? = resolve(exerciseId, "end")

    fun hasImages(exerciseId: String): Boolean =
        startImage(exerciseId) != null || endImage(exerciseId) != null

    private fun resolve(exerciseId: String, position: String): String? {
        sideloadDir?.let { dir ->
            for (extension in EXTENSIONS) {
                val override = File(dir, "${exerciseId}_$position.$extension")
                // Uri.fromFile, not File.toURI: the latter renders "file:/storage/..."
                // with one slash, which Coil's URI parsing does not reliably accept — so
                // the whole documented adb-push override path was silently dead.
                if (override.isFile) return Uri.fromFile(override).toString()
            }
        }

        val stem = ExerciseMediaMap.stemFor(exerciseId) ?: return null
        for (extension in EXTENSIONS) {
            val filename = "${stem}_$position.$extension"
            if (filename in assetFiles) return "file:///android_asset/$ASSET_DIR/$filename"
        }
        return null
    }

    private companion object {
        const val ASSET_DIR = "exercises"

        /** Tried in order. The bundled set is JPEG; a sideloaded set may be anything. */
        val EXTENSIONS = listOf("jpg", "png", "webp")
    }
}
