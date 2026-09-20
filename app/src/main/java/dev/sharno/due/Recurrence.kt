package dev.sharno.due

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

enum class RecurrenceFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
    WEEKDAYS,
}

sealed interface RecurrenceEnd {
    object Never : RecurrenceEnd

    data class On(val date: LocalDate) : RecurrenceEnd

    data class After(val occurrences: Int) : RecurrenceEnd {
        init {
            require(occurrences > 0) { "A recurrence must have at least one occurrence" }
        }
    }
}

data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val dayOfMonth: Int? = null,
    val monthOfYear: Int? = null,
    val end: RecurrenceEnd = RecurrenceEnd.Never,
) {
    init {
        require(interval > 0) { "A recurrence interval must be positive" }
        when (frequency) {
            RecurrenceFrequency.WEEKLY -> {
                require(daysOfWeek.isNotEmpty()) { "A weekly recurrence needs a day" }
            }

            RecurrenceFrequency.MONTHLY -> {
                require(dayOfMonth in 1..31) { "A monthly recurrence needs a valid day" }
            }

            RecurrenceFrequency.YEARLY -> {
                require(monthOfYear in 1..12) { "A yearly recurrence needs a valid month" }
                require(dayOfMonth in 1..31) { "A yearly recurrence needs a valid day" }
            }

            RecurrenceFrequency.DAILY,
            RecurrenceFrequency.WEEKDAYS,
            -> Unit
        }
    }

    fun allowsNextOccurrence(date: LocalDate, occurrence: Int): Boolean = when (val limit = end) {
        RecurrenceEnd.Never -> true
        is RecurrenceEnd.On -> !date.isAfter(limit.date)
        is RecurrenceEnd.After -> occurrence <= limit.occurrences
    }
}

object RecurrenceRuleCodec {
    private const val VERSION = 1

    fun encode(rule: RecurrenceRule): JSONObject {
        val days = JSONArray().apply {
            rule.daysOfWeek.sortedBy(DayOfWeek::getValue).forEach { put(it.name) }
        }
        val end = when (val value = rule.end) {
            RecurrenceEnd.Never -> JSONObject().put("type", "never")
            is RecurrenceEnd.On -> JSONObject()
                .put("type", "on")
                .put("date", value.date.toString())

            is RecurrenceEnd.After -> JSONObject()
                .put("type", "after")
                .put("occurrences", value.occurrences)
        }

        return JSONObject()
            .put("version", VERSION)
            .put("frequency", rule.frequency.name)
            .put("interval", rule.interval)
            .put("daysOfWeek", days)
            .put("dayOfMonth", rule.dayOfMonth ?: JSONObject.NULL)
            .put("monthOfYear", rule.monthOfYear ?: JSONObject.NULL)
            .put("end", end)
    }

    fun decode(value: JSONObject): RecurrenceRule {
        require(value.optInt("version", VERSION) == VERSION) { "Unsupported recurrence version" }
        val days = mutableSetOf<DayOfWeek>()
        value.optJSONArray("daysOfWeek")?.let { values ->
            for (index in 0 until values.length()) {
                days += DayOfWeek.valueOf(values.getString(index))
            }
        }

        val endValue = value.optJSONObject("end") ?: JSONObject().put("type", "never")
        val end = when (endValue.getString("type")) {
            "never" -> RecurrenceEnd.Never
            "on" -> RecurrenceEnd.On(LocalDate.parse(endValue.getString("date")))
            "after" -> RecurrenceEnd.After(endValue.getInt("occurrences"))
            else -> error("Unsupported recurrence end")
        }

        return RecurrenceRule(
            frequency = RecurrenceFrequency.valueOf(value.getString("frequency")),
            interval = value.optInt("interval", 1),
            daysOfWeek = days,
            dayOfMonth = value.optionalInt("dayOfMonth"),
            monthOfYear = value.optionalInt("monthOfYear"),
            end = end,
        )
    }

