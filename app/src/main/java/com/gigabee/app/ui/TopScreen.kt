package com.gigabee.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigabee.app.R
import com.gigabee.app.data.AppState
import com.gigabee.app.data.LeaderboardEntry
import com.gigabee.app.data.formatHoney
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenHeader(stringResource(R.string.top_title), subtitle = stringResource(R.string.top_reset))
            Spacer(Modifier.height(12.dp))
        }
        item { MeCard(s, lb?.myRank, lb?.myScore ?: 0L, onReroll) }
        item {
            Spacer(Modifier.height(4.dp))
            InviteCard(s, onApplyCode)
            Spacer(Modifier.height(18.dp))
            SectionLabel(stringResource(R.string.top_title))
        }
        if (lb == null || lb.top.isEmpty()) {
            item { Text(stringResource(R.string.top_unranked), color = Bee.Muted, fontSize = 14.sp) }
        } else {
            items(lb.top, key = { "${it.rank}-${it.nickname}" }) { RankRow(it) }
        }
    }
}

@Composable
private fun MeCard(s: AppState, myRank: Int?, myScore: Long, onReroll: () -> Unit) {
    BeeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BeeMark(side = 36.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.nickname ?: "…", color = Bee.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                if (myRank != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.top_you, myRank) + "  ·  ", color = Bee.Muted, fontSize = 13.sp)
                        HoneyAmount(myScore, fontSize = 13.sp, color = Bee.Yellow)
                    }
                } else {
                    Text(stringResource(R.string.top_unranked), color = Bee.Muted, fontSize = 13.sp)
                }
            }
            // Round "new name" button.
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Bee.Surface2)
                    .clickable(onClick = onReroll),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.reroll), tint = Bee.Yellow, modifier = Modifier.size(20.dp))
            }
        }
        if (s.streak > 0) {
            Spacer(Modifier.height(14.dp))
            Pill(stringResource(R.string.streak_label, s.streak) + "  ·  " + stringResource(R.string.streak_bonus, s.streakBonusPct), selected = true)
        }
    }
}

@Composable
private fun InviteCard(s: AppState, onApplyCode: (String) -> Unit) {
    val context = LocalContext.current
    var code by rememberSaveable { mutableStateOf("") }
    BeeCard(highlight = true) {
        Text(stringResource(R.string.invite_title), color = Bee.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.invite_body, formatHoney(s.referralHoney) + " " + stringResource(R.string.honey_unit), s.referralTarget), color = Bee.Muted, fontSize = 14.sp)

        val myCode = s.referralCode
        if (myCode != null) {
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Bee.Surface2)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(myCode, color = Bee.Yellow, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp)
            }
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
                Text(stringResource(R.string.invite_done), color = Bee.Yellow, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            s.referred -> {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.invite_progress, minOf(s.lifetimeAds, s.referralTarget), s.referralTarget),
                    color = Bee.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            s.canEnterReferral -> {
                Spacer(Modifier.height(18.dp))
                Text(stringResource(R.string.invite_have_code), color = Bee.Muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
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
                        Text(stringResource(R.string.invite_apply), color = if (code.length >= 4) Bee.Yellow else Bee.Muted, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RankRow(e: LeaderboardEntry) {
    val shape = RoundedCornerShape(18.dp)
    val podium = e.rank <= 3
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (e.me) Bee.YellowSoft else Bee.Surface)
            .border(1.dp, if (e.me) Bee.Yellow.copy(alpha = 0.6f) else Bee.Line, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(if (podium) Bee.Yellow else Bee.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                e.rank.toString(),
                color = if (podium) Bee.Black else Bee.Muted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            e.nickname,
            color = Bee.Text,
            fontSize = 15.sp,
            fontWeight = if (e.me) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        HoneyAmount(e.score, fontSize = 15.sp, color = if (e.me) Bee.Yellow else Bee.Text)
    }
}
