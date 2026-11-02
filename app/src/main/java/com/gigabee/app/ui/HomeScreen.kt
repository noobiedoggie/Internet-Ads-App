package com.gigabee.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigabee.app.BuildConfig
import com.gigabee.app.R
import com.gigabee.app.data.formatHoney
import com.gigabee.app.ui.theme.Bee
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun HomeScreen(
    ui: UiState,
    onWatch: () -> Unit,
    onOpenWheel: () -> Unit,
    onDebugBonus: () -> Unit,
    onSurveys: (() -> Unit)? = null,
) {
    val s = ui.state ?: return
    val haptics = LocalHapticFeedback.current

    // The ring fills up toward the next wheel spin; the number counts up smoothly.
    val target = if (s.spinCost > 0) (s.honey.toFloat() / s.spinCost).coerceIn(0f, 1f) else 0f
    val progress by animateFloatAsState(target, tween(900, easing = FastOutSlowInEasing), label = "ring")
    val shownHoney by animateIntAsState(s.honey.toInt(), tween(900, easing = FastOutSlowInEasing), label = "honey")

    // "+22" floats up, the ring pulses and the phone ticks each time a reward lands.
    val pop = remember { Animatable(0f) }
    LaunchedEffect(ui.rewardEvent) {
        if (ui.rewardEvent > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            pop.snapTo(0f)
            pop.animateTo(1f, tween(1400))
        }
    }
    val p = pop.value

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BeeMark(side = 26.dp)
            Spacer(Modifier.width(10.dp))
            Text("GigaBee", color = Bee.Text, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
            Spacer(Modifier.weight(1f))
            HoneyChip(s.honey)
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.streak > 0) {
                Pill(stringResource(R.string.streak_label, s.streak) + "  ·  " + stringResource(R.string.streak_bonus, s.streakBonusPct), selected = true)
            }
            Pill(stringResource(R.string.ads_today, s.adsToday, s.dailyCap))
        }

        Spacer(Modifier.weight(1f))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(268.dp)
                .graphicsLayer {
                    val k = 1f + 0.035f * sin(PI * p).toFloat()
                    scaleX = k
                    scaleY = k
                }
                .then(
                    if (BuildConfig.DEBUG) {
                        Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onDebugBonus() }) }
                    } else {
                        Modifier
                    },
                ),
        ) {
            ProgressRing(progress, Modifier.fillMaxSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HoneyDrop(size = 24.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    formatHoney(shownHoney.toLong()),
                    color = Bee.Text,
                    fontSize = 62.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp,
                )
                Text(stringResource(R.string.honey_unit), color = Bee.Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (s.canSpin) stringResource(R.string.spin_ready)
                    else stringResource(R.string.of_spin, formatHoney(s.spinCost)),
                    color = if (s.canSpin) Bee.Yellow else Bee.Muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (p > 0f && p < 1f && ui.rewardHoney > 0) {
                HoneyAmount(
                    ui.rewardHoney,
                    prefix = "+",
                    fontSize = 22.sp,
                    color = Bee.Yellow,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = (40 - 56 * p).dp).alpha(1f - p),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        val ready = !s.capReached && ui.adReady && !ui.busy
        BeeButton(
            text = when {
                s.capReached -> stringResource(R.string.cap_reached)
                ui.verifying -> stringResource(R.string.verifying)
                !ui.adReady -> stringResource(R.string.loading_ad)
                else -> stringResource(R.string.watch_ad)
            },
            onClick = onWatch,
            enabled = ready,
            icon = Icons.Rounded.PlayArrow,
            loading = !s.capReached && (ui.verifying || (!ui.adReady && !ui.busy)),
            trailing = if (ready) {
                { HoneyAmount(s.honeyPerAd, prefix = "+", fontSize = 17.sp, color = Bee.Black, dropColor = Bee.Black) }
            } else {
                null
            },
        )

        Spacer(Modifier.height(12.dp))
        when {
            s.canSpin -> GhostButton(stringResource(R.string.go_spin), onClick = onOpenWheel, icon = Icons.Rounded.Refresh)
            onSurveys != null -> GhostButton(stringResource(R.string.surveys), onClick = onSurveys, icon = Icons.Rounded.Star)
            else -> Box(Modifier.height(52.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(
                        when {
                            s.capReached -> R.string.helper_cap
                            s.streak > 0 && s.adsToday == 0 -> R.string.streak_keep
                            s.streak == 0 -> R.string.streak_hint
                            else -> R.string.helper_honey
                        },
                        if (s.streak == 0) 20 else s.streak,
                    ),
                    color = Bee.Muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}
