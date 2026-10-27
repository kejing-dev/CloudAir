package io.embodit.cloudair.platform

import android.content.Context
import android.util.Log
import io.embodit.cloudair.data.repository.GameRepository
import io.embodit.cloudair.data.repository.PrefsGameRepository
import io.embodit.cloudair.data.repository.PrefsSettingsRepository
import io.embodit.cloudair.data.repository.SettingsRepository
import io.embodit.cloudair.data.session.GameSessionStore
import io.embodit.cloudair.domain.usecase.GameFlowUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled object graph. The board window and the control window have separate ViewModels;
 * what they share lives here: the live session, the game flow that drives it, and the preferences.
 */
class CloudAirGraph(context: Context) {
    val sessionStore = GameSessionStore()
    val gameRepository: GameRepository = PrefsGameRepository(context)
    val settings: SettingsRepository = PrefsSettingsRepository(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val flow = GameFlowUseCase(sessionStore, gameRepository, scope, log = { Log.i(TAG, it) })
    val sounds = SoundPlayer(context)

    companion object {
        const val TAG = "CloudAir"

        fun from(context: Context): CloudAirGraph = (context.applicationContext as SpatialApplication).graph
    }
}
