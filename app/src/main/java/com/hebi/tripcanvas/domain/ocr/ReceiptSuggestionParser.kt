package com.hebi.tripcanvas.domain.ocr

import java.time.LocalDate

data class ReceiptSuggestion(
    val amount: String?,
    val currencyCode: String?,
    val date: String?,
    val merchant: String?
) {
    val hasApplicableFields: Boolean get() = amount != null || currencyCode != null || date != null
}

/**
 * Conservative receipt candidate parser. Never auto-saves expenses or makes up totals.
 * Ambiguous total amounts are intentionally left blank for manual entry.
 */
object ReceiptSuggestionParser {
    private val totalLabel = Regex("""(?i)(합\s*계|총\s*액|결제\s*금액|지불\s*금액|\btotal\b|\bamount paid\b)""")
    private val amountToken = Regex("""(?:₩|¥|€|\$)?\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\.([0-9]{1,2}))?\s*(원|KRW|USD|JPY|EUR|₩|¥|€|\$)?""")
    private val dateToken = Regex("""(?<!\d)(20\d{2})[./-](0?[1-9]|1[0-2])[./-](0?[1-9]|[12][0-9]|3[01])(?!\d)""")

    fun parse(raw: String): ReceiptSuggestion {
        val lines = raw.take(12_000).lineSequence().map(String::trim)
            .filter { it.isNotBlank() }.take(120).toList()
        val totalMatches = lines.mapNotNull { line ->
            val label = totalLabel.find(line) ?: return@mapNotNull null
            val suffix = line.substring(label.range.last + 1)
            val tokens = amountToken.findAll(suffix).map { match ->
                val amount = match.groupValues[1].replace(",", "") +
                    match.groupValues[2].takeIf { it.isNotEmpty() }?.let { "." + it }.orEmpty()
                val currency = determineCurrency(line)
                if (amount.toBigDecimalOrNull()?.signum() != 1) null
                else amount to currency
            }.filterNotNull().toList()
            tokens.singleOrNull()
        }.distinct()
        val amountCurrency = totalMatches.singleOrNull()
        val dates = lines.flatMap { line ->
            dateToken.findAll(line).mapNotNull { match ->
                runCatching {
                    LocalDate.of(
                        match.groupValues[1].toInt(),
                        match.groupValues[2].toInt(),
                        match.groupValues[3].toInt()
                    ).toString()
                }.getOrNull()
            }.toList()
        }.distinct()
        val merchant = lines.firstOrNull { line ->
            line.length in 2..60 && line.any(Char::isLetter) &&
                !totalLabel.containsMatchIn(line) &&
                dateToken.find(line) == null
        }?.take(60)
        return ReceiptSuggestion(
            amount = amountCurrency?.first,
            currencyCode = amountCurrency?.second,
            date = dates.singleOrNull(),
            merchant = merchant
        )
    }

    private fun determineCurrency(line: String): String? {
        val currencies = buildSet {
            if (line.contains('₩') || line.contains('원') || line.contains("KRW", true)) add("KRW")
            if (line.contains('$') || line.contains("USD", true)) add("USD")
            if (line.contains('¥') || line.contains("JPY", true)) add("JPY")
            if (line.contains('€') || line.contains("EUR", true)) add("EUR")
        }
        return currencies.singleOrNull()
    }
}
