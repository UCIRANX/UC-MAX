package com.v2ray.ang.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.psiphon3.psiphonlibrary.EgressRegionPicker
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.enums.ConnectionMode
import com.v2ray.ang.enums.EConfigType
import kotlinx.coroutines.flow.MutableStateFlow

private val ThemeBgTop = Color(0xFFF3F3F5)
private val ThemeBgBottom = Color(0xFFE9E9ED)
private val ThemeInk = Color(0xFF1F1F26)
private val ThemeMuted = Color(0xFF8C8AA8)
private val ThemeAccent = Color(0xFF7C79A6)
private val ThemeRed = Color(0xFFE53935)
private val ThemeConnecting = Color(0xFFFFB74D)

/**
 * صفحهٔ اصلی تم جدید. همه‌ی عملیات‌ها از همان MainAction و همان مسیرهای پروژه‌ی اصلی انجام می‌شوند.
 */
@Composable
fun NewThemeMainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val isRunning = uiState.isRunning
    val isConnecting = uiState.isConnecting
    val statusText = mainViewModel.formatStatus(uiState.status)

    val groups = uiState.groups
    val group = groups.firstOrNull { it.id == uiState.selectedGroupId } ?: groups.firstOrNull()
    val groupId = group?.id.orEmpty()

    val emptyState = remember { MutableStateFlow(ServerGroupUiState()) }
    val groupStateFlow = remember(groupId) {
        if (groupId.isEmpty()) emptyState else mainViewModel.serverGroupState(groupId)
    }
    val groupState by groupStateFlow.collectAsStateWithLifecycle()

    // مرتب‌سازی زنده فقط در تم جدید: کانفیگ‌هایی که پینگ دارند بالا می‌آیند و بر اساس پینگ مرتب می‌شوند.
    val sortedRows = remember(groupState.rows) {
        groupState.rows.sortedWith(
            compareBy<ServerRowUiModel> { if (it.testDelayMillis > 0) 0 else 1 }
                .thenBy { if (it.testDelayMillis > 0) it.testDelayMillis else 0L }
        )
    }

    var showScanner by remember { mutableStateOf(false) }
    var showModePicker by remember { mutableStateOf(false) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showManualMenu by remember { mutableStateOf(false) }
    var showGroupMenu by remember { mutableStateOf(false) }
    var showDeleteGroup by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ThemeBgTop, ThemeBgBottom)))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {

            // ----- نوار بالا -----
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onNavigate(MainDestination.Settings) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings_24dp),
                            contentDescription = stringResource(R.string.title_settings),
                            tint = ThemeInk
                        )
                    }
                    Text(
                        text = "UC",
                        color = ThemeRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .clickable { uriHandler.openUri("https://t.me/uciranir") }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { NewThemeState.set(false) }) {
                        Text("Classic", color = ThemeAccent, fontSize = 13.sp)
                    }
                    Box {
                        IconButton(onClick = { showImportMenu = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_add_24dp),
                                contentDescription = stringResource(R.string.acc_add),
                                tint = ThemeInk
                            )
                        }
                        DropdownMenu(
                            expanded = showImportMenu,
                            onDismissRequest = { showImportMenu = false },
                            containerColor = Color.White
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.title_sub_setting)) },
                                onClick = { showImportMenu = false; onNavigate(MainDestination.Subscriptions) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_item_import_config_clipboard)) },
                                onClick = { showImportMenu = false; onAction(MainAction.ImportClipboard) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_item_import_config_qrcode)) },
                                onClick = { showImportMenu = false; onAction(MainAction.ImportQRcode) }
                            )
                            DropdownMenuItem(
                                text = { Text("Manual Input") },
                                onClick = { showImportMenu = false; showManualMenu = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Paste JSON") },
                                onClick = { showImportMenu = false; onAction(MainAction.ImportClipboard) }
                            )
                        }
                        DropdownMenu(
                            expanded = showManualMenu,
                            onDismissRequest = { showManualMenu = false },
                            containerColor = Color.White
                        ) {
                            ManualItem(R.string.menu_item_import_config_manually_vmess, EConfigType.VMESS) { showManualMenu = false; onAction(it) }
                            ManualItem(R.string.menu_item_import_config_manually_vless, EConfigType.VLESS) { showManualMenu = false; onAction(it) }
                            ManualItem(R.string.menu_item_import_config_manually_ss, EConfigType.SHADOWSOCKS) { showManualMenu = false; onAction(it) }
                            ManualItem(R.string.menu_item_import_config_manually_socks, EConfigType.SOCKS) { showManualMenu = false; onAction(it) }
                            ManualItem(R.string.menu_item_import_config_manually_http, EConfigType.HTTP) { showManualMenu = false; onAction(it) }
                            ManualItem(R.string.menu_item_import_config_manually_trojan, EConfigType.TROJAN) { showManualMenu = false; onAction(it) }
                            ManualItem(R.string.menu_item_import_config_manually_wireguard, EConfigType.WIREGUARD) { showManualMenu = false; onAction(it) }
                            ManualItem(R.string.menu_item_import_config_manually_hysteria2, EConfigType.HYSTERIA2) { showManualMenu = false; onAction(it) }
                        }
                    }
                }
            }

            // ----- دکمه‌ی اتصال -----
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE2E2E7))
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(164.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { onAction(MainAction.ToggleService) }
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(150.dp),
                                color = ThemeConnecting,
                                strokeWidth = 3.dp
                            )
                        }
                        PowerSymbol(
                            color = if (isRunning || isConnecting) ThemeRed else ThemeInk,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }
                Text(
                    text = statusText,
                    color = ThemeMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // ----- ردیف چهار دکمه (میانبر بخش‌های پروژه‌ی اصلی) -----
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ShortcutButton(
                    text = stringResource(uiState.connectionMode.titleRes()),
                    enabled = !isRunning && !isConnecting,
                    modifier = Modifier.weight(1f)
                ) { showModePicker = true }
                ShortcutButton(
                    text = stringResource(R.string.cdn_scanner_button),
                    enabled = true,
                    modifier = Modifier.weight(1f)
                ) { showScanner = true }
                ShortcutButton(
                    text = stringResource(R.string.bottom_psiphon),
                    enabled = true,
                    modifier = Modifier.weight(1f)
                ) { onNavigate(MainDestination.Psiphon) }
                ShortcutButton(
                    text = stringResource(R.string.bottom_country),
                    enabled = true,
                    modifier = Modifier.weight(1f)
                ) {
                    EgressRegionPicker.show(context) {
                        if (isRunning) onAction(MainAction.RestartService)
                    }
                }
            }

            // ----- لیست سرورها -----
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = group?.remarks.orEmpty(),
                        color = ThemeInk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (groupId.isNotEmpty()) {
                        TextButton(onClick = { onAction(MainAction.UpdateGroupSubscription(groupId)) }) {
                            Text("↻", color = ThemeAccent, fontSize = 18.sp)
                        }
                    }
                    TextButton(onClick = { onAction(MainAction.TestAllServers) }) {
                        Text("Ping", color = ThemeAccent, fontSize = 13.sp)
                    }
                    Box {
                        IconButton(onClick = { showGroupMenu = true }) {
                            Text("⋮", color = ThemeAccent, fontSize = 20.sp)
                        }
                        DropdownMenu(
                            expanded = showGroupMenu,
                            onDismissRequest = { showGroupMenu = false },
                            containerColor = Color.White
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.title_settings)) },
                                onClick = { showGroupMenu = false; onNavigate(MainDestination.Subscriptions) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.title_sub_update)) },
                                onClick = { showGroupMenu = false; if (groupId.isNotEmpty()) onAction(MainAction.UpdateGroupSubscription(groupId)) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.title_ping_all_server)) },
                                onClick = { showGroupMenu = false; onAction(MainAction.TestAllServers) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                onClick = {
                                    showGroupMenu = false
                                    if (groupId.isNotEmpty()) {
                                        // همان منطق آیکون سطل‌آشغال صفحهٔ Subscriptions پروژه‌ی اصلی
                                        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE, false)) {
                                            showDeleteGroup = true
                                        } else {
                                            deleteSubscription(groupId)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = sortedRows, key = { it.guid }) { row ->
                        NewServerRow(
                            row = row,
                            selected = row.guid == uiState.selectedGuid,
                            onSelect = { onAction(MainAction.SelectServer(row.guid)) },
                            onEdit = { onAction(MainAction.EditServer(row.guid, row.profile)) },
                            onDelete = { onAction(MainAction.RemoveServer(row.guid)) },
                            onPing = { onAction(MainAction.TestAllServers) }
                        )
                    }
                }
            }
        }
    }

    if (showScanner) {
        CdnScannerDialog(onDismiss = { showScanner = false })
    }
    if (showDeleteGroup) {
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_subscription_group),
            itemName = group?.remarks.orEmpty(),
            onConfirm = {
                showDeleteGroup = false
                deleteSubscription(groupId)
            },
            onDismiss = { showDeleteGroup = false }
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
                                .clickable {
                                    onAction(MainAction.SetConnectionMode(mode))
                                    showModePicker = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = mode == uiState.connectionMode, onClick = null)
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
}

