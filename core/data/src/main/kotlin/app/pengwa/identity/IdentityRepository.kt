package app.pengwa.identity

import android.content.Context
import androidx.datastore.preferences.core.*
import app.pengwa.settings.identityDataStore
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Initialize once before enabling UI/hardware. Callbacks subsequently read only an immutable cache. */
class IdentityRepository internal constructor(private val store: androidx.datastore.core.DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.identityDataStore)
    private val mutex = Mutex()
    @Volatile private var snapshot: Preferences? = null
    @Volatile private var identity: Identity? = null

    val id: UUID get() = UUID.fromString(checkNotNull(snapshot) { "Identity storage is not ready" }[ID])

    suspend fun initialize() = mutex.withLock {
        if (snapshot != null) return@withLock
        withContext(Dispatchers.IO) {
            publish(store.edit {
                if (it[ID]?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() } == null) it[ID] = UUID.randomUUID().toString()
            })
        }
    }

    fun load(): Identity? = identity

    fun onboardingStep(): OnboardingStep {
        val prefs = checkNotNull(snapshot) { "Identity storage is not ready" }
        return OnboardingStep.restore(hasIdentity = prefs[NAME] != null, storedValue = prefs[STEP])
    }

    suspend fun saveDisplayName(name: String) = update { it[NAME] = name }
    suspend fun saveOnboardingStep(step: OnboardingStep) = update { it[STEP] = step.storedValue }

    suspend fun saveCard(identity: Identity, onboardingStep: OnboardingStep? = null) = update {
        require(identity.id == id) { "Cannot replace the local identity" }
        it[NAME] = identity.displayName
        it[CARD] = Base64.getEncoder().encodeToString(CardCodec.encode(identity))
        onboardingStep?.let { step -> it[STEP] = step.storedValue }
    }

    private suspend fun update(transform: (MutablePreferences) -> Unit) = mutex.withLock {
        checkNotNull(snapshot) { "Identity storage is not ready" }
        withContext(Dispatchers.IO) { publish(store.edit(transform)) }
    }

    private fun publish(prefs: Preferences) {
        val name = prefs[NAME]
        val card = prefs[CARD]?.let { runCatching { CardCodec.decode(Base64.getDecoder().decode(it))?.card }.getOrNull() } ?: CardContent()
        identity = name?.let { Identity(UUID.fromString(prefs[ID]), it, card, cardComplete = true) }
        snapshot = prefs
    }

    private companion object {
        val ID = stringPreferencesKey("id")
        val NAME = stringPreferencesKey("display_name")
        val CARD = stringPreferencesKey("card")
        val STEP = intPreferencesKey("onboarding_step")
    }
}
