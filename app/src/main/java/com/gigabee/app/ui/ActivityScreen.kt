package com.gigabee.app.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigabee.app.BuildConfig
import com.gigabee.app.R
import com.gigabee.app.ads.BannerAd
import com.gigabee.app.data.LedgerEntry
import com.gigabee.app.data.formatData
import com.gigabee.app.ui.theme.Bee
import kotlin.math.abs

@Composable
fun ActivityScreen(ui: UiState) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp)) {
            ScreenHeader(stringResource(R.string.tab_activity))
        }
        if (ui.history.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BeeMark(side = 44.dp)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.activity_empty), color = Bee.Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)) {
                items(ui.history, key = { it.id }) { entry ->
                    EntryRow(entry)
                    HorizontalDivider(color = Bee.Line, modifier = Modifier.padding(start = 54.dp))
                }
            }
        }
        if (BuildConfig.ADMOB_BANNER_ID.isNotBlank()) BannerAd(BuildConfig.ADMOB_BANNER_ID)
    }
}

@Composable
private fun EntryRow(e: LedgerEntry) {
    val earn = e.amount > 0
    val title = stringResource(
        when (e.kind) {
            "ad" -> R.string.kind_ad
            "redeem" -> R.string.kind_redeem
            "refund" -> R.string.kind_refund
            "survey" -> R.string.kind_survey
            "spin" -> R.string.wheel_title
            "buy" -> R.string.kind_buy
            else -> R.string.kind_bonus
        },
    )
    val status = when (e.status) {
        "pending" -> stringResource(R.string.status_pending)
        "failed" -> stringResource(R.string.status_failed)
        else -> null
    }
    val card = e.cardKb?.let { stringResource(R.string.card_label, formatData(it)) }
    val subtitle = listOfNotNull(
        DateUtils.getRelativeTimeSpanString(e.timeMillis).toString(),
        card,
        e.note,
        status,
    ).joinToString(" · ")
    val icon = when (e.kind) {
        "ad" -> Icons.Rounded.PlayArrow
        "redeem" -> Icons.AutoMirrored.Rounded.Send
        "refund", "spin" -> Icons.Rounded.Refresh
        else -> Icons.Rounded.Star
    }

    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (earn) Bee.YellowSoft else Bee.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (earn) Bee.Yellow else Bee.Muted, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Bee.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Bee.Muted, fontSize = 12.sp, maxLines = 1)
        }
        if (e.currency == "honey") {
            HoneyAmount(
                abs(e.amount),
                prefix = if (earn) "+" else "−",
                fontSize = 15.sp,
                color = if (earn) Bee.Yellow else Bee.Text,
                dropColor = if (earn) Bee.Yellow else Bee.Muted,
            )
        } else {
            Text(
                (if (earn) "+" else "−") + formatData(abs(e.amount)),
                color = if (earn) Bee.Yellow else Bee.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
