package com.gigabee.app.ads

import android.app.Activity
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/** Google's consent SDK (required by AdMob in the EEA/UK; a no-op elsewhere). */
object Consent {
    fun gather(activity: Activity, onReady: (canRequestAds: Boolean) -> Unit) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    onReady(info.canRequestAds())
                }
            },
            { onReady(info.canRequestAds()) },
        )
        // Returning users with consent already stored can load ads right away.
        if (info.canRequestAds()) onReady(true)
    }
}
