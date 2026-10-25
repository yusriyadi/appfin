package com.tellolabs.appfin.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class DataStoreRepositoriesTest {
    @Test fun exclusionsAndSettingsShareStorageWithoutOverwritingEachOther() = runBlocking {
        val directory = Files.createTempDirectory("appfin-prefs").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { directory.resolve("test.preferences_pb") }
            val exclusions = DataStoreExclusionRepository(store)
            val settings = DataStoreSettingsRepository(store)
            assertTrue(exclusions.excludedPackages.first().isEmpty())
            assertTrue(settings.settings.first().showPackageNames)
            assertFalse(settings.settings.first().showSystemApps)
            exclusions.setExcluded("one", true)
            exclusions.setExcluded("two", true)
            exclusions.setExcluded("one", true)
            settings.setShowPackageNames(false)
            settings.setShowSystemApps(true)
            assertEquals(setOf("one", "two"), exclusions.excludedPackages.first())
            assertFalse(settings.settings.first().showPackageNames)
            assertTrue(settings.settings.first().showSystemApps)
            exclusions.setExcluded("one", false)
            assertEquals(setOf("two"), exclusions.excludedPackages.first())
            assertFalse(settings.settings.first().showPackageNames)
            assertTrue(settings.settings.first().showSystemApps)
        } finally { scope.cancel(); directory.deleteRecursively() }
    }
}
