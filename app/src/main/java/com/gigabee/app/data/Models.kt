package com.gigabee.app.data

/** All data amounts are kept in kilobytes so per-ad rewards like 3.3 MB stay exact. */
const val KB_PER_MB = 1024L
const val KB_PER_GB = 1024L * 1024L

data class DataBundle(
    val id: String,
    val carrier: String,
    val label: String,
    val dataKb: Long,
)

data class AppState(
    val balanceKb: Long,
    val adsToday: Int,
    val dailyCap: Int,
    val kbPerAd: Long,
    val bundles: List<DataBundle>,
) {
    val capReached get() = adsToday >= dailyCap
    private val sizes get() = bundles.map { it.dataKb }.distinct().sorted()

    /** The next bundle the user is working towards (drives the progress ring). */
    val goalKb: Long
        get() = sizes.firstOrNull { it > balanceKb } ?: sizes.lastOrNull() ?: KB_PER_GB

    /** Smallest bundle, if the user can already afford it. */
    val redeemable: Long?
        get() = sizes.firstOrNull()?.takeIf { it <= balanceKb }
}

data class LedgerEntry(
    val id: String,
    val deltaKb: Long,
    val kind: String, // ad | redeem | refund | bonus
    val note: String?,
    val status: String?, // for redeem: pending | success | failed
    val timeMillis: Long,
)

sealed interface RedeemResult {
    data object Ok : RedeemResult
    data class Error(val message: String) : RedeemResult
}

class HttpException(val code: Int, body: String) : Exception("HTTP $code: ${body.take(300)}")
