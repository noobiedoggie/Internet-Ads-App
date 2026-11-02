package com.gigabee.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.gigabee.app.ads.Consent
import com.gigabee.app.ui.AppRoot
import com.gigabee.app.ui.AppViewModel
import com.gigabee.app.ui.theme.GigaBeeTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as GigaBeeApp
        // GDPR/CCPA consent first (only shows a form where the law requires it), then preload an ad.
        Consent.gather(this) { canRequestAds -> if (canRequestAds) app.rewardedAds.load() }
        setContent { GigaBeeTheme { AppRoot(vm) } }
    }
}
