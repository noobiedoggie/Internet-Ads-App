package com.gigabee.app.data

import java.text.NumberFormat
import java.util.Locale

// French writes octets: Mo / Go instead of MB / GB.
private val french get() = Locale.getDefault().language == "fr"

/** Splits an amount into number and unit, e.g. 348160 KB -> ("340", "MB"), 1.5 GB -> ("1.5", "GB"). */
fun dataParts(kb: Long): Pair<String, String> {
    val mb = kb.toDouble() / KB_PER_MB
    return if (mb >= 1024) {
        number(mb / 1024, if (mb / 1024 >= 10) 1 else 2) to (if (french) "Go" else "GB")
    } else {
        number(mb, if (mb >= 10) 0 else 1) to (if (french) "Mo" else "MB")
    }
}

fun formatData(kb: Long): String = dataParts(kb).let { "${it.first} ${it.second}" }

private fun number(value: Double, maxDecimals: Int): String =
    NumberFormat.getNumberInstance().apply {
        maximumFractionDigits = maxDecimals
        minimumFractionDigits = 0
    }.format(value)

/** Shows only the last 4 digits of a phone number. */
fun maskPhone(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    return if (digits.length <= 4) digits else "•••• " + digits.takeLast(4)
}