    fun encodeToString(rule: RecurrenceRule): String = encode(rule).toString()

    fun decodeFromString(raw: String): RecurrenceRule = decode(JSONObject(raw))

    private fun JSONObject.optionalInt(name: String): Int? =
        if (has(name) && !isNull(name)) getInt(name) else null
}

object RecurrenceCalculator {
    fun nextDueAtMillis(
        currentMillis: Long,
        rule: RecurrenceRule,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val current = Instant.ofEpochMilli(currentMillis).atZone(zoneId)
        val nextDate = nextDate(current.toLocalDate(), rule)
        return ZonedDateTime.of(nextDate, current.toLocalTime(), zoneId).toInstant().toEpochMilli()
    }

    internal fun nextDate(current: LocalDate, rule: RecurrenceRule): LocalDate = when (rule.frequency) {
        RecurrenceFrequency.DAILY -> current.plusDays(rule.interval.toLong())
        RecurrenceFrequency.WEEKDAYS -> nextWeekday(current)
        RecurrenceFrequency.WEEKLY -> nextWeeklyDate(current, rule)
        RecurrenceFrequency.MONTHLY -> nextMonthlyDate(current, rule)
        RecurrenceFrequency.YEARLY -> nextYearlyDate(current, rule)
    }

    private fun nextWeekday(current: LocalDate): LocalDate {
        var candidate = current.plusDays(1)
        while (candidate.dayOfWeek == DayOfWeek.SATURDAY || candidate.dayOfWeek == DayOfWeek.SUNDAY) {
            candidate = candidate.plusDays(1)
        }
        return candidate
    }

    private fun nextWeeklyDate(current: LocalDate, rule: RecurrenceRule): LocalDate {
        val nextInCurrentWeek = (1L..7L)
            .asSequence()
            .map(current::plusDays)
            .firstOrNull { it.dayOfWeek in rule.daysOfWeek }
        if (nextInCurrentWeek != null) return nextInCurrentWeek

        val nextWeek = current
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusWeeks(rule.interval.toLong())
        return (0L..6L)
            .asSequence()
            .map(nextWeek::plusDays)
            .first { it.dayOfWeek in rule.daysOfWeek }
    }

    private fun nextMonthlyDate(current: LocalDate, rule: RecurrenceRule): LocalDate {
        val targetMonth = YearMonth.from(current).plusMonths(rule.interval.toLong())
        val day = minOf(rule.dayOfMonth!!, targetMonth.lengthOfMonth())
        return targetMonth.atDay(day)
    }

    private fun nextYearlyDate(current: LocalDate, rule: RecurrenceRule): LocalDate {
        val targetYear = current.year + rule.interval
        val targetMonth = YearMonth.of(targetYear, rule.monthOfYear!!)
        val day = minOf(rule.dayOfMonth!!, targetMonth.lengthOfMonth())
        return targetMonth.atDay(day)
    }
}

fun Todo.completeAt(nowMillis: Long): Todo {
    if (completed) return this
    val rule = recurrence ?: return copy(completed = true)

    var completedOccurrences = occurrencesCompleted + 1
    var nextDueAtMillis = RecurrenceCalculator.nextDueAtMillis(dueAtMillis, rule)
    while (true) {
        val nextOccurrence = completedOccurrences + 1
        val nextDate = Instant.ofEpochMilli(nextDueAtMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        if (!rule.allowsNextOccurrence(nextDate, nextOccurrence)) {
            return copy(completed = true, occurrencesCompleted = completedOccurrences)
        }
        if (nextDueAtMillis > nowMillis) {
            return copy(
                dueAtMillis = nextDueAtMillis,
                completed = false,
                occurrencesCompleted = completedOccurrences,
            )
        }
        completedOccurrences = nextOccurrence
        nextDueAtMillis = RecurrenceCalculator.nextDueAtMillis(nextDueAtMillis, rule)
    }
}
