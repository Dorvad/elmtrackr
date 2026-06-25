package com.elmtrackr.app.domain.compensation

import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.CompensationRules
import com.elmtrackr.app.domain.model.CompensationSnapshot
import com.elmtrackr.app.domain.model.OvertimeTier
import com.elmtrackr.app.domain.model.RegionCode
import com.elmtrackr.app.domain.model.ResolvedCompensation
import com.elmtrackr.app.domain.model.RoundingRules
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.StackingPolicy
import com.elmtrackr.app.domain.model.UserSettings
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object CompensationRulesCodec {

    fun encode(rules: CompensationRules): String = JSONObject().apply {
        put("regular", JSONObject().apply {
            put("dailyStandardMinutes", rules.dailyStandardMinutes)
            put("weeklyStandardMinutes", rules.weeklyStandardMinutes)
            put("weekendDays", JSONArray(rules.weekendDays))
            put("paidBreaks", rules.paidBreaks)
            put("autoDeductBreakMinutes", rules.autoDeductBreakMinutes ?: JSONObject.NULL)
            put("minimumShiftMinutes", rules.minimumShiftMinutes ?: JSONObject.NULL)
            put("rounding", JSONObject().apply {
                put("enabled", rules.rounding.enabled)
                put("incrementMinutes", rules.rounding.incrementMinutes)
                put("direction", rules.rounding.direction)
            })
        })
        put("overtime", JSONObject().apply {
            put("enabled", rules.overtimeEnabled)
            put("dailyTiers", encodeTiers(rules.dailyOvertimeTiers))
            put("weeklyTiers", encodeTiers(rules.weeklyOvertimeTiers))
        })
        put("weekend", JSONObject().apply {
            put("enabled", rules.weekendEnabled)
            put("days", JSONArray(rules.weekendDays))
            put("multiplier", rules.weekendMultiplier)
            put("stacking", rules.weekendStacking.name.lowercase())
        })
        put("holiday", JSONObject().apply {
            put("enabled", rules.holidayEnabled)
            put("manualSpecialDayEnabled", rules.holidayManualSpecialDayEnabled)
            put("multiplier", rules.holidayMultiplier)
            put("stacking", rules.holidayStacking.name.lowercase())
            rules.holidayTiers?.let { put("tiers", encodeTiers(it)) }
        })
        put("night", JSONObject().apply {
            put("enabled", rules.nightEnabled)
            put("startTime", rules.nightStartTime)
            put("endTime", rules.nightEndTime)
            put("multiplier", rules.nightMultiplier)
            put("applyTo", rules.nightApplyTo)
            put("stacking", rules.nightStacking.name.lowercase())
        })
        put("deductions", JSONObject().apply {
            put("enabled", rules.deductionsEnabled)
            put("mode", rules.deductionsMode)
            put("percentage", rules.deductionsPercentage)
            put("fixedAmount", rules.deductionsFixedAmount)
        })
    }.toString()

    fun decode(json: String): CompensationRules = decode(JSONObject(json))

    fun decode(obj: JSONObject): CompensationRules {
        val regular = obj.optJSONObject("regular") ?: JSONObject()
        val overtime = obj.optJSONObject("overtime") ?: JSONObject()
        val weekend = obj.optJSONObject("weekend") ?: JSONObject()
        val holiday = obj.optJSONObject("holiday") ?: JSONObject()
        val night = obj.optJSONObject("night") ?: JSONObject()
        val deductions = obj.optJSONObject("deductions") ?: JSONObject()
        val rounding = regular.optJSONObject("rounding") ?: JSONObject()

        return CompensationRules(
            dailyStandardMinutes = regular.optInt("dailyStandardMinutes", 480),
            weeklyStandardMinutes = regular.optInt("weeklyStandardMinutes", 2400),
            weekendDays = regular.optJSONArray("weekendDays")?.toIntList() ?: listOf(5, 6),
            paidBreaks = regular.optBoolean("paidBreaks", false),
            autoDeductBreakMinutes = regular.optNullableInt("autoDeductBreakMinutes"),
            minimumShiftMinutes = regular.optNullableInt("minimumShiftMinutes"),
            rounding = RoundingRules(
                enabled = rounding.optBoolean("enabled", false),
                incrementMinutes = rounding.optInt("incrementMinutes", 15),
                direction = rounding.optString("direction", "nearest"),
            ),
            overtimeEnabled = overtime.optBoolean("enabled", true),
            dailyOvertimeTiers = decodeTiers(overtime.optJSONArray("dailyTiers")),
            weeklyOvertimeTiers = decodeTiers(overtime.optJSONArray("weeklyTiers")),
            weekendEnabled = weekend.optBoolean("enabled", true),
            weekendMultiplier = weekend.optDouble("multiplier", 1.5),
            weekendStacking = weekend.optString("stacking", "highest_only").toStackingPolicy(),
            holidayEnabled = holiday.optBoolean("enabled", true),
            holidayManualSpecialDayEnabled = holiday.optBoolean("manualSpecialDayEnabled", true),
            holidayMultiplier = holiday.optDouble("multiplier", 1.5),
            holidayTiers = holiday.optJSONArray("tiers")?.let { decodeTiers(it) },
            holidayStacking = holiday.optString("stacking", "highest_only").toStackingPolicy(),
            nightEnabled = night.optBoolean("enabled", false),
            nightStartTime = night.optString("startTime", "22:00"),
            nightEndTime = night.optString("endTime", "06:00"),
            nightMultiplier = night.optDouble("multiplier", 1.25),
            nightApplyTo = night.optString("applyTo", "minutes_inside_window"),
            nightStacking = night.optString("stacking", "highest_only").toStackingPolicy(),
            deductionsEnabled = deductions.optBoolean("enabled", false),
            deductionsMode = deductions.optString("mode", "none"),
            deductionsPercentage = deductions.optDouble("percentage", 0.0),
            deductionsFixedAmount = deductions.optDouble("fixedAmount", 0.0),
        )
    }

    fun encodeSnapshot(snapshot: CompensationSnapshot): String = JSONObject().apply {
        put("profile_id", snapshot.profileId)
        put("profile_name", snapshot.profileName)
        put("region_code", snapshot.regionCode.name)
        put("currency_code", snapshot.currencyCode)
        put("timezone", snapshot.timezone)
        put("base_hourly_rate", snapshot.baseHourlyRate ?: JSONObject.NULL)
        put("rules_json", JSONObject(encode(snapshot.rules)))
        put("stacking_policy", snapshot.stackingPolicy.name.lowercase())
        put("calculated_at", snapshot.calculatedAt.toString())
    }.toString()

    fun decodeSnapshot(json: String): CompensationSnapshot {
        val obj = JSONObject(json)
        val rulesObj = obj.opt("rules_json")
        val rules = when (rulesObj) {
            is JSONObject -> decode(rulesObj)
            is String -> decode(rulesObj)
            else -> RegionPresets.forRegion(RegionCode.IL).rules
        }
        return CompensationSnapshot(
            profileId = obj.getString("profile_id"),
            profileName = obj.optString("profile_name", "Main job"),
            regionCode = RegionCode.valueOf(obj.optString("region_code", "IL")),
            currencyCode = obj.optString("currency_code", "USD"),
            timezone = obj.optString("timezone", "UTC"),
            baseHourlyRate = obj.optNullableDouble("base_hourly_rate"),
            rules = rules,
            stackingPolicy = obj.optString("stacking_policy", "highest_only").toStackingPolicy(),
            calculatedAt = Instant.parse(obj.getString("calculated_at")),
        )
    }

    private fun encodeTiers(tiers: List<OvertimeTier>): JSONArray =
        JSONArray().apply {
            tiers.forEach { tier ->
                put(JSONObject().apply {
                    put("afterMinutes", tier.afterMinutes)
                    put("multiplier", tier.multiplier)
                })
            }
        }

    private fun decodeTiers(array: JSONArray?): List<OvertimeTier> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    OvertimeTier(
                        afterMinutes = item.optInt("afterMinutes"),
                        multiplier = item.optDouble("multiplier", 1.0),
                    ),
                )
            }
        }
    }

    private fun JSONArray.toIntList(): List<Int> = buildList {
        for (i in 0 until length()) add(optInt(i))
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key)
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key)
    }

    private fun String.toStackingPolicy(): StackingPolicy =
        if (this == "additive") StackingPolicy.ADDITIVE else StackingPolicy.HIGHEST_ONLY
}
