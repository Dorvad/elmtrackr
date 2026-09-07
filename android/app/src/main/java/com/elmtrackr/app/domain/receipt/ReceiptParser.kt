package com.elmtrackr.app.domain.receipt

import com.elmtrackr.app.domain.model.ReceiptParseConfidence
import com.elmtrackr.app.domain.model.ReceiptParseResult
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Parses on-device OCR text from receipts into structured fields.
 * Optimized for Israeli-style receipts (Hebrew labels, ₪ amounts, dd/MM/yyyy dates).
 *
 * Total-amount extraction scores every numeric candidate on the receipt:
 * strong "total" labels (סה"כ, לתשלום, total …) dominate, lines that are known
 * to carry non-total numbers (מע"מ, עודף, subtotal …) are penalized, and
 * position (totals live near the bottom) plus the largest-amount heuristic
 * break the remaining ties.
 */
class ReceiptParser(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun parse(rawText: String): ReceiptParseResult {
        val normalized = normalize(rawText)
        if (normalized.isBlank()) {
            return emptyResult(rawText)
        }

        val lines = normalized.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val amountResult = extractAmount(lines)
        val dateResult = extractDate(lines)
        val merchant = extractMerchantName(lines)
        val currency = extractCurrency(lines, amountResult.currency)

        val confidence = computeConfidence(
            amount = amountResult.amount,
            amountNearTotal = amountResult.nearTotalKeyword,
            date = dateResult,
            merchant = merchant,
        )

        return ReceiptParseResult(
            merchantName = merchant,
            amount = amountResult.amount,
            currency = currency,
            receiptDate = dateResult,
            rawOcrText = rawText,
            confidence = confidence,
            parserVersion = VERSION,
            amountNearTotalKeyword = amountResult.nearTotalKeyword,
        )
    }

    private fun emptyResult(rawText: String) = ReceiptParseResult(
        merchantName = null,
        amount = null,
        currency = null,
        receiptDate = null,
        rawOcrText = rawText,
        confidence = ReceiptParseConfidence.NONE,
        parserVersion = VERSION,
    )

    internal fun normalize(text: String): String = text
        .replace('\u00A0', ' ')
        .replace('\u200F', ' ')
        .replace('\u200E', ' ')
        .replace('\u061C', ' ')
        .replace('\u05F4', '"') // Hebrew gershayim -> ASCII quote (OCR emits both for סה"כ)
        .replace('\u05F3', '\'') // Hebrew geresh
        .replace('\u201D', '"')
        .replace('\u201C', '"')
        .replace(Regex("[ \t]+"), " ")
        .trim()

    private data class AmountExtraction(
        val amount: Double?,
        val currency: String?,
        val nearTotalKeyword: Boolean,
    )

    private data class AmountCandidate(
        val value: Double,
        val score: Int,
        val nearTotal: Boolean,
    )

    private fun extractAmount(lines: List<String>): AmountExtraction {
        data class RawCandidate(
            val value: Double,
            val lineIndex: Int,
            val strongKeyword: Boolean,
            val adjacentKeyword: Boolean,
            val weakKeyword: Boolean,
            val negativeKeyword: Boolean,
            val currencyHint: Boolean,
            val hasDecimal: Boolean,
        )

        val raw = mutableListOf<RawCandidate>()

        lines.forEachIndexed { index, line ->
            if (isLikelyDateLine(line)) return@forEachIndexed
            val canonLine = canonical(line)
            val strong = containsAny(canonLine, STRONG_TOTAL_KEYWORDS)
            val adjacent = !strong && hasAdjacentTotalContext(lines, index)
            val weak = containsAny(canonLine, WEAK_TOTAL_KEYWORDS)
            val negative = isNegativeLine(canonLine)
            val currencyHint = hasCurrencyHint(line)

            AMOUNT_PATTERN.findAll(line).forEach { match ->
                if (isInsideDateMatch(line, match.range)) return@forEach
                val token = match.groupValues[1]
                val value = token.replace(",", "").toDoubleOrNull() ?: return@forEach
                if (value !in MIN_REASONABLE_AMOUNT..MAX_REASONABLE_AMOUNT) return@forEach
                val hasDecimal = token.contains('.')
                // A bare integer with no context is almost never the total (order
                // numbers, quantities, street numbers) — require some signal.
                if (!strong && !adjacent && !weak && !currencyHint && !hasDecimal) return@forEach
                raw.add(
                    RawCandidate(
                        value = value,
                        lineIndex = index,
                        strongKeyword = strong,
                        adjacentKeyword = adjacent,
                        weakKeyword = weak,
                        negativeKeyword = negative,
                        currencyHint = currencyHint,
                        hasDecimal = hasDecimal,
                    ),
                )
            }
        }

        if (raw.isEmpty()) return AmountExtraction(null, null, false)

        val maxValue = raw.maxOf { it.value }
        val candidates = raw.map { c ->
            var score = 0
            if (c.strongKeyword) score += 100
            if (c.adjacentKeyword) score += 60
            if (c.weakKeyword && !c.strongKeyword) score += 25
            if (c.negativeKeyword) score -= 80
            if (c.currencyHint) score += 15
            if (c.hasDecimal) score += 5
            if (c.value == maxValue) score += 20
            // Totals cluster at the bottom of receipts.
            if (lines.size > 1) score += (c.lineIndex * 10) / (lines.size - 1)
            AmountCandidate(
                value = c.value,
                score = score,
                nearTotal = (c.strongKeyword || c.adjacentKeyword) && !c.negativeKeyword,
            )
        }

        val best = candidates.maxWithOrNull(
            compareBy<AmountCandidate> { it.score }.thenBy { it.value },
        ) ?: return AmountExtraction(null, null, false)

        val currency = lines.firstNotNullOfOrNull { line ->
            CURRENCY_PATTERN.find(line)?.groupValues?.get(1)?.uppercase(Locale.US)
        }

        return AmountExtraction(best.value, currency, best.nearTotal)
    }

    /**
     * Whether this line's numbers are known *not* to be the receipt total.
     *
     * The tax rule is the whole reason this is a function rather than one
     * `containsAny`. An Israeli receipt states the tax three times:
     *
     * ```
     * סה"כ לפני מע"מ   100.00
     * מע"מ 18%          18.00
     * סה"כ כולל מע"מ   118.00
     * ```
     *
     * and the last of those is the number the user actually paid — the most
     * common way an Israeli total is written. Matching a bare "מע"מ" anywhere on
     * the line penalised it as if it were the tax line, so the correct total
     * scored barely above noise and, worse, came back with
     * `amountNearTotalKeyword = false`: it was still often picked by the
     * largest-amount tie-break, but at LOW confidence, and it lost every
     * arbitration against the Latin pass in [ReceiptParseResultMerger].
     *
     * So a tax mention is read with its qualifier. "כולל מע"מ" (including) is a
     * total; "לפני מע"מ" (before) is a subtotal and is penalised even though the
     * line also says סה"כ; a bare tax line is penalised as before.
     */
    private fun isNegativeLine(canonLine: String): Boolean {
        if (containsAny(canonLine, TAX_EXCLUSIVE_PHRASES)) return true
        if (containsAny(canonLine, NEGATIVE_KEYWORDS)) return true
        val mentionsTax = containsAny(canonLine, TAX_KEYWORDS)
        return mentionsTax && !containsAny(canonLine, TAX_INCLUSIVE_PHRASES)
    }

    private fun hasCurrencyHint(line: String): Boolean {
        if (line.contains('₪') || line.contains('$') || line.contains('€') || line.contains('£')) return true
        if (CURRENCY_PATTERN.containsMatchIn(line)) return true
        val tokens = canonical(line).split(Regex("[^\\p{L}\\p{N}]+"))
        return tokens.any { it == "שח" || it == "שקל" || it == "שקלים" }
    }

    private fun isNearTotalKeyword(line: String): Boolean {
        val canonLine = canonical(line)
        return containsAny(canonLine, STRONG_TOTAL_KEYWORDS) || containsAny(canonLine, WEAK_TOTAL_KEYWORDS)
    }

    private fun hasAdjacentTotalContext(lines: List<String>, index: Int): Boolean {
        val window = listOfNotNull(lines.getOrNull(index - 1), lines.getOrNull(index + 1))
        return window.any { containsAny(canonical(it), STRONG_TOTAL_KEYWORDS) }
    }

    private fun isLikelyDateLine(line: String): Boolean =
        DATE_PATTERNS.any { (pattern, _) -> pattern.containsMatchIn(line) } && !isNearTotalKeyword(line)

    private fun isInsideDateMatch(line: String, range: IntRange): Boolean =
        DATE_PATTERNS.any { (pattern, _) ->
            pattern.findAll(line).any { dateMatch -> range.first >= dateMatch.range.first && range.last <= dateMatch.range.last }
        }

    private fun extractDate(lines: List<String>): Instant? {
        val candidates = mutableListOf<Pair<Instant, Int>>()

        lines.forEachIndexed { index, line ->
            val nearDateLabel = isNearDateKeyword(line) ||
                listOfNotNull(lines.getOrNull(index - 1), lines.getOrNull(index + 1)).any(::isNearDateKeyword)

            DATE_PATTERNS.forEach { (pattern, formatter) ->
                pattern.findAll(line).forEach { match ->
                    val parsed = parseDate(match.value, formatter) ?: return@forEach
                    val score = if (nearDateLabel) 10 else 1
                    candidates.add(parsed to score)
                }
            }
        }

        return candidates.maxByOrNull { it.second }?.first
    }

    private fun parseDate(value: String, formatter: DateTimeFormatter): Instant? = try {
        when {
            value.contains(':') -> LocalDateTime.parse(value, formatter).atZone(zoneId).toInstant()
            else -> LocalDate.parse(value, formatter).atStartOfDay(zoneId).toInstant()
        }
    } catch (_: DateTimeParseException) {
        null
    }

    private fun isNearDateKeyword(line: String): Boolean {
        val lower = line.lowercase(Locale.US)
        return DATE_KEYWORDS.any { keyword ->
            lower.contains(keyword.lowercase(Locale.US)) || line.contains(keyword)
        }
    }

    private fun extractMerchantName(lines: List<String>): String? {
        val skipPatterns = listOf(
            Regex("^\\d+$"),
            Regex("^[₪$€£].*"),
            Regex("^\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4}"),
            Regex("^(tel|phone|fax|vat|ע\\.?מ\\.?|ח\\.?פ\\.?).*", RegexOption.IGNORE_CASE),
            Regex("^(קבלה|חשבונית מס|חשבונית|receipt|invoice|tax invoice)\\b.*", RegexOption.IGNORE_CASE),
        )

        return lines
            .take(MERCHANT_SCAN_LINES)
            .map { it.trim() }
            .firstOrNull { line ->
                line.length >= MIN_MERCHANT_LENGTH &&
                    line.any { it.isLetter() } &&
                    skipPatterns.none { it.containsMatchIn(line) } &&
                    !isNearTotalKeyword(line) &&
                    !AMOUNT_ONLY_LINE.matches(line)
            }
            ?.take(MAX_MERCHANT_LENGTH)
    }

    private fun extractCurrency(lines: List<String>, pendingCurrency: String?): String? {
        pendingCurrency?.let { return it }

        val joined = lines.joinToString("\n")
        // The shekel word is matched as a whole token, not a substring: "שח"
        // appears inside ordinary Hebrew words — משחק, שחור, משחקייה — and any
        // of them used to declare the receipt priced in shekels.
        val shekelWord = joined.split(Regex("[^\\p{L}]+")).any { it in SHEKEL_WORDS }
        return when {
            joined.contains('₪') || joined.contains("ILS", ignoreCase = true) ||
                joined.contains("NIS", ignoreCase = true) || shekelWord -> "ILS"
            joined.contains('$') || joined.contains("USD", ignoreCase = true) -> "USD"
            joined.contains('€') || joined.contains("EUR", ignoreCase = true) -> "EUR"
            joined.contains('£') || joined.contains("GBP", ignoreCase = true) -> "GBP"
            else -> null
        }
    }

    companion object {
        const val VERSION = "1.2.0"

        /**
         * Hebrew final letters, mapped to the base form they are confused with.
         *
         * Declared first because [canonical] reads it and the keyword lists below
         * fold themselves through [canonical] as they initialise.
         */
        private val HEBREW_FINAL_FORMS = mapOf(
            'ך' to 'כ',
            'ם' to 'מ',
            'ן' to 'נ',
            'ף' to 'פ',
            'ץ' to 'צ',
        )

        private const val MIN_REASONABLE_AMOUNT = 0.01
        private const val MAX_REASONABLE_AMOUNT = 50_000.0
        private const val MIN_MERCHANT_LENGTH = 3
        private const val MAX_MERCHANT_LENGTH = 80
        private const val MERCHANT_SCAN_LINES = 8

        internal fun computeConfidence(
            amount: Double?,
            amountNearTotal: Boolean,
            date: Instant?,
            merchant: String?,
        ): ReceiptParseConfidence = when {
            amount != null && amountNearTotal && date != null && merchant != null -> ReceiptParseConfidence.HIGH
            amount != null && (amountNearTotal || date != null) -> ReceiptParseConfidence.MEDIUM
            amount != null || date != null || merchant != null -> ReceiptParseConfidence.LOW
            else -> ReceiptParseConfidence.NONE
        }

        /**
         * Lowercased, quote-stripped, final-form-folded text for keyword matching.
         *
         * Quotes go because OCR mangles the gershayim in `סה"כ` every way there
         * is — ASCII quote, curly quote, U+05F4, or dropped entirely.
         *
         * Final letters fold to their base form because the five Hebrew finals
         * are the single most common confusion an OCR engine makes on receipt
         * type: `ך` for `כ`, `ם` for `מ`, and so on. `סה"ך` is not a word, but it
         * is what Tesseract reads off a faded thermal print often enough to
         * matter — and before this, that one glyph meant the total label was not
         * found at all and the amount was returned unlabelled at LOW confidence.
         * Folding costs nothing: no keyword here is distinguished by a final
         * form.
         */
        private fun canonical(line: String): String =
            line.lowercase(Locale.US)
                .replace(Regex("[\"'`’‘]"), "")
                .map { HEBREW_FINAL_FORMS[it] ?: it }
                .joinToString("")


        private fun containsAny(canonLine: String, keywords: List<String>): Boolean =
            keywords.any { canonLine.contains(it) }

        // Written as they are printed; stored canonical — lowercased, quotes
        // stripped, Hebrew final letters folded. See [canonical].
        private val STRONG_TOTAL_KEYWORDS = listOf(
            "סהכ",
            "סה כ",
            "סך הכל",
            "סך כל",
            "לתשלום",
            "סכום לחיוב",
            "סכום החיוב",
            "לחיוב",
            "סכום כולל",
            "שולם",
            "total",
            "grand total",
            "balance due",
            "amount due",
            "amount payable",
            "total due",
            "to pay",
        ).map(::canonical)

        /** A tax mention, which only says something once its qualifier is read. */
        private val TAX_KEYWORDS = listOf("מע\"מ", "vat").map(::canonical)

        /** Qualifiers that make a tax line the total. */
        private val TAX_INCLUSIVE_PHRASES = listOf(
            "כולל מע\"מ",
            "כולל מע מ",
            "כולל מעמ",
            "incl vat",
            "incl. vat",
            "including vat",
            "inc vat",
            "with vat",
        ).map(::canonical)

        /**
         * Qualifiers that make the line a pre-tax subtotal, penalised even when
         * it also carries a total label — `סה"כ לפני מע"מ` is a real receipt line
         * and it is not what the user paid.
         */
        private val TAX_EXCLUSIVE_PHRASES = listOf(
            "לפני מע\"מ",
            "לפני מעמ",
            "לא כולל מע\"מ",
            "before vat",
            "excl vat",
            "excl. vat",
            "excluding vat",
            "ex vat",
            "net of vat",
        ).map(::canonical)

        private val WEAK_TOTAL_KEYWORDS = listOf(
            "amount",
            "sum",
            "סכום",
        ).map(::canonical)

        // Lines whose numbers are known NOT to be the receipt total.
        private val NEGATIVE_KEYWORDS = listOf(
            "subtotal",
            "sub-total",
            "סכום ביניים",
            "ביניים",
            "עודף",
            "מזומן",
            "החזר",
            "הנחה",
            "change",
            "cash",
            "discount",
            "tip",
        ).map(::canonical)

        /** Whole-word forms of "shekel" as receipts print them, quotes already stripped. */
        private val SHEKEL_WORDS = setOf("שח", "שקל", "שקלים", "שהח").map(::canonical).toSet()

        private val DATE_KEYWORDS = listOf(
            "date",
            "issued",
            "time",
            "תאריך",
            "שעה",
        )

        /**
         * One money amount, with an optional currency mark on either side.
         *
         * The comma-grouped alternative takes `+`, not `*`, and that one
         * character is the difference between reading a receipt and misreading
         * it. Alternation is ordered: with `*` the grouped branch matched a bare
         * `5310.00` as its first three digits and the engine accepted it without
         * ever trying the plain branch, so **every total of a thousand or more
         * written without a thousands separator was truncated to a tenth of
         * itself** — 5310.00 read as 531, 4500.00 as 450, 12345.67 as 123 — with
         * the decimals silently dropped. Israeli thermal printers commonly omit
         * the separator, so this hit exactly the large receipts worth claiming.
         * Requiring a real comma group sends those to the plain branch instead.
         *
         * The trailing guard stops a partial match being accepted where the
         * number runs on past two decimal places.
         */
        private val AMOUNT_PATTERN = Regex(
            """(?<![\d.])(?:₪|ILS|NIS|\$|€|£)?\s*(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)(?!\d)\s*(?:₪|ILS|NIS|\$|€|£)?""",
            RegexOption.IGNORE_CASE,
        )

        private val CURRENCY_PATTERN = Regex("""(ILS|NIS|USD|EUR|GBP|CAD|AUD|JPY|CHF)""", RegexOption.IGNORE_CASE)

        private val AMOUNT_ONLY_LINE = Regex("""^[\d\s.,₪$€£ILSNIS-]+$""", RegexOption.IGNORE_CASE)

        private val DATE_PATTERNS = listOf(
            Regex("""\d{1,2}/\d{1,2}/\d{4}""") to DateTimeFormatter.ofPattern("d/M/yyyy"),
            Regex("""\d{1,2}/\d{1,2}/\d{2}""") to DateTimeFormatter.ofPattern("d/M/yy"),
            Regex("""\d{1,2}\.\d{1,2}\.\d{4}""") to DateTimeFormatter.ofPattern("d.M.yyyy"),
            Regex("""\d{1,2}-\d{1,2}-\d{4}""") to DateTimeFormatter.ofPattern("d-M-yyyy"),
            Regex("""\d{4}-\d{2}-\d{2}""") to DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            Regex("""\d{1,2}/\d{1,2}/\d{4}\s+\d{1,2}:\d{2}""") to DateTimeFormatter.ofPattern("d/M/yyyy H:mm"),
            Regex("""\d{1,2}\.\d{1,2}\.\d{4}\s+\d{1,2}:\d{2}""") to DateTimeFormatter.ofPattern("d.M.yyyy H:mm"),
        )
    }
}
