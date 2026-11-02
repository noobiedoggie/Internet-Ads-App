package com.gigabee.app.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

/** Offline demo (no backend configured): honey, cards and the wheel all live on the phone. */
class LocalRepository(context: Context) : Repository {
    private val prefs = context.getSharedPreferences("gigabee_demo", Context.MODE_PRIVATE)
    private val kbPerAd = Economics.kbPerAd(
        Economics.DEMO_ECPM_USD, Economics.DEMO_COST_PER_GB_USD, Economics.DEMO_MARGIN,
    )
    private val honeyPerAd = (kbPerAd / 102.4).toLong()
    private val spinCost = 100L

    // Same prizes, odds and prices as the server.
    private val wheel = listOf(
        WheelPrize(2048, 3990, "common"), WheelPrize(5120, 3900, "common"), WheelPrize(10240, 1500, "uncommon"),
        WheelPrize(25600, 500, "rare"), WheelPrize(102400, 100, "epic"), WheelPrize(KB_PER_GB, 10, "legendary"),
    )
    private val shop = listOf(ShopItem(10240, 100), ShopItem(51200, 500), ShopItem(102400, 1000))

    private val bundles = listOf(
        DataBundle("demo-us-1g", "USA", "1 GB", KB_PER_GB),
        DataBundle("demo-eu-1g", "Europe", "1 GB", KB_PER_GB),
    )

    override val isDemo = true

    override suspend fun userId(): String =
        prefs.getString("uid", null) ?: UUID.randomUUID().toString().also { prefs.edit { putString("uid", it) } }

    private fun today() = LocalDate.now(ZoneOffset.UTC).toString()
    private fun honey() = prefs.getLong("honey", 0L)
    private fun nick() = prefs.getString("nick", null) ?: "LuckyBee123"

    private fun cardCounts(): MutableMap<Long, Int> {
        val o = JSONObject(prefs.getString("cards", "{}") ?: "{}")
        return o.keys().asSequence().associate { it.toLong() to o.getInt(it) }.toMutableMap()
    }

    private fun saveCards(map: Map<Long, Int>) {
        val o = JSONObject()
        map.filterValues { it > 0 }.forEach { (kb, n) -> o.put(kb.toString(), n) }
        prefs.edit { putString("cards", o.toString()) }
    }

    private fun addCard(kb: Long) = saveCards(cardCounts().also { it[kb] = (it[kb] ?: 0) + 1 })

    override suspend fun state(): AppState {
        val adsToday = if (prefs.getString("day", "") == today()) prefs.getInt("ads", 0) else 0
        val cards = cardCounts()
        return AppState(
            balanceKb = cards.entries.sumOf { it.key * it.value },
            adsToday = adsToday,
            dailyCap = Economics.DEMO_DAILY_CAP,
            kbPerAd = kbPerAd,
            bundles = emptyList(), // redemptions closed in the demo too: cards stay in the vault
            streak = if (adsToday > 0) 1 else 0,
            nickname = nick(),
            referralCode = "DEMO42",
            canEnterReferral = true,
            honey = honey(),
            honeyPerAd = honeyPerAd,
            spinCost = spinCost,
            wheel = wheel,
            shop = shop,
            cards = cards.entries.sortedByDescending { it.key }.map { CardStack(it.key, cardRarity(it.key), it.value) },
        )
    }

    override suspend fun history(): List<LedgerEntry> {
        val arr = JSONArray(prefs.getString("ledger", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            LedgerEntry(
                id = o.getString("id"),
                amount = o.getLong("amount"),
                kind = o.getString("kind"),
                note = o.optString("note").ifBlank { null },
                status = null,
                timeMillis = o.getLong("ts"),
                currency = "honey",
                cardKb = o.optLong("card", 0L).takeIf { it > 0 },
            )
        }
    }

    /** Adds [drops] honey (negative to spend) and logs it. */
    private fun addHoney(drops: Long, kind: String, note: String? = null, cardKb: Long? = null) {
        val old = JSONArray(prefs.getString("ledger", "[]"))
        val entry = JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("amount", drops)
            .put("kind", kind)
            .put("note", note ?: "")
            .put("ts", System.currentTimeMillis())
        if (cardKb != null) entry.put("card", cardKb)
        val updated = JSONArray().put(entry)
        for (i in 0 until minOf(old.length(), 199)) updated.put(old.get(i))
        prefs.edit {
            putLong("honey", honey() + drops)
            putString("ledger", updated.toString())
        }
    }

    override suspend fun onAdRewarded(previous: AppState): AppState {
        val current = state()
        if (current.capReached) return current
        addHoney(honeyPerAd, "ad")
        prefs.edit {
            putString("day", today())
            putInt("ads", current.adsToday + 1)
        }
        return state()
    }

    override suspend fun spin(): GameResult {
        if (honey() < spinCost) return GameResult.Error("honey")
        var roll = Random.nextInt(wheel.sumOf { it.weight })
        val prize = wheel.first { p -> (roll < p.weight).also { if (!it) roll -= p.weight } }
        addHoney(-spinCost, "spin", cardKb = prize.kb)
        addCard(prize.kb)
        return GameResult.Ok(prize.kb, prize.rarity, honey())
    }

    override suspend fun buyCard(kb: Long): GameResult {
        val item = shop.firstOrNull { it.kb == kb } ?: return GameResult.Error("item")
        if (honey() < item.cost) return GameResult.Error("honey")
        addHoney(-item.cost, "buy", cardKb = kb)
        addCard(kb)
        return GameResult.Ok(kb, cardRarity(kb), honey())
    }

    override suspend fun redeem(bundleId: String): RedeemResult = RedeemResult.Error("Demo")

    override suspend fun debugBonus() = addHoney(1000, "bonus")

    override suspend fun demoSurvey() = addHoney(1000, "survey", "Demo")

    override suspend fun leaderboard(): Leaderboard {
        val mine = prefs.getLong("honey_earned", 0L) + honey()
        val all = (listOf("TurboBee482" to 9000L, "GoldenHive117" to 6400L, "CosmicBuzz305" to 4100L) + (nick() to mine))
            .sortedByDescending { it.second }
        val top = all.mapIndexed { i, (n, score) -> LeaderboardEntry(i + 1, n, score, n == nick()) }
        return Leaderboard(top, top.firstOrNull { it.me }?.rank, mine)
    }

    override suspend fun redeemReferral(code: String): String? = "invalid"

    override suspend fun rerollNickname(): String {
        val nick = listOf("Swift", "Mega", "Neon", "Royal").random() + listOf("Bee", "Honey", "Queen").random() + (100..999).random()
        prefs.edit { putString("nick", nick) }
        return nick
    }
}
