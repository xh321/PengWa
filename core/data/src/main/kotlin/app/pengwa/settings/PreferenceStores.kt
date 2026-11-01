package app.pengwa.settings

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

internal val Context.identityDataStore by preferencesDataStore(
    name = "identity",
    produceMigrations = { listOf(SharedPreferencesMigration(it, "identity")) },
)
private val Context.settingsDataStore by preferencesDataStore(
    name = "settings",
    produceMigrations = { listOf(SharedPreferencesMigration(it, "settings")) },
)

/** Synchronous hardware callbacks read only this memory snapshot; all disk access is suspended. */
object SettingsStorage {
    internal val snapshot = MutableStateFlow<Preferences>(emptyPreferences())

    suspend fun initialize(context: Context) { snapshot.value = context.applicationContext.settingsDataStore.data.first() }

    internal suspend fun set(context: Context, key: Preferences.Key<Boolean>, value: Boolean) {
        snapshot.value = context.applicationContext.settingsDataStore.edit { it[key] = value }
    }
}
