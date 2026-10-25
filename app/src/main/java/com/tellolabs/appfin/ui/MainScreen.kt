package com.tellolabs.appfin.ui

import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tellolabs.appfin.R
import com.tellolabs.appfin.domain.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainState,
    connected: Boolean,
    session: SessionState,
    vm: MainViewModel,
    openAppInfo: (InstalledApp) -> Unit,
    openSettings: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showResults by rememberSaveable { mutableStateOf(false) }
    var pendingBatch by remember { mutableStateOf<List<InstalledApp>?>(null) }
    val tab = AppListTab.entries.getOrElse(tabIndex) { AppListTab.ACTIVE }
    val classificationReady = state.exclusionsReady && state.settingsReady
    val ready = classificationReady && !state.dataError && !state.loading && !session.running
    val filtered = remember(state.apps, state.excluded, tab, query, classificationReady) {
        if (!classificationReady) emptyList() else state.appsFor(tab).filter {
            it.label.contains(query, true) || it.packageName.contains(query, true)
        }
    }
    val batchTargets = remember(state.apps, state.excluded) { state.batchTargets }
    val listState = rememberLazyListState()
    LaunchedEffect(tab, query) { listState.scrollToItem(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = vm::refresh, enabled = !session.running && !state.loading) {
                        Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.refresh))
                    }
                    IconButton(onClick = openSettings, enabled = !session.running) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings_title))
                    }
                },
            )
        },
        bottomBar = {
            if (tab == AppListTab.ACTIVE || session.running || session.results.isNotEmpty()) {
                Surface(tonalElevation = 2.dp) {
                    Column(
                        Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        if (session.running) {
                            Text(stringResource(R.string.session_running, session.current?.label.orEmpty()),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { vm.sessions.cancel() }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.cancel))
                            }
                        } else if (tab == AppListTab.ACTIVE) {
                            Button(
                                onClick = {
                                    if (!connected) openSettings() else pendingBatch = batchTargets.toList()
                                },
                                enabled = ready && batchTargets.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(
                                    if (state.settings.showSystemApps) R.string.stop_all_user_apps else R.string.stop_all,
                                    batchTargets.size,
                                ))
                            }
                        }
                        if (!session.running && session.results.isNotEmpty()) {
                            TextButton(onClick = { showResults = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.results, session.results.size))
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            PrimaryTabRow(selectedTabIndex = tabIndex) {
                AppListTab.entries.forEachIndexed { index, item ->
                    Tab(
                        selected = tab == item,
                        onClick = { tabIndex = index },
                        enabled = !session.running,
                        text = { Text(stringResource(tabLabel(item)), style = MaterialTheme.typography.labelMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            if (state.dataError) HomeError(R.string.list_data_error)
            if (state.startError) HomeError(R.string.start_error)
            if (session.error != null) HomeError(R.string.session_error)
            val waiting = state.loading || (!classificationReady && !state.dataError)
            if (waiting) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (filtered.isEmpty() && !waiting && !state.dataError) {
                Box(Modifier.fillMaxWidth().weight(1f).padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(if (query.isNotBlank()) R.string.empty_apps else emptyTabLabel(tab)),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f), state = listState,
                    contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            excluded = app.packageName in state.excluded,
                            showPackage = state.settings.showPackageNames,
                            canEdit = ready,
                            canOpenInfo = !session.running,
                            vm = vm,
                            openAppInfo = openAppInfo,
                            stop = { if (!connected) openSettings() else vm.start(listOf(it)) },
                        )
                    }
                }
            }
        }
    }
    pendingBatch?.let { targets ->
        AlertDialog(
            onDismissRequest = { pendingBatch = null },
            title = { Text(stringResource(R.string.confirm_title, targets.size)) },
            text = { Text(stringResource(R.string.confirm_body)) },
            confirmButton = {
                TextButton(onClick = { pendingBatch = null; vm.start(targets) }, enabled = ready && connected) {
                    Text(stringResource(R.string.start))
                }
            },
            dismissButton = { TextButton(onClick = { pendingBatch = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showResults) SessionResultsDialog(session) { showResults = false }
}

@Composable
private fun HomeError(@StringRes message: Int) {
    Text(stringResource(message), Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun AppRow(
    app: InstalledApp,
    excluded: Boolean,
    showPackage: Boolean,
    canEdit: Boolean,
    canOpenInfo: Boolean,
    vm: MainViewModel,
    openAppInfo: (InstalledApp) -> Unit,
    stop: (InstalledApp) -> Unit,
) {
    var menuOpen by remember(app.packageName) { mutableStateOf(false) }
    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        vm.icon(app)?.let { value = BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                icon?.let { Image(it, contentDescription = null, Modifier.fillMaxSize()) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                if (showPackage) Text(app.packageName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { openAppInfo(app) }, enabled = canOpenInfo) {
                Icon(painterResource(R.drawable.ic_info), stringResource(R.string.app_info, app.label))
            }
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = canEdit) {
                    Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.app_options, app.label))
                }
                DropdownMenu(expanded = menuOpen && canEdit, onDismissRequest = { menuOpen = false }) {
                    if (!excluded && !app.isStopped) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.stop)) },
                            onClick = { menuOpen = false; stop(app) })
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(if (excluded) R.string.remove_exclusion else R.string.exclude)) },
                        onClick = { menuOpen = false; vm.setExcluded(app, !excluded) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionResultsDialog(session: SessionState, close: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text(stringResource(R.string.results, session.results.size)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.session_summary,
                    session.results.count { it.outcome == Outcome.STOPPED_VERIFIED },
                    session.results.count { it.outcome == Outcome.ALREADY_STOPPED || it.outcome == Outcome.SKIPPED },
                    session.results.count { it.outcome !in setOf(Outcome.STOPPED_VERIFIED, Outcome.ALREADY_STOPPED, Outcome.SKIPPED) }),
                    style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(session.results, key = { it.app.packageName }) {
                        Column {
                            Text(it.app.label, style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(outcomeLabel(it.outcome)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text(stringResource(R.string.close)) } },
    )
}

private fun tabLabel(tab: AppListTab) = when (tab) {
    AppListTab.ACTIVE -> R.string.tab_active
    AppListTab.STOPPED -> R.string.tab_stopped
    AppListTab.EXCLUDED -> R.string.tab_excluded
}
private fun emptyTabLabel(tab: AppListTab) = when (tab) {
    AppListTab.ACTIVE -> R.string.empty_active
    AppListTab.STOPPED -> R.string.empty_stopped
    AppListTab.EXCLUDED -> R.string.empty_excluded
}
private fun outcomeLabel(outcome: Outcome) = when (outcome) {
    Outcome.STOPPED_VERIFIED -> R.string.stopped_verified
    Outcome.ALREADY_STOPPED -> R.string.already_stopped
    Outcome.SKIPPED -> R.string.skipped
    Outcome.UNAVAILABLE -> R.string.unavailable
    Outcome.TIMED_OUT -> R.string.timed_out
    Outcome.FAILED -> R.string.failed
    Outcome.UNVERIFIED -> R.string.unverified
    Outcome.CANCELLED -> R.string.cancelled
}
