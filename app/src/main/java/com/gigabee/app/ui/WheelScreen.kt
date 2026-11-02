package com.gigabee.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gigabee.app.R
import com.gigabee.app.data.GameResult
import com.gigabee.app.data.KB_PER_GB
import com.gigabee.app.data.WheelPrize
import com.gigabee.app.data.dataParts
import com.gigabee.app.data.formatData
import com.gigabee.app.ui.theme.Bee
import com.gigabee.app.ui.theme.AppFont
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import java.text.NumberFormat
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/** 8 slices from the 6 prizes, spread so small and big wins alternate around the wheel. */
private fun wheelSegments(prizes: List<WheelPrize>): List<Long> {
    val kb = prizes.map { it.kb }.sorted().ifEmpty { listOf(2048L, 5120L, 10240L, 25600L, 102400L, KB_PER_GB) }
    return if (kb.size == 6) listOf(0, 2, 1, 4, 0, 3, 1, 5).map { kb[it] } else kb
}

@Composable
fun WheelScreen(ui: UiState, onSpin: () -> Unit, onFinished: () -> Unit) {
    val s = ui.state ?: return
    val segments = remember(s.wheel) { wheelSegments(s.wheel) }
    val rotation = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    var reveal by remember { mutableStateOf<GameResult.Ok?>(null) }

    // A light tick each time a slice passes the pointer.
    LaunchedEffect(segments.size) {
        val sweep = 360f / segments.size
        snapshotFlow { floor((rotation.value + sweep / 2f) / sweep).toInt() }
            .distinctUntilChanged()
            .drop(1)
            .collect { if (rotation.isRunning) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }

    // When the server returns a prize, spin several turns and stop on a slice showing that prize.
    LaunchedEffect(ui.spinSeq) {
        val r = ui.spinResult ?: return@LaunchedEffect
        val matches = segments.indices.filter { segments[it] == r.kb }
        val index = if (matches.isEmpty()) 0 else matches.random()
        val sweep = 360f / segments.size
        val current = rotation.value
        val wanted = ((-index * sweep) % 360f + 360f) % 360f
        val jitter = (Random.nextFloat() - 0.5f) * sweep * 0.6f
        var targetAngle = current - (current % 360f) + 360f * 6 + wanted + jitter
        if (targetAngle < current + 360f * 4) targetAngle += 360f
        rotation.animateTo(targetAngle, tween(4300, easing = CubicBezierEasing(0.12f, 0.8f, 0.2f, 1f)))
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        reveal = r
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader(stringResource(R.string.wheel_title), trailing = { HoneyChip(s.honey) })
        Spacer(Modifier.height(20.dp))

        LuckyWheel(segments, rotation.value, Modifier.fillMaxWidth(0.92f))

        Spacer(Modifier.height(26.dp))
        val enabled = s.canSpin && !ui.spinning
        val ink = if (enabled) Bee.Black else Bee.Muted
        BeeButton(
            text = stringResource(if (s.canSpin) R.string.spin_action else R.string.need_honey),
            onClick = onSpin,
            enabled = enabled,
            icon = Icons.Rounded.Refresh,
            loading = ui.spinning,
            trailing = { HoneyAmount(s.spinCost, fontSize = 17.sp, color = ink, dropColor = ink) },
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.wheel_note), color = Bee.Muted, fontSize = 13.sp, textAlign = TextAlign.Center)

        Spacer(Modifier.height(26.dp))
        OddsCard(s.wheel)
    }

    reveal?.let { r ->
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
        ) {
            Box(Modifier.padding(28.dp)) {
                WinContent(r) {
                    reveal = null
                    onFinished()
                }
            }
        }
    }
}

/** "You won!" panel with the card popping in. */
@Composable
fun WinContent(result: GameResult.Ok, animate: Boolean = true, onCollect: () -> Unit) {
    val scale = remember { Animatable(if (animate) 0.6f else 1f) }
    LaunchedEffect(result) { scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 280f)) }
    val shape = RoundedCornerShape(32.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Bee.Surface)
            .border(1.dp, Bee.Line, shape)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.won_title), color = Bee.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        CoCard(
            result.kb,
            result.rarity,
            Modifier.width(200.dp).graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
            big = true,
        )
        Spacer(Modifier.height(24.dp))
        BeeButton(stringResource(R.string.won_ok), onClick = onCollect)
    }
}

