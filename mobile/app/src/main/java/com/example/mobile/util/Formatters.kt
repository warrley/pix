package com.example.mobile.util

import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private val brazil = Locale.forLanguageTag("pt-BR")
private val saoPauloTz = TimeZone.getTimeZone("America/Sao_Paulo")
private val utcTz = TimeZone.getTimeZone("UTC")

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

fun todayIsoDate(nowMillis: Long = System.currentTimeMillis()): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = saoPauloTz }
    return formatter.format(nowMillis)
}

fun tomorrowIsoDate(nowMillis: Long = System.currentTimeMillis()): String {
    val cal = Calendar.getInstance(saoPauloTz).apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_MONTH, 1)
    }
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = saoPauloTz }
    return formatter.format(cal.time)
}

fun daysAgoIsoDate(days: Int, nowMillis: Long = System.currentTimeMillis()): String {
    val cal = Calendar.getInstance(saoPauloTz).apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_MONTH, -days)
    }
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = saoPauloTz }
    return formatter.format(cal.time)
}

fun formatIsoTimestampToBr(isoTimestamp: String?): String {
    val raw = isoTimestamp?.trim().orEmpty()
    if (raw.length < 10) return raw
    val dateBr = formatIsoDateToBr(raw.take(10))
    val timePart = raw.drop(11).take(5)
    return if (timePart.length == 5 && timePart[2] == ':') "$dateBr às $timePart" else dateBr
}

fun formatIsoDateToBr(isoDate: String): String {
    val clean = isoDate.trim().take(10)
    val parts = clean.split('-')
    if (parts.size != 3) return isoDate
    return "${parts[2]}/${parts[1]}/${parts[0]}"
}

fun utcMillisToIsoDate(utcMillis: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = utcTz }
    return formatter.format(utcMillis)
}

fun isoDateToUtcMillis(isoDate: String): Long? = runCatching {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = utcTz
        isLenient = false
    }
    formatter.parse(isoDate.trim())?.time
}.getOrNull()
