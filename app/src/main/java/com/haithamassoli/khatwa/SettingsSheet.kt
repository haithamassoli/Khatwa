package com.haithamassoli.khatwa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable

/** Walking-limit presets in seconds; shared by the plan screen and settings. */
internal val walkPresets = listOf(0 to "بدون مشي", 120 to "2 د", 300 to "5 د", 600 to "10 د")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WalkPresetChips(valueS: Int, enabled: Boolean = true, onChange: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        walkPresets.forEach { (s, label) ->
            FilterChip(selected = valueS == s, onClick = { onChange(s) }, label = { Text(label) }, enabled = enabled)
        }
    }
}

@Composable
internal fun NightWalkSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("لا مشي بعد المغرب", style = MaterialTheme.typography.titleMedium)
            Text(
                "بعد غروب الشمس يقترح التطبيق الركوب من الباب فقط.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val settings by vm.store.settings.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("الإعدادات", style = MaterialTheme.typography.titleLarge)
            NightWalkSwitch(settings.noWalkAtNight) { v -> vm.updateSettings { it.copy(noWalkAtNight = v) } }
            HorizontalDivider()
            Text("وقت المشي الافتراضي", style = MaterialTheme.typography.titleMedium)
            Text(
                "أقصى مشي قبل الركوب وبعد النزول لكل رحلة جديدة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WalkPresetChips(settings.defaultWalkS) { s ->
                vm.updateSettings { it.copy(defaultWalkS = s) }
                vm.setWalkStart(s)
                vm.setWalkEnd(s)
            }
            HorizontalDivider()
            SettingsLink(Icons.AutoMirrored.Filled.ListAlt, "نقاطي", "نقاط الركوب والنزول المحفوظة") { onDismiss(); vm.go(Screen.POINTS) }
            SettingsLink(Icons.Filled.History, "السجل", "الأسعار والرحلات السابقة") { onDismiss(); vm.go(Screen.HISTORY) }
            SettingsLink(Icons.Filled.WavingHand, "شاشة الترحيب", "اعرض فكرة التطبيق مرة أخرى") {
                onDismiss()
                vm.updateSettings { it.copy(onboarded = false) }
            }
        }
    }
}

@Composable
private fun SettingsLink(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
