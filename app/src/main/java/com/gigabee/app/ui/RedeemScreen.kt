package com.gigabee.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telephony.euicc.EuiccManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigabee.app.R
import com.gigabee.app.data.DataBundle
import com.gigabee.app.data.EsimInfo
import com.gigabee.app.data.ShopItem
import com.gigabee.app.data.dataParts
import com.gigabee.app.data.formatData
import com.gigabee.app.ui.theme.Bee

@Composable
fun RedeemScreen(ui: UiState, onRedeem: (DataBundle) -> Unit, onBuy: (Long) -> Unit) {
    val s = ui.state ?: return
    val regions = s.bundles.map { it.carrier }.distinct()
    var chosenRegion by rememberSaveable { mutableStateOf<String?>(null) }
    val region = chosenRegion?.takeIf { it in regions } ?: regions.firstOrNull()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<DataBundle?>(null) }

    val bundles = s.bundles.filter { it.carrier == region }
    val selected = bundles.firstOrNull { it.id == selectedId }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        ScreenHeader(stringResource(R.string.tab_redeem), trailing = { HoneyChip(s.honey) })
        Spacer(Modifier.height(20.dp))

        // Total co, the number that will turn into real internet.
        BeeCard {
            SectionLabel(stringResource(R.string.co_label))
            val (number, unit) = dataParts(s.balanceKb)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(number, color = Bee.Text, fontSize = 46.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp)
                Spacer(Modifier.width(6.dp))
                Text(unit, color = Bee.Yellow, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 9.dp))
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Bee.Yellow, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.vault_soon), color = Bee.Yellow, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.vault_body), color = Bee.Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(26.dp))

        // Honey -> co swap.
        SectionLabel(stringResource(R.string.shop_title))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            s.shop.forEach { item ->
                ShopTile(item, affordable = s.honey >= item.cost, modifier = Modifier.weight(1f)) { onBuy(item.kb) }
            }
        }
        Spacer(Modifier.height(26.dp))

        // The user's co cards.
        SectionLabel(stringResource(R.string.cards_title))
        if (s.cards.isEmpty()) {
            BeeCard { Text(stringResource(R.string.cards_empty), color = Bee.Muted, fontSize = 14.sp) }
        } else {
            s.cards.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { c -> CoCard(c.kb, c.rarity, Modifier.weight(1f), count = c.count) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        Spacer(Modifier.height(12.dp))

        // Redemptions not open yet: cards wait in the vault.
        if (s.bundles.isEmpty()) return@Column

        EsimCard(s.esim, ui.isDemo)
        Spacer(Modifier.height(24.dp))

        if (regions.size > 1) {
            SectionLabel(stringResource(R.string.region))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                regions.forEach { r ->
                    Pill(r, selected = r == region, onClick = { chosenRegion = r; selectedId = null })
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        SectionLabel(stringResource(R.string.choose_bundle))
        bundles.forEach { b ->
            BundleCard(b, s.balanceKb, selected = b.id == selectedId) { selectedId = b.id }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(14.dp))

        val buttonText = when {
            selected == null -> stringResource(R.string.choose_bundle)
            s.esim == null -> stringResource(R.string.get_esim, selected.label)
            else -> stringResource(R.string.add_to_esim, selected.label)
        }
        BeeButton(
            text = buttonText,
            onClick = { confirm = selected },
            enabled = selected != null && selected.dataKb <= s.balanceKb && !ui.redeeming,
            icon = Icons.AutoMirrored.Rounded.Send,
            loading = ui.redeeming,
        )
        if (ui.isDemo) {
            Text(
                stringResource(R.string.demo_note),
                color = Bee.Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }

    confirm?.let { b ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Bee.Surface,
            title = {
                Text(
                    if (s.esim == null) stringResource(R.string.get_esim, b.label) else stringResource(R.string.add_to_esim, b.label),
                    color = Bee.Text,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = { Text(stringResource(R.string.confirm_esim_body, b.label, b.carrier), color = Bee.Muted) },
            confirmButton = {
                TextButton(onClick = { confirm = null; onRedeem(b) }) {
                    Text(stringResource(R.string.confirm), color = Bee.Yellow, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel), color = Bee.Muted) }
            },
        )
    }
}

@Composable
private fun ShopTile(item: ShopItem, affordable: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(Bee.Surface)
            .border(1.dp, if (affordable) Bee.Yellow.copy(alpha = 0.6f) else Bee.Line, shape)
            .clickable(enabled = affordable, onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(formatData(item.kb), color = if (affordable) Bee.Text else Bee.Muted, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        HoneyAmount(
            item.cost,
            fontSize = 13.sp,
            color = if (affordable) Bee.Yellow else Bee.Muted,
            dropColor = if (affordable) Bee.Yellow else Bee.Muted,
        )
    }
}

@Composable
private fun EsimCard(esim: EsimInfo?, demo: Boolean) {
    val context = LocalContext.current
    val supported = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            runCatching { context.getSystemService(EuiccManager::class.java)?.isEnabled == true }.getOrDefault(false)
    }
    var showInstall by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)

    Column(
        Modifier.fillMaxWidth().clip(shape).background(Bee.Surface).border(1.dp, Bee.Line, shape).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BeeMark(side = 26.dp)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.esim_title), color = Bee.Text, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(10.dp))

        if (esim == null) {
            Text(stringResource(R.string.esim_none), color = Bee.Muted, fontSize = 14.sp)
        } else {
            Text("ICCID ${esim.iccid}", color = Bee.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(12.dp))
            if (!showInstall) {
                BeeButton(stringResource(R.string.esim_show), onClick = { showInstall = true })
            } else {
                Text(stringResource(R.string.esim_steps), color = Bee.Text, fontSize = 14.sp)
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QrCode(esim.lpa) }
                Spacer(Modifier.height(10.dp))
                Text(
                    esim.lpa,
                    color = Bee.Yellow,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (demo) {
                    Text(
                        stringResource(R.string.esim_demo_code),
                        color = Bee.Muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                val copied = stringResource(R.string.esim_copied)
                BeeButton(stringResource(R.string.esim_copy), onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("eSIM", esim.lpa))
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                })
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = null, tint = Bee.Yellow, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.esim_settings), color = Bee.Yellow, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { showInstall = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.esim_hide), color = Bee.Muted)
                }
            }
        }
        if (!supported) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.esim_unsupported), color = Bee.Red, fontSize = 12.sp)
        }
    }
}

