package com.gigabee.app.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gigabee.app.BuildConfig
import com.gigabee.app.GigaBeeApp
import com.gigabee.app.R
import com.gigabee.app.data.AppState
import com.gigabee.app.data.KB_PER_MB
import java.net.URLEncoder
import com.gigabee.app.data.Leaderboard
import com.gigabee.app.data.LedgerEntry
import com.gigabee.app.reminder.Reminder
import com.gigabee.app.data.RedeemResult
import com.gigabee.app.data.GameResult
import com.gigabee.app.data.formatData
import com.gigabee.app.data.formatHoney
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val state: AppState? = null,
    val history: List<LedgerEntry> = emptyList(),
    val loading: Boolean = true,
    val isDemo: Boolean = true,
    val adReady: Boolean = false,
    val busy: Boolean = false,
    val verifying: Boolean = false,
    val redeeming: Boolean = false,
    val rewardHoney: Long = 0,
    val rewardEvent: Int = 0,
    val message: String? = null,
    val messageId: Int = 0,
    val surveysAvailable: Boolean = false,
    val surveyUrl: String? = null,
    val showDemoSurvey: Boolean = false,
    val leaderboard: Leaderboard? = null,
    val spinning: Boolean = false,
    val spinResult: GameResult.Ok? = null,
    val spinSeq: Int = 0,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val bee = app as GigaBeeApp
    private val repo = bee.repository
    private val ads = bee.rewardedAds

    private val _ui = MutableStateFlow(
        UiState(isDemo = repo.isDemo, surveysAvailable = BuildConfig.BITLABS_TOKEN.isNotBlank() || repo.isDemo),
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch { ads.ready.collect { r -> _ui.update { it.copy(adReady = r) } } }
        refresh()
    }

    private fun text(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    private fun say(message: String) = _ui.update { it.copy(message = message, messageId = it.messageId + 1) }

    private suspend fun reload() {
        val s = repo.state()
        val h = repo.history()
        _ui.update { it.copy(state = s, history = h, loading = false) }
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            try {
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false) }
                say(text(R.string.error_generic))
            }
        }
    }

    fun watchAd(activity: Activity) {
        val before = _ui.value.state ?: return
        if (before.capReached || _ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try {
                val earned = ads.show(activity, repo.userId())
                if (!earned) {
                    say(text(R.string.no_reward))
                    return@launch
                }
                _ui.update { it.copy(verifying = true) }
                val after = repo.onAdRewarded(before)
                val gained = after.honey - before.honey
                _ui.update {
                    it.copy(
                        state = after,
                        rewardHoney = gained,
                        rewardEvent = if (gained > 0) it.rewardEvent + 1 else it.rewardEvent,
                    )
                }
                if (gained > 0) say(text(R.string.reward_added, formatHoney(gained) + " 🍯"))
                Reminder.markWatchedToday(getApplication())
                val h = repo.history()
                _ui.update { it.copy(history = h) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(text(R.string.error_generic))
            } finally {
                _ui.update { it.copy(busy = false, verifying = false) }
            }
        }
    }

    fun redeem(bundleId: String) {
        if (_ui.value.redeeming) return
        viewModelScope.launch {
            _ui.update { it.copy(redeeming = true) }
            try {
                when (val result = repo.redeem(bundleId)) {
                    RedeemResult.Ok -> say(text(R.string.redeem_ok_esim))
                    is RedeemResult.Error -> say(result.message)
                }
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(text(R.string.error_generic))
            } finally {
                _ui.update { it.copy(redeeming = false) }
            }
        }
    }

    /** Opens the BitLabs survey wall. Rewards arrive through the server callback, not the app. */
    fun openSurveys() {
        if (BuildConfig.BITLABS_TOKEN.isBlank()) {
            if (repo.isDemo) _ui.update { it.copy(showDemoSurvey = true) }
            return
        }
        viewModelScope.launch {
            try {
                val uid = URLEncoder.encode(repo.userId(), "UTF-8")
                val token = URLEncoder.encode(BuildConfig.BITLABS_TOKEN, "UTF-8")
                _ui.update { it.copy(surveyUrl = "https://web.bitlabs.ai/?uid=$uid&token=$token") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(text(R.string.error_generic))
            }
        }
    }

    fun closeSurveys() {
        _ui.update { it.copy(surveyUrl = null) }
        refresh()
    }

    fun dismissDemoSurvey() = _ui.update { it.copy(showDemoSurvey = false) }

    fun completeDemoSurvey() {
        _ui.update { it.copy(showDemoSurvey = false) }
        viewModelScope.launch {
            repo.demoSurvey()
            refresh()
            say(text(R.string.reward_added, formatHoney(1000) + " 🍯"))
        }
    }

    /** Spends honey on a spin. The UI animates the wheel, shows the card, then calls [spinFinished]. */
    fun spin() {
        if (_ui.value.spinning) return
        viewModelScope.launch {
            _ui.update { it.copy(spinning = true) }
            try {
                when (val r = repo.spin()) {
                    is GameResult.Ok -> _ui.update { u ->
                        u.copy(state = u.state?.copy(honey = r.honeyLeft), spinResult = r, spinSeq = u.spinSeq + 1)
                    }
                    is GameResult.Error -> {
                        _ui.update { it.copy(spinning = false) }
                        say(text(if (r.reason == "honey") R.string.not_enough_honey else R.string.error_generic))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(spinning = false) }
                say(text(R.string.error_generic))
            }
        }
    }

    fun spinFinished() {
        _ui.update { it.copy(spinning = false, spinResult = null) }
        viewModelScope.launch { runCatching { reload() } }
    }

    fun buyCard(kb: Long) {
        viewModelScope.launch {
            try {
                when (val r = repo.buyCard(kb)) {
                    is GameResult.Ok -> {
                        say(text(R.string.bought, formatData(r.kb)))
                        reload()
                    }
                    is GameResult.Error ->
                        say(text(if (r.reason == "honey") R.string.not_enough_honey else R.string.error_generic))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(text(R.string.error_generic))
            }
        }
    }

    fun loadLeaderboard() {
        viewModelScope.launch {
            try {
                val lb = repo.leaderboard()
                _ui.update { it.copy(leaderboard = lb) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(text(R.string.error_generic))
            }
        }
    }

    fun applyReferral(code: String) {
        val clean = code.trim().uppercase()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            try {
                val reason = repo.redeemReferral(clean)
                if (reason == null) {
                    say(text(R.string.invite_ok, _ui.value.state?.referralTarget ?: 30))
                    reload()
                } else {
                    say(text(R.string.invite_invalid))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(text(R.string.error_generic))
            }
        }
    }

    fun rerollNickname() {
        viewModelScope.launch {
            try {
                val nick = repo.rerollNickname()
                _ui.update { u -> u.copy(state = u.state?.copy(nickname = nick)) }
                loadLeaderboard()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(text(R.string.error_generic))
            }
        }
    }

    fun debugBonus() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            repo.debugBonus()
            refresh()
        }
    }
}
