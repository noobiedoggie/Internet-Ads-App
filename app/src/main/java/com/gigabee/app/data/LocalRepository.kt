package com.gigabee.app.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** Offline demo: everything is stored on the phone and redemptions are simulated. */
class LocalRepository(context: Context) : Repository {
    private val prefs = context.getSharedPreferences("gigabee_demo", Context.MODE_PRIVATE)
    private val kbPerAd = Economics.kbPerAd(
        Economics.DEMO_ECPM_USD, Economics.DEMO_COST_PER_GB_USD, Economics.DEMO_MARGIN,
    )
    private val bundles = listOf(
        DataBundle("demo-500m", "Demo", "500 MB", 500 * KB_PER_MB),
        DataBundle("demo-1g", "Demo", "1 GB", KB_PER_GB),
        DataBundle("demo-3g", "Demo", "3 GB", 3 * KB_PER_GB),
    )

    override val isDemo = true

    override suspend fun userId(): String =
        prefs.getString("uid", null) ?: UUID.randomUUID().toString().also { prefs.edit { putString("uid", it) } }

    private fun today() = LocalDate.now(ZoneOffset.UTC).toString()

    override suspend fun state(): AppState {
        val adsToday = if (prefs.getString("day", "") == today()) prefs.getInt("ads", 0) else 0
        return AppState(
            balanceKb = prefs.getLong("balance", 0L),
            adsToday = adsToday,
            dailyCap = Economics.DEMO_DAILY_CAP,
            kbPerAd = kbPerAd,
            bundles = bundles,
        )
    }

    override suspend fun history(): List<LedgerEntry> {
        val arr = JSONArray(prefs.getString("ledger", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            LedgerEntry(
                id = o.getString("id"),
                deltaKb = o.getLong("delta"),
                kind = o.getString("kind"),
                note = o.optString("note").ifBlank { null },
                status = o.optString("status").ifBlank { null },
                timeMillis = o.getLong("ts"),
            )
        }
    }

    private fun addEntry(deltaKb: Long, kind: String, note: String? = null, status: String? = null) {
        val old = JSONArray(prefs.getString("ledger", "[]"))
        val updated = JSONArray().put(
            JSONObject()
                .put("id", UUID.randomUUID().toString())
                .put("delta", deltaKb)
                .put("kind", kind)
                .put("note", note ?: "")
                .put("status", status ?: "")
                .put("ts", System.currentTimeMillis()),
        )
        for (i in 0 until minOf(old.length(), 199)) updated.put(old.get(i))
        prefs.edit {
            putLong("balance", prefs.getLong("balance", 0L) + deltaKb)
            putString("ledger", updated.toString())
        }
    }

    override suspend fun onAdRewarded(previous: AppState): AppState {
        val current = state()
        if (current.capReached) return current
        addEntry(kbPerAd, "ad")
        prefs.edit {
            putString("day", today())
            putInt("ads", current.adsToday + 1)
        }
        return state()
    }

    override suspend fun redeem(bundleId: String, phone: String): RedeemResult {
        val bundle = bundles.firstOrNull { it.id == bundleId } ?: return RedeemResult.Error("Unknown bundle")
        if (prefs.getLong("balance", 0L) < bundle.dataKb) return RedeemResult.Error("Not enough MB yet")
        addEntry(-bundle.dataKb, "redeem", "${bundle.label} → ${maskPhone(phone)}", "success")
        return RedeemResult.Ok
    }

    override suspend fun debugBonus() = addEntry(100 * KB_PER_MB, "bonus")
}
