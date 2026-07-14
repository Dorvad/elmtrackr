package com.elmtrackr.app.notification

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.util.UUID

/**
 * When a configurable clock-out reminder fires. Persisted by name — add new
 * values freely, never rename existing ones.
 */
enum class ReminderTriggerKind {
    /** [ReminderRule.offsetMinutes] before the daily overtime threshold. */
    BEFORE_OVERTIME,

    /**
     * After the daily overtime threshold: offset 0 fires once when overtime
     * starts; a positive offset repeats every [ReminderRule.offsetMinutes]
     * for as long as the shift is running.
     */
    AFTER_OVERTIME,

    /** At a fixed wall-clock time ([ReminderRule.timeMinuteOfDay]) while clocked in. */
    AT_TIME,
}

/**
 * One user-configured reminder, read as a sentence in the settings UI:
 * "Remind me [30 min] [before overtime]" / "and [every hour] [after overtime]"
 * / "and [at 17:30] [on Sun, Mon]".
 *
 * [daysOfWeek] only applies to [ReminderTriggerKind.AT_TIME] rules. It holds
 * ISO day-of-week values (Monday = 1 .. Sunday = 7, per [DayOfWeek.getValue]).
 * An empty set means "every day" — this keeps rules persisted before the
 * day-of-week option existed behaving exactly as they did.
 */
@Serializable
data class ReminderRule(
    val id: String,
    val kind: ReminderTriggerKind,
    val offsetMinutes: Int = 0,
    val timeMinuteOfDay: Int = DEFAULT_TIME_MINUTE_OF_DAY,
    val daysOfWeek: Set<Int> = emptySet(),
) {
    /** True when this rule may fire on [day]; an empty [daysOfWeek] fires every day. */
    fun firesOn(day: DayOfWeek): Boolean = daysOfWeek.isEmpty() || day.value in daysOfWeek

    companion object {
        const val DEFAULT_TIME_MINUTE_OF_DAY = 17 * 60

        fun newBeforeOvertime(offsetMinutes: Int = 30) =
            ReminderRule(newId(), ReminderTriggerKind.BEFORE_OVERTIME, offsetMinutes = offsetMinutes)

        fun newAfterOvertime(offsetMinutes: Int = 60) =
            ReminderRule(newId(), ReminderTriggerKind.AFTER_OVERTIME, offsetMinutes = offsetMinutes)

        fun newAtTime(
            timeMinuteOfDay: Int = DEFAULT_TIME_MINUTE_OF_DAY,
            daysOfWeek: Set<Int> = emptySet(),
        ) = ReminderRule(
            newId(),
            ReminderTriggerKind.AT_TIME,
            timeMinuteOfDay = timeMinuteOfDay,
            daysOfWeek = daysOfWeek,
        )

        private fun newId(): String = UUID.randomUUID().toString()
    }
}

object ReminderRulesCodec {

    /**
     * The legacy fixed behavior, used until the user edits the schedule:
     * 30 minutes before overtime, then every hour after overtime starts.
     */
    val DEFAULT_RULES: List<ReminderRule> = listOf(
        ReminderRule(id = "default_before_30", kind = ReminderTriggerKind.BEFORE_OVERTIME, offsetMinutes = 30),
        ReminderRule(id = "default_after_hourly", kind = ReminderTriggerKind.AFTER_OVERTIME, offsetMinutes = 60),
    )

    const val MAX_RULES = 6

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(rules: List<ReminderRule>): String = json.encodeToString(
        kotlinx.serialization.builtins.ListSerializer(ReminderRule.serializer()),
        rules,
    )

    /** Returns null for blank/corrupt payloads so callers can fall back to [DEFAULT_RULES]. */
    fun decode(raw: String?): List<ReminderRule>? {
        if (raw.isNullOrBlank()) return null
        return try {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(ReminderRule.serializer()),
                raw,
            )
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
