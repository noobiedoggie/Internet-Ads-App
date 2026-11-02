package com.gigabee.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.rounded.Star
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigabee.app.BuildConfig
import com.gigabee.app.R
import com.gigabee.app.data.dataParts
import com.gigabee.app.data.formatData
import com.gigabee.app.ui.theme.Bee
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun HomeScreen(
    ui: UiState,
    onWatch: () -> Unit,
    onRedeem: () -> Unit,
    onDebugBonus: () -> Unit,
    onSurveys: (() -> Unit)? = null,
) {
    val s = ui.state ?: return
    val target = (s.balanceKb.toFloat() / s.goalKb).coerceIn(0f, 1f)
    val progress by animateFloatAsState(target, tween(900, easing = FastOutSlowInEasing), label = "ring")

    // "+3.3 MB" floats up and the ring pulses each time a reward lands.
    val pop = remember { Animatable(0f) }
    LaunchedEffect(ui.rewardEvent) {
        if (ui.rewardEvent > 0) {
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
            BeeMark(side = 28.dp)
            Spacer(Modifier.width(10.dp))
            Text("GigaBee", color = Bee.Text, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Pill(stringResource(R.string.ads_today, s.adsToday, s.dailyCap))
        }

        Spacer(Modifier.height(14.dp))
        if (s.streak > 0) {
            Pill(stringResource(R.string.streak_label, s.streak) + " · " + stringResource(R.string.streak_bonus, s.streakBonusPct), selected = true)
        } else {
            Text(stringResource(R.string.streak_hint, 20), color = Bee.Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.weight(1f))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(276.dp)
                .graphicsLayer {
                    val k = 1f + 0.04f * sin(PI * p).toFloat()
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
                val (number, unit) = dataParts(s.balanceKb)
                Text(number, color = Bee.Text, fontSize = 64.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp)
                Text(unit, color = Bee.Yellow, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.of_goal, formatData(s.goalKb)), color = Bee.Muted, fontSize = 14.sp)
            }
            if (p > 0f && p < 1f && ui.rewardKb > 0) {
                Text(
                    "+" + formatData(ui.rewardKb),
                    color = Bee.Yellow,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = (44 - 56 * p).dp).alpha(1f - p),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        val label = when {
            s.capReached -> stringResource(R.string.cap_reached)
            ui.verifying -> stringResource(R.string.verifying)
            !ui.adReady -> stringResource(R.string.loading_ad)
            else -> stringResource(R.string.watch_ad) + "  +" + formatData(s.kbPerAd)
        }
        BeeButton(
            text = label,
            onClick = onWatch,
            enabled = !s.capReached && ui.adReady && !ui.busy,
            icon = Icons.Rounded.PlayArrow,
            loading = !s.capReached && (ui.verifying || (!ui.adReady && !ui.busy)),
        )

        if (onSurveys != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onSurveys,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.5.dp, Bee.Yellow),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Bee.Yellow),
            ) {
                Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.surveys), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        val redeemable = s.redeemable
        Column(
            Modifier.fillMaxWidth().height(64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (redeemable != null) {
                TextButton(onClick = onRedeem) {
                    Text(
                        stringResource(R.string.helper_ready, formatData(redeemable)) + "  " +
                            stringResource(R.string.redeem_now) + " →",
                        color = Bee.Yellow,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text(
                    stringResource(
                        when {
                            s.capReached -> R.string.helper_cap
                            s.streak > 0 && s.adsToday == 0 -> R.string.streak_keep
                            s.bundles.isEmpty() -> R.string.home_vault
                            else -> R.string.helper_earn
                        },
                        s.streak,
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