@Composable
private fun BundleCard(b: DataBundle, balanceKb: Long, selected: Boolean, onClick: () -> Unit) {
    val affordable = balanceKb >= b.dataKb
    val shape = RoundedCornerShape(20.dp)
    val fg = if (selected) Bee.Black else Bee.Text
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Bee.Yellow else Bee.Surface)
            .border(1.dp, if (selected) Bee.Yellow else Bee.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(b.label, color = fg, fontSize = 20.sp, fontWeight = FontWeight.Black)
            if (!affordable) {
                Text(
                    stringResource(R.string.need_more, formatData(b.dataKb - balanceKb)),
                    color = if (selected) Bee.Black.copy(alpha = 0.7f) else Bee.Muted,
                    fontSize = 13.sp,
                )
            }
        }
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (affordable) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = if (selected) Bee.Black else Bee.Yellow,
                    modifier = Modifier.size(30.dp))
            } else {
                ProgressRing(
                    progress = (balanceKb.toFloat() / b.dataKb).coerceIn(0f, 1f),
                    modifier = Modifier.size(32.dp),
                    stroke = 4.dp,
                    track = if (selected) Bee.Black.copy(alpha = 0.2f) else Bee.Surface2,
                    color = if (selected) Bee.Black else Bee.Yellow,
                )
            }
        }
    }
}
