package com.elmtrackr.app.domain

import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UserSettings
import java.time.Instant
import kotlin.math.roundToInt
import java.text.NumberFormat

enum class InsightColor { INDIGO, VIOLET, EMERALD, AMBER, ROSE, SKY }

data class DailyInsight(
    val icon: String,
    val title: String,
    /** Body text — segments wrapped in ** ** should render as bold. */
    val text: String,
    val color: InsightColor,
)

/**
 * Produces 4 daily-rotating insights from a pool of fun facts.
 * The 4 cards rotate together at UTC midnight — each one is distinct.
 * All references use Israeli/real-world context (same as the web app).
 */
object DailyInsightsBuilder {

    // ── reference constants (match the web app) ───────────────
    private const val AVG_WORKDAY_HRS  = 8.5
    private const val AVG_MONTH_HRS    = 182.0
    private const val AVG_MOVIE_HRS    = 2.0
    private const val AVG_BOOK_HRS     = 8.0
    private const val TEL_EILAT_HRS    = 3.5
    private const val TLV_JFK_HRS      = 12.0
    private const val PIZZA_PRICE_ILS  = 65.0
    private const val BREAKING_BAD_HRS = 47.0
    private const val COFFEE_MIN       = 20.0
    private const val AVG_GAMER_HRS    = 30.0
    private const val SNAIL_KMH        = 0.05
    private const val ISS_ORBIT_MIN    = 92.0
    private const val BEE_LIFETIME_HRS = 540.0
    private const val MIN_WAGE_ILS     = 32.30
    private const val HATIKVA_MIN      = 1.33
    private const val SHARK_KMH        = 56.0
    private const val SURF_HRS         = 1.5
    private const val SHWARMA_MIN      = 5.0
    private const val MONOPOLY_HRS     = 3.0
    private const val SHAKESPEARE_HRS  = 10_000.0
    private const val ARMY_SERVICE_HRS = 4_600.0
    private const val GUITAR_SOLO_SEC  = 30.0
    private const val RAMEN_MIN        = 3.0

    /**
     * Returns up to 4 insight cards for today, based on [totalMinutes] and
     * [completedShifts]. Returns a single fallback card if no shifts yet.
     */
    fun build(
        completedShifts: List<Shift>,
        settings: UserSettings,
        totalMinutes: Int,
        profiles: List<CompensationProfile> = emptyList(),
    ): List<DailyInsight> {
        val fallback = DailyInsight(
            icon  = "✨",
            title = "Just getting started",
            text  = "Log your first completed shifts to unlock daily insights.",
            color = InsightColor.INDIGO,
        )
        if (completedShifts.isEmpty() || totalMinutes == 0) return listOf(fallback)

        val pool = buildPool(completedShifts, settings, totalMinutes, profiles)
        if (pool.isEmpty()) return listOf(fallback)

        val dayIndex = (Instant.now().toEpochMilli() / 86_400_000).toInt()
        val count = minOf(4, pool.size)
        return (0 until count).map { i -> pool[(dayIndex + i) % pool.size] }
    }

