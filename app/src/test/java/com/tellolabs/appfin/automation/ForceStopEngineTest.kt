package com.tellolabs.appfin.automation

import com.tellolabs.appfin.domain.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForceStopEngineTest {
    private val target = InstalledApp("test.target", "Target", true)
    private class Apps : AppRepository {
        var packageState = PackageState.ACTIVE
        val states = mutableMapOf<String, PackageState>()
        override val protectedPackages = setOf("com.tellolabs.appfin", "com.android.settings")
        override suspend fun getApps(includeSystem: Boolean, includeStopped: Boolean) = emptyList<InstalledApp>()
        override suspend fun getState(packageName: String) = states[packageName] ?: packageState
        override suspend fun getIconPng(packageName: String): ByteArray? = null
    }
    private class Exclusions : ExclusionRepository {
        val values = MutableStateFlow(emptySet<String>())
        override val excludedPackages: Flow<Set<String>> = values
        override suspend fun setExcluded(packageName: String, excluded: Boolean) {
            values.value = if (excluded) values.value + packageName else values.value - packageName
        }
    }
    private class Automation(private val apps: Apps) : AutomationPort {
        var force = Interaction.CLICKED
        var confirm = Interaction.CLICKED
        var verify = true
        var openFails = false
        var opens = 0
        var clicks = 0
        var finishes = 0
        val openedPackages = mutableListOf<String>()
        val forceByPackage = mutableMapOf<String, Interaction>()
        val openFailsFor = mutableSetOf<String>()
        var targetsEnded = 0
        override fun openAppInfo(app: InstalledApp) {
            opens++
            openedPackages += app.packageName
            if (openFails || app.packageName in openFailsFor) error("cannot open")
        }
        override fun clickForceStop(app: InstalledApp): Interaction { clicks++; return forceByPackage[app.packageName] ?: force }
        override fun clickConfirmation(app: InstalledApp): Interaction {
            if (confirm == Interaction.CLICKED && verify) apps.states[app.packageName] = PackageState.STOPPED
            return confirm
        }
        override fun finish() { finishes++ }
        override fun endTarget() { targetsEnded++ }
    }
    private class Overlay : OverlayPort {
        var attached = false
        var fail = false
        override fun show(onCancel: () -> Unit) { if (fail) error("attach"); attached = true }
        override fun render(state: SessionState) = Unit
        override fun hide() { attached = false }
    }
    private class Fixture(scope: TestScope, exclusionsOverride: ExclusionRepository? = null) {
        val apps = Apps()
        val exclusions = Exclusions()
        val automation = Automation(apps)
        val overlay = Overlay()
        val engine = ForceStopEngine(scope, apps, exclusionsOverride ?: exclusions, automation, overlay, 500, 50)
        val result get() = engine.state.value.results.single().outcome
    }

    @Test fun successRequiresObservedStoppedState() = runTest {
        val f = Fixture(this)
        assertTrue(f.engine.start(listOf(target)))
        advanceUntilIdle()
        assertEquals(Outcome.STOPPED_VERIFIED, f.result)
        assertFalse(f.engine.state.value.running)
        assertFalse(f.overlay.attached)
        assertEquals(1, f.automation.finishes)
    }
    @Test fun acceptedClicksWithoutStoppedStateAreUnverified() = runTest {
        val f = Fixture(this).apply { automation.verify = false }
        f.engine.start(listOf(target)); advanceUntilIdle()
        assertEquals(Outcome.UNVERIFIED, f.result)
    }
    @Test fun confirmationTimeoutIsNeverSuccess() = runTest {
        val f = Fixture(this).apply { automation.confirm = Interaction.WAITING }
        f.engine.start(listOf(target)); advanceUntilIdle()
        assertEquals(Outcome.TIMED_OUT, f.result)
        assertFalse(f.overlay.attached)
    }
    @Test fun rejectedClickIsFailure() = runTest {
        val f = Fixture(this).apply { automation.force = Interaction.REJECTED }
        f.engine.start(listOf(target)); advanceUntilIdle()
        assertEquals(Outcome.FAILED, f.result)
    }
    @Test fun disabledButtonDoesNotProveAlreadyStopped() = runTest {
        val f = Fixture(this).apply { automation.force = Interaction.DISABLED }
        f.engine.start(listOf(target)); advanceUntilIdle()
        assertEquals(Outcome.SKIPPED, f.result)
    }
    @Test fun missingPackageIsUnavailable() = runTest {
        val f = Fixture(this).apply { apps.packageState = PackageState.UNAVAILABLE }
        f.engine.start(listOf(target)); advanceUntilIdle()
        assertEquals(Outcome.UNAVAILABLE, f.result)
        assertEquals(0, f.automation.opens)
    }
    @Test fun stoppedPackageNeedsNoUiActions() = runTest {
        val f = Fixture(this).apply { apps.packageState = PackageState.STOPPED }
        f.engine.start(listOf(target)); advanceUntilIdle()
        assertEquals(Outcome.ALREADY_STOPPED, f.result)
        assertEquals(0, f.automation.opens)
    }
    @Test fun exclusionsAreEnforcedInEngine() = runTest {
        val f = Fixture(this)
        f.exclusions.setExcluded(target.packageName, true)
        f.engine.start(listOf(target)); advanceUntilIdle()
        assertEquals(Outcome.SKIPPED, f.result)
        assertEquals(0, f.automation.opens)
    }
    @Test fun protectedPackagesAreNeverOpened() = runTest {
        val f = Fixture(this)
        f.engine.start(listOf(target.copy(packageName = "com.tellolabs.appfin"))); advanceUntilIdle()
        assertEquals(Outcome.SKIPPED, f.result)
        assertEquals(0, f.automation.opens)
    }
    @Test fun failedExclusionReadDoesNotProceedWithEmptySet() = runTest {
        val broken = object : ExclusionRepository {
            override val excludedPackages = flow<Set<String>> { error("disk read") }
            override suspend fun setExcluded(packageName: String, excluded: Boolean) = Unit
        }
        val f = Fixture(this, broken)
        f.engine.start(listOf(target)); advanceUntilIdle()
        assertNotNull(f.engine.state.value.error)
        assertEquals(0, f.automation.opens)
    }
    @Test fun overlayFailurePreventsOpeningSettings() = runTest {
        val f = Fixture(this).apply { overlay.fail = true }
        f.engine.start(listOf(target)); advanceUntilIdle()
        assertEquals(Outcome.FAILED, f.result)
        assertEquals(0, f.automation.opens)
    }
    @Test fun openingFailureTerminatesOnce() = runTest {
        val f = Fixture(this).apply { automation.openFails = true }
        f.engine.start(listOf(target)); advanceUntilIdle()
        assertEquals(Outcome.FAILED, f.result)
        assertEquals(1, f.automation.opens)
        assertEquals(1, f.automation.finishes)
    }
    @Test fun cancelStopsPollingAndOldSessionCannotAffectRestart() = runTest {
        val f = Fixture(this).apply { automation.force = Interaction.WAITING }
        f.engine.start(listOf(target)); runCurrent()
        assertFalse(f.engine.start(listOf(target)))
        f.engine.cancel(); advanceUntilIdle()
        val clicks = f.automation.clicks
        advanceTimeBy(2000); runCurrent()
        assertEquals(clicks, f.automation.clicks)
        assertEquals(Outcome.CANCELLED, f.result)
        assertFalse(f.overlay.attached)
        f.automation.force = Interaction.CLICKED
        assertTrue(f.engine.start(listOf(target)))
        advanceUntilIdle()
        assertEquals(Outcome.STOPPED_VERIFIED, f.result)
        assertEquals(2, f.automation.finishes)
    }
    @Test fun disconnectStopsActiveSession() = runTest {
        val f = Fixture(this).apply { automation.force = Interaction.WAITING }
        val coordinator = SessionCoordinator(this)
        coordinator.connect(f.engine)
        coordinator.start(listOf(target)); runCurrent()
        coordinator.disconnect(); advanceUntilIdle()
        assertFalse(coordinator.connected.value)
        assertFalse(coordinator.state.value.running)
        assertEquals("service_disconnected", coordinator.state.value.error)
        assertFalse(f.overlay.attached)
        assertEquals(Outcome.CANCELLED, coordinator.state.value.results.single().outcome)
    }
    @Test fun batchProcessesUniqueTargetsInOrder() = runTest {
        val f = Fixture(this)
        val second = target.copy(packageName = "test.second", label = "Second")
        f.engine.start(listOf(target, second, target))
        advanceUntilIdle()
        assertEquals(listOf(target.packageName, second.packageName), f.automation.openedPackages)
        assertEquals(2, f.engine.state.value.total)
        assertEquals(listOf(Outcome.STOPPED_VERIFIED, Outcome.STOPPED_VERIFIED), f.engine.state.value.results.map { it.outcome })
        assertEquals(2, f.automation.targetsEnded)
        assertEquals(1, f.automation.finishes)
    }
    @Test fun disconnectPreservesCompletedResultEvenIfObserverHasNotRun() = runTest {
        val f = Fixture(this)
        val coordinator = SessionCoordinator(this)
        coordinator.connect(f.engine)
        coordinator.start(listOf(target))
        coordinator.disconnect()
        advanceUntilIdle()
        assertFalse(coordinator.connected.value)
        assertFalse(coordinator.state.value.running)
        assertEquals(Outcome.STOPPED_VERIFIED, coordinator.state.value.results.single().outcome)
    }
    @Test fun oneTimeoutDoesNotSkipOrBlockNextTarget() = runTest {
        val f = Fixture(this)
        val second = target.copy(packageName = "test.second")
        f.automation.forceByPackage[target.packageName] = Interaction.WAITING
        f.engine.start(listOf(target, second))
        advanceUntilIdle()
        assertEquals(listOf(Outcome.TIMED_OUT, Outcome.STOPPED_VERIFIED), f.engine.state.value.results.map { it.outcome })
        assertEquals(listOf(target.packageName, second.packageName), f.automation.openedPackages)
    }
    @Test fun openingFailureDoesNotAdvanceQueueTwice() = runTest {
        val f = Fixture(this)
        val second = target.copy(packageName = "test.second")
        f.automation.openFailsFor += target.packageName
        f.engine.start(listOf(target, second))
        advanceUntilIdle()
        advanceTimeBy(2000); runCurrent()
        assertEquals(listOf(Outcome.FAILED, Outcome.STOPPED_VERIFIED), f.engine.state.value.results.map { it.outcome })
        assertEquals(2, f.automation.opens)
    }
    @Test fun excludedAndProtectedTargetsCannotBeReachedViaBatch() = runTest {
        val f = Fixture(this)
        f.exclusions.setExcluded(target.packageName, true)
        f.engine.start(listOf(target, target.copy(packageName = "com.android.settings"), target.copy(packageName = "test.allowed")))
        advanceUntilIdle()
        assertEquals(listOf("test.allowed"), f.automation.openedPackages)
        assertEquals(listOf(Outcome.SKIPPED, Outcome.SKIPPED, Outcome.STOPPED_VERIFIED), f.engine.state.value.results.map { it.outcome })
    }
    @Test fun cancellingBatchMarksRemainingTargetsAndNeverOpensThem() = runTest {
        val f = Fixture(this).apply { automation.force = Interaction.WAITING }
        val second = target.copy(packageName = "test.second")
        f.engine.start(listOf(target, second)); runCurrent()
        f.engine.cancel(); advanceUntilIdle()
        assertEquals(listOf(target.packageName), f.automation.openedPackages)
        assertEquals(listOf(Outcome.CANCELLED, Outcome.CANCELLED), f.engine.state.value.results.map { it.outcome })
        assertFalse(f.overlay.attached)
    }
    @Test fun immediateCancelDuringPreparationStillFinishes() = runTest {
        val exclusions = object : ExclusionRepository {
            override val excludedPackages = flow<Set<String>> { kotlinx.coroutines.delay(1000); emit(emptySet()) }
            override suspend fun setExcluded(packageName: String, excluded: Boolean) = Unit
        }
        val f = Fixture(this, exclusions)
        f.engine.start(listOf(target)); f.engine.cancel(); advanceUntilIdle()
        assertFalse(f.engine.state.value.running)
        assertEquals(Outcome.CANCELLED, f.result)
        assertEquals(0, f.automation.opens)
    }
    @Test fun preparationTimeoutDoesNotLookLikeSuccess() = runTest {
        val exclusions = object : ExclusionRepository {
            override val excludedPackages = flow<Set<String>> { kotlinx.coroutines.delay(1000); emit(emptySet()) }
            override suspend fun setExcluded(packageName: String, excluded: Boolean) = Unit
        }
        val f = Fixture(this, exclusions)
        f.engine.start(listOf(target)); advanceUntilIdle()
        assertEquals(Outcome.TIMED_OUT, f.result)
        assertNotNull(f.engine.state.value.error)
        assertEquals(0, f.automation.opens)
    }
}
