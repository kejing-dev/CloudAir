package io.embodit.cloudair.domain.usecase

import io.embodit.cloudair.domain.model.Board
import io.embodit.cloudair.domain.model.GameSnapshot
import io.embodit.cloudair.domain.model.Seat
import io.embodit.cloudair.domain.model.SeatKind
import io.embodit.cloudair.domain.model.SegmentKind
import io.embodit.cloudair.domain.model.Team
import org.junit.Assert.assertEquals
import org.junit.Test

/** QA D4: the classic chain where a connected jump lands on the fly-over cell. */
class RulesChainTest {
    private val rules = RulesEngine()
    private val hangar = List(4) { Board.HANGAR }

    private fun game(mint: List<Int>, apricot: List<Int> = hangar) = GameSnapshot(
        seats = listOf(Seat(Team.MINT, SeatKind.HUMAN, mint), Seat(Team.APRICOT, SeatKind.AI_EASY, apricot)),
    )

    @Test
    fun `a jump onto the fly-over cell flies and jumps again`() {
        val plan = rules.plan(game(listOf(15, -1, -1, -1), listOf(53, -1, -1, -1)), 0, 0, 1)!!
        assertEquals(listOf(SegmentKind.WALK, SegmentKind.JUMP, SegmentKind.FLY, SegmentKind.JUMP), plan.segments.map { it.kind })
        assertEquals(32, plan.to)
        assertEquals(1, plan.captures.size) // the apricot plane on the crossed lane cell
    }

    @Test
    fun `a plain jump never chains a second jump`() {
        val plan = rules.plan(game(listOf(23, -1, -1, -1)), 0, 0, 1)!!
        assertEquals(listOf(SegmentKind.WALK, SegmentKind.JUMP), plan.segments.map { it.kind })
        assertEquals(28, plan.to)
    }

    @Test
    fun `a roll onto the fly-over cell still flies then jumps once`() {
        val plan = rules.plan(game(listOf(18, -1, -1, -1)), 0, 0, 2)!!
        assertEquals(listOf(SegmentKind.WALK, SegmentKind.FLY, SegmentKind.JUMP), plan.segments.map { it.kind })
        assertEquals(32, plan.to)
    }
}
