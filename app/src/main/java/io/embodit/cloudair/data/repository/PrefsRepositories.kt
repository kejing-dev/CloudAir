package io.embodit.cloudair.data.repository

import android.content.Context
import android.content.SharedPreferences
import io.embodit.cloudair.domain.model.DecisionMode
import io.embodit.cloudair.domain.model.Language
import io.embodit.cloudair.domain.model.MotionSpeed
import io.embodit.cloudair.domain.model.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The saved game in the app's SharedPreferences: one slot, written atomically with commit(). */
class PrefsGameRepository(context: Context) : GameRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences("cloud_air_game", Context.MODE_PRIVATE)

    override fun load(): String? = prefs.getString(KEY, null)

    override fun save(text: String): Boolean = runCatching { prefs.edit().putString(KEY, text).commit() }.getOrDefault(false)

    override fun clear() { prefs.edit().remove(KEY).commit() }

    private companion object { const val KEY = "save" }
}

class PrefsSettingsRepository(context: Context) : SettingsRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences("cloud_air_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    override val settings: StateFlow<Settings> = _settings

    override fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit()
            .putString("language", next.language.name)
            .putBoolean("sound", next.sound)
            .putBoolean("reducedMotion", next.reducedMotion)
            .putString("speed", next.speed.name)
            .putString("decisionMode", next.decisionMode.name)
            .apply()
    }

    private fun read(): Settings = Settings(
        language = prefs.getString("language", null)?.let { runCatching { Language.valueOf(it) }.getOrNull() } ?: Language.SYSTEM,
        sound = prefs.getBoolean("sound", true),
        reducedMotion = prefs.getBoolean("reducedMotion", false),
        speed = prefs.getString("speed", null)?.let { runCatching { MotionSpeed.valueOf(it) }.getOrNull() } ?: MotionSpeed.STANDARD,
        decisionMode = prefs.getString("decisionMode", null)?.let { runCatching { DecisionMode.valueOf(it) }.getOrNull() } ?: DecisionMode.RECOMMEND,
    )
}
