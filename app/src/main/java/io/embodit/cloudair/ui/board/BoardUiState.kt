package io.embodit.cloudair.ui.board

import io.embodit.cloudair.domain.model.DiceThrowSpec
import io.embodit.cloudair.domain.model.MotionSpeed
import io.embodit.cloudair.domain.model.MoveSpec
import io.embodit.cloudair.domain.model.Phase
import io.embodit.cloudair.domain.model.Team
import io.embodit.cloudair.domain.usecase.DiceThrow

data class PlaneState(val seat: Int, val plane: Int, val team: Team, val progress: Int)

/** What the board shows: fleets at rest, whose turn it is, and the throw or move to play. */
data class BoardUiState(
    val gameId: Int = 0,
    val teams: List<Team> = emptyList(),
    val planes: List<PlaneState> = emptyList(),
    val activeSeat: Int? = null,
    val phase: Phase? = null,
    val dice: DiceThrowSpec? = null,
    val diceThrow: DiceThrow? = null,
    val move: MoveSpec? = null,
    val lastDice: Int? = null,
    val reducedMotion: Boolean = false,
    val speed: MotionSpeed = MotionSpeed.STANDARD,
    val soundOn: Boolean = true,
    /** A human may throw the die right now. */
    val canRoll: Boolean = false,
    /** Planes the human may pick by tapping (manual mode). */
    val selectable: Set<Int> = emptySet(),
    /** The move the game would recommend while the human chooses; its destination glows. */
    val recommended: io.embodit.cloudair.domain.model.MovePlan? = null,
)

enum class SoundKind { DICE_THROW, DICE_LAND, TAKEOFF, HOP, JUMP, FLY, CAPTURE, HOME, WIN, TAP }

/** What the 3D scene reports back. */
sealed interface SceneEvent {
    data object DieTapped : SceneEvent
    data class PlaneTapped(val seat: Int, val plane: Int) : SceneEvent
    data class DiceSettled(val id: Int) : SceneEvent
    data class MoveFinished(val id: Int) : SceneEvent
    data class Sound(val kind: SoundKind) : SceneEvent
}
