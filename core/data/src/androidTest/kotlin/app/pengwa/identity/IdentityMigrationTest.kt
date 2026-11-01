package app.pengwa.identity

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.pengwa.settings.BooleanSetting
import app.pengwa.settings.SettingsStorage
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.*

@RunWith(AndroidJUnit4::class)
class IdentityMigrationTest {
    @Test fun migratesIdentityCardProgressAndSettingsAndPersistsChanges() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID()
        val original = Identity(id, "测试娃娃", CardContent(blocks = listOf(CardBlock(BlockType.Text, "介绍", "必须保留"))), cardComplete = true)
        val legacy = context.getSharedPreferences("identity", Context.MODE_PRIVATE)
        assertTrue(legacy.edit().putString("id", id.toString()).putString("display_name", original.displayName)
            .putString("card", Base64.getEncoder().encodeToString(CardCodec.encode(original)))
            .putInt("onboarding_step", OnboardingStep.LearnExchange.storedValue).commit())
        val oldSettings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        assertTrue(oldSettings.edit().putBoolean("background_broadcast", true).putBoolean("ongoing_notification", true).commit())
        val repository = IdentityRepository(context)
        coroutineScope { repeat(5) { launch { repository.initialize() } } }
        SettingsStorage.initialize(context)
        assertEquals(id, repository.id)
        assertEquals(original, repository.load())
        assertEquals(OnboardingStep.LearnExchange, repository.onboardingStep())
        assertTrue(BooleanSetting("background_broadcast").get(context))
        assertTrue(BooleanSetting("ongoing_notification").get(context))
        assertFalse(legacy.contains("id"))
        assertFalse(oldSettings.contains("background_broadcast"))
        val edited = original.copy(displayName = "改名后")
        repository.saveCard(edited, OnboardingStep.Complete)
        BooleanSetting("background_broadcast").set(context, false)
        val reopened = IdentityRepository(context)
        reopened.initialize()
        SettingsStorage.initialize(context)
        assertEquals(id, reopened.id)
        assertEquals(edited, reopened.load())
        assertEquals(OnboardingStep.Complete, reopened.onboardingStep())
        assertFalse(BooleanSetting("background_broadcast").get(context))
        assertTrue(BooleanSetting("ongoing_notification").get(context))
        assertFailsWith<IllegalArgumentException> { reopened.saveCard(edited.copy(id = UUID.randomUUID())) }
        assertEquals(edited, reopened.load())
    }
    @Test fun freshInstallCreatesOneDurableIdAcrossConcurrentRepositories() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(context.cacheDir, "fresh-identity.preferences_pb")
        file.delete()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = scope) { file }
        try {
            val repositories = List(8) { IdentityRepository(store) }
            coroutineScope { repositories.forEach { launch { it.initialize() } } }
            assertEquals(1, repositories.map { it.id }.toSet().size)
            repositories.forEach {
                assertNull(it.load())
                assertEquals(OnboardingStep.CreateCard, it.onboardingStep())
            }
            repositories.first().saveDisplayName("已有用户")
            val reopened = IdentityRepository(store)
            reopened.initialize()
            assertEquals(repositories.first().id, reopened.id)
            assertEquals(OnboardingStep.Complete, reopened.onboardingStep())
        } finally { scope.cancel(); file.delete() }
    }

}
