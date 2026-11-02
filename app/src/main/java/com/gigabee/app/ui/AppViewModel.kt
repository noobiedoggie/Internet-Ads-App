package com.gigabee.app.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gigabee.app.BuildConfig
import com.gigabee.app.GigaBeeApp
import com.gigabee.app.R
import com.gigabee.app.data.AppState
import com.gigabee.app.data.LedgerEntry
import com.gigabee.app.data.RedeemResult
import com.gigabee.app.data.formatData
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
    val rewardKb: Long = 0,
    val rewardEvent: Int = 0,
    val message: String? = null,
    val messageId: Int = 0,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val bee = app as GigaBeeApp
    private val repo = bee.repository
    private val ads = bee.rewardedAds

    private val _ui = MutableStateFlow(UiState(isDemo = repo.isDemo))
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
                val gained = after.balanceKb - before.balanceKb
                _ui.update {
                    it.copy(
                        state = after,
                        rewardKb = gained,
                        rewardEvent = if (gained > 0) it.rewardEvent + 1 else it.rewardEvent,
                    )
                }
                if (gained > 0) say(text(R.string.reward_added, formatData(gained)))
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

    fun redeem(bundleId: String, phone: String) {
        if (_ui.value.redeeming) return
        viewModelScope.launch {
            _ui.update { it.copy(redeeming = true) }
            try {
                when (val result = repo.redeem(bundleId, phone)) {
                    RedeemResult.Ok -> say(text(R.string.redeem_ok))
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

    fun debugBonus() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            repo.debugBonus()
            refresh()
        }
    }
}
