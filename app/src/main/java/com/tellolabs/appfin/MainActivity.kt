package com.tellolabs.appfin

import android.content.Intent
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tellolabs.appfin.ui.MainScreen
import com.tellolabs.appfin.ui.MainViewModel
import com.tellolabs.appfin.ui.SettingsScreen
import com.tellolabs.appfin.domain.Stage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AppFinApplication).container
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                val vm: MainViewModel = viewModel(factory = viewModelFactory {
                    initializer { MainViewModel(container.apps, container.exclusions, container.settings, container.sessions) }
                })
                val state by vm.state.collectAsStateWithLifecycle()
                val connected by vm.sessions.connected.collectAsStateWithLifecycle()
                val session by vm.sessions.state.collectAsStateWithLifecycle()
                var settingsOpen by rememberSaveable { mutableStateOf(false) }
                val screenState = rememberSaveableStateHolder()
                val appInfoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    vm.refresh()
                }
                val accessibilityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    vm.refresh()
                }
                LaunchedEffect(session.sessionId, session.stage) {
                    if (session.stage == Stage.FINISHED && !session.running) vm.refresh()
                }
                BackHandler(enabled = settingsOpen) { settingsOpen = false }
                if (settingsOpen) {
                    screenState.SaveableStateProvider("settings") {
                        SettingsScreen(
                            state = state, connected = connected, running = session.running, vm = vm,
                            back = { settingsOpen = false },
                            openAccessibility = {
                                launchSystemSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                                    accessibilityLauncher, R.string.accessibility_error)
                            },
                        )
                    }
                } else {
                    screenState.SaveableStateProvider("main") {
                        MainScreen(
                            state = state, connected = connected, session = session, vm = vm,
                            openAppInfo = { openAppInfo(it.packageName, appInfoLauncher) },
                            openSettings = { settingsOpen = true },
                        )
                    }
                }
            }
        }
    }

    private fun openAppInfo(packageName: String, launcher: ActivityResultLauncher<Intent>) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
        launchSystemSettings(intent, launcher, R.string.app_info_error)
    }

    private fun launchSystemSettings(intent: Intent, launcher: ActivityResultLauncher<Intent>, @StringRes errorMessage: Int) {
        try {
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }
}
