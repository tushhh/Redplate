package dev.redplate.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redplate.data.BodyweightDao
import dev.redplate.data.BodyweightEntryEntity
import dev.redplate.data.BodyweightPoint
import dev.redplate.data.BodyweightTrends
import dev.redplate.data.ExerciseDao
import dev.redplate.data.ProfileDao
import dev.redplate.data.SessionDao
import dev.redplate.data.TrainingClock
import dev.redplate.data.isConditioning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs

/** One row of the weigh-in log. */
data class BodyweightRow(
    val entry: BodyweightEntryEntity,
    val dateLabel: String,
    val weightLabel: String,
    val waistLabel: String?,
)

/** How many lifts moved over the last four weeks, against the four before. */
data class StrengthTrend(val up: Int, val flat: Int, val down: Int) {
    val total: Int get() = up + flat + down
}

/** Which number the keypad is collecting. */
enum class BodyEntryField { WEIGHT, WAIST }

data class BodyweightUiState(
    val isLoading: Boolean = true,
    val averageLabel: String = "—",
    val rateLabel: String? = null,
    val coachLine: String = "",
    val points: List<BodyweightPoint> = emptyList(),
    val fourWeekWeightLabel: String? = null,
    val waistLabel: String? = null,
    val strength: StrengthTrend? = null,
    val rows: List<BodyweightRow> = emptyList(),
    /** Today already has a weigh-in, so a waist can be added to it. */
    val canAddWaist: Boolean = false,
    val entryField: BodyEntryField? = null,
    val entry: String = "",
    /** The weigh-in just removed, offered back until something else happens. */
    val justRemoved: BodyweightRow? = null,
) {
    val canCommit: Boolean
        get() = entry.toDoubleOrNull()?.let {
            when (entryField) {
                BodyEntryField.WEIGHT -> it in 30.0..300.0
                BodyEntryField.WAIST -> it in 40.0..200.0
                null -> false
            }
        } == true
}

/**
 * The "leaner" half of leaner-and-stronger, read beside the "stronger" half.
 *
 * Bodyweight on its own says little — it moves a kilo with water and salt. What says
 * whether the plan is working is the two together: the trend line coming down while the
 * lifts keep going up. So this screen puts the strength trend right next to the scale.
 */
