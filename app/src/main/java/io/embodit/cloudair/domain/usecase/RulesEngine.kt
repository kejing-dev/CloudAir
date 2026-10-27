package io.embodit.cloudair.domain.usecase

import io.embodit.cloudair.domain.model.ActionKind
import io.embodit.cloudair.domain.model.Board
import io.embodit.cloudair.domain.model.Capture
import io.embodit.cloudair.domain.model.EventType
import io.embodit.cloudair.domain.model.GameSnapshot
import io.embodit.cloudair.domain.model.LastEvent
import io.embodit.cloudair.domain.model.MovePlan
import io.embodit.cloudair.domain.model.MoveSegment
import io.embodit.cloudair.domain.model.Phase
import io.embodit.cloudair.domain.model.Seat
import io.embodit.cloudair.domain.model.SegmentKind
import io.embodit.cloudair.domain.model.Team
import kotlin.random.Random

/**
 * The classic rules, resolved into fully planned moves:
 * - a six launches a plane onto the launch cell (no extra steps) and grants another roll;
 * - other rolls advance a plane; landing on your own colour jumps 4 further; landing on the fly-over
 *   cell (by the roll or by that jump) flies across the opposite arm (sending home a plane parked on the
 *   crossed lane cell) and then jumps 4 as well; a move chains at most one fly-over and one jump after it;
 * - the centre needs an exact roll; the overshoot walks back;
 * - every landing sends home the other fleets' planes on that loop cell; own planes may stack;
 * - the first fleet with four planes in the centre wins.
 */
class RulesEngine {

    fun legalPlans(game: GameSnapshot, dice: Int): List<MovePlan> =
        game.current.planes.indices.mapNotNull { plan(game, game.turn, it, dice) }

    /** The plan for moving [plane] of [seat] with [dice], or null when that is not legal. */
    fun plan(game: GameSnapshot, seat: Int, plane: Int, dice: Int): MovePlan? {
        require(dice in 1..6)
        val owner = game.seats[seat]
        val from = owner.planes[plane]
        val taken = HashSet<Pair<Int, Int>>()
        fun capturesAt(progress: Int): List<Capture> {
            val index = Board.loopIndexOf(owner.team, progress) ?: return emptyList()
            val hits = ArrayList<Capture>()
            game.seats.forEachIndexed { s, other ->
                if (s == seat) return@forEachIndexed
                other.planes.forEachIndexed { p, value ->
                    if (Board.loopIndexOf(other.team, value) == index && taken.add(s to p)) hits += Capture(s, p, value)
                }
            }
            return hits
        }
        if (from == Board.HANGAR) {
            if (dice != 6) return null
            return MovePlan(seat, plane, ActionKind.LAUNCH, dice, from, 0, listOf(MoveSegment(SegmentKind.LAUNCH, from, 0, capturesAt(0))))
        }
        if (from >= Board.FINISH) return null
        val segments = ArrayList<MoveSegment>()
        val raw = from + dice
        var at: Int
        if (raw > Board.FINISH) {
            segments += MoveSegment(SegmentKind.WALK, from, Board.FINISH)
            at = 2 * Board.FINISH - raw
            segments += MoveSegment(SegmentKind.BOUNCE, Board.FINISH, at)
        } else {
            at = raw
            segments += MoveSegment(if (at == Board.FINISH) SegmentKind.FINISH else SegmentKind.WALK, from, at, capturesAt(at))
        }
        // Chain: one jump and one fly-over at most, in either order; a jump that lands on the
        // fly-over cell flies, and a fly-over always ends with its jump (the classic "+16").
        var flew = false
        var jumped = false
        fun fly() {
            val crossing = flyCrossingCaptures(game, seat, taken)
            segments += MoveSegment(SegmentKind.FLY, at, Board.FLY_TO, crossing + capturesAt(Board.FLY_TO))
            at = Board.FLY_TO
            flew = true
        }
        fun jump() {
            segments += MoveSegment(SegmentKind.JUMP, at, at + Board.JUMP, capturesAt(at + Board.JUMP))
            at += Board.JUMP
            jumped = true
        }
        if (Board.isOwnColor(owner.team, at) && at == Board.FLY_FROM) fly()
        if (Board.isOwnColor(owner.team, at) && !jumped && at + Board.JUMP <= Board.LAST_LOOP) jump()
        if (Board.isOwnColor(owner.team, at) && at == Board.FLY_FROM && !flew) {
            fly()
            if (at + Board.JUMP <= Board.LAST_LOOP) jump()
        }
        return MovePlan(seat, plane, ActionKind.ADVANCE, dice, from, at, segments)
    }

