package com.v2ray.ang.ui.main

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.Image
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.psiphon3.psiphonlibrary.EgressRegionPicker
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.enums.ConnectionMode
import com.v2ray.ang.ui.main.titleRes
import com.v2ray.ang.ui.compose.AppDivider
import com.v2ray.ang.ui.compose.colorFabActive
import com.v2ray.ang.ui.compose.colorFabInactiveDark
import com.v2ray.ang.ui.compose.colorFabInactiveLight
import kotlinx.coroutines.launch

@Composable
fun MainBottomBar(
    displayText: String,
    isRunning: Boolean,
    isConnecting: Boolean = false,
    isDarkTheme: Boolean,
    connectionMode: com.v2ray.ang.enums.ConnectionMode,
    onNavigate: (MainDestination) -> Unit,
    onAction: (MainAction) -> Unit
) {
    var showCdnScanner by remember { mutableStateOf(false) }
    var showModePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rotationAnim = remember { Animatable(0f) }

    LaunchedEffect(isRunning) {
        if (!isRunning) {
            rotationAnim.snapTo(0f)
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = { onAction(MainAction.TestCurrentServer) })
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            AppDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 36.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactButton(
                    text = stringResource(connectionMode.titleRes()),
                    icon = connectionMode.iconRes(),
                    enabled = !isRunning && !isConnecting,
                    modifier = Modifier.weight(1f),
                    onClick = { showModePicker = true }
                )
                CompactButton(
                    text = stringResource(R.string.cdn_scanner_button),
                    icon = R.drawable.ic_btn_ip_scanner,
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = { showCdnScanner = true }
                )
                CompactButton(
                    text = stringResource(R.string.bottom_psiphon),
                    icon = R.drawable.ic_btn_psiphon,
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(MainDestination.Psiphon) }
                )
                CompactButton(
                    text = stringResource(R.string.bottom_country),
                    icon = R.drawable.ic_btn_country,
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        EgressRegionPicker.show(context) {
                            if (isRunning) LauncherManager.restartService(context)
                        }
                    }
                )
            }
            if (showModePicker) {
                AlertDialog(
                    onDismissRequest = { showModePicker = false },
                    title = { Text(stringResource(R.string.connection_mode_title)) },
                    text = {
                        Column {
                            ConnectionMode.entries.forEach { mode ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = mode == connectionMode,
                                            role = Role.RadioButton,
                                            onClick = {
                                                onAction(MainAction.SetConnectionMode(mode))
                                                showModePicker = false
                                            }
                                        )
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = mode == connectionMode, onClick = null)
                                    Column(modifier = Modifier.padding(start = 8.dp)) {
                                        Text(stringResource(mode.titleRes()), style = MaterialTheme.typography.bodyMedium)
                                        Text(stringResource(mode.descRes()), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showModePicker = false }) {
                            Text(stringResource(R.string.cdn_scanner_close))
                        }
                    }
                )
            }
            if (showCdnScanner) {
                CdnScannerDialog(onDismiss = { showCdnScanner = false })
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics {
                        contentDescription = displayText
                    }
                )
            }
        }
        val colorFabConnecting = Color(0xFFFFB74D)
        val fabActive = isRunning || isConnecting
        FloatingActionButton(
            onClick = {
                if (!fabActive) {
                    scope.launch {
                        rotationAnim.animateTo(
                            targetValue = 720f,
                            animationSpec = tween(durationMillis = 3000)
                        )
                    }
                }
                onAction(MainAction.ToggleService)
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 24.dp)
                .offset(y = (-28).dp)
                .navigationBarsPadding(),
            containerColor = when {
                isConnecting -> colorFabConnecting
                isRunning -> colorFabActive
                isDarkTheme -> colorFabInactiveDark
                else -> colorFabInactiveLight
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                }
                Icon(
                    painter = if (fabActive) painterResource(R.drawable.ic_stop_24dp)
                    else painterResource(R.drawable.ic_play_24dp),
                    contentDescription = stringResource(
                        if (fabActive) R.string.acc_stop else R.string.acc_start
                    ),
                    tint = Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = rotationAnim.value }
                )
            }
        }
    }
}

@DrawableRes
internal fun ConnectionMode.iconRes(): Int = when (this) {
    ConnectionMode.V2RAY_ONLY -> R.drawable.ic_mode_v2ray_only
    ConnectionMode.PSIPHON_ONLY -> R.drawable.ic_mode_psiphon_only
    ConnectionMode.PSIPHON_VIA_V2RAY -> R.drawable.ic_mode_psiphon_via_v2ray
    ConnectionMode.V2RAY_VIA_PSIPHON -> R.drawable.ic_mode_v2ray_via_psiphon
}

@Composable
private fun CompactButton(
    text: String,
    icon: Int,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(43.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = text,
            modifier = Modifier
                .fillMaxHeight(0.85f)
                .aspectRatio(1f),
        )
    }
}
