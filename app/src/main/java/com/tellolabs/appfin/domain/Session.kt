package com.tellolabs.appfin.domain

enum class Stage { IDLE, PREPARING, OPENING_APP_INFO, FINDING_FORCE_STOP, AWAITING_CONFIRMATION, VERIFYING_RESULT, FINISHED }
enum class Outcome { STOPPED_VERIFIED, ALREADY_STOPPED, SKIPPED, UNAVAILABLE, TIMED_OUT, FAILED, UNVERIFIED, CANCELLED }
data class TargetResult(val app: InstalledApp, val outcome: Outcome)
data class SessionState(
    val sessionId: Long = 0,
    val running: Boolean = false,
    val stage: Stage = Stage.IDLE,
    val current: InstalledApp? = null,
    val total: Int = 0,
    val results: List<TargetResult> = emptyList(),
    val error: String? = null,
)
enum class Interaction { WAITING, CLICKED, DISABLED, REJECTED }
interface AutomationPort {
    fun openAppInfo(app: InstalledApp)
    fun clickForceStop(app: InstalledApp): Interaction
    fun clickConfirmation(app: InstalledApp): Interaction
    fun endTarget() = Unit
    fun finish()
}
interface OverlayPort {
    fun show(onCancel: () -> Unit)
    fun render(state: SessionState)
    fun hide()
}
