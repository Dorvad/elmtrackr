package com.elmtrackr.app.domain.leave

import com.elmtrackr.app.domain.model.AbsenceType
import com.elmtrackr.app.domain.model.LeaveBalanceUnit
import com.elmtrackr.app.domain.model.LeaveCalculationSnapshot
import com.elmtrackr.app.domain.model.LeaveManualOverride
import com.elmtrackr.app.domain.model.LeavePolicyRules
import com.elmtrackr.app.domain.model.LeavePolicySnapshot
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.SickLeavePolicy
import com.elmtrackr.app.domain.model.SickPayBasis
import com.elmtrackr.app.domain.model.SickPayTier
import com.elmtrackr.app.domain.model.VacationLeavePolicy
import com.elmtrackr.app.domain.model.VacationPayBasis
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

/**
 * JSON for the policy and for the two snapshots stored on an allocation, in the
 * same shape and with the same tolerance as
 * [com.elmtrackr.app.domain.compensation.CompensationRulesCodec]: every read has
 * a documented default, and malformed JSON decodes to a usable policy rather
 * than throwing on a screen the user is trying to open.
 */
object LeavePolicyCodec {

    // ── Policy rules ──────────────────────────────────────────────────────────

    fun encode(rules: LeavePolicyRules): String = JSONObject().apply {
        put("sick", JSONObject().apply {
            put("enabled", rules.sick.enabled)
            put("payTiers", encodeTiers(rules.sick.payTiers))
            put("payBasis", rules.sick.payBasis.persistedValue)
            put("fixedDailyMinutes", rules.sick.fixedDailyMinutes ?: JSONObject.NULL)
            put("accrualEnabled", rules.sick.accrualEnabled)
            put("accrualDaysPerMonth", rules.sick.accrualDaysPerMonth ?: JSONObject.NULL)
            put("maxAccruedDays", rules.sick.maxAccruedDays ?: JSONObject.NULL)
        })
        put("vacation", JSONObject().apply {
            put("enabled", rules.vacation.enabled)
            put("payBasis", rules.vacation.payBasis.persistedValue)
            put("fixedDailyMinutes", rules.vacation.fixedDailyMinutes ?: JSONObject.NULL)
            put("accrualEnabled", rules.vacation.accrualEnabled)
        })
        put("standardDayMinutes", rules.standardDayMinutes ?: JSONObject.NULL)
        put("balanceUnit", rules.balanceUnit.persistedValue)
    }.toString()

    fun decode(json: String?): LeavePolicyRules {
        if (json.isNullOrBlank() || json.equals("null", ignoreCase = true)) return LeavePolicyRules()
        return runCatching { decode(JSONObject(json)) }.getOrElse { LeavePolicyRules() }
    }

    fun decode(obj: JSONObject): LeavePolicyRules {
        val sick = obj.optJSONObject("sick") ?: JSONObject()
        val vacation = obj.optJSONObject("vacation") ?: JSONObject()
        return LeavePolicyRules(
            sick = SickLeavePolicy(
                enabled = sick.optBoolean("enabled", true),
                payTiers = decodeTiers(sick.optJSONArray("payTiers")),
                payBasis = SickPayBasis.fromPersisted(sick.optString("payBasis")),
                fixedDailyMinutes = sick.optNullableInt("fixedDailyMinutes"),
                accrualEnabled = sick.optBoolean("accrualEnabled", false),
                accrualDaysPerMonth = sick.optNullableDouble("accrualDaysPerMonth"),
                maxAccruedDays = sick.optNullableDouble("maxAccruedDays"),
            ),
            vacation = VacationLeavePolicy(
                enabled = vacation.optBoolean("enabled", true),
                payBasis = VacationPayBasis.fromPersisted(vacation.optString("payBasis")),
                fixedDailyMinutes = vacation.optNullableInt("fixedDailyMinutes"),
                accrualEnabled = vacation.optBoolean("accrualEnabled", false),
            ),
            standardDayMinutes = obj.optNullableInt("standardDayMinutes"),
            balanceUnit = LeaveBalanceUnit.fromPersisted(obj.optString("balanceUnit")),
        )
    }

    // ── Policy snapshot ───────────────────────────────────────────────────────

    fun encodePolicySnapshot(snapshot: LeavePolicySnapshot): String = JSONObject().apply {
        put("policy_id", snapshot.policyId ?: JSONObject.NULL)
        put("region_code", snapshot.regionCode.name.lowercase())
        put("rules_json", JSONObject(encode(snapshot.rules)))
        put("captured_at", snapshot.capturedAt.toString())
    }.toString()

    fun decodePolicySnapshot(json: String?): LeavePolicySnapshot? {
        if (json.isNullOrBlank() || json.equals("null", ignoreCase = true)) return null
        return runCatching {
            val obj = JSONObject(json)
            LeavePolicySnapshot(
                policyId = obj.optNullableString("policy_id"),
                regionCode = RegionCode.fromPersisted(obj.optString("region_code")),
                rules = when (val rules = obj.opt("rules_json")) {
                    is JSONObject -> decode(rules)
                    is String -> decode(rules)
                    else -> LeavePolicyRules()
                },
                capturedAt = obj.optInstant("captured_at"),
            )
        }.getOrNull()
    }

