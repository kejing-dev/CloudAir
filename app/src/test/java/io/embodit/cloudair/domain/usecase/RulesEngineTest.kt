package io.embodit.cloudair.domain.usecase

import io.embodit.cloudair.domain.model.ActionKind
import io.embodit.cloudair.domain.model.Board
import io.embodit.cloudair.domain.model.GameSnapshot
import io.embodit.cloudair.domain.model.Phase
import io.embodit.cloudair.domain.model.Seat
import io.embodit.cloudair.domain.model.SeatKind
import io.embodit.cloudair.domain.model.SegmentKind
import io.embodit.cloudair.domain.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RulesEngineTest {
    private val rules = RulesEngine()

    private fun game(vararg planes: List<Int>, turn: Int = 0): GameSnapshot {
        val teams = listOf(Team.MINT, Team.SKY, Team.APRICOT, Team.LAVENDER)
        return GameSnapshot(seats = planes.mapIndexed { i, p -> Seat(teams[i], if (i == 0) SeatKind.HUMAN else SeatKind.AI_EASY, p) }, turn = turn)
    }

    private val hangar = List(4) { Board.HANGAR }

    @Test
    fun `only a six launches, and launching does not move further`() {
        val g = game(hangar, hangar)
        assertTrue(rules.legalPlans(g, 5).isEmpty())
        val plans = rules.legalPlans(g, 6)
        assertEquals(4, plans.size)
        assertTrue(plans.all { it.kind == ActionKind.LAUNCH && it.to == 0 && it.segments.single().kind == SegmentKind.LAUNCH })
    }

    @Test
    fun `a six after a launch keeps the turn, other rolls pass it on`() {
        val g = game(hangar, hangar)
        val after = rules.apply(g, rules.legalPlans(g, 6).first())
        assertEquals(0, after.turn)
        assertEquals(Phase.READY, after.phase)
        val moved = rules.apply(after, rules.plan(after, 0, 0, 3)!!)
        assertEquals(1, moved.turn)
        assertEquals(3, moved.seats[0].planes[0])
    }

    @Test
    fun `landing on your own colour jumps four cells, once`() {
        val g = game(listOf(1, -1, -1, -1), hangar)
        val plan = rules.plan(g, 0, 0, 3)!!
        assertEquals(8, plan.to)
        assertEquals(listOf(SegmentKind.WALK, SegmentKind.JUMP), plan.segments.map { it.kind })
        assertTrue(plan.jumps)
    }

    @Test
    fun `the fly-over crosses the opposite lane, sends a parked plane home and jumps on`() {
        val g = game(listOf(16, -1, -1, -1), hangar, listOf(Board.FLY_CROSSING, -1, -1, -1))
        val plan = rules.plan(g, 0, 0, 4)!!
        assertEquals(listOf(SegmentKind.WALK, SegmentKind.FLY, SegmentKind.JUMP), plan.segments.map { it.kind })
        assertEquals(32, plan.to)
        assertEquals(1, plan.captures.size)
        assertEquals(2, plan.captures.single().seat)
        val after = rules.apply(g, plan)
        assertEquals(Board.HANGAR, after.seats[2].planes[0])
        assertEquals(32, after.seats[0].planes[0])
    }

    @Test
    fun `an overshoot walks back from the centre and never jumps`() {
        val g = game(listOf(55, 57, 57, 57), hangar)
        val plan = rules.plan(g, 0, 0, 5)!!
        assertEquals(listOf(SegmentKind.WALK, SegmentKind.BOUNCE), plan.segments.map { it.kind })
        assertEquals(54, plan.to)
        assertTrue(plan.bounces)
    }

    @Test
    fun `an exact roll finishes and four planes home win the game`() {
        val g = game(listOf(55, 57, 57, 57), hangar)
        val plan = rules.plan(g, 0, 0, 2)!!
        assertTrue(plan.finishes)
        val after = rules.apply(g, plan)
        assertEquals(Phase.ENDED, after.phase)
        assertEquals(0, after.winner)
    }

    @Test
    fun `landing on opponents sends them all home, friends may stack`() {
        // Sky's launch cell is mint's loop index 13; mint progress 13 sits on it.
        val g = game(listOf(10, 13, -1, -1), listOf(0, 0, -1, -1))
        val plan = rules.plan(g, 0, 0, 3)!!
        assertEquals(13, plan.to)
        assertEquals(2, plan.captures.size)
        assertTrue(plan.captures.all { it.seat == 1 })
        val after = rules.apply(g, plan)
        assertEquals(listOf(-1, -1, -1, -1), after.seats[1].planes)
        assertEquals(listOf(13, 13, -1, -1), after.seats[0].planes)
    }

    @Test
    fun `planes in the lane are safe from other fleets`() {
        val g = game(listOf(52, -1, -1, -1), listOf(48, -1, -1, -1), turn = 1)
        // Sky at 48 rolling 4 reaches sky 52: its own lane, no capture of mint's 52.
        val plan = rules.plan(g, 1, 0, 4)!!
        assertTrue(plan.captures.isEmpty())
    }

    @Test
    fun `recommendation prefers finishing, then capturing, then bonus distance`() {
        val g = game(listOf(55, 10, 1, -1), listOf(13, -1, -1, -1))
        val finishing = rules.recommend(rules.legalPlans(g, 2))!!
        assertTrue(finishing.finishes)
        assertEquals(0, finishing.plane)
        val g2 = game(listOf(10, 1, -1, -1), listOf(0, -1, -1, -1))
        val capture = rules.recommend(rules.legalPlans(g2, 3))!!
        assertEquals(0, capture.plane)
        assertEquals(1, capture.captures.size)
        val g3 = game(listOf(1, 2, -1, -1), hangar)
        val jump = rules.recommend(rules.legalPlans(g3, 3))!!
        assertEquals(0, jump.plane) // 1 + 3 = 4 is own colour and jumps to 8
        assertEquals(8, jump.to)
    }

    @Test
    fun `smart pick avoids a cell an opponent can reach`() {
        // Mint plane 0 at 1 rolling 1 lands on 2 (sky at 1... sky's index 14 = mint 14): use direct threat.
        val g = game(listOf(5, 20, -1, -1), listOf(-1, -1, -1, -1), listOf(-1, -1, -1, -1), listOf(-1, -1, -1, -1))
        val plans = rules.legalPlans(g, 1)
        val smart = rules.smartPick(g, plans)!!
        assertNotNull(smart)
        assertEquals(0, rules.risk(g, smart))
        val threatened = game(listOf(5, 2, -1, -1), listOf(42, -1, -1, -1)) // sky 42 + 3 = 45, absolute 58 % 52 = 6: mint's cell 6
        val risky = rules.plan(threatened, 0, 0, 1)!!
        assertTrue(rules.risk(threatened, risky) >= 1)
    }

    @Test
    fun `easy pick is a legal plan`() {
        val g = game(listOf(1, 5, -1, -1), hangar)
        val plans = rules.legalPlans(g, 2)
        val pick = rules.easyPick(plans, Random(3))
        assertTrue(pick in plans)
        assertNull(rules.easyPick(emptyList(), Random(1)))
    }

    @Test
    fun `passing with a six rolls again, otherwise the next seat plays and rounds count`() {
        val g = game(hangar, hangar)
        val passed = rules.pass(g, 3)
        assertEquals(1, passed.turn)
        assertEquals(1, passed.round)
        val wrapped = rules.pass(passed, 2)
        assertEquals(0, wrapped.turn)
        assertEquals(2, wrapped.round)
        val again = rules.pass(wrapped, 6)
        assertEquals(0, again.turn)
    }
}
