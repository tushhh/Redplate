package dev.redplate.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.data.BackupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class BackupUiState(
    val sessionCount: Int = 0,
    val setCount: Int = 0,
    val lastSessionLabel: String = "No sessions logged yet",
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
                    lastSessionLabel = describeLastSession(status.lastSessionAt),
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

    private fun describeLastSession(epochMillis: Long?): String {
        if (epochMillis == null) return "No sessions logged yet"

        val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return when (val days = ChronoUnit.DAYS.between(date, LocalDate.now())) {
            0L -> "Last session: today"
            1L -> "Last session: yesterday"
            in 2L..6L -> "Last session: $days days ago"
            else -> "Last session: ${date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
        }
    }

    companion object {
        fun suggestedJsonName(): String = "redplate-${today()}.json"

        fun suggestedCsvName(): String = "redplate-sets-${today()}.csv"

        private fun today(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
}