    // ── Calculation snapshot ──────────────────────────────────────────────────

    fun encodeCalculationSnapshot(snapshot: LeaveCalculationSnapshot): String = JSONObject().apply {
        put("calculated_at", snapshot.calculatedAt.toString())
        put("absence_type", snapshot.absenceType.persistedValue)
        put("workplace_id", snapshot.workplaceId)
        put("pay_basis", snapshot.payBasis)
        put("currency_code", snapshot.currencyCode)
        put("sick_day_ordinal", snapshot.sickDayOrdinal ?: JSONObject.NULL)
        put("multiplier", snapshot.multiplier)
        put("expected_work_minutes", snapshot.expectedWorkMinutes ?: JSONObject.NULL)
        put("base_amount", snapshot.baseAmount)
        put("estimated_gross_pay", snapshot.estimatedGrossPay)
        put("balance_units_used", snapshot.balanceUnitsUsed)
        put("balance_unit", snapshot.balanceUnit.persistedValue)
        put("average_period_start", snapshot.averagePeriodStart?.toString() ?: JSONObject.NULL)
        put("average_period_end", snapshot.averagePeriodEnd?.toString() ?: JSONObject.NULL)
        put("average_gross_included", snapshot.averageGrossIncluded ?: JSONObject.NULL)
        put("average_divisor", snapshot.averageDivisor ?: JSONObject.NULL)
        put("used_fallback_average_period", snapshot.usedFallbackAveragePeriod)
        snapshot.manualOverride?.let { override ->
            put("manual_override", JSONObject().apply {
                put("enabled", override.enabled)
                put("reason", override.reason ?: JSONObject.NULL)
            })
        }
    }.toString()

    fun decodeCalculationSnapshot(json: String?): LeaveCalculationSnapshot? {
        if (json.isNullOrBlank() || json.equals("null", ignoreCase = true)) return null
        return runCatching {
            val obj = JSONObject(json)
            // The leave type is the one field with no safe default: a snapshot that
            // does not say whether it priced a sick day or a vacation day cannot be
            // shown as either, so the row is dropped instead.
            val type = AbsenceType.fromPersisted(obj.optString("absence_type")) ?: return null
            LeaveCalculationSnapshot(
                calculatedAt = obj.optInstant("calculated_at"),
                absenceType = type,
                workplaceId = obj.optString("workplace_id"),
                payBasis = obj.optString("pay_basis"),
                currencyCode = obj.optString("currency_code"),
                sickDayOrdinal = obj.optNullableInt("sick_day_ordinal"),
                multiplier = obj.optDouble("multiplier", 1.0),
                expectedWorkMinutes = obj.optNullableInt("expected_work_minutes"),
                baseAmount = obj.optDouble("base_amount", 0.0),
                estimatedGrossPay = obj.optDouble("estimated_gross_pay", 0.0),
                balanceUnitsUsed = obj.optDouble("balance_units_used", 1.0),
                balanceUnit = LeaveBalanceUnit.fromPersisted(obj.optString("balance_unit")),
                averagePeriodStart = obj.optLocalDate("average_period_start"),
                averagePeriodEnd = obj.optLocalDate("average_period_end"),
                averageGrossIncluded = obj.optNullableDouble("average_gross_included"),
                averageDivisor = obj.optNullableDouble("average_divisor"),
                usedFallbackAveragePeriod = obj.optBoolean("used_fallback_average_period", false),
                manualOverride = obj.optJSONObject("manual_override")?.let { override ->
                    LeaveManualOverride(
                        enabled = override.optBoolean("enabled", false),
                        reason = override.optNullableString("reason"),
                    )
                },
            )
        }.getOrNull()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun encodeTiers(tiers: List<SickPayTier>): JSONArray = JSONArray().apply {
        tiers.forEach { tier ->
            put(
                JSONObject().apply {
                    put("fromDay", tier.fromDay)
                    put("toDay", tier.toDay ?: JSONObject.NULL)
                    put("multiplier", tier.multiplier)
                },
            )
        }
    }

    private fun decodeTiers(array: JSONArray?): List<SickPayTier> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    SickPayTier(
                        fromDay = item.optInt("fromDay", 1),
                        toDay = item.optNullableInt("toDay"),
                        multiplier = item.optDouble("multiplier", 1.0),
                    ),
                )
            }
        }
    }

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key)

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.optLocalDate(key: String): LocalDate? {
        val raw = optNullableString(key) ?: return null
        return runCatching { LocalDate.parse(raw) }.getOrNull()
    }

    /** A snapshot with an unreadable timestamp is still worth showing, so EPOCH stands in. */
    private fun JSONObject.optInstant(key: String): Instant {
        val raw = optNullableString(key) ?: return Instant.EPOCH
        return runCatching { Instant.parse(raw) }.getOrElse { Instant.EPOCH }
    }
}
