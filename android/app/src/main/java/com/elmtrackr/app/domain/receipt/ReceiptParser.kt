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
            val negative = containsAny(canonLine, NEGATIVE_KEYWORDS)
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
        return when {
            joined.contains('₪') || joined.contains("ILS", ignoreCase = true) ||
                joined.contains("NIS", ignoreCase = true) || joined.contains("ש\"ח") ||
                joined.contains("שח") -> "ILS"
            joined.contains('$') || joined.contains("USD", ignoreCase = true) -> "USD"
            joined.contains('€') || joined.contains("EUR", ignoreCase = true) -> "EUR"
            joined.contains('£') || joined.contains("GBP", ignoreCase = true) -> "GBP"
            else -> null
        }
    }

    companion object {
        const val VERSION = "1.1.0"

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

        /** Lowercased, quote-stripped form used for keyword matching (OCR mangles quotes in סה"כ). */
        private fun canonical(line: String): String =
            line.lowercase(Locale.US).replace(Regex("[\"'`’‘]"), "")

        private fun containsAny(canonLine: String, keywords: List<String>): Boolean =
            keywords.any { canonLine.contains(it) }

        // Stored in canonical form: lowercase, quotes stripped.
        private val STRONG_TOTAL_KEYWORDS = listOf(
            "סהכ",
            "סה כ",
            "סך הכל",
            "סך כל",
            "לתשלום",
            "סכום לחיוב",
            "לחיוב",
            "שולם",
            "total",
            "grand total",
            "balance due",
            "amount due",
            "to pay",
        )

        private val WEAK_TOTAL_KEYWORDS = listOf(
            "amount",
            "sum",
            "סכום",
        )

        // Lines whose numbers are known NOT to be the receipt total.
        private val NEGATIVE_KEYWORDS = listOf(
            "subtotal",
            "sub-total",
            "סכום ביניים",
            "ביניים",
            "מעמ",
            "vat",
            "עודף",
            "מזומן",
            "החזר",
            "הנחה",
            "change",
            "cash",
            "discount",
            "tip",
        )

        private val DATE_KEYWORDS = listOf(
            "date",
            "issued",
            "time",
            "תאריך",
            "שעה",
        )

        private val AMOUNT_PATTERN = Regex(
            """(?<![\d.])(?:₪|ILS|NIS|\$|€|£)?\s*(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)\s*(?:₪|ILS|NIS|\$|€|£)?""",
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
