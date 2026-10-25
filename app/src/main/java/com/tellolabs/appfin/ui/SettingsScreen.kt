package com.tellolabs.appfin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tellolabs.appfin.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: MainState,
    connected: Boolean,
    running: Boolean,
    vm: MainViewModel,
    back: () -> Unit,
    openAccessibility: () -> Unit,
) {
    val editable = state.settingsReady && !state.loading && !state.dataError && !running
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.dataError) {
                Text(stringResource(R.string.data_error), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = vm::refresh, enabled = !running) { Text(stringResource(R.string.refresh)) }
            }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.accessibility_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(if (connected) R.string.service_active else R.string.service_inactive),
                        color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.permission_explanation), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = openAccessibility, enabled = !running) {
                        Text(stringResource(if (connected) R.string.open_accessibility else R.string.enable_service))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.list_settings), style = MaterialTheme.typography.titleMedium)
                SettingsToggle(
                    title = stringResource(R.string.show_system_apps),
                    description = stringResource(R.string.system_apps_description),
                    checked = state.settings.showSystemApps,
                    enabled = editable,
                    change = { vm.setShowSystemApps(it) },
                )
                SettingsToggle(
                    title = stringResource(R.string.show_packages),
                    description = stringResource(R.string.package_names_description),
                    checked = state.settings.showPackageNames,
                    enabled = editable,
                    change = { vm.showPackages(it) },
                )
            }
            HorizontalDivider()
            Text(stringResource(R.string.active_explanation), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsToggle(title: String, description: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch,
            onValueChange = change).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
