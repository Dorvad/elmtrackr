package com.elmtrackr.app.domain

import com.elmtrackr.app.R
import com.elmtrackr.app.domain.model.CompensationProfile
import com.elmtrackr.app.domain.model.PremiumProfile
import com.elmtrackr.app.domain.model.Shift
import com.elmtrackr.app.domain.model.UiText
import com.elmtrackr.app.domain.model.UserSettings
import java.time.Instant
import kotlin.math.roundToInt
import java.text.NumberFormat

enum class InsightColor { INDIGO, VIOLET, EMERALD, AMBER, ROSE, SKY }

data class DailyInsight(
    val icon: String,
    val title: UiText,
    /** Body text — segments wrapped in ** ** should render as bold. */
    val text: UiText,
    val color: InsightColor,
)

/**
 * Produces 4 daily-rotating insights from a pool of fun facts.
 * The 4 cards rotate together at UTC midnight — each one is distinct.
 * All references use Israeli/real-world context (same as the web app).
 * Titles and texts are string resources so they localize at render time.
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
    private const val EARTH_WALK_HRS   = 8015.0
    private const val DEGREE_HRS       = 4000.0
    private const val ARMY_SERVICE_HRS = 4_600.0
    private const val GUITAR_SOLO_SEC  = 30.0
    private const val RAMEN_MIN        = 3.0
    private const val COCA_COLA_ILS    = 6.5
    private const val MOVIE_TICKET_ILS = 45.0
    private const val BIG_MAC_ILS      = 55.0
    private const val NETFLIX_MONTH_ILS = 50.0
    private const val SPOTIFY_MONTH_ILS = 35.0
    private const val AVOCADO_ILS      = 5.0
    private const val BEER_BOTTLE_ILS  = 12.0
    private const val BUS_RIDE_MIN     = 45.0
    private const val PODCAST_MIN      = 42.0
    private const val LAUNDRY_MIN      = 90.0
    private const val DISHWASHER_MIN   = 120.0
    private const val EGGS_CARTON_ILS  = 18.0

    /**
     * Returns up to 4 insight cards for today, based on [totalMinutes] and
     * [completedShifts]. Returns a single fallback card if no shifts yet.
     */
    fun build(
        completedShifts: List<Shift>,
        settings: UserSettings,
        totalMinutes: Int,
        profiles: List<CompensationProfile> = emptyList(),
        premiumProfiles: List<PremiumProfile> = emptyList(),
        /**
         * The pay weeks [completedShifts] belong to. The insight cards quote a
         * month's gross, and without this they computed a *second*, context-free
         * one beside the figure on the card above them — so the two could differ
         * for any month whose first week straddles the 1st.
         */
        contextShifts: List<Shift> = completedShifts,
    ): List<DailyInsight> {
        val fallback = DailyInsight(
            icon  = "✨",
            title = UiText.Res(R.string.insight_fallback_title),
            text  = UiText.Res(R.string.insight_fallback_text),
            color = InsightColor.INDIGO,
        )
        // Employee-paid only, so the shift count stays consistent with
        // [totalMinutes], which the caller takes from the monthly report.
        val employeeShifts = completedShifts.employeePaidOnly()
        if (employeeShifts.isEmpty() || totalMinutes == 0) return listOf(fallback)

        val pool = buildPool(
            employeeShifts, settings, totalMinutes, profiles, premiumProfiles, contextShifts,
        )
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
        premiumProfiles: List<PremiumProfile>,
        contextShifts: List<Shift>,
    ): List<DailyInsight> {
        val totalHours = totalMinutes / 60.0
        val hoursInt = totalHours.roundToInt()
        val shiftCount = shifts.size
        val totalGross = PayrollCalculator.sumMonthlyPay(shifts, settings, profiles, premiumProfiles, contextShifts).totalGross

        val pool = mutableListOf<DailyInsight>()

        fun card(icon: String, titleRes: Int, textRes: Int, color: InsightColor, vararg args: Any) {
            pool += DailyInsight(icon, UiText.Res(titleRes), UiText.Res(textRes, args.toList()), color)
        }

        // ── Work-hours comparisons ────────────────────────────

        val workdays = (totalHours / AVG_WORKDAY_HRS).roundToInt()
        if (workdays > 0) card(
            "📅", R.string.insight_workdays_title, R.string.insight_workdays_text,
            InsightColor.INDIGO, hoursInt, workdays, AVG_WORKDAY_HRS.toString(),
        )

        val movies = (totalHours / AVG_MOVIE_HRS).roundToInt()
        if (movies > 0) card(
            "🎬", R.string.insight_movies_title, R.string.insight_movies_text,
            InsightColor.VIOLET, hoursInt, movies,
        )

        val books = (totalHours / AVG_BOOK_HRS).roundToInt()
        if (books > 0) card(
            "📚", R.string.insight_books_title, R.string.insight_books_text,
            InsightColor.EMERALD, hoursInt, books,
        )

        val drivesEilat = (totalHours / TEL_EILAT_HRS).roundToInt()
        if (drivesEilat > 0) card(
            "🚗", R.string.insight_eilat_title, R.string.insight_eilat_text,
            InsightColor.EMERALD, drivesEilat,
        )

        val coffees = (totalMinutes / COFFEE_MIN).roundToInt()
        if (coffees > 0) card(
            "☕", R.string.insight_coffee_title, R.string.insight_coffee_text,
            InsightColor.AMBER, hoursInt, coffees,
        )

        val flights = (totalHours / TLV_JFK_HRS).roundToInt()
        if (flights > 0) card(
            "✈️", R.string.insight_flights_title, R.string.insight_flights_text,
            InsightColor.SKY, hoursInt, flights,
        )

        val bbRuns = (totalHours / BREAKING_BAD_HRS * 10).roundToInt().toDouble() / 10.0
        if (bbRuns >= 0.5) card(
            "📺", R.string.insight_binge_title, R.string.insight_binge_text,
            InsightColor.VIOLET, hoursInt, bbRuns.toString(), BREAKING_BAD_HRS.roundToInt(),
        )

        // ── Shift-count fun facts ─────────────────────────────

        if (shiftCount == 1) card(
            "🏖️", R.string.insight_beach_title, R.string.insight_beach_text_one,
            InsightColor.SKY,
        ) else if (shiftCount > 1) card(
            "🏖️", R.string.insight_beach_title, R.string.insight_beach_text_other,
            InsightColor.SKY, shiftCount,
        )

        val surfSessions = (totalHours / SURF_HRS).roundToInt()
        if (surfSessions > 0) card(
            "🏄", R.string.insight_surf_title, R.string.insight_surf_text,
            InsightColor.SKY, SURF_HRS.toString(), surfSessions,
        )

        val monopolyGames = (totalHours / MONOPOLY_HRS).roundToInt()
        if (monopolyGames > 0) card(
            "🎲", R.string.insight_monopoly_title, R.string.insight_monopoly_text,
            InsightColor.VIOLET, MONOPOLY_HRS.roundToInt(), monopolyGames,
        )

        // ── Israeli context ───────────────────────────────────

        val shwarmas = (totalMinutes / SHWARMA_MIN).roundToInt()
        if (shwarmas > 0) card(
            "🥙", R.string.insight_shwarma_title, R.string.insight_shwarma_text,
            InsightColor.AMBER, SHWARMA_MIN.roundToInt(), shwarmas,
        )

        val pizzas = if (totalGross > 0) (totalGross / PIZZA_PRICE_ILS).roundToInt() else 0
        if (pizzas > 0) card(
            "🍕", R.string.insight_pizza_title, R.string.insight_pizza_text,
            InsightColor.AMBER, pizzas, PIZZA_PRICE_ILS.roundToInt(),
        )

        val cokes = if (totalGross > 0) (totalGross / COCA_COLA_ILS).roundToInt() else 0
        if (cokes > 0) card(
            "🥤", R.string.insight_coke_title, R.string.insight_coke_text,
            InsightColor.ROSE, cokes, "%.2f".format(java.util.Locale.US, COCA_COLA_ILS),
        )

        val movieTickets = if (totalGross > 0) (totalGross / MOVIE_TICKET_ILS).roundToInt() else 0
        if (movieTickets > 0) card(
            "🎟️", R.string.insight_cinema_title, R.string.insight_cinema_text,
            InsightColor.VIOLET, movieTickets, MOVIE_TICKET_ILS.roundToInt(),
        )

        val bigMacs = if (totalGross > 0) (totalGross / BIG_MAC_ILS).roundToInt() else 0
        if (bigMacs > 0) card(
            "🍔", R.string.insight_burger_title, R.string.insight_burger_text,
            InsightColor.AMBER, bigMacs, BIG_MAC_ILS.roundToInt(),
        )

        val netflixMonths = if (totalGross > 0) (totalGross / NETFLIX_MONTH_ILS).roundToInt() else 0
        if (netflixMonths > 0) card(
            "📺", R.string.insight_netflix_title, R.string.insight_netflix_text,
            InsightColor.VIOLET, netflixMonths, NETFLIX_MONTH_ILS.roundToInt(),
        )

        val spotifyMonths = if (totalGross > 0) (totalGross / SPOTIFY_MONTH_ILS).roundToInt() else 0
        if (spotifyMonths > 0) card(
            "🎧", R.string.insight_spotify_title, R.string.insight_spotify_text,
            InsightColor.EMERALD, spotifyMonths, SPOTIFY_MONTH_ILS.roundToInt(),
        )

        val avocados = if (totalGross > 0) (totalGross / AVOCADO_ILS).roundToInt() else 0
        if (avocados > 0) card(
            "🥑", R.string.insight_avocado_title, R.string.insight_avocado_text,
            InsightColor.EMERALD, avocados, AVOCADO_ILS.roundToInt(),
        )

        val beers = if (totalGross > 0) (totalGross / BEER_BOTTLE_ILS).roundToInt() else 0
        if (beers > 0) card(
            "🍺", R.string.insight_beer_title, R.string.insight_beer_text,
            InsightColor.AMBER, beers, BEER_BOTTLE_ILS.roundToInt(),
        )

        val eggCartons = if (totalGross > 0) (totalGross / EGGS_CARTON_ILS).roundToInt() else 0
        if (eggCartons > 0) card(
            "🥚", R.string.insight_eggs_title, R.string.insight_eggs_text,
            InsightColor.AMBER, eggCartons, EGGS_CARTON_ILS.roundToInt(),
        )

        val busRides = (totalMinutes / BUS_RIDE_MIN).roundToInt()
        if (busRides > 0) card(
            "🚌", R.string.insight_bus_title, R.string.insight_bus_text,
            InsightColor.SKY, BUS_RIDE_MIN.roundToInt(), busRides,
        )

        val podcasts = (totalMinutes / PODCAST_MIN).roundToInt()
        if (podcasts > 0) card(
            "🎙️", R.string.insight_podcast_title, R.string.insight_podcast_text,
            InsightColor.INDIGO, PODCAST_MIN.roundToInt(), hoursInt, podcasts,
        )

        val laundryCycles = (totalMinutes / LAUNDRY_MIN).roundToInt()
        if (laundryCycles > 0) card(
            "🧺", R.string.insight_laundry_title, R.string.insight_laundry_text,
            InsightColor.SKY, LAUNDRY_MIN.roundToInt(), laundryCycles,
        )

        val dishwasherRuns = (totalMinutes / DISHWASHER_MIN).roundToInt()
        if (dishwasherRuns > 0) card(
            "🍽️", R.string.insight_dishes_title, R.string.insight_dishes_text,
            InsightColor.SKY, DISHWASHER_MIN.roundToInt(), dishwasherRuns,
        )

        val armyPct = (totalHours / ARMY_SERVICE_HRS * 100).roundToInt()
        if (armyPct in 1..100) card(
            "🎖️", R.string.insight_army_title, R.string.insight_army_text,
            InsightColor.INDIGO, ARMY_SERVICE_HRS.roundToInt(), armyPct,
        )

        val hatikvaPlays = (totalMinutes / HATIKVA_MIN).roundToInt()
        if (hatikvaPlays > 0) card(
            "🎵", R.string.insight_hatikva_title, R.string.insight_hatikva_text,
            InsightColor.INDIGO, HATIKVA_MIN.toString(), hatikvaPlays,
        )

        // ── Nature + absurd ───────────────────────────────────

        val snailKm = (totalHours * SNAIL_KMH * 10).roundToInt().toDouble() / 10.0
        if (snailKm > 0) card(
            "🐌", R.string.insight_snail_title, R.string.insight_snail_text,
            InsightColor.EMERALD, SNAIL_KMH.toString(), hoursInt, snailKm.toString(),
        )

        val issOrbits = (totalMinutes / ISS_ORBIT_MIN).roundToInt()
        if (issOrbits > 0) card(
            "🚀", R.string.insight_iss_title, R.string.insight_iss_text,
            InsightColor.INDIGO, ISS_ORBIT_MIN.roundToInt(), hoursInt, issOrbits,
        )

        val sharkKm = (totalHours * SHARK_KMH).roundToInt()
        if (sharkKm > 0) card(
            "🦈", R.string.insight_shark_title, R.string.insight_shark_text,
            InsightColor.ROSE, SHARK_KMH.roundToInt(), hoursInt, sharkKm,
        )

        val beeLifetimePct = (totalHours / BEE_LIFETIME_HRS * 100 * 10).roundToInt().toDouble() / 10.0
        if (beeLifetimePct > 0) card(
            "🐝", R.string.insight_bee_title, R.string.insight_bee_text,
            InsightColor.AMBER, BEE_LIFETIME_HRS.roundToInt(), beeLifetimePct.toString(),
        )

        val shakespearePct = (totalHours / SHAKESPEARE_HRS * 100 * 10).roundToInt().toDouble() / 10.0
        if (shakespearePct > 0) card(
            "🎭", R.string.insight_shakespeare_title, R.string.insight_shakespeare_text,
            InsightColor.VIOLET, SHAKESPEARE_HRS.roundToInt().toLocaleString(), shakespearePct.toString(),
        )

        val earthPct = (totalHours / EARTH_WALK_HRS * 100 * 10).roundToInt().toDouble() / 10.0
        if (earthPct > 0) card(
            "🌍", R.string.insight_earth_title, R.string.insight_earth_text,
            InsightColor.EMERALD, EARTH_WALK_HRS.roundToInt().toLocaleString(), earthPct.toString(),
        )

        val degreePct = (totalHours / DEGREE_HRS * 100 * 10).roundToInt().toDouble() / 10.0
        if (degreePct > 0) card(
            "🎓", R.string.insight_degree_title, R.string.insight_degree_text,
            InsightColor.INDIGO, DEGREE_HRS.roundToInt().toLocaleString(), degreePct.toString(),
        )

        val guitarSolos = (totalMinutes * 60 / GUITAR_SOLO_SEC).roundToInt()
        if (guitarSolos > 0) card(
            "🎸", R.string.insight_guitar_title, R.string.insight_guitar_text,
            InsightColor.ROSE, GUITAR_SOLO_SEC.roundToInt(), guitarSolos,
        )

        val ramenBowls = (totalMinutes / RAMEN_MIN).roundToInt()
        if (ramenBowls > 0) card(
            "🍜", R.string.insight_ramen_title, R.string.insight_ramen_text,
            InsightColor.AMBER, RAMEN_MIN.roundToInt(), hoursInt, ramenBowls,
        )

        return pool
    }

    private fun Int.toLocaleString(): String = NumberFormat.getNumberInstance().format(this)
}
