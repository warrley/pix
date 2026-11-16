package com.example.mobile.util

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private val brazil = Locale.forLanguageTag("pt-BR")

fun BigDecimal.toBrl(): String = NumberFormat.getCurrencyInstance(brazil).format(this)

/** Formats a backend decimal string ("1500.50") as "R$ 1.500,50". */
fun String.toBrl(): String = (toBigDecimalOrNull() ?: BigDecimal.ZERO).toBrl()

private val AMOUNT_INPUT = Regex("""^\d+([.,]\d{1,2})?$""")

/**
 * Converts user input ("10", "10,5", "10.50") into the backend's decimal-string format
 * ("10.00", "10.50"), or null if it is not a valid positive amount with at most two decimals.
 */
fun parseAmountInput(raw: String): String? {
    val text = raw.trim()
    if (!AMOUNT_INPUT.matches(text)) return null
    val value = text.replace(',', '.').toBigDecimal().setScale(2)
    return if (value.signum() > 0) value.toPlainString() else null
}

/** Normalizes a phone number to E.164 for Brazil (+55DDXXXXXXXXX); blank stays blank. */
fun normalizePhone(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    return if (raw.trim().startsWith("+")) "+$digits" else "+55$digits"
}
