package com.gigabee.app.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigabee.app.R
import com.gigabee.app.data.DataBundle
import com.gigabee.app.data.formatData
import com.gigabee.app.ui.theme.Bee

@Composable
fun RedeemScreen(ui: UiState, onRedeem: (DataBundle, String) -> Unit) {
    val s = ui.state ?: return
    val carriers = s.bundles.map { it.carrier }.distinct()
    var chosenCarrier by rememberSaveable { mutableStateOf<String?>(null) }
    val carrier = chosenCarrier?.takeIf { it in carriers } ?: carriers.firstOrNull()
    var phone by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<DataBundle?>(null) }

    val bundles = s.bundles.filter { it.carrier == carrier }
    val selected = bundles.firstOrNull { it.id == selectedId }
    val phoneOk = phone.count { it.isDigit() } in 8..15

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(stringResource(R.string.tab_redeem), color = Bee.Text, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text(stringResource(R.string.balance, formatData(s.balanceKb)), color = Bee.Muted, fontSize = 15.sp)
        Spacer(Modifier.height(24.dp))

        if (s.bundles.isEmpty()) {
            Text(stringResource(R.string.no_bundles), color = Bee.Muted, fontSize = 15.sp)
            return@Column
        }

        if (carriers.size > 1) {
            SectionLabel(stringResource(R.string.carrier))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                carriers.forEach { c ->
                    Pill(c, selected = c == carrier, onClick = { chosenCarrier = c; selectedId = null })
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        SectionLabel(stringResource(R.string.phone))
        OutlinedTextField(
            value = phone,
            onValueChange = { v -> phone = v.filter { it.isDigit() || it in "+ -()" }.take(20) },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.phone_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Bee.Yellow,
                unfocusedBorderColor = Bee.Line,
                cursorColor = Bee.Yellow,
                focusedTextColor = Bee.Text,
                unfocusedTextColor = Bee.Text,
                focusedLeadingIconColor = Bee.Yellow,
                unfocusedLeadingIconColor = Bee.Muted,
                focusedPlaceholderColor = Bee.Muted,
                unfocusedPlaceholderColor = Bee.Muted,
                focusedContainerColor = Bee.Surface,
                unfocusedContainerColor = Bee.Surface,
            ),
        )
        Spacer(Modifier.height(20.dp))

        SectionLabel(stringResource(R.string.choose_bundle))
        bundles.forEach { b ->
            BundleCard(b, s.balanceKb, selected = b.id == selectedId) { selectedId = b.id }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(14.dp))

        BeeButton(
            text = if (selected != null) stringResource(R.string.send_bundle, selected.label) else stringResource(R.string.choose_bundle),
            onClick = { confirm = selected },
            enabled = selected != null && selected.dataKb <= s.balanceKb && phoneOk && !ui.redeeming,
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
            title = { Text(stringResource(R.string.confirm_title, b.label), color = Bee.Text, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.confirm_body, b.label, phone, b.carrier), color = Bee.Muted) },
            confirmButton = {
                TextButton(onClick = { confirm = null; onRedeem(b, phone) }) {
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
                    glow = false,
                )
            }
        }
    }
}
