package com.gigabee.app.data

/** Data amounts are kept in kilobytes; honey is counted in drops (1 drop = 0.1 MB of value). */
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

/** One wheel prize. Chance = weight / sum of all weights. */
data class WheelPrize(val kb: Long, val weight: Int, val rarity: String)

/** Direct swap: [cost] honey drops for a [kb] co card. */
data class ShopItem(val kb: Long, val cost: Long)

/** [count] unused co cards of size [kb]. */
data class CardStack(val kb: Long, val rarity: String, val count: Int)

data class AppState(
    val balanceKb: Long, // total co (sum of unused cards)
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
    val honey: Long = 0,
    val honeyPerAd: Long = 0,
    val spinCost: Long = 100,
    val referralHoney: Long = 500,
    val wheel: List<WheelPrize> = emptyList(),
    val shop: List<ShopItem> = emptyList(),
    val cards: List<CardStack> = emptyList(),
) {
    val capReached get() = adsToday >= dailyCap
    val canSpin get() = honey >= spinCost
}

data class LedgerEntry(
    val id: String,
    val amount: Long, // drops when currency = "honey", KB when currency = "kb"
    val kind: String, // ad | bonus | survey | spin | buy | redeem | refund
    val note: String?,
    val status: String?, // for redeem: pending | success | failed
    val timeMillis: Long,
    val currency: String = "kb",
    val cardKb: Long? = null, // card won (spin) or bought (buy)
)

sealed interface RedeemResult {
    data object Ok : RedeemResult
    data class Error(val message: String) : RedeemResult
}

/** Result of a wheel spin or a honey swap. */
sealed interface GameResult {
    data class Ok(val kb: Long, val rarity: String, val honeyLeft: Long) : GameResult
    data class Error(val reason: String) : GameResult
}

class HttpException(val code: Int, body: String) : Exception("HTTP $code: ${body.take(300)}")

data class LeaderboardEntry(val rank: Int, val nickname: String, val score: Long, val me: Boolean)

data class Leaderboard(val top: List<LeaderboardEntry>, val myRank: Int?, val myScore: Long)

/** Same tiers as the server's card_rarity(). */
fun cardRarity(kb: Long): String = when {
    kb >= 512_000 -> "legendary"
    kb >= 102_400 -> "epic"
    kb >= 25_600 -> "rare"
    kb >= 10_240 -> "uncommon"
    else -> "common"
}
