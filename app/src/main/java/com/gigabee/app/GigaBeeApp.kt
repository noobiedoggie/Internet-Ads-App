package com.gigabee.app

import android.app.Application
import com.gigabee.app.ads.RewardedAds
import com.gigabee.app.data.LocalRepository
import com.gigabee.app.data.Repository
import com.gigabee.app.data.SupabaseRepository
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GigaBeeApp : Application() {
    lateinit var repository: Repository
        private set
    lateinit var rewardedAds: RewardedAds
        private set

    override fun onCreate() {
        super.onCreate()
        repository = if (BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            SupabaseRepository(this, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
        } else {
            LocalRepository(this)
        }
        rewardedAds = RewardedAds(this, BuildConfig.ADMOB_REWARDED_ID)
        CoroutineScope(Dispatchers.IO).launch { MobileAds.initialize(this@GigaBeeApp) {} }
    }
}
