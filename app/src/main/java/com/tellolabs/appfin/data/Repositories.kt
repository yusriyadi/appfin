package com.tellolabs.appfin.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.graphics.drawable.toBitmap
import android.graphics.Bitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.tellolabs.appfin.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class PackageManagerAppRepository(context: Context) : AppRepository {
    private val pm = context.packageManager
    override val protectedPackages = setOfNotNull(
        context.packageName, "com.android.settings",
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
            .resolveActivity(pm)?.packageName,
    )
    override suspend fun getApps(includeSystem: Boolean, includeStopped: Boolean) = withContext(Dispatchers.IO) {
        val all = pm.getInstalledApplications(0).map { it to pm.getApplicationLabel(it).toString() }
        val labelCounts = all.groupingBy { it.second.lowercase() }.eachCount()
        all.filter { (it, _) ->
            (includeSystem || !it.isSystemApp) &&
                (includeStopped || it.flags and ApplicationInfo.FLAG_STOPPED == 0) &&
                it.packageName !in protectedPackages
        }.map { (info, label) ->
            InstalledApp(
                packageName = info.packageName,
                label = label,
                uniqueLabel = labelCounts[label.lowercase()] == 1,
                isSystemApp = info.isSystemApp,
                isStopped = info.flags and ApplicationInfo.FLAG_STOPPED != 0,
            )
        }
            .sortedBy { it.label.lowercase() }
    }
    override suspend fun getState(packageName: String) = withContext(Dispatchers.IO) {
        try {
            val info = pm.getApplicationInfo(packageName, 0)
            if (info.flags and ApplicationInfo.FLAG_STOPPED != 0) PackageState.STOPPED else PackageState.ACTIVE
        } catch (_: PackageManager.NameNotFoundException) { PackageState.UNAVAILABLE }
    }
    override suspend fun getIconPng(packageName: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            ByteArrayOutputStream().use { output ->
                pm.getApplicationIcon(packageName).toBitmap(96, 96).compress(Bitmap.CompressFormat.PNG, 100, output)
                output.toByteArray()
            }
        } catch (_: PackageManager.NameNotFoundException) { null }
    }
}

private val ApplicationInfo.isSystemApp: Boolean
    get() = flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

class DataStoreExclusionRepository(private val store: DataStore<Preferences>) : ExclusionRepository {
    private val key = stringSetPreferencesKey("excluded_packages")
    override val excludedPackages = store.data.map { it[key]?.toSet() ?: emptySet() }
    override suspend fun setExcluded(packageName: String, excluded: Boolean) {
        store.edit { prefs ->
            val current = prefs[key].orEmpty()
            prefs[key] = if (excluded) current + packageName else current - packageName
        }
    }
}
class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    private val packageNamesKey = booleanPreferencesKey("show_package_names")
    private val systemAppsKey = booleanPreferencesKey("show_system_apps")
    override val settings = store.data.map {
        AppSettings(showPackageNames = it[packageNamesKey] ?: true, showSystemApps = it[systemAppsKey] ?: false)
    }
    override suspend fun setShowPackageNames(show: Boolean) { store.edit { it[packageNamesKey] = show } }
    override suspend fun setShowSystemApps(show: Boolean) { store.edit { it[systemAppsKey] = show } }
}
