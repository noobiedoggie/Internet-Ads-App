package com.gigabee.app.data

interface Repository {
    /** True when running without a backend: balance lives on the phone and nothing is really sent. */
    val isDemo: Boolean

    /** Stable id sent to AdMob for server-side verification of rewards. */
    suspend fun userId(): String

    suspend fun state(): AppState

    suspend fun history(): List<LedgerEntry>

    /**
     * Called after the ad SDK reports that the user earned the reward.
     * Demo: credits locally. Backend: AdMob calls our server directly (SSV), so we just wait
     * for the credit to show up. The client can never grant itself MB.
     */
    suspend fun onAdRewarded(previous: AppState): AppState

    suspend fun redeem(bundleId: String, phone: String): RedeemResult

    /** Debug builds only: lets you test redemption without watching hundreds of ads. */
    suspend fun debugBonus() {}
}
