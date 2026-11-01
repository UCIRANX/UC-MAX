package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.core.CdnIpScanner

/** صفحهٔ اسکن IP آکامای: اسکن رنج‌ها، اسکن دستی، توقف و نمایش پیشرفت */
@Composable
fun CdnScannerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state by CdnIpScanner.state.collectAsState()
    var rangesText by remember { mutableStateOf(CdnIpScanner.DEFAULT_AKAMAI_RANGES.joinToString("\n")) }
    var manualText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cdn_scanner_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = rangesText,
                    onValueChange = { rangesText = it },
                    label = { Text(stringResource(R.string.cdn_scanner_ranges)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = manualText,
                    onValueChange = { manualText = it },
                    label = { Text(stringResource(R.string.cdn_scanner_manual)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(
                        R.string.cdn_scanner_progress,
                        state.scanned,
                        state.healthy,
                        state.saved,
                    )
                )
            }
        },
        confirmButton = {
            if (state.running) {
                TextButton(onClick = { CdnIpScanner.stop() }) {
                    Text(stringResource(R.string.cdn_scanner_stop))
                }
            } else {
                Row {
                    TextButton(onClick = {
                        CdnIpScanner.start(context, rangesText, manualText, manualMode = false)
                    }) {
                        Text(stringResource(R.string.cdn_scanner_scan_ranges))
                    }
                    TextButton(onClick = {
                        CdnIpScanner.start(context, rangesText, manualText, manualMode = true)
                    }) {
                        Text(stringResource(R.string.cdn_scanner_scan_manual))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cdn_scanner_close))
            }
        },
    )
}
