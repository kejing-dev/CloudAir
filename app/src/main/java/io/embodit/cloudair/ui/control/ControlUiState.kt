package io.embodit.cloudair.ui.control

import io.embodit.cloudair.domain.model.ActionKind
import io.embodit.cloudair.domain.model.DecisionMode
import io.embodit.cloudair.domain.model.Language
import io.embodit.cloudair.domain.model.LastEvent
import io.embodit.cloudair.domain.model.MotionSpeed
import io.embodit.cloudair.domain.model.MovePlan
import io.embodit.cloudair.domain.model.Phase
import io.embodit.cloudair.domain.model.SeatKind
import io.embodit.cloudair.domain.model.Settings
import io.embodit.cloudair.domain.model.Team

enum class ControlPage { HOME, SETUP, GAME, PAUSE, SETTINGS, RULES, VICTORY }

data class SeatDraft(val team: Team, val kind: SeatKind)

data class SavedSummary(val seats: Int, val round: Int)

/** The running game as the control window shows it. */
data class GameView(
    val teams: List<Team>,
    val kinds: List<SeatKind>,
    val turn: Int,
    val round: Int,
    val phase: Phase,
    val dice: Int?,
    val currentTeam: Team,
    val isHumanTurn: Boolean,
    val canRoll: Boolean,
    val homeCounts: List<Int>,
    /** Progress of every plane, per seat (see Board for the values). */
    val planes: List<List<Int>>,
    val legalPlans: List<MovePlan>,
    val recommended: Int?,
    val selectedKind: ActionKind?,
    val last: LastEvent?,
    val winner: Int?,
    val saveFailed: Boolean,
    val paused: Boolean,
    /** Every plane of the current seat is still in the hangar: only a six can do anything. */
    val allInHangar: Boolean = false,
    /** The player picks planes by hand (the six choice is followed by a plane list). */
    val manual: Boolean = false,
)

data class ControlUiState(
    val page: ControlPage = ControlPage.HOME,
    val returnTo: ControlPage = ControlPage.HOME,
    val settings: Settings = Settings(),
    val saved: SavedSummary? = null,
    val brokenSave: Boolean = false,
    val draft: List<SeatDraft> = emptyList(),
    val draftValid: Boolean = false,
    val confirmRestart: Boolean = false,
    val confirmReplace: Boolean = false,
    val game: GameView? = null,
) {
    val language: Language get() = settings.language
}

sealed interface ControlEvent {
    data object NewGame : ControlEvent
    data object ContinueGame : ControlEvent
    data object StartGame : ControlEvent
    data object ConfirmReplace : ControlEvent
    data class SetSeatCount(val count: Int) : ControlEvent
    data class SetSeatKind(val index: Int, val kind: SeatKind) : ControlEvent
    data object Roll : ControlEvent
    data class ChooseKind(val kind: ActionKind) : ControlEvent
    data class ChoosePlane(val plane: Int) : ControlEvent
    data object BackToChoice : ControlEvent
    data object OpenMenu : ControlEvent
    data object Resume : ControlEvent
    data object Restart : ControlEvent
    data object ConfirmRestart : ControlEvent
    data object CancelConfirm : ControlEvent
    data object LeaveGame : ControlEvent
    data object OpenSettings : ControlEvent
    data object OpenRules : ControlEvent
    data object Back : ControlEvent
    data class SetLanguage(val language: Language) : ControlEvent
    data class SetSound(val on: Boolean) : ControlEvent
    data class SetReducedMotion(val on: Boolean) : ControlEvent
    data class SetSpeed(val speed: MotionSpeed) : ControlEvent
    data class SetDecisionMode(val mode: DecisionMode) : ControlEvent
    data object PlayAgain : ControlEvent
    data object ToHome : ControlEvent
}
