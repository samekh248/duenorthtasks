package app.duenorth.tasks.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.duenorth.tasks.data.db.ListKey
import app.duenorth.tasks.design.theme.Accent
import app.duenorth.tasks.provider.api.ProviderKind
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** T059 and the store half of T063: theme defaults and list shade keys. */
class ThemeSettingsStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() = scope.cancel()

    private fun dataStore(name: String) = PreferenceDataStoreFactory.create(scope = scope) {
        File(folder.root, "$name.preferences_pb")
    }

    @Test
    fun defaultsFollowThePhoneWithMagenta() = runBlocking {
        val settings = ThemeSettingsStore(dataStore("theme")).settings.first()
        assertEquals(ThemeMode.SYSTEM, settings.mode)
        assertEquals(Accent.Magenta, settings.accent)
    }

    @Test
    fun choicesAreKept() = runBlocking {
        val store = ThemeSettingsStore(dataStore("theme"))
        store.setMode(ThemeMode.DARK)
        store.setAccent(Accent.Cobalt)
        assertEquals(ThemeSettings(ThemeMode.DARK, Accent.Cobalt), store.settings.first())
    }

    @Test
    fun followPhoneUsesThePhoneAndOverridesDoNot() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
    }

    @Test
    fun aListHasNoShadeUntilOneIsPickedAndTheMiddleSwatchResetsIt() = runBlocking {
        val store = ListShadeStore(dataStore("shades"))
        val errands = ListKey("l1", "g-errands")
        val key = ListShadeStore.key(ProviderKind.GOOGLE, errands)
        assertEquals(0, ListShadeStore.stepFor(store.steps.first(), ProviderKind.GOOGLE, errands))

        store.set(key, -2)
        assertEquals(-2, ListShadeStore.stepFor(store.steps.first(), ProviderKind.GOOGLE, errands))

        store.set(key, 0)
        assertEquals(emptyMap<String, Int>(), store.steps.first())
    }

    @Test
    fun aNewListsShadeMovesToItsRemoteIdAfterTheFirstPush() = runBlocking {
        val store = ListShadeStore(dataStore("shades"))
        store.set(ListShadeStore.key(ProviderKind.GOOGLE, ListKey("l1", null)), 2)
        assertEquals(mapOf("local:l1" to 2), store.steps.first())

        val pushed = listOf(ListKey("l1", "g-new"))
        assertTrue(ListShadeStore.needsAdopt(store.steps.first(), pushed))
        store.adopt(ProviderKind.GOOGLE, pushed)
        assertEquals(mapOf("google:g-new" to 2), store.steps.first())
        assertFalse(ListShadeStore.needsAdopt(store.steps.first(), pushed))
    }

    @Test
    fun theSameRemoteIdUnderTheOtherServiceIsADifferentList() = runBlocking {
        val store = ListShadeStore(dataStore("shades"))
        store.set(ListShadeStore.key(ProviderKind.GOOGLE, ListKey("l1", "same")), 3)
        assertEquals(0, ListShadeStore.stepFor(store.steps.first(), ProviderKind.MICROSOFT, ListKey("l2", "same")))
    }
}
