package dev.redplate.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import dev.redplate.data.BackupRepository
import dev.redplate.data.TrainingClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class BackupUiState(
    val sessionCount: Int = 0,
    val setCount: Int = 0,
    /** "Last session yesterday" — design 9b states this as a date, never as "enabled". */
    val headline: String = "Nothing logged yet",
    /** "146 sessions, 3,912 sets and your equipment setup — about 1.4 MB." */
    val detail: String = "Log a session and your history starts here.",
    val hasData: Boolean = false,
    val isBusy: Boolean = false,
    /** Result of the last export or restore. Cleared once shown. */
    val message: String? = null,
    val isError: Boolean = false,
)

/**
 * Backs the one screen standing between a bug and a lost training log, so everything it
 * reports is read from the database. It previously rendered "Last backup: today ·
 * 42 sessions · 1,284 sets" as literal text with no data behind it, next to a green SAFE
 * badge — a screen that claimed your data was backed up when nothing had been exported.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val repo: BackupRepository,
    private val trainingClock: TrainingClock,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val status = repo.status()
            _state.update {
                it.copy(
                    sessionCount = status.sessionCount,
                    setCount = status.setCount,
                    headline = describeLastSession(status.lastSessionAt),
                    detail = describeContents(status.sessionCount, status.setCount, repo.approximateJsonBytes()),
                    hasData = status.sessionCount > 0,
                )
            }
        }
    }

    fun exportJson(uri: Uri) = run("Exported everything. Keep that file somewhere safe.") {
        repo.exportToUri(uri)
    }

    fun exportCsv(uri: Uri) = run("Exported your sets as CSV.") {
        repo.exportCsvToUri(uri)
    }

    fun import(uri: Uri) = run("Restored. Everything is back.") {
        repo.importFromUri(uri)
    }

    fun consumeMessage() = _state.update { it.copy(message = null, isError = false) }

    /**
     * Every file operation reports what happened. A silent failure here is the worst
     * outcome on this screen — the user would believe a backup exists when it does not.
     */
    private fun run(successMessage: String, block: suspend () -> Unit) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, message = null, isError = false) }

        viewModelScope.launch {
            val result = runCatching { block() }
            // runCatching catches Throwable, cancellation included. Swallowing it leaves a
            // cancelled coroutine running on to report success it never achieved.
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            refresh()
            _state.update {
                it.copy(
                    isBusy = false,
                    message = result.fold(
                        onSuccess = { successMessage },
                        onFailure = { error ->
                            error.message ?: "That didn't work. Try a different location."
                        },
                    ),
                    isError = result.isFailure,
                )
            }
        }
    }

    private suspend fun describeLastSession(epochMillis: Long?): String {
        if (epochMillis == null) return "Nothing logged yet"

        // Training days, so "Last session yesterday" agrees with what History says.
        val dayStartHour = trainingClock.dayStartHour()
        val date = trainingClock.trainingDate(epochMillis, dayStartHour)
        val today = trainingClock.trainingDate(System.currentTimeMillis(), dayStartHour)
        return when (val days = ChronoUnit.DAYS.between(date, today)) {
            0L -> "Last session today"
            1L -> "Last session yesterday"
            in 2L..6L -> "Last session $days days ago"
            else -> "Last session ${date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
        }
    }

    /**
     * How many, and how big. Both matter: the count is what you would lose, and the
     * size is what tells you the export is not going to be a problem to keep.
     */
    private fun describeContents(sessions: Int, sets: Int, bytes: Long): String {
        if (sessions == 0) return "Log a session and your history starts here."
        val size = when {
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "${(bytes / 1024).coerceAtLeast(1)} KB"
        }
        return "$sessions session${plural(sessions)}, $sets working set${plural(sets)} and " +
            "your equipment setup — about $size. Nothing leaves this phone unless you export it."
    }

    private fun plural(n: Int) = if (n == 1) "" else "s"

    companion object {
        fun suggestedJsonName(): String = "redplate-${today()}.json"

        fun suggestedCsvName(): String = "redplate-sets-${today()}.csv"

        /** Wall-clock, not the training day: a file is named for the day it was written. */
        private fun today(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
}
