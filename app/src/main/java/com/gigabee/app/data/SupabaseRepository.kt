package com.gigabee.app.data

import android.content.Context
import android.telephony.TelephonyManager
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Production repository backed by Supabase (see /backend).
 * Users get an anonymous account on first launch; balances live in Postgres and only the
 * server can add MB (after AdMob's signed server-side-verification callback).
 */
class SupabaseRepository(
    context: Context,
    private val baseUrl: String,
    private val anonKey: String,
) : Repository {
    private val prefs = context.getSharedPreferences("gigabee_auth", Context.MODE_PRIVATE)
    private val country = detectCountry(context)
    private val authLock = Mutex()

    override val isDemo = false

    // ---------- auth ----------

    private suspend fun accessToken(forceRefresh: Boolean = false): String = authLock.withLock {
        val token = prefs.getString("access", null)
        val expiresAt = prefs.getLong("exp", 0L)
        val now = System.currentTimeMillis() / 1000
        if (!forceRefresh && token != null && expiresAt - 60 > now) return@withLock token

        val refresh = prefs.getString("refresh", null)
        if (refresh != null) {
            try {
                return@withLock saveSession(
                    call("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh), null),
                )
            } catch (e: HttpException) {
                // Only a rejected refresh token means "start over". Network errors must bubble up,
                // otherwise a bad connection would silently create a new, empty account.
                if (e.code !in 400..499) throw e
            }
        }
        saveSession(call("POST", "/auth/v1/signup", JSONObject().put("data", JSONObject()), null))
    }

    private fun saveSession(body: String): String {
        val json = JSONObject(body)
        val token = json.getString("access_token")
        prefs.edit {
            putString("access", token)
            putString("refresh", json.getString("refresh_token"))
            putLong("exp", System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600))
            putString("uid", json.getJSONObject("user").getString("id"))
        }
        return token
    }

    override suspend fun userId(): String {
        accessToken()
        return prefs.getString("uid", null) ?: error("No user id")
    }

    // ---------- data ----------

    override suspend fun state(): AppState {
        val json = JSONObject(authed("POST", "/rest/v1/rpc/app_state", JSONObject().put("p_country", country)))
        val arr = json.optJSONArray("bundles") ?: JSONArray()
        return AppState(
            balanceKb = json.getLong("balance_kb"),
            adsToday = json.getInt("ads_today"),
            dailyCap = json.getInt("daily_cap"),
            kbPerAd = json.getLong("kb_per_ad"),
            bundles = (0 until arr.length()).map { i ->
                val b = arr.getJSONObject(i)
                DataBundle(b.getString("id"), b.getString("carrier"), b.getString("label"), b.getLong("data_kb"))
            },
        )
    }

    override suspend fun history(): List<LedgerEntry> {
        val arr = JSONArray(authed("POST", "/rest/v1/rpc/my_history", JSONObject().put("p_limit", 100)))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            LedgerEntry(
                id = o.get("id").toString(),
                deltaKb = o.getLong("delta_kb"),
                kind = o.getString("kind"),
                note = o.optString("note").takeUnless { it.isBlank() || it == "null" },
                status = o.optString("status").takeUnless { it.isBlank() || it == "null" },
                timeMillis = o.getLong("ts"),
            )
        }
    }

    override suspend fun onAdRewarded(previous: AppState): AppState {
        // AdMob usually calls the SSV endpoint within a second or two.
        repeat(8) { attempt ->
            delay(if (attempt == 0) 1200 else 1500)
            val s = state()
            if (s.balanceKb != previous.balanceKb || s.adsToday != previous.adsToday) return s
        }
        return state()
    }

    override suspend fun redeem(bundleId: String, phone: String): RedeemResult = try {
        val body = JSONObject(
            authed("POST", "/functions/v1/redeem", JSONObject().put("bundle_id", bundleId).put("phone", phone)),
        )
        if (body.optBoolean("ok")) RedeemResult.Ok else RedeemResult.Error(body.optString("message", "Error"))
    } catch (e: HttpException) {
        RedeemResult.Error(e.message?.substringAfter("\"message\":\"")?.substringBefore('"') ?: "Error")
    }

    // ---------- http ----------

    private suspend fun authed(method: String, path: String, body: JSONObject?): String = try {
        call(method, path, body, accessToken())
    } catch (e: HttpException) {
        if (e.code != 401) throw e
        call(method, path, body, accessToken(forceRefresh = true))
    }

    private suspend fun call(method: String, path: String, body: JSONObject?, token: String?): String =
        withContext(Dispatchers.IO) {
            val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("apikey", anonKey)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                if (token != null) setRequestProperty("Authorization", "Bearer $token")
            }
            try {
                if (body != null) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw HttpException(code, text)
                text
            } finally {
                conn.disconnect()
            }
        }

    private fun detectCountry(context: Context): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val iso = tm?.simCountryIso?.takeIf { it.length == 2 }
            ?: tm?.networkCountryIso?.takeIf { it.length == 2 }
            ?: Locale.getDefault().country
        return iso.uppercase(Locale.ROOT)
    }
}
