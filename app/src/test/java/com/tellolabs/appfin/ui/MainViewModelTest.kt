package com.tellolabs.appfin.ui

import com.tellolabs.appfin.automation.SessionCoordinator
import com.tellolabs.appfin.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @Test fun slowAppReadDoesNotOverwriteLoadedExclusionsOrSettings() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var includedStopped: Boolean? = null
            val apps = object : AppRepository {
                override val protectedPackages = emptySet<String>()
                override suspend fun getApps(includeSystem: Boolean, includeStopped: Boolean): List<InstalledApp> {
                    includedStopped = includeStopped
                    delay(100)
                    return listOf(InstalledApp("one", "One"))
                }
                override suspend fun getState(packageName: String) = PackageState.ACTIVE
                override suspend fun getIconPng(packageName: String): ByteArray? = null
            }
            val exclusions = object : ExclusionRepository {
                override val excludedPackages = flowOf(setOf("protected"))
                override suspend fun setExcluded(packageName: String, excluded: Boolean) = Unit
            }
            val settings = object : SettingsRepository {
                override val settings = flowOf(AppSettings(false))
                override suspend fun setShowPackageNames(show: Boolean) = Unit
                override suspend fun setShowSystemApps(show: Boolean) = Unit
            }
            val vm = MainViewModel(apps, exclusions, settings, SessionCoordinator(this))
            advanceUntilIdle()
            assertTrue(vm.state.value.exclusionsReady)
            assertEquals(setOf("protected"), vm.state.value.excluded)
            assertFalse(vm.state.value.settings.showPackageNames)
            assertEquals(1, vm.state.value.apps.size)
            assertFalse(vm.state.value.loading)
            assertTrue(vm.state.value.settingsReady)
            assertEquals(true, includedStopped)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun tabsAreExclusiveAndBatchOnlyIncludesActiveUserApps() {
        val active = InstalledApp("active", "Active")
        val stopped = InstalledApp("stopped", "Stopped", isStopped = true)
        val excludedActive = InstalledApp("excluded.active", "Excluded active")
        val excludedStopped = InstalledApp("excluded.stopped", "Excluded stopped", isStopped = true)
        val system = InstalledApp("system", "System", isSystemApp = true)
        val state = MainState(
            apps = listOf(active, stopped, excludedActive, excludedStopped, system),
            excluded = setOf(excludedActive.packageName, excludedStopped.packageName),
        )
        assertEquals(listOf(active, system), state.appsFor(AppListTab.ACTIVE))
        assertEquals(listOf(stopped), state.appsFor(AppListTab.STOPPED))
        assertEquals(listOf(excludedActive, excludedStopped), state.appsFor(AppListTab.EXCLUDED))
        assertEquals(listOf(active), state.batchTargets)
    }
}
