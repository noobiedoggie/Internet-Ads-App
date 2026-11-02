package com.gigabee.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigabee.app.R
import com.gigabee.app.data.AppState
import com.gigabee.app.data.LeaderboardEntry
import com.gigabee.app.data.formatData
import com.gigabee.app.ui.theme.Bee

private const val SHARE_URL = "https://github.com/noobiedoggie/Internet-Ads-App/releases/download/latest/GigaBee.apk"

@Composable
fun TopScreen(ui: UiState, onLoad: () -> Unit, onReroll: () -> Unit, onApplyCode: (String) -> Unit) {
    val s = ui.state ?: return
    LaunchedEffect(Unit) { onLoad() }
    val lb = ui.leaderboard

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(stringResource(R.string.top_title), color = Bee.Text, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.top_reset), color = Bee.Muted, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
        }
        item { MeCard(s, lb?.myRank, lb?.myKb ?: 0L, onReroll) }
        item { InviteCard(s, onApplyCode) }
        item { Spacer(Modifier.height(6.dp)) }
        if (lb == null || lb.top.isEmpty()) {
            item { Text(stringResource(R.string.top_unranked), color = Bee.Muted, fontSize = 14.sp) }
        } else {
            items(lb.top, key = { "${it.rank}-${it.nickname}" }) { RankRow(it) }
        }
    }
}

private val cardShape = RoundedCornerShape(24.dp)

@Composable
private fun MeCard(s: AppState, myRank: Int?, myKb: Long, onReroll: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(cardShape).background(Bee.Surface).border(1.dp, Bee.Line, cardShape).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BeeMark(side = 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.nickname ?: "…", color = Bee.Text, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(
                    if (myRank != null) stringResource(R.string.top_you, myRank) + " · " + formatData(myKb)
                    else stringResource(R.string.top_unranked),
                    color = Bee.Muted,
                    fontSize = 13.sp,
                )
            }
            TextButton(onClick = onReroll) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Bee.Yellow)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.reroll), color = Bee.Yellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        if (s.streak > 0) {
            Spacer(Modifier.height(12.dp))
            Pill(stringResource(R.string.streak_label, s.streak) + " · " + stringResource(R.string.streak_bonus, s.streakBonusPct))
        }
    }
}

@Composable
private fun InviteCard(s: AppState, onApplyCode: (String) -> Unit) {
    val context = LocalContext.current
    var code by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().clip(cardShape).background(Bee.Surface).border(1.5.dp, Bee.Yellow, cardShape).padding(20.dp),
    ) {
        Text(stringResource(R.string.invite_title), color = Bee.Text, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.invite_body, formatData(s.referralKb), s.referralTarget), color = Bee.Muted, fontSize = 14.sp)

        val myCode = s.referralCode
        if (myCode != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                myCode,
                color = Bee.Yellow,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(12.dp))
            val message = stringResource(R.string.invite_message, myCode, SHARE_URL)
            val title = stringResource(R.string.invite_share)
            BeeButton(title, icon = Icons.Rounded.Share, onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message)
                context.startActivity(Intent.createChooser(send, title))
            })
        }

        when {
            s.referred && s.referralPaid -> {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.invite_done), color = Bee.Yellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            s.referred -> {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.invite_progress, minOf(s.lifetimeAds, s.referralTarget), s.referralTarget),
                    color = Bee.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            s.canEnterReferral -> {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.invite_have_code), color = Bee.Muted, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { v -> code = v.filter { it.isLetterOrDigit() }.take(12).uppercase() },
                        singleLine = true,
                        placeholder = { Text("A1B2C3") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Bee.Yellow,
                            unfocusedBorderColor = Bee.Line,
                            cursorColor = Bee.Yellow,
                            focusedTextColor = Bee.Text,
                            unfocusedTextColor = Bee.Text,
                            focusedPlaceholderColor = Bee.Muted,
                            unfocusedPlaceholderColor = Bee.Muted,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onApplyCode(code); code = "" }, enabled = code.length >= 4) {
                        Text(stringResource(R.string.invite_apply), color = Bee.Yellow, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RankRow(e: LeaderboardEntry) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (e.me) Bee.Yellow else Bee.Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (e.me) Bee.Black else Bee.Text
        Text(
            when (e.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#${e.rank}" },
            color = fg,
            fontSize = if (e.rank <= 3) 22.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(44.dp),
        )
        Text(e.nickname, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(formatData(e.kb), color = if (e.me) Bee.Black else Bee.Yellow, fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}
