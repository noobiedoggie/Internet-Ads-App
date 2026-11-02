package com.gigabee.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gigabee.app.R
import com.gigabee.app.ui.theme.Bee

@Composable
fun AppRoot(vm: AppViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val activity = LocalContext.current.findActivity()

    LaunchedEffect(ui.messageId) { ui.message?.let { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Bee.Black,
        snackbarHost = {
            SnackbarHost(snackbar) {
                Snackbar(it, shape = RoundedCornerShape(16.dp), containerColor = Bee.Yellow, contentColor = Bee.Black)
            }
        },
        bottomBar = { BeeTabs(tab) { tab = it } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                ui.state == null && ui.loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = Bee.Yellow)
                ui.state == null -> Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BeeMark(side = 56.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.error_generic), color = Bee.Muted)
                    Spacer(Modifier.height(24.dp))
                    BeeButton(stringResource(R.string.retry), onClick = vm::refresh)
                }
                tab == 0 -> HomeScreen(
                    ui,
                    onWatch = { activity?.let(vm::watchAd) },
                    onRedeem = { tab = 1 },
                    onDebugBonus = vm::debugBonus,
                    onSurveys = if (ui.surveysAvailable) vm::openSurveys else null,
                )
                tab == 1 -> RedeemScreen(ui, onRedeem = { bundle -> vm.redeem(bundle.id) })
                else -> ActivityScreen(ui)
            }
        }
    }

    ui.surveyUrl?.let { SurveyScreen(it, onClose = vm::closeSurveys) }
    }

    if (ui.showDemoSurvey) {
        AlertDialog(
            onDismissRequest = vm::dismissDemoSurvey,
            containerColor = Bee.Surface,
            title = { Text(stringResource(R.string.survey_demo_title), color = Bee.Text, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.survey_demo_body), color = Bee.Muted) },
            confirmButton = {
                TextButton(onClick = vm::completeDemoSurvey) {
                    Text(stringResource(R.string.survey_complete), color = Bee.Yellow, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissDemoSurvey) { Text(stringResource(R.string.close), color = Bee.Muted) }
            },
        )
    }
}

@Composable
private fun BeeTabs(selected: Int, onSelect: (Int) -> Unit) {
    val tabs: List<Pair<Int, ImageVector>> = listOf(
        R.string.tab_earn to Icons.Rounded.PlayArrow,
        R.string.tab_redeem to Icons.AutoMirrored.Rounded.Send,
        R.string.tab_activity to Icons.AutoMirrored.Rounded.List,
    )
    Column(Modifier.background(Bee.Black).navigationBarsPadding()) {
        HorizontalDivider(color = Bee.Line)
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            tabs.forEachIndexed { i, (label, icon) ->
                val active = i == selected
                val tint = if (active) Bee.Yellow else Bee.Muted
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Text(
                        stringResource(label),
                        color = tint,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
