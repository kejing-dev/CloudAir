package io.embodit.cloudair.ui.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.embodit.cloudair.data.repository.SettingsRepository
import io.embodit.cloudair.data.session.GameSessionStore
import io.embodit.cloudair.domain.model.ActionKind
import io.embodit.cloudair.domain.model.GameConfig
import io.embodit.cloudair.domain.model.GameSession
import io.embodit.cloudair.domain.model.Phase
import io.embodit.cloudair.domain.model.Seat
import io.embodit.cloudair.domain.model.SeatKind
import io.embodit.cloudair.domain.model.Team
import io.embodit.cloudair.domain.model.Settings
import io.embodit.cloudair.domain.usecase.GameFlowUseCase
import io.embodit.cloudair.domain.usecase.RulesEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * The control window: menus, setup, the turn controls and the settings. Game rules never live
 * here; every game action goes to [GameFlowUseCase] and the window re-renders from the session.
 */
class ControlViewModel(
    private val store: GameSessionStore,
    private val settings: SettingsRepository,
    private val flow: GameFlowUseCase,
    private val rules: RulesEngine = RulesEngine(),
) : ViewModel() {

    /** Navigation state owned by this window. */
    private data class Nav(
        val page: ControlPage = ControlPage.HOME,
        val returnTo: ControlPage = ControlPage.HOME,
        val draft: List<SeatDraft> = defaultDraft(2),
        /** Every kind the player ever chose, by fleet, so a seat that leaves and comes back keeps it. */
        val kinds: Map<Team, SeatKind> = emptyMap(),
        val confirmRestart: Boolean = false,
        val confirmReplace: Boolean = false,
    )

    private val nav = MutableStateFlow(Nav())

    val state: StateFlow<ControlUiState> =
        combine(store.state, settings.settings, nav) { session, prefs, n -> reduce(session, prefs, n) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, reduce(store.state.value, settings.settings.value, nav.value))

    private fun reduce(session: GameSession, prefs: Settings, n: Nav): ControlUiState {
        val game = session.game
        val page = when {
            game == null && n.page in setOf(ControlPage.GAME, ControlPage.PAUSE, ControlPage.VICTORY) -> ControlPage.HOME
            game != null && game.phase == Phase.ENDED && n.page in setOf(ControlPage.HOME, ControlPage.GAME, ControlPage.PAUSE) -> ControlPage.VICTORY
            game != null && n.page == ControlPage.HOME -> ControlPage.GAME
            else -> n.page
        }
        val savedGame = if (game == null) flow.peekSavedGame() else null
        val broken = game == null && savedGame == null && flow.hasBrokenSave()
        val view = game?.let { g ->
            val legal = session.legalPlans
            GameView(
                teams = g.seats.map { it.team },
                kinds = g.seats.map { it.kind },
                turn = g.turn,
                round = g.round,
                phase = g.phase,
                dice = g.dice,
                currentTeam = g.current.team,
                isHumanTurn = g.current.kind == SeatKind.HUMAN,
                canRoll = g.current.kind == SeatKind.HUMAN && g.phase == Phase.READY && !session.paused,
                homeCounts = g.seats.map { it.homeCount },
                planes = g.seats.map { it.planes },
                legalPlans = legal,
                recommended = rules.recommend(legal)?.plane,
                selectedKind = g.selectedKind,
                last = g.last,
                winner = g.winner,
                saveFailed = session.saveFailed,
                paused = session.paused,
                allInHangar = g.current.planes.all { it == io.embodit.cloudair.domain.model.Board.HANGAR },
                manual = g.decisionMode == io.embodit.cloudair.domain.model.DecisionMode.MANUAL,
            )
        }
        return ControlUiState(
            page = page,
            returnTo = n.returnTo,
            settings = prefs,
            saved = savedGame?.takeIf { it.phase != Phase.ENDED }?.let { SavedSummary(it.seats.size, it.round) },
            brokenSave = broken,
            draft = n.draft,
            draftValid = GameConfig(n.draft.map { Seat(it.team, it.kind) }).isValid,
            confirmRestart = n.confirmRestart,
            confirmReplace = n.confirmReplace,
            game = view,
        )
    }

    fun onEvent(event: ControlEvent) {
        when (event) {
            ControlEvent.NewGame -> nav.update { it.copy(page = ControlPage.SETUP, returnTo = ControlPage.HOME, confirmReplace = false) }
            ControlEvent.ContinueGame -> if (flow.continueSaved()) nav.update { it.copy(page = ControlPage.GAME) }
            ControlEvent.StartGame -> {
                val n = nav.value
                val config = GameConfig(n.draft.map { Seat(it.team, it.kind) })
                if (!config.isValid) return
                val replacing = flow.game == null && flow.peekSavedGame()?.let { it.phase != Phase.ENDED } == true
                if (replacing && !n.confirmReplace) { nav.update { it.copy(confirmReplace = true) }; return }
                start(config)
            }
            ControlEvent.ConfirmReplace -> {
                val config = GameConfig(nav.value.draft.map { Seat(it.team, it.kind) })
                if (config.isValid) start(config)
            }
            is ControlEvent.SetSeatCount -> nav.update { n ->
                val count = event.count.coerceIn(2, 4)
                val kinds = n.kinds + n.draft.associate { it.team to it.kind }
                val teams = GameConfig.teamsFor(count)
                n.copy(draft = teams.mapIndexed { i, team -> SeatDraft(team, kinds[team] ?: if (i == 0) SeatKind.HUMAN else SeatKind.AI_EASY) }, kinds = kinds)
            }
            is ControlEvent.SetSeatKind -> nav.update { n ->
                val draft = n.draft.mapIndexed { i, d -> if (i == event.index) d.copy(kind = event.kind) else d }
                n.copy(draft = draft, kinds = n.kinds + draft.associate { it.team to it.kind })
            }
            ControlEvent.Roll -> flow.requestRoll()
            is ControlEvent.ChooseKind -> flow.chooseKind(event.kind)
            is ControlEvent.ChoosePlane -> flow.choosePlane(event.plane)
            ControlEvent.BackToChoice -> flow.backToChoice()
            ControlEvent.OpenMenu -> { flow.pause(); nav.update { it.copy(page = ControlPage.PAUSE, confirmRestart = false) } }
            ControlEvent.Resume -> { flow.resume(); nav.update { it.copy(page = ControlPage.GAME, confirmRestart = false) } }
            ControlEvent.Restart -> nav.update { it.copy(confirmRestart = true) }
            ControlEvent.ConfirmRestart -> {
                val game = flow.game
                val draft = game?.seats?.map { SeatDraft(it.team, it.kind) } ?: nav.value.draft
                nav.update { it.copy(page = ControlPage.SETUP, returnTo = ControlPage.PAUSE, draft = draft, confirmRestart = false, confirmReplace = false) }
            }
            ControlEvent.CancelConfirm -> nav.update { it.copy(confirmRestart = false, confirmReplace = false) }
            ControlEvent.LeaveGame -> { flow.leave(); nav.update { it.copy(page = ControlPage.HOME, confirmRestart = false) } }
            ControlEvent.OpenSettings -> nav.update { it.copy(page = ControlPage.SETTINGS, returnTo = it.page) }
            ControlEvent.OpenRules -> nav.update { it.copy(page = ControlPage.RULES, returnTo = it.page) }
            ControlEvent.Back -> nav.update { n ->
                when (n.page) {
                    ControlPage.SETUP -> n.copy(page = if (flow.game != null) ControlPage.PAUSE else ControlPage.HOME, confirmReplace = false)
                    ControlPage.SETTINGS, ControlPage.RULES -> n.copy(page = n.returnTo)
                    else -> n
                }
            }
            is ControlEvent.SetLanguage -> settings.update { it.copy(language = event.language) }
            is ControlEvent.SetSound -> settings.update { it.copy(sound = event.on) }
            is ControlEvent.SetReducedMotion -> settings.update { it.copy(reducedMotion = event.on) }
            is ControlEvent.SetSpeed -> settings.update { it.copy(speed = event.speed) }
            is ControlEvent.SetDecisionMode -> { settings.update { it.copy(decisionMode = event.mode) }; flow.setDecisionMode(event.mode) }
            ControlEvent.PlayAgain -> {
                val draft = flow.game?.seats?.map { SeatDraft(it.team, it.kind) } ?: nav.value.draft
                flow.abandon()
                nav.update { it.copy(page = ControlPage.SETUP, returnTo = ControlPage.HOME, draft = draft) }
            }
            ControlEvent.ToHome -> { flow.abandon(); nav.update { it.copy(page = ControlPage.HOME) } }
        }
    }

    private fun start(config: GameConfig) {
        flow.newGame(config, settings.settings.value.decisionMode)
        nav.update { it.copy(page = ControlPage.GAME, confirmReplace = false, confirmRestart = false) }
    }

    companion object {
        fun defaultDraft(count: Int): List<SeatDraft> =
            GameConfig.teamsFor(count).mapIndexed { i, team -> SeatDraft(team, if (i == 0) SeatKind.HUMAN else SeatKind.AI_EASY) }

        fun factory(store: GameSessionStore, settings: SettingsRepository, flow: GameFlowUseCase): ViewModelProvider.Factory =
            viewModelFactory { initializer { ControlViewModel(store, settings, flow) } }
    }
}
