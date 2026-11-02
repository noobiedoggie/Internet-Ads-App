package com.gigabee.app

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gigabee.app.data.AppState
import com.gigabee.app.data.CardStack
import com.gigabee.app.data.GameResult
import com.gigabee.app.data.Leaderboard
import com.gigabee.app.data.LeaderboardEntry
import com.gigabee.app.data.LedgerEntry
import com.gigabee.app.data.ShopItem
import com.gigabee.app.data.WheelPrize
import com.gigabee.app.ui.ActivityScreen
import com.gigabee.app.ui.BeeTabs
import com.gigabee.app.ui.HomeScreen
import com.gigabee.app.ui.RedeemScreen
import com.gigabee.app.ui.TopScreen
import com.gigabee.app.ui.UiState
import com.gigabee.app.ui.WheelScreen
import com.gigabee.app.ui.WinContent
import com.gigabee.app.ui.theme.Bee
import com.gigabee.app.ui.theme.GigaBeeTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders each screen with sample data to app/build/screenshots/*.png (published by CI). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "fr-w400dp-h860dp-xhdpi", application = Application::class)
class ScreenshotTest {
    private val now = System.currentTimeMillis()

    private val state = AppState(
        balanceKb = 102400 + 2 * 25600 + 3 * 5120 + 2048,
        adsToday = 7,
        dailyCap = 25,
        kbPerAd = 2350,
        bundles = emptyList(),
        streak = 3,
        streakBonusPct = 10,
        nickname = "JollyNectar786",
        referralCode = "4601AE",
        canEnterReferral = true,
        lifetimeAds = 12,
        honey = 86,
        honeyPerAd = 22,
        spinCost = 100,
        referralHoney = 500,
        wheel = listOf(
            WheelPrize(2048, 3990, "common"), WheelPrize(5120, 3900, "common"), WheelPrize(10240, 1500, "uncommon"),
            WheelPrize(25600, 500, "rare"), WheelPrize(102400, 100, "epic"), WheelPrize(1048576, 10, "legendary"),
        ),
        shop = listOf(ShopItem(10240, 100), ShopItem(51200, 500), ShopItem(102400, 1000)),
        cards = listOf(
            CardStack(102400, "epic", 1), CardStack(25600, "rare", 2),
            CardStack(5120, "common", 3), CardStack(2048, "common", 1),
        ),
    )

    private val ui = UiState(
        state = state,
        loading = false,
        isDemo = false,
        adReady = true,
        history = listOf(
            LedgerEntry("1", 24, "ad", "🔥 +10%", null, now - 60_000, "honey"),
            LedgerEntry("2", -100, "spin", null, null, now - 3_600_000, "honey", 25600),
            LedgerEntry("3", 22, "ad", null, null, now - 7_200_000, "honey"),
            LedgerEntry("4", -100, "buy", null, null, now - 86_400_000, "honey", 10240),
            LedgerEntry("5", 500, "bonus", "Referral", null, now - 2 * 86_400_000, "honey"),
        ),
        leaderboard = Leaderboard(
            top = listOf(
                LeaderboardEntry(1, "TurboBee482", 4210, false),
                LeaderboardEntry(2, "GoldenHive117", 3880, false),
                LeaderboardEntry(3, "CosmicBuzz305", 2950, false),
                LeaderboardEntry(4, "JollyNectar786", 1620, true),
                LeaderboardEntry(5, "HappyComb908", 990, false),
            ),
            myRank = 4,
            myScore = 1620,
        ),
    )

    private fun shot(name: String, tab: Int, content: @Composable () -> Unit) =
        captureRoboImage("build/screenshots/$name.png") {
            GigaBeeTheme {
                Column(Modifier.fillMaxSize().background(Bee.Black)) {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(top = 24.dp)) { content() }
                    BeeTabs(tab) {}
                }
            }
        }

    @Test fun home() = shot("1_home", 0) { HomeScreen(ui, {}, {}, {}) }

    @Test fun homeSpinReady() = shot("2_home_ready", 0) {
        HomeScreen(ui.copy(state = state.copy(honey = 140)), {}, {}, {})
    }

    @Test fun wheel() = shot("3_wheel", 1) { WheelScreen(ui.copy(state = state.copy(honey = 140)), {}, {}) }

    @Test fun vault() = shot("4_vault", 2) { RedeemScreen(ui, {}, {}) }

    @Test fun top() = shot("5_top", 3) { TopScreen(ui, {}, {}, {}) }

    @Test fun activity() = shot("6_activity", 4) { ActivityScreen(ui) }

    @Test fun win() = captureRoboImage("build/screenshots/7_win.png") {
        GigaBeeTheme {
            Box(Modifier.fillMaxSize().background(Bee.Black).padding(28.dp), contentAlignment = Alignment.Center) {
                WinContent(GameResult.Ok(102400, "epic", 40), animate = false) {}
            }
        }
    }
}
