package com.tellolabs.appfin.domain

import kotlinx.coroutines.flow.Flow

data class InstalledApp(
    val packageName: String,
    val label: String,
    val uniqueLabel: Boolean = false,
    val isSystemApp: Boolean = false,
    val isStopped: Boolean = false,
)
enum class PackageState { ACTIVE, STOPPED, UNAVAILABLE }
data class AppSettings(val showPackageNames: Boolean = true, val showSystemApps: Boolean = false)

interface AppRepository {
    suspend fun getApps(includeSystem: Boolean = false, includeStopped: Boolean = false): List<InstalledApp>
    suspend fun getState(packageName: String): PackageState
    suspend fun getIconPng(packageName: String): ByteArray?
    val protectedPackages: Set<String>
}
interface ExclusionRepository {
    val excludedPackages: Flow<Set<String>>
    suspend fun setExcluded(packageName: String, excluded: Boolean)
}
interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setShowPackageNames(show: Boolean)
    suspend fun setShowSystemApps(show: Boolean)
}