@Composable
private fun ManualItem(labelRes: Int, type: EConfigType, onPick: (MainAction) -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        onClick = { onPick(MainAction.ImportManually(type.value)) }
    )
}

@Composable
private fun ShortcutButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(text = text, fontSize = 12.sp, color = ThemeInk, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NewServerRow(
    row: ServerRowUiModel,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPing: () -> Unit,
) {
    var menuOpen by remember(row.guid) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.remarks,
                color = if (selected) ThemeRed else ThemeInk,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = row.typeDescription,
                color = ThemeMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = if (row.testDelayMillis > 0) "${row.testDelayMillis} ms" else "—",
            color = ThemeAccent,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        IconButton(onClick = onEdit) {
            Text("›", color = ThemeAccent, fontSize = 22.sp)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Text("⋮", color = ThemeAccent, fontSize = 18.sp)
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = Color.White
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit)) },
                    onClick = { menuOpen = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.title_ping_all_server)) },
                    onClick = { menuOpen = false; onPing() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

/** نماد پاور به‌صورت Canvas (بدون نیاز به فایل تصویری جدید). */
@Composable
private fun PowerSymbol(color: Color, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
        val r = w * 0.36f
        val cx = w / 2f
        val cy = h / 2f + h * 0.04f
        drawArc(
            color = color,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
            style = stroke
        )
        drawLine(
            color = color,
            start = Offset(cx, cy - r * 1.15f),
            end = Offset(cx, cy - r * 0.15f),
            strokeWidth = w * 0.08f,
            cap = StrokeCap.Round
        )
    }
}

/** حذف ساب‌لینک: دقیقاً همان توابعی که ViewModel صفحهٔ Subscriptions پروژه‌ی اصلی صدا می‌زند. */
private fun deleteSubscription(subId: String) {
    SettingsManager.removeSubscriptionWithDefault(subId)
    SettingsChangeManager.makeSetupGroupTab()
}
