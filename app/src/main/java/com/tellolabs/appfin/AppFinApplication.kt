package com.tellolabs.appfin

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.tellolabs.appfin.automation.SessionCoordinator
import com.tellolabs.appfin.data.*
import com.tellolabs.appfin.domain.*

private val Context.preferences by preferencesDataStore(name = "appfin")
class AppContainer(context: Context) {
    val apps: AppRepository = PackageManagerAppRepository(context)
    val exclusions: ExclusionRepository = DataStoreExclusionRepository(context.preferences)
    val settings: SettingsRepository = DataStoreSettingsRepository(context.preferences)
    val sessions = SessionCoordinator()
}
class AppFinApplication : Application() {
    val container by lazy { AppContainer(this) }
}
