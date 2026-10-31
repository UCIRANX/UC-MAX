package com.v2ray.ang.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.enums.ConnectionMode

@StringRes
private fun ConnectionMode.titleRes(): Int = when (this) {
    ConnectionMode.V2RAY_ONLY -> R.string.connection_mode_v2ray
    ConnectionMode.PSIPHON_ONLY -> R.string.connection_mode_psiphon
    ConnectionMode.PSIPHON_VIA_V2RAY -> R.string.connection_mode_psiphon_via_v2ray
    ConnectionMode.V2RAY_VIA_PSIPHON -> R.string.connection_mode_v2ray_via_psiphon
}

@StringRes
private fun ConnectionMode.descRes(): Int = when (this) {
    ConnectionMode.V2RAY_ONLY -> R.string.connection_mode_v2ray_desc
    ConnectionMode.PSIPHON_ONLY -> R.string.connection_mode_psiphon_desc
    ConnectionMode.PSIPHON_VIA_V2RAY -> R.string.connection_mode_psiphon_via_v2ray_desc
    ConnectionMode.V2RAY_VIA_PSIPHON -> R.string.connection_mode_v2ray_via_psiphon_desc
}

/** کارت انتخاب حالت اتصال: هر گزینه یک دکمهٔ رادیویی با نام و وضعیت انتخاب است */
@Composable
fun ConnectionModeCard(
    selected: ConnectionMode,
    enabled: Boolean,
    onSelect: (ConnectionMode) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Text(
            text = stringResource(R.string.connection_mode_title),
            style = MaterialTheme.typography.titleSmall,
        )
        ConnectionMode.entries.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { mode ->
                    ModeOption(
                        mode = mode,
                        isSelected = mode == selected,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(mode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeOption(
    mode: ConnectionMode,
    isSelected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.selectable(
            selected = isSelected,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onClick,
        ),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = stringResource(mode.titleRes()), style = MaterialTheme.typography.bodyMedium)
            Text(text = stringResource(mode.descRes()), style = MaterialTheme.typography.bodySmall)
        }
    }
}
