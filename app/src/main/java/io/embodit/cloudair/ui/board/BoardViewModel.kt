package io.embodit.cloudair.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.embodit.cloudair.data.repository.SettingsRepository
import io.embodit.cloudair.data.session.GameSessionStore
import io.embodit.cloudair.domain.model.DiceThrowSpec
import io.embodit.cloudair.domain.model.GameSession
import io.embodit.cloudair.domain.model.Phase
import io.embodit.cloudair.domain.model.SeatKind
import io.embodit.cloudair.domain.model.Settings
import io.embodit.cloudair.domain.usecase.DiceSolver
import io.embodit.cloudair.domain.usecase.DiceThrow
import io.embodit.cloudair.domain.usecase.GameFlowUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * The board window. It projects the live session onto what the 3D scene needs, computes the dice
 * trajectory for each committed throw off the main thread, and hands the scene's reports back to
 * the game flow.
 */
class BoardViewModel(
    private val store: GameSessionStore,
    private val settings: SettingsRepository,
    private val flow: GameFlowUseCase,
    private val sounds: (SoundKind) -> Unit = {},
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val rules: io.embodit.cloudair.domain.usecase.RulesEngine = io.embodit.cloudair.domain.usecase.RulesEngine(),
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val throws: StateFlow<DiceThrow?> =
        store.state.map { it.dice }
            .distinctUntilChanged()
            .mapLatest { spec -> spec?.let { withContext(computeDispatcher) { DiceSolver.createThrow(it.result, it.seed) } } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<BoardUiState> =
        combine(store.state, settings.settings, throws) { session, prefs, throwData -> reduce(session, prefs, throwData) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, reduce(store.state.value, settings.settings.value, null))

    private var lastPhase: Phase? = null

    fun onEvent(event: SceneEvent) {
        when (event) {
            SceneEvent.DieTapped -> { if (flow.requestRoll()) sounds(SoundKind.TAP) }
            is SceneEvent.PlaneTapped -> {
                val g = flow.game ?: return
                if (g.phase == Phase.SELECT && event.seat == g.turn) { if (flow.choosePlane(event.plane)) sounds(SoundKind.TAP) }
            }
            is SceneEvent.DiceSettled -> flow.diceSettled(event.id)
            is SceneEvent.MoveFinished -> {
                flow.moveFinished(event.id)
                if (flow.game?.phase == Phase.ENDED && lastPhase != Phase.ENDED) sounds(SoundKind.WIN)
                lastPhase = flow.game?.phase
            }
            is SceneEvent.Sound -> sounds(event.kind)
        }
    }

    private fun reduce(session: GameSession, prefs: Settings, throwData: DiceThrow?): BoardUiState {
        val game = session.game ?: return BoardUiState(gameId = session.gameId, reducedMotion = prefs.reducedMotion, speed = prefs.speed, soundOn = prefs.sound)
        val spec: DiceThrowSpec? = session.dice
        val human = game.current.kind == SeatKind.HUMAN
        return BoardUiState(
            gameId = session.gameId,
            teams = game.seats.map { it.team },
            planes = game.seats.flatMapIndexed { s, seat -> seat.planes.mapIndexed { p, progress -> PlaneState(s, p, seat.team, progress) } },
            activeSeat = if (game.phase == Phase.ENDED) game.winner else game.turn,
            phase = game.phase,
            dice = spec,
            diceThrow = if (spec != null && throwData != null && throwData.result == spec.result) throwData else null,
            move = session.move,
            lastDice = game.dice,
            reducedMotion = prefs.reducedMotion,
            speed = prefs.speed,
            soundOn = prefs.sound,
            canRoll = human && game.phase == Phase.READY && !session.paused,
            selectable = if (game.phase == Phase.SELECT && human) session.legalPlans.map { it.plane }.toSet() else emptySet(),
            recommended = if (game.phase == Phase.SELECT && human) rules.recommend(session.legalPlans) else null,
        )
    }

    companion object {
        fun factory(store: GameSessionStore, settings: SettingsRepository, flow: GameFlowUseCase, sounds: (SoundKind) -> Unit): ViewModelProvider.Factory =
            viewModelFactory { initializer { BoardViewModel(store, settings, flow, sounds) } }
    }
}
