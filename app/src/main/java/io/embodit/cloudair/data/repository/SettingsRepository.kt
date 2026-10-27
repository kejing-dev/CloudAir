package io.embodit.cloudair.data.repository

import io.embodit.cloudair.domain.model.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The user's preferences, kept in memory for the session and persisted when possible. */
interface SettingsRepository {
    val settings: StateFlow<Settings>

    fun update(transform: (Settings) -> Settings)
}

class FakeSettingsRepository(initial: Settings = Settings()) : SettingsRepository {
    private val _settings = MutableStateFlow(initial)
    override val settings: StateFlow<Settings> = _settings

    override fun update(transform: (Settings) -> Settings) { _settings.value = transform(_settings.value) }
}
