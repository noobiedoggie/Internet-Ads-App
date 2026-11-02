package com.gigabee.app.data

import kotlin.math.floor

/**
 * The one formula that keeps GigaBee profitable. The backend runs the same formula
 * (public.kb_per_ad in the SQL migration) per country, so the app never decides payouts
 * in production. These constants are only used by the offline demo.
 *
 *   reward per ad = (eCPM / 1000) x (1 - margin) / (cost of 1 KB of data)
 */
object Economics {
    const val DEMO_ECPM_USD = 4.0          // net rewarded-video eCPM you earn (check your AdMob report)
    const val DEMO_COST_PER_GB_USD = 0.80  // what 1 GB costs you through the top-up provider
    const val DEMO_MARGIN = 0.35           // share of ad revenue you keep
    const val DEMO_DAILY_CAP = 25          // ads per user per day

    fun kbPerAd(ecpmUsd: Double, costPerGbUsd: Double, margin: Double): Long {
        val payoutBudgetUsd = ecpmUsd / 1000.0 * (1.0 - margin)
        val costPerKbUsd = costPerGbUsd / KB_PER_GB
        return floor(payoutBudgetUsd / costPerKbUsd).toLong().coerceIn(256L, 50L * KB_PER_MB)
    }
}
