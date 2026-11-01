package app.pengwa.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Settings are persisted before observers and hardware side effects see the new value. */
class BooleanSetting(key: String, private val defaultValue: Boolean = false) {
    private val preference = booleanPreferencesKey(key)

    fun get(context: Context): Boolean = SettingsStorage.snapshot.value[preference] ?: defaultValue

    suspend fun set(context: Context, value: Boolean) = SettingsStorage.set(context, preference, value)

    fun observe(context: Context, onChange: (Boolean) -> Unit): () -> Unit {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            SettingsStorage.snapshot.map { it[preference] ?: defaultValue }.distinctUntilChanged().collect(onChange)
        }
        return { scope.cancel() }
    }
}