    private fun flyCrossingCaptures(game: GameSnapshot, seat: Int, taken: MutableSet<Pair<Int, Int>>): List<Capture> {
        val crossed = Board.opposite(game.seats[seat].team)
        val hits = ArrayList<Capture>()
        game.seats.forEachIndexed { s, other ->
            if (other.team != crossed) return@forEachIndexed
            other.planes.forEachIndexed { p, value -> if (value == Board.FLY_CROSSING && taken.add(s to p)) hits += Capture(s, p, value) }
        }
        return hits
    }

    /** Default recommendation: finish > capture > bonus distance > progress > lowest plane number. */
    fun recommend(plans: List<MovePlan>): MovePlan? =
        plans.sortedWith(
            compareByDescending<MovePlan> { it.finishes }
                .thenByDescending { it.captures.size }
                .thenByDescending { it.to - it.from }
                .thenByDescending { it.to }
                .thenBy { it.plane },
        ).firstOrNull()

    /** The smart AI also avoids landing where an opponent can hit next turn. */
    fun smartPick(game: GameSnapshot, plans: List<MovePlan>): MovePlan? =
        plans.sortedWith(
            compareByDescending<MovePlan> { it.finishes }
                .thenByDescending { it.captures.size }
                .thenBy { risk(game, it) }
                .thenByDescending { it.to - it.from }
                .thenByDescending { it.to }
                .thenBy { it.plane },
        ).firstOrNull()

    fun easyPick(plans: List<MovePlan>, random: Random): MovePlan? = if (plans.isEmpty()) null else plans[random.nextInt(plans.size)]

    /** How many of the 6 next rolls of some opponent would land on the plan's final cell. */
    fun risk(game: GameSnapshot, plan: MovePlan): Int {
        val team = game.seats[plan.seat].team
        val target = Board.loopIndexOf(team, plan.to) ?: return 0
        val captured = plan.captures.map { it.seat to it.plane }.toSet()
        var risk = 0
        game.seats.forEachIndexed { s, other ->
            if (s == plan.seat) return@forEachIndexed
            for (dice in 1..6) {
                val canLand = other.planes.indices.any { p ->
                    val progress = if ((s to p) in captured) Board.HANGAR else other.planes[p]
                    val landing = when {
                        progress == Board.HANGAR -> if (dice == 6) 0 else null
                        progress >= Board.FINISH -> null
                        else -> (progress + dice).takeIf { it <= Board.LAST_LOOP }
                    }
                    landing != null && Board.absoluteIndex(other.team, landing) == target
                }
                if (canLand) risk++
            }
        }
        return risk
    }

    /** Applies a committed plan: captures, the plane's new progress, the winner, and whose turn is next. */
    fun apply(game: GameSnapshot, plan: MovePlan): GameSnapshot {
        val seats = game.seats.map { it.planes.toMutableList() }.toMutableList()
        plan.captures.forEach { seats[it.seat][it.plane] = Board.HANGAR }
        seats[plan.seat][plan.plane] = plan.to
        val updated = game.seats.mapIndexed { i, seat -> seat.copy(planes = seats[i].toList()) }
        val team = game.seats[plan.seat].team
        val event = LastEvent(
            type = if (plan.kind == ActionKind.LAUNCH) EventType.LAUNCH else EventType.MOVE,
            team = team, plane = plan.plane, dice = plan.dice, to = plan.to,
            captures = plan.captures.size, flew = plan.flies, jumped = plan.jumps, finished = plan.finishes, bounced = plan.bounces,
        )
        val won = updated[plan.seat].planes.all { it == Board.FINISH }
        if (won) return game.copy(seats = updated, phase = Phase.ENDED, winner = plan.seat, last = event, selectedKind = null, dice = plan.dice)
        return nextTurn(game.copy(seats = updated, last = event, dice = plan.dice))
    }

    /** No legal move: the turn passes (a six still rolls again). */
    fun pass(game: GameSnapshot, dice: Int): GameSnapshot =
        nextTurn(game.copy(last = LastEvent(EventType.PASS, game.current.team, -1, dice), dice = dice))

    private fun nextTurn(game: GameSnapshot): GameSnapshot {
        val again = game.dice == 6
        val turn = if (again) game.turn else (game.turn + 1) % game.seats.size
        val round = if (!again && turn == 0) game.round + 1 else game.round
        return game.copy(turn = turn, round = round, phase = Phase.READY, selectedKind = null)
    }

    companion object {
        fun teamOf(seats: List<Seat>, index: Int): Team = seats[index].team
    }
}
