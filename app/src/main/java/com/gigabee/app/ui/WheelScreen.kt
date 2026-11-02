package com.gigabee.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigabee.app.R
import com.gigabee.app.data.GameResult
import com.gigabee.app.data.KB_PER_GB
import com.gigabee.app.data.WheelPrize
import com.gigabee.app.data.formatData
import com.gigabee.app.data.formatHoney
import com.gigabee.app.ui.theme.Bee
import java.text.NumberFormat
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val Ink = Color(0xFF0A0A0A)

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
    var reveal by remember { mutableStateOf<GameResult.Ok?>(null) }

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
        reveal = r
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.wheel_title),
                color = Bee.Text,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
            )
            Pill("🍯 " + formatHoney(s.honey), selected = true)
        }
        Spacer(Modifier.height(20.dp))

        LuckyWheel(segments, rotation.value, Modifier.fillMaxWidth(0.92f))

        Spacer(Modifier.height(24.dp))
        BeeButton(
            text = if (s.canSpin) stringResource(R.string.wheel_spin, formatHoney(s.spinCost))
            else stringResource(R.string.wheel_need, formatHoney(s.spinCost)),
            onClick = onSpin,
            enabled = s.canSpin && !ui.spinning,
            icon = Icons.Rounded.Refresh,
            loading = ui.spinning,
        )
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.wheel_note), color = Bee.Muted, fontSize = 13.sp, textAlign = TextAlign.Center)

        Spacer(Modifier.height(24.dp))
        OddsTable(s.wheel)
    }

    reveal?.let { r ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = Bee.Surface,
            title = {
                Text(
                    stringResource(R.string.won_title),
                    color = Bee.Text,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CoCard(r.kb, r.rarity, Modifier.width(190.dp), big = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { reveal = null; onFinished() }) {
                    Text(stringResource(R.string.won_ok), color = Bee.Yellow, fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}

/** Minimalist wheel: white slices, black outlines, a yellow inner ring and a yellow pointer. */
@Composable
fun LuckyWheel(segments: List<Long>, rotationDeg: Float, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labels = remember(segments) {
        val top = segments.maxOrNull()
        segments.map { (if (it == top) "★ " else "") + formatData(it) }
    }
    Canvas(modifier.aspectRatio(1f)) {
        val c = center
        val pointerH = 22.dp.toPx()
        val r = size.minDimension / 2f - pointerH * 0.6f
        val n = segments.size
        val sweep = 360f / n
        val textStyle = TextStyle(color = Ink, fontSize = (r * 0.105f).toSp(), fontWeight = FontWeight.Black)

        rotate(rotationDeg, c) {
            drawCircle(Color.White, r, c)
            for (i in 0 until n) {
                // Slice i is centred at -90° (top) + i * sweep.
                val edge = Math.toRadians((-90f + i * sweep - sweep / 2f).toDouble())
                drawLine(
                    Ink,
                    c,
                    Offset(c.x + r * cos(edge).toFloat(), c.y + r * sin(edge).toFloat()),
                    strokeWidth = 2.5.dp.toPx(),
                )
                val layout = measurer.measure(labels[i], textStyle)
                rotate(i * sweep, c) {
                    drawText(
                        layout,
                        topLeft = Offset(c.x - layout.size.width / 2f, c.y - r * 0.68f - layout.size.height / 2f),
                    )
                }
            }
            drawCircle(Bee.Yellow, r * 0.93f, c, style = Stroke(3.dp.toPx()))
        }

        // Rim, hub and pointer don't turn.
        drawCircle(Ink, r, c, style = Stroke(6.dp.toPx()))
        drawCircle(Bee.Yellow, r * 0.15f, c)
        drawCircle(Ink, r * 0.15f, c, style = Stroke(3.dp.toPx()))
        drawCircle(Ink, r * 0.04f, c)

        val half = 14.dp.toPx()
        val tipY = c.y - r + pointerH * 0.75f
        val baseY = c.y - r - pointerH * 0.55f
        val pointer = Path().apply {
            moveTo(c.x - half, baseY)
            lineTo(c.x + half, baseY)
            lineTo(c.x, tipY)
            close()
        }
        drawPath(pointer, Bee.Yellow)
        drawPath(pointer, Ink, style = Stroke(3.dp.toPx()))
    }
}

@Composable
private fun OddsTable(prizes: List<WheelPrize>) {
    val total = prizes.sumOf { it.weight }.coerceAtLeast(1)
    val pct = remember { NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 } }
    Column(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.wheel_odds))
        prizes.sortedByDescending { it.kb }.forEach { p ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatData(p.kb), color = Bee.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                Text(rarityLabel(p.rarity), color = Bee.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(pct.format(100.0 * p.weight / total) + " %", color = Bee.Yellow, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
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

/** A co card: white, black outline, yellow header band. Epic and legendary cards get a yellow frame. */
@Composable
fun CoCard(kb: Long, rarity: String, modifier: Modifier = Modifier, count: Int = 1, big: Boolean = false) {
    val shape = RoundedCornerShape(if (big) 22.dp else 16.dp)
    val shiny = rarity == "epic" || rarity == "legendary"
    Box(
        modifier
            .clip(shape)
            .background(if (shiny) Bee.Yellow else Ink)
            .padding(if (shiny) 4.dp else 2.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(if (big) 18.dp else 14.dp)).background(Color.White),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().background(Bee.Yellow).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.card_header),
                    color = Ink,
                    fontSize = if (big) 12.sp else 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                )
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(Ink))
            Spacer(Modifier.height(if (big) 22.dp else 14.dp))
            Text(formatData(kb), color = Ink, fontSize = if (big) 40.sp else 26.sp, fontWeight = FontWeight.Black)
            Text(rarityLabel(rarity), color = Color(0xFF5A5A5A), fontSize = if (big) 14.sp else 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(if (big) 22.dp else 12.dp))
            if (count > 1) {
                Text(
                    "×$count",
                    color = Bee.Yellow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Ink)
                        .border(1.dp, Ink, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                )
            }
        }
    }
}
