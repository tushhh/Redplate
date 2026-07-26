package dev.redplate.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves exercise media from bundled assets.
 *
 * Naming convention inside `assets/exercises/`:
 *   `<exerciseId>_start.jpg` — start position still
 *   `<exerciseId>_end.jpg`   — end position still
 *
 * Returns a `file:///android_asset/` URI string that Coil can load directly.
 * Missing files are normal — the UI degrades to the hatched placeholder.
 */
@Singleton
class MediaResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Set of filenames in assets/exercises/, loaded once. */
    private val assetFiles: Set<String> by lazy {
        context.assets.list("exercises")?.toHashSet() ?: emptySet()
    }

    /** Returns an asset URI for the start-position image, or null if it doesn't exist. */
    fun startImage(exerciseId: String): String? {
        val filename = "${exerciseId}_start.jpg"
        return if (filename in assetFiles) "file:///android_asset/exercises/$filename" else null
    }

    /** Returns an asset URI for the end-position image, or null if it doesn't exist. */
    fun endImage(exerciseId: String): String? {
        val filename = "${exerciseId}_end.jpg"
        return if (filename in assetFiles) "file:///android_asset/exercises/$filename" else null
    }

    /** Returns true if at least one image exists for this exercise. */
    fun hasImages(exerciseId: String): Boolean {
        return startImage(exerciseId) != null || endImage(exerciseId) != null
    }
}
