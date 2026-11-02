package com.gigabee.app.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Keeps one rewarded ad preloaded so the button reacts instantly. Call from the main thread. */
class RewardedAds(private val context: Context, private val adUnitId: String) {
    private val main = Handler(Looper.getMainLooper())
    private var ad: RewardedAd? = null
    private var loading = false
    private var failures = 0

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    fun load() {
        if (loading || ad != null) return
        loading = true
        RewardedAd.load(context, adUnitId, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(loaded: RewardedAd) {
                ad = loaded
                loading = false
                failures = 0
                _ready.value = true
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                loading = false
                _ready.value = false
                failures++
                // Back off 2s, 4s, 8s ... up to 2 min so we don't hammer the network with no fill.
                main.postDelayed({ load() }, (1000L shl failures.coerceAtMost(7)).coerceAtMost(120_000L))
            }
        })
    }

    /** Shows the ad and suspends until it is closed. Returns true if the user earned the reward. */
    suspend fun show(activity: Activity, userId: String): Boolean = suspendCancellableCoroutine { cont ->
        val current = ad
        if (current == null) {
            load()
            cont.resume(false)
            return@suspendCancellableCoroutine
        }
        ad = null
        _ready.value = false
        var earned = false
        // The user id travels to our server in AdMob's signed SSV callback.
        current.setServerSideVerificationOptions(
            ServerSideVerificationOptions.Builder().setUserId(userId).build(),
        )
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                load()
                if (cont.isActive) cont.resume(earned)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                load()
                if (cont.isActive) cont.resume(false)
            }
        }
        current.show(activity) { earned = true }
    }
}
