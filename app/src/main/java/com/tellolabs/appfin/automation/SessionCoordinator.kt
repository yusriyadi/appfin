package com.tellolabs.appfin.automation

import com.tellolabs.appfin.domain.InstalledApp
import com.tellolabs.appfin.domain.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

class SessionCoordinator(
    private val observationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val mutableConnected = MutableStateFlow(false)
    val connected = mutableConnected.asStateFlow()
    private val mutableState = MutableStateFlow(SessionState())
    val state = mutableState.asStateFlow()
    private var engine: ForceStopEngine? = null
    private var observer: Job? = null

    fun connect(engine: ForceStopEngine) {
        this.engine = engine
        mutableConnected.value = true
        observer?.cancel()
        observer = observationScope.launch {
            engine.state.takeWhile {
                mutableState.value = it
                it.running || this@SessionCoordinator.engine === engine
            }.collect {}
        }
    }
    fun start(apps: List<InstalledApp>): Boolean = engine?.start(apps) ?: false
    fun cancel() { engine?.cancel() }
    fun disconnect() {
        val current = engine
        engine = null
        mutableConnected.value = false
        if (current?.state?.value?.running == true) current.cancel("service_disconnected")
        else {
            current?.let { mutableState.value = it.state.value }
            observer?.cancel()
        }
    }
}
