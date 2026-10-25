package com.tellolabs.appfin.automation

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.tellolabs.appfin.AppFinApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class AppKillerService : AccessibilityService() {
    private var scope: CoroutineScope? = null
    private var engine: ForceStopEngine? = null
    private var overlay: ProgressOverlay? = null
    private val container get() = (application as AppFinApplication).container

    override fun onServiceConnected() {
        super.onServiceConnected()
        cleanup()
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = serviceScope
        overlay = ProgressOverlay(this)
        val automation = SettingsAutomation(this) { container.sessions.connected.value }
        engine = ForceStopEngine(serviceScope, container.apps, container.exclusions, automation, overlay!!)
        container.sessions.connect(engine!!)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Events only wake the loop. Re-read fresh windows instead of retaining event.source nodes.
        if (event != null) engine?.onWindowChanged()
    }
    override fun onInterrupt() { engine?.cancel("service_interrupted") }
    override fun onUnbind(intent: Intent?): Boolean { cleanup(); return super.onUnbind(intent) }
    override fun onDestroy() { cleanup(); super.onDestroy() }
    private fun cleanup() {
        if (scope == null) return
        container.sessions.disconnect()
        engine?.cancel("service_disconnected")
        runCatching { overlay?.hide() }
        scope?.cancel()
        scope = null
        engine = null
        overlay = null
    }
}
