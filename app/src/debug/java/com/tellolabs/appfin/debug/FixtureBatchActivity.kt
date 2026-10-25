package com.tellolabs.appfin.debug

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.tellolabs.appfin.AppFinApplication
import com.tellolabs.appfin.domain.Stage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Debug-only, shell-protected test driver. It cannot accept arbitrary package names. */
class FixtureBatchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Preparing fixture batch" })
        val container = (application as AppFinApplication).container
        val report = java.io.File(cacheDir, "fixture-batch-report.txt")
        report.delete()
        // Returning to MainActivity may destroy this activity. Keep only this bounded
        // debug report job alive until the service's terminal state has been recorded.
        val reportScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        reportScope.launch {
            try {
                val fixtures = setOf("dev.appfin.fixture", "dev.appfin.fixture.second")
                val targets = container.apps.getApps().filter { it.packageName in fixtures }
                check(targets.size == 2 && container.sessions.connected.value) {
                    "Install both fixtures and enable AppFin Accessibility first."
                }
                val previousId = container.sessions.state.value.sessionId
                check(container.sessions.start(targets)) { "Another session is running." }
                if (intent.getBooleanExtra("cancelImmediately", false)) {
                    withTimeoutOrNull(5_000) {
                        container.sessions.state.first { it.running && it.stage == Stage.FINDING_FORCE_STOP }
                    }
                    container.sessions.cancel()
                }
                val result = withTimeout(45_000) {
                    container.sessions.state.first { it.sessionId != previousId && it.stage == Stage.FINISHED }
                }
                report.writeText(result.results.joinToString("\n") { "${it.app.packageName}=${it.outcome}" } +
                    "\nerror=${result.error.orEmpty()}")
            } catch (cancelled: CancellationException) {
                report.writeText("ERROR: fixture report cancelled or timed out")
                throw cancelled
            } catch (error: Exception) {
                report.writeText("ERROR: ${error.message}")
            } finally {
                finish()
                reportScope.cancel()
            }
        }
    }
}