@HiltViewModel
class BodyweightViewModel @Inject constructor(
    private val bodyweightDao: BodyweightDao,
    private val profileDao: ProfileDao,
    private val sessionDao: SessionDao,
    private val exerciseDao: ExerciseDao,
    private val trainingClock: TrainingClock,
) : ViewModel() {

    private val strength = MutableStateFlow<StrengthTrend?>(null)
    private val keypad = MutableStateFlow<Pair<BodyEntryField?, String>>(null to "")
    private val removed = MutableStateFlow<BodyweightRow?>(null)

    val state: StateFlow<BodyweightUiState> = combine(
        bodyweightDao.observeAll(),
        strength,
        keypad,
        removed,
    ) { entries, strengthTrend, (field, typed), justRemoved ->
        buildState(entries, strengthTrend, field, typed).copy(justRemoved = justRemoved)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyweightUiState())

    init {
        viewModelScope.launch { strength.value = computeStrengthTrend() }
    }

    private suspend fun buildState(
        entries: List<BodyweightEntryEntity>,
        strengthTrend: StrengthTrend?,
        field: BodyEntryField?,
        typed: String,
    ): BodyweightUiState {
        val trend = BodyweightTrends.compute(entries)
        val dayStartHour = trainingClock.dayStartHour()
        val today = trainingClock.today(dayStartHour)
        val latest = entries.maxByOrNull { it.measuredAt }
        val latestIsToday = latest != null &&
            trainingClock.trainingDate(latest.measuredAt, dayStartHour) == today

        val fourWeeks = trend.weeklyChangeKg?.let { perWeek ->
            val weeks = minOf(4.0, spanWeeks(trend.points))
            signed(perWeek * weeks) + " kg"
        }

        return BodyweightUiState(
            isLoading = false,
            averageLabel = trend.averageKg?.let { kg(it) } ?: "—",
            rateLabel = trend.weeklyChangeKg?.let { perWeek ->
                val percent = trend.weeklyChangePercent ?: 0.0
                "${signed(perWeek)} KG / WEEK · ${signed(percent)}%"
            },
            coachLine = BodyweightTrends.describe(trend),
            points = trend.points.takeLast(MAX_CHART_POINTS),
            fourWeekWeightLabel = fourWeeks,
            waistLabel = trend.latestWaistCm?.let { waist ->
                kg(waist) + " cm" + (trend.waistChangeCm?.let { " (${signed(it)})" } ?: "")
            },
            strength = strengthTrend,
            rows = entries.take(MAX_ROWS).map { e ->
                BodyweightRow(
                    entry = e,
                    dateLabel = DATE.format(Instant.ofEpochMilli(e.measuredAt).atZone(ZoneId.systemDefault())).uppercase(),
                    weightLabel = "${kg(e.weightKg)} kg",
                    waistLabel = e.waistCm?.let { "${kg(it)} cm" },
                )
            },
            canAddWaist = latestIsToday,
            entryField = field,
            entry = typed,
        )
    }

    // ── Keypad ──

    fun startWeightEntry() {
        removed.value = null
        keypad.update { BodyEntryField.WEIGHT to "" }
    }

    fun startWaistEntry() = keypad.update { BodyEntryField.WAIST to "" }

    fun cancelEntry() = keypad.update { null to "" }

    fun appendDigit(key: Char) = keypad.update { (field, current) ->
        val next = when {
            key.isDigit() -> current + key
            key == '.' && !current.contains('.') -> if (current.isEmpty()) "0." else "$current."
            else -> current
        }
        field to if (next.length > MAX_DIGITS) current else next
    }

    fun backspace() = keypad.update { (field, current) -> field to current.dropLast(1) }

    /**
     * A weight is a new entry; a waist joins today's. Bodyweight on the profile follows
     * the latest weigh-in, so the rest of the app reads a real number.
     */
    fun commitEntry() {
        val current = state.value
        if (!current.canCommit) return
        val value = current.entry.toDouble()
        val field = current.entryField ?: return
        keypad.value = null to ""
        viewModelScope.launch {
            when (field) {
                BodyEntryField.WEIGHT -> {
                    bodyweightDao.insert(
                        BodyweightEntryEntity(measuredAt = System.currentTimeMillis(), weightKg = value),
                    )
                    profileDao.get()?.let { profileDao.upsert(it.copy(bodyweightKg = value)) }
                }

                BodyEntryField.WAIST -> {
                    val latest = bodyweightDao.getLatest() ?: return@launch
                    bodyweightDao.update(latest.copy(waistCm = value))
                }
            }
        }
    }

    /** One tap removes, and the screen offers it straight back — no confirm dialog. */
    fun delete(row: BodyweightRow) {
        removed.value = row
        val entry = row.entry
        viewModelScope.launch {
            bodyweightDao.delete(entry)
            val latest = bodyweightDao.getLatest()
            profileDao.get()?.let { profile ->
                profileDao.upsert(profile.copy(bodyweightKg = latest?.weightKg ?: profile.bodyweightKg))
            }
        }
    }

    fun undoDelete() {
        val row = removed.value ?: return
        removed.value = null
        viewModelScope.launch {
            bodyweightDao.insertAll(listOf(row.entry))
            val latest = bodyweightDao.getLatest()
            profileDao.get()?.let { profile ->
                profileDao.upsert(profile.copy(bodyweightKg = latest?.weightKg ?: profile.bodyweightKg))
            }
        }
    }

    // ── The strength half ──

    /**
     * Best estimated 1RM per lift over the last four weeks against the four before it.
     * Within 1% either way is flat: that is inside the noise of one good or bad day.
     */
    private suspend fun computeStrengthTrend(): StrengthTrend? {
        val now = System.currentTimeMillis()
        val window = TimeUnit.DAYS.toMillis(TREND_WINDOW_DAYS)
        val sets = sessionDao.getSetLogsBetween(now - 2 * window, now + 1)
            .filter { !it.isWarmup && it.reps in 1..12 && it.loadKg > 0 }
        if (sets.isEmpty()) return null

        val conditioning = exerciseDao.getAll().filter { it.isConditioning }.mapTo(mutableSetOf()) { it.id }
        var up = 0
        var flat = 0
        var down = 0
        for ((exerciseId, forLift) in sets.groupBy { it.exerciseId }) {
            if (exerciseId in conditioning) continue
            val recent = forLift.filter { it.completedAt >= now - window }.maxOfOrNull { it.estimated1Rm() }
            val before = forLift.filter { it.completedAt < now - window }.maxOfOrNull { it.estimated1Rm() }
            if (recent == null || before == null || before <= 0.0) continue
            val change = (recent - before) / before
            when {
                change > FLAT_BAND -> up++
                change < -FLAT_BAND -> down++
                else -> flat++
            }
        }
        return StrengthTrend(up, flat, down).takeIf { it.total > 0 }
    }

    private fun spanWeeks(points: List<BodyweightPoint>): Double {
        if (points.size < 2) return 0.0
        return (points.last().measuredAt - points.first().measuredAt).toDouble() / TimeUnit.DAYS.toMillis(7)
    }

    private fun kg(value: Double): String =
        if (abs(value % 1.0) < 0.05) value.toLong().toString()
        else String.format(Locale.ROOT, "%.1f", value)

    private fun signed(value: Double): String {
        val formatted = String.format(Locale.ROOT, "%.1f", abs(value))
        return when {
            value <= -0.05 -> "−$formatted"
            value >= 0.05 -> "+$formatted"
            else -> "±0"
        }
    }

    private companion object {
        const val MAX_ROWS = 14
        const val MAX_CHART_POINTS = 90
        const val MAX_DIGITS = 5
        const val TREND_WINDOW_DAYS = 28L
        const val FLAT_BAND = 0.01
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    }
}
