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
        .replace(Regex("[ \t]+"), " ")
        .trim()

    private data class AmountExtraction(
        val amount: Double?,
        val currency: String?,
        val nearTotalKeyword: Boolean,
    )

    private fun extractAmount(lines: List<String>): AmountExtraction {
        val candidates = mutableListOf<Triple<Double, Int, Boolean>>()

        lines.forEachIndexed { index, line ->
            if (isLikelyDateLine(line)) return@forEachIndexed
            val nearTotal = isNearTotalKeyword(line) || hasAdjacentTotalContext(lines, index)
            AMOUNT_PATTERN.findAll(line).forEach { match ->
                if (isInsideDateMatch(line, match.range)) return@forEach
                val value = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@forEach
                if (value in MIN_REASONABLE_AMOUNT..MAX_REASONABLE_AMOUNT) {
                    val hasCurrencyHint = line.contains('₪') || line.contains('$') || line.contains('€') ||
                        line.contains('£') || CURRENCY_PATTERN.containsMatchIn(line)
                    val hasDecimal = match.groupValues[1].contains('.') || match.groupValues[1].contains(',')
                    if (!nearTotal && !hasCurrencyHint && !hasDecimal) return@forEach
                    candidates.add(Triple(value, scoreAmountLine(line, nearTotal), nearTotal))
                }
            }
        }

        if (candidates.isEmpty()) return AmountExtraction(null, null, false)

        val best = candidates.maxWithOrNull(
            compareBy<Triple<Double, Int, Boolean>> { it.third }
                .thenBy { it.second }
                .thenBy { it.first },
        ) ?: return AmountExtraction(null, null, false)

        val currency = lines.firstNotNullOfOrNull { line ->
            CURRENCY_PATTERN.find(line)?.groupValues?.get(1)?.uppercase(Locale.US)
        }

        return AmountExtraction(best.first, currency, best.third)
    }

    private fun scoreAmountLine(line: String, nearTotal: Boolean): Int {
        var score = if (nearTotal) 100 else 0
        val lower = line.lowercase(Locale.US)
        TOTAL_KEYWORDS.forEach { keyword ->
            if (lower.contains(keyword.lowercase(Locale.US)) || line.contains(keyword)) {
                score += 50
            }
        }
        if (line.contains('₪') || line.contains("ILS", ignoreCase = true) || line.contains("NIS", ignoreCase = true)) {
            score += 10
        }
        return score
    }

    private fun isNearTotalKeyword(line: String): Boolean {
        val lower = line.lowercase(Locale.US)
        return TOTAL_KEYWORDS.any { keyword ->
            lower.contains(keyword.lowercase(Locale.US)) || line.contains(keyword)
        }
    }

    private fun hasAdjacentTotalContext(lines: List<String>, index: Int): Boolean {
        val window = listOfNotNull(lines.getOrNull(index - 1), lines.getOrNull(index + 1))
        return window.any(::isNearTotalKeyword)
    }

    private fun isLikelyDateLine(line: String): Boolean =
        DATE_PATTERNS.any { (pattern, _) -> pattern.containsMatchIn(line) } &&
            !TOTAL_KEYWORDS.any { keyword ->
                line.contains(keyword, ignoreCase = true) || line.contains(keyword)
            }

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

    private fun computeConfidence(
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

    companion object {
        const val VERSION = "1.0.0"

        private const val MIN_REASONABLE_AMOUNT = 0.01
        private const val MAX_REASONABLE_AMOUNT = 50_000.0
        private const val MIN_MERCHANT_LENGTH = 3
        private const val MAX_MERCHANT_LENGTH = 80
        private const val MERCHANT_SCAN_LINES = 8

        private val TOTAL_KEYWORDS = listOf(
            "total",
            "amount",
            "sum",
            "balance due",
            "grand total",
            "subtotal",
            "סה\"כ",
            "סהכ",
            "סה״כ",
            "לתשלום",
            "סך הכל",
            "סך הכל לתשלום",
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