/** Minimalist wheel: white slices, black outline with a yellow inner line, yellow pointer. */
@Composable
fun LuckyWheel(segments: List<Long>, rotationDeg: Float, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labels = remember(segments) { segments.map { formatData(it) } }
    val jackpot = remember(segments) { segments.maxOrNull() }
    Canvas(modifier.aspectRatio(1f)) {
        val c = center
        val pointerH = 26.dp.toPx()
        val r = size.minDimension / 2f - pointerH * 0.45f
        val n = segments.size
        val sweep = 360f / n
        val rimW = 5.dp.toPx()
        val lineW = 2.5.dp.toPx()
        val style = TextStyle(color = Bee.Ink, fontSize = (r * 0.095f).toSp(), fontWeight = FontWeight.Bold, fontFamily = AppFont)

        rotate(rotationDeg, c) {
            drawCircle(Bee.Paper, r, c)
            for (i in 0 until n) {
                // Slice i is centred at -90° (top) + i * sweep; separators sit half a slice away.
                val edge = Math.toRadians((-90f + i * sweep - sweep / 2f).toDouble())
                val ex = cos(edge).toFloat()
                val ey = sin(edge).toFloat()
                drawLine(Bee.Ink, Offset(c.x + r * 0.16f * ex, c.y + r * 0.16f * ey), Offset(c.x + r * ex, c.y + r * ey), 1.5.dp.toPx())
                val dotR = r - rimW - lineW - 7.dp.toPx()
                drawCircle(Bee.Ink, 2.5.dp.toPx(), Offset(c.x + dotR * ex, c.y + dotR * ey))

                val layout = measurer.measure(labels[i], style)
                // Labels sit across the slice, so the prize under the pointer reads upright.
                rotate(i * sweep, c) {
                    val label = Offset(c.x, c.y - r * 0.64f)
                    if (segments[i] == jackpot) {
                        val padH = 9.dp.toPx()
                        val padV = 4.dp.toPx()
                        val h = layout.size.height + padV * 2
                        drawRoundRect(
                            Bee.Yellow,
                            topLeft = Offset(label.x - layout.size.width / 2f - padH, label.y - h / 2f),
                            size = Size(layout.size.width + padH * 2, h),
                            cornerRadius = CornerRadius(h / 2f),
                        )
                    }
                    drawText(layout, topLeft = Offset(label.x - layout.size.width / 2f, label.y - layout.size.height / 2f))
                }
            }
        }

        // Outline, hub and pointer stay still.
        drawCircle(Bee.Ink, r - rimW / 2f, c, style = Stroke(rimW))
        drawCircle(Bee.Yellow, r - rimW - lineW / 2f, c, style = Stroke(lineW))
        drawCircle(Bee.Ink, r * 0.15f, c)
        drawCircle(Bee.Yellow, r * 0.06f, c)

        val half = 13.dp.toPx()
        val baseY = c.y - r - pointerH * 0.35f
        val tipY = c.y - r + pointerH * 0.85f
        val pointer = Path().apply {
            moveTo(c.x - half, baseY)
            lineTo(c.x + half, baseY)
            lineTo(c.x, tipY)
            close()
        }
        drawPath(pointer, Bee.Yellow)
        drawPath(pointer, Bee.Ink, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round))
    }
}

@Composable
private fun OddsCard(prizes: List<WheelPrize>) {
    val total = prizes.sumOf { it.weight }.coerceAtLeast(1)
    val pct = remember { NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 } }
    Column(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.wheel_odds))
        BeeCard(padding = 0.dp) {
            val sorted = prizes.sortedByDescending { it.kb }
            sorted.forEachIndexed { i, p ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(formatData(p.kb), color = Bee.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(78.dp))
                    RarityDots(rarityLevel(p.rarity), filled = Bee.Text, empty = Bee.Line)
                    Spacer(Modifier.width(10.dp))
                    Text(rarityLabel(p.rarity), color = Bee.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(pct.format(100.0 * p.weight / total) + " %", color = Bee.Yellow, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                if (i < sorted.lastIndex) HorizontalDivider(color = Bee.Line, modifier = Modifier.padding(horizontal = 18.dp))
            }
        }
    }
}

fun rarityLevel(rarity: String): Int = when (rarity) {
    "legendary" -> 5
    "epic" -> 4
    "rare" -> 3
    "uncommon" -> 2
    else -> 1
}

@Composable
fun rarityLabel(rarity: String): String = stringResource(
    when (rarity) {
        "legendary" -> R.string.rarity_legendary
        "epic" -> R.string.rarity_epic
        "rare" -> R.string.rarity_rare
        "uncommon" -> R.string.rarity_uncommon
        else -> R.string.rarity_common
    },
)

@Composable
fun RarityDots(level: Int, filled: Color = Bee.Ink, empty: Color = Bee.Ink.copy(alpha = 0.15f), dot: Int = 6) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 1..5) {
            Box(Modifier.size(dot.dp).clip(CircleShape).background(if (i <= level) filled else empty))
        }
    }
}

/**
 * A co card: white paper, black outline (yellow for epic/legendary), big size in the middle,
 * rarity dots in the corner.
 */
@Composable
fun CoCard(kb: Long, rarity: String, modifier: Modifier = Modifier, count: Int = 1, big: Boolean = false) {
    val level = rarityLevel(rarity)
    val shiny = level >= 4
    val shape = RoundedCornerShape(if (big) 24.dp else 18.dp)
    val pad = if (big) 16.dp else 12.dp
    val (number, unit) = dataParts(kb)
    Box(
        modifier
            .aspectRatio(0.74f)
            .clip(shape)
            .background(Bee.Paper)
            .border(if (shiny) 3.dp else 1.5.dp, if (shiny) Bee.Yellow else Bee.Ink, shape),
    ) {
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(pad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BeeMark(side = if (big) 18.dp else 14.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.card_header),
                color = Bee.Ink,
                fontSize = if (big) 11.sp else 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            RarityDots(level, dot = if (big) 6 else 5)
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(number, color = Bee.Ink, fontSize = if (big) 64.sp else 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp)
            Text(unit, color = Bee.Ink.copy(alpha = 0.5f), fontSize = if (big) 18.sp else 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(pad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                rarityLabel(rarity),
                color = if (shiny) Bee.Ink else Color(0xFF6B6B6B),
                fontSize = if (big) 13.sp else 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (count > 1) {
                Text(
                    "×$count",
                    color = Bee.Yellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Bee.Ink).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        if (shiny) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp).background(Bee.Yellow))
        }
    }
}
