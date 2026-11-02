package com.gigabee.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.gigabee.app.reminder.Reminder
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
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as GigaBeeApp
        // GDPR/CCPA consent first (only shows a form where the law requires it), then preload an ad.
        Consent.gather(this) { canRequestAds -> if (canRequestAds) app.rewardedAds.load() }
        // Daily evening reminder (Android 13+ asks the user once).
        Reminder.schedule(this)
        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { GigaBeeTheme { AppRoot(vm) } }
    }
}
