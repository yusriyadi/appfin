package com.tellolabs.appfin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tellolabs.appfin.automation.SessionCoordinator
import com.tellolabs.appfin.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppListTab { ACTIVE, STOPPED, EXCLUDED }

data class MainState(
    val apps: List<InstalledApp> = emptyList(),
    val excluded: Set<String> = emptySet(),
    val settings: AppSettings = AppSettings(),
    val loading: Boolean = true,
    val exclusionsReady: Boolean = false,
    val settingsReady: Boolean = false,
    val dataError: Boolean = false,
    val startError: Boolean = false,
) {
    // Exclusions take priority, so an app appears in exactly one tab.
    fun appsFor(tab: AppListTab): List<InstalledApp> = apps.filter { app ->
        val category = when {
            app.packageName in excluded -> AppListTab.EXCLUDED
            app.isStopped -> AppListTab.STOPPED
            else -> AppListTab.ACTIVE
        }
        category == tab
    }
    val batchTargets: List<InstalledApp>
        get() = appsFor(AppListTab.ACTIVE).filterNot { it.isSystemApp }
}

class MainViewModel(
    private val appRepository: AppRepository,
    private val exclusions: ExclusionRepository,
    private val settingsRepository: SettingsRepository,
    val sessions: SessionCoordinator,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MainState())
    val state = mutableState.asStateFlow()
    private var exclusionsJob: Job? = null
    private var settingsJob: Job? = null
    private var loadJob: Job? = null

    init { refresh() }

    fun refresh() {
        exclusionsJob?.cancel()
        settingsJob?.cancel()
        loadJob?.cancel()
        loadJob = null
        mutableState.value = state.value.copy(
            loading = true, dataError = false, exclusionsReady = false, settingsReady = false,
        )
        exclusionsJob = viewModelScope.launch {
            safely {
                exclusions.excludedPackages.collect {
                    mutableState.value = state.value.copy(excluded = it, exclusionsReady = true)
                }
            }
        }
        settingsJob = viewModelScope.launch {
            safely {
                settingsRepository.settings.collect { settings ->
                    val systemFilterChanged = settings.showSystemApps != state.value.settings.showSystemApps
                    mutableState.value = state.value.copy(settings = settings, settingsReady = true)
                    if (loadJob == null || systemFilterChanged) loadApps(settings.showSystemApps)
                }
            }
            // A failed settings read must not leave the page loading forever.
            if (!state.value.settingsReady) mutableState.value = state.value.copy(loading = false)
        }
    }

    private fun loadApps(includeSystem: Boolean) {
        loadJob?.cancel()
        mutableState.value = state.value.copy(loading = true)
        loadJob = viewModelScope.launch {
            safely {
                // All stopped states are needed for the Terhenti and Dikecualikan tabs.
                val loaded = appRepository.getApps(includeSystem = includeSystem, includeStopped = true)
                mutableState.value = state.value.copy(apps = loaded)
            }
            mutableState.value = state.value.copy(loading = false)
        }
    }

    fun setExcluded(app: InstalledApp, value: Boolean) = viewModelScope.launch {
        if (!sessions.state.value.running) safely { exclusions.setExcluded(app.packageName, value) }
    }
    fun showPackages(value: Boolean) = viewModelScope.launch {
        if (!sessions.state.value.running) safely { settingsRepository.setShowPackageNames(value) }
    }
    fun setShowSystemApps(value: Boolean) = viewModelScope.launch {
        if (!sessions.state.value.running) safely { settingsRepository.setShowSystemApps(value) }
    }
    fun start(targets: List<InstalledApp>) {
        val current = state.value
        val ready = current.exclusionsReady && current.settingsReady && !current.loading && !current.dataError
        mutableState.value = current.copy(startError = !ready || !sessions.start(targets))
    }
    suspend fun icon(app: InstalledApp): ByteArray? = appRepository.getIconPng(app.packageName)
    private suspend fun safely(block: suspend () -> Unit) {
        try { block() } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { mutableState.value = state.value.copy(dataError = true) }
    }
}
