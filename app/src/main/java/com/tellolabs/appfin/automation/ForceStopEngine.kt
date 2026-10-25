package com.tellolabs.appfin.automation

import com.tellolabs.appfin.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** One coroutine owns a session. No delayed callback or accessibility node survives a target. */
class ForceStopEngine(
    private val scope: CoroutineScope,
    private val apps: AppRepository,
    private val exclusions: ExclusionRepository,
    private val automation: AutomationPort,
    private val overlay: OverlayPort,
    private val stepTimeoutMs: Long = 5_000,
    private val pollMs: Long = 150,
) {
    private val mutableState = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = mutableState.asStateFlow()
    private var job: Job? = null
    private var cancellationReason: String? = null
    private var nextSessionId = 0L
    private val windowChanges = Channel<Unit>(Channel.CONFLATED)

    fun start(targets: List<InstalledApp>): Boolean {
        if (state.value.running || targets.isEmpty()) return false
        val queue = targets.distinctBy { it.packageName }
        cancellationReason = null
        mutableState.value = SessionState(sessionId = ++nextSessionId, running = true, stage = Stage.PREPARING, total = queue.size)
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val excluded = withTimeout(stepTimeoutMs) { exclusions.excludedPackages.first() }
                ensureActive()
                overlay.show { cancel() }
                for (target in queue) {
                    ensureActive()
                    update(state.value.copy(current = target, stage = Stage.PREPARING))
                    val outcome = if (target.packageName in excluded || target.packageName in apps.protectedPackages) {
                        Outcome.SKIPPED
                    } else process(target)
                    ensureActive()
                    update(state.value.copy(results = state.value.results + TargetResult(target, outcome)))
                }
            } catch (cancelled: CancellationException) {
                if (cancelled is TimeoutCancellationException) {
                    mutableState.value = state.value.copy(error = "preparation_timeout")
                    addRemaining(queue, Outcome.TIMED_OUT)
                } else {
                    addRemaining(queue, Outcome.CANCELLED)
                }
            } catch (_: Exception) {
                mutableState.value = state.value.copy(error = "session_failed")
                addRemaining(queue, Outcome.FAILED)
            } finally {
                // Keep running=true until all old-session effects have been cleaned up.
                runCatching { overlay.hide() }
                runCatching { automation.finish() }
                mutableState.value = state.value.copy(
                    running = false, stage = Stage.FINISHED, current = null,
                    error = cancellationReason ?: state.value.error,
                )
            }
        }
        return true
    }

    fun cancel(reason: String? = null) {
        cancellationReason = reason
        job?.cancel()
    }

    fun onWindowChanged() {
        if (state.value.running) windowChanges.trySend(Unit)
    }

    private suspend fun process(target: InstalledApp): Outcome {
        var confirmed = false
        return try {
            when (withTimeout(stepTimeoutMs) { apps.getState(target.packageName) }) {
                PackageState.STOPPED -> return Outcome.ALREADY_STOPPED
                PackageState.UNAVAILABLE -> return Outcome.UNAVAILABLE
                PackageState.ACTIVE -> Unit
            }
            stage(Stage.OPENING_APP_INFO)
            automation.openAppInfo(target)
            stage(Stage.FINDING_FORCE_STOP)
            when (awaitInteraction { automation.clickForceStop(target) }) {
                Interaction.REJECTED -> return Outcome.FAILED
                Interaction.DISABLED -> return if (withTimeout(stepTimeoutMs) { apps.getState(target.packageName) } == PackageState.STOPPED) {
                    Outcome.ALREADY_STOPPED
                } else Outcome.SKIPPED
                else -> Unit
            }
            stage(Stage.AWAITING_CONFIRMATION)
            if (awaitInteraction { automation.clickConfirmation(target) } != Interaction.CLICKED) return Outcome.FAILED
            confirmed = true
            stage(Stage.VERIFYING_RESULT)
            withTimeout(stepTimeoutMs) {
                while (true) {
                    ensureActive()
                    when (apps.getState(target.packageName)) {
                        PackageState.STOPPED -> return@withTimeout Outcome.STOPPED_VERIFIED
                        PackageState.UNAVAILABLE -> return@withTimeout Outcome.UNAVAILABLE
                        PackageState.ACTIVE -> delay(pollMs)
                    }
                }
                @Suppress("UNREACHABLE_CODE") Outcome.UNVERIFIED
            }
        } catch (_: TimeoutCancellationException) {
            if (confirmed) Outcome.UNVERIFIED else Outcome.TIMED_OUT
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { Outcome.FAILED
        } finally { automation.endTarget() }
    }

    private suspend fun awaitInteraction(action: () -> Interaction): Interaction = withTimeout(stepTimeoutMs) {
        while (true) {
            ensureActive()
            val result = action()
            if (result != Interaction.WAITING) return@withTimeout result
            withTimeoutOrNull(pollMs) { windowChanges.receive() }
        }
        @Suppress("UNREACHABLE_CODE") Interaction.REJECTED
    }
    private fun stage(stage: Stage) = update(state.value.copy(stage = stage))
    private fun update(value: SessionState) {
        mutableState.value = value
        overlay.render(value)
    }
    private fun addRemaining(queue: List<InstalledApp>, outcome: Outcome) {
        val done = state.value.results.map { it.app.packageName }.toSet()
        mutableState.value = state.value.copy(results = state.value.results +
            queue.filter { it.packageName !in done }.map { TargetResult(it, outcome) })
    }
}
