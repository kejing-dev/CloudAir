package io.embodit.cloudair.domain.model

/** Stable phases are saved; ROLLING and MOVING only exist while an animation runs. */
enum class Phase { READY, ROLLING, DECISION, SELECT, MOVING, NO_MOVES, ENDED }

enum class ActionKind { LAUNCH, ADVANCE }

enum class SegmentKind { LAUNCH, WALK, BOUNCE, JUMP, FLY, FINISH }

/** A plane of another seat sent back to its hangar. */
data class Capture(val seat: Int, val plane: Int, val from: Int)

/**
 * One leg of a move as the board animates it. Progress values are the moving plane's own.
 * WALK/BOUNCE follow the cells one by one; JUMP, FLY and LAUNCH are arcs; FINISH is the glide into the centre.
 */
data class MoveSegment(val kind: SegmentKind, val from: Int, val to: Int, val captures: List<Capture> = emptyList())

/** A fully resolved move: every landing, every capture and the final progress, decided before anything moves. */
data class MovePlan(
    val seat: Int,
    val plane: Int,
    val kind: ActionKind,
    val dice: Int,
    val from: Int,
    val to: Int,
    val segments: List<MoveSegment>,
) {
    val captures: List<Capture> get() = segments.flatMap { it.captures }
    val finishes: Boolean get() = to == Board.FINISH
    val flies: Boolean get() = segments.any { it.kind == SegmentKind.FLY }
    val jumps: Boolean get() = segments.any { it.kind == SegmentKind.JUMP }
    val bounces: Boolean get() = segments.any { it.kind == SegmentKind.BOUNCE }
}

enum class EventType { LAUNCH, MOVE, PASS }

/** What just happened, for the status line and the log. */
data class LastEvent(
    val type: EventType,
    val team: Team,
    val plane: Int,
    val dice: Int,
    val to: Int = Board.HANGAR,
    val captures: Int = 0,
    val flew: Boolean = false,
    val jumped: Boolean = false,
    val finished: Boolean = false,
    val bounced: Boolean = false,
)

/** The saved state of a game. */
data class GameSnapshot(
    val seats: List<Seat>,
    val turn: Int = 0,
    val round: Int = 1,
    val dice: Int? = null,
    val phase: Phase = Phase.READY,
    val selectedKind: ActionKind? = null,
    val winner: Int? = null,
    val decisionMode: DecisionMode = DecisionMode.RECOMMEND,
    val last: LastEvent? = null,
    /** A roll that was committed but not yet resolved when the game was saved; replayed on restore. */
    val pendingRoll: Int? = null,
) {
    val current: Seat get() = seats[turn]
    val isStable: Boolean get() = phase != Phase.ROLLING && phase != Phase.MOVING
}

/** A dice throw the board must play: result decided, animation seeded. */
data class DiceThrowSpec(val id: Int, val result: Int, val seed: Int)

/** A move the board must play. */
data class MoveSpec(val id: Int, val plan: MovePlan)

/** The live session shared by the board and the control window. */
data class GameSession(
    val game: GameSnapshot? = null,
    /** Changes whenever a game starts or is restored, so views can rebuild their fleets. */
    val gameId: Int = 0,
    val dice: DiceThrowSpec? = null,
    val move: MoveSpec? = null,
    val paused: Boolean = false,
    val saveFailed: Boolean = false,
    /** Shows for a moment when a human's roll gave no legal move. */
    val legalPlans: List<MovePlan> = emptyList(),
)
