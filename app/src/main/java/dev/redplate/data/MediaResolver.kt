package dev.redplate.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves optional media files for exercises from external storage.
 * Convention:
 *   <exerciseId>_start.jpg  → start position still
 *   <exerciseId>_end.jpg    → end position still
 *   <exerciseId>.webp       → animation
 *
 * Returns a file:// URI string if the file exists on disk, or null otherwise.
 * Missing files are expected and normal — the UI degrades silently.
 */
@Singleton
class MediaResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val baseDir: File? get() = context.getExternalFilesDir(null)

    fun startImage(exerciseId: String): String? =
        resolve("${exerciseId}_start.jpg")

    fun endImage(exerciseId: String): String? =
        resolve("${exerciseId}_end.jpg")

    fun animation(exerciseId: String): String? =
        resolve("${exerciseId}.webp")

    private fun resolve(filename: String): String? {
        val file = File(baseDir ?: return null, filename)
        return if (file.exists()) file.toURI().toString() else null
    }
}
