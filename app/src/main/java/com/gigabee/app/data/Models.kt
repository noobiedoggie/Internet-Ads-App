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

/** The user's GigaBee eSIM. [lpa] is what QR codes and "enter code manually" use. */
data class EsimInfo(val iccid: String, val smdp: String, val code: String) {
    val lpa get() = "LPA:1$$smdp$$code"
}

data class AppState(
    val balanceKb: Long,
    val adsToday: Int,
    val dailyCap: Int,
    val kbPerAd: Long,
    val bundles: List<DataBundle>,
    val esim: EsimInfo? = null,
    val streak: Int = 0,
    val streakBonusPct: Int = 0,
    val nickname: String? = null,
    val referralCode: String? = null,
    val referred: Boolean = false,
    val referralPaid: Boolean = false,
    val canEnterReferral: Boolean = false,
    val lifetimeAds: Int = 0,
    val referralTarget: Int = 30,
    val referralKb: Long = 50 * KB_PER_MB,
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

data class LeaderboardEntry(val rank: Int, val nickname: String, val kb: Long, val me: Boolean)

data class Leaderboard(val top: List<LeaderboardEntry>, val myRank: Int?, val myKb: Long)
