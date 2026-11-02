package app.pengwa.settings

import android.content.Context
import android.content.SharedPreferences

/** Boolean preference with a shared storage and listener lifecycle. */
class BooleanSetting(private val key: String, private val defaultValue: Boolean = false) {
    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun get(context: Context): Boolean = prefs(context).getBoolean(key, defaultValue)

    fun set(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    /** Retain the returned function: it keeps the weakly registered listener alive. */
    fun observe(context: Context, onChange: (Boolean) -> Unit): () -> Unit {
        val prefs = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { shared, changedKey ->
            if (changedKey == key) onChange(shared.getBoolean(key, defaultValue))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