    private fun buildPool(
        shifts: List<Shift>,
        settings: UserSettings,
        totalMinutes: Int,
        profiles: List<CompensationProfile>,
    ): List<DailyInsight> {
        val totalHours = totalMinutes / 60.0
        val shiftCount = shifts.size

        val pool = mutableListOf<DailyInsight>()

        // ── Work-hours comparisons ────────────────────────────

        val workdays = (totalHours / AVG_WORKDAY_HRS).roundToInt()
        if (workdays > 0) pool += DailyInsight(
            "📅", "Work Days Equivalent",
            "Your **${totalHours.roundToInt()}h** of work equals **$workdays full ${AVG_WORKDAY_HRS}h work days**. Consistent.",
            InsightColor.INDIGO,
        )

        val movies = (totalHours / AVG_MOVIE_HRS).roundToInt()
        if (movies > 0) pool += DailyInsight(
            "🎬", "Movie Marathon",
            "Instead of working **${totalHours.roundToInt()}h**, you could have watched **$movies movies**. No popcorn included.",
            InsightColor.VIOLET,
        )

        val books = (totalHours / AVG_BOOK_HRS).roundToInt()
        if (books > 0) pool += DailyInsight(
            "📚", "Book Club",
            "Your **${totalHours.roundToInt()}h** of work = **$books books** you could have read cover-to-cover. Bookworm status pending.",
            InsightColor.EMERALD,
        )

        val drivesEilat = (totalHours / TEL_EILAT_HRS).roundToInt()
        if (drivesEilat > 0) pool += DailyInsight(
            "🚗", "Road Trip",
            "Your hours this month equal **$drivesEilat drives** from Tel Aviv to Eilat. Sand dunes, desert wind, and deadlines.",
            InsightColor.EMERALD,
        )

        val coffees = (totalMinutes / COFFEE_MIN).roundToInt()
        if (coffees > 0) pool += DailyInsight(
            "☕", "Coffee Math",
            "Your **${totalHours.roundToInt()}h** of work required roughly **$coffees cups of coffee** — at 20 minutes of ritual each. No decaf.",
            InsightColor.AMBER,
        )

        val flights = (totalHours / TLV_JFK_HRS).roundToInt()
        if (flights > 0) pool += DailyInsight(
            "✈️", "Airborne Hours",
            "**${totalHours.roundToInt()}h** worked = **$flights non-stop El Al flights** from Tel Aviv to New York. Jet lag not included.",
            InsightColor.SKY,
        )

        val bbRuns = (totalHours / BREAKING_BAD_HRS * 10).roundToInt().toDouble() / 10.0
        if (bbRuns >= 0.5) pool += DailyInsight(
            "📺", "Binge Watch",
            "**${totalHours.roundToInt()}h** of work = **${bbRuns}× through Breaking Bad** (all ${BREAKING_BAD_HRS.roundToInt()}h). Better call it a productive month.",
            InsightColor.VIOLET,
        )

        // ── Shift-count fun facts ─────────────────────────────

        if (shiftCount > 0) pool += DailyInsight(
            "🏖️", "Beach Tax",
            "You clocked **$shiftCount shift${if (shiftCount != 1) "s" else ""}** this month. That's **$shiftCount potential beach days** surrendered to productivity. Worth it?",
            InsightColor.SKY,
        )

        val surfSessions = (totalHours / SURF_HRS).roundToInt()
        if (surfSessions > 0) pool += DailyInsight(
            "🏄", "Surf's Up",
            "An average surf session runs **${SURF_HRS}h**. Your work this month = **$surfSessions surf sessions** you could have caught instead. Hang ten.",
            InsightColor.SKY,
        )

        val monopolyGames = (totalHours / MONOPOLY_HRS).roundToInt()
        if (monopolyGames > 0) pool += DailyInsight(
            "🎲", "Board Game Champion",
            "An average Monopoly game takes **${MONOPOLY_HRS.roundToInt()}h**. Your work this month = **$monopolyGames full Monopoly games**. You've been collecting rent in real life.",
            InsightColor.VIOLET,
        )

        // ── Israeli context ───────────────────────────────────

        val shwarmas = (totalMinutes / SHWARMA_MIN).roundToInt()
        if (shwarmas > 0) pool += DailyInsight(
            "🥙", "Shwarma Economy",
            "At ${SHWARMA_MIN.roundToInt()} minutes per shwarma, you could have eaten **$shwarmas shwarmas** in the time you worked this month. B'te'avon.",
            InsightColor.AMBER,
        )

        val pizzas = run {
            val gross = PayrollCalculator.sumMonthlyPay(shifts, settings, profiles).totalGross
            if (gross > 0) (gross / PIZZA_PRICE_ILS).roundToInt() else 0
        }
        if (pizzas > 0) pool += DailyInsight(
            "🍕", "Pizza Fund",
            "This month's earnings could buy you **$pizzas pizzas** at ₪${PIZZA_PRICE_ILS.roundToInt()} each. That's a lot of slices.",
            InsightColor.AMBER,
        )

        val armyPct = (totalHours / ARMY_SERVICE_HRS * 100).roundToInt()
        if (armyPct in 1..100) pool += DailyInsight(
            "🎖️", "Service Hours",
            "Israeli male army service is ~**${ARMY_SERVICE_HRS.roundToInt()}h** total. Your work this month = **$armyPct%** of full service. Salute.",
            InsightColor.INDIGO,
        )

        val hatikvaPlays = (totalMinutes / HATIKVA_MIN).roundToInt()
        if (hatikvaPlays > 0) pool += DailyInsight(
            "🎵", "National Anthem",
            "HaTikvah runs **~${HATIKVA_MIN} minutes**. Your work this month = **$hatikvaPlays plays** of the national anthem. 🇮🇱",
            InsightColor.INDIGO,
        )

        // ── Nature + absurd ───────────────────────────────────

        val snailKm = (totalHours * SNAIL_KMH * 10).roundToInt().toDouble() / 10.0
        if (snailKm > 0) pool += DailyInsight(
            "🐌", "Snail Race",
            "A garden snail tops out at **${SNAIL_KMH} km/h**. In your **${totalHours.roundToInt()}h** of work this month, a snail could have travelled **$snailKm km**.",
            InsightColor.EMERALD,
        )

        val issOrbits = (totalMinutes / ISS_ORBIT_MIN).roundToInt()
        if (issOrbits > 0) pool += DailyInsight(
            "🚀", "Orbital Mechanic",
            "The ISS orbits Earth every **${ISS_ORBIT_MIN.roundToInt()} minutes**. While you worked **${totalHours.roundToInt()}h**, it completed **$issOrbits full laps**. Perspective.",
            InsightColor.INDIGO,
        )

        val sharkKm = (totalHours * SHARK_KMH).roundToInt()
        if (sharkKm > 0) pool += DailyInsight(
            "🦈", "Shark Sprint",
            "A great white shark hits **${SHARK_KMH.roundToInt()} km/h**. In your **${totalHours.roundToInt()} working hours**, a shark could swim **$sharkKm km**. Don't go in the water.",
            InsightColor.ROSE,
        )

        val beeLifetimePct = (totalHours / BEE_LIFETIME_HRS * 100 * 10).roundToInt().toDouble() / 10.0
        if (beeLifetimePct > 0) pool += DailyInsight(
            "🐝", "Busy Bee",
            "A worker bee forages for ~**${BEE_LIFETIME_HRS.roundToInt()}h** in its lifetime. Your work this month = **$beeLifetimePct%** of a bee's full career. Bzz.",
            InsightColor.AMBER,
        )

        val shakespearePct = (totalHours / SHAKESPEARE_HRS * 100 * 10).roundToInt().toDouble() / 10.0
        if (shakespearePct > 0) pool += DailyInsight(
            "🎭", "Shakespeare Mode",
            "Experts estimate Shakespeare spent **${SHAKESPEARE_HRS.roundToInt().toLocaleString()}h** writing all his works. This month you completed **$shakespearePct%** of a Shakespeare.",
            InsightColor.VIOLET,
        )

        val guitarSolos = (totalMinutes * 60 / GUITAR_SOLO_SEC).roundToInt()
        if (guitarSolos > 0) pool += DailyInsight(
            "🎸", "Guitar God",
            "An average guitar solo lasts **${GUITAR_SOLO_SEC.roundToInt()}s**. You worked long enough for **$guitarSolos guitar solos**. Shred on.",
            InsightColor.ROSE,
        )

        val ramenBowls = (totalMinutes / RAMEN_MIN).roundToInt()
        if (ramenBowls > 0) pool += DailyInsight(
            "🍜", "Ramen Counter",
            "Instant ramen takes **${RAMEN_MIN.roundToInt()} minutes**. In your **${totalHours.roundToInt()}h** of work, you could have made **$ramenBowls bowls**. Budget living.",
            InsightColor.AMBER,
        )

        return pool
    }

    private fun Int.toLocaleString(): String = NumberFormat.getNumberInstance().format(this)
}
