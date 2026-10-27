package io.embodit.cloudair.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BoardTest {
    @Test
    fun `the loop has 52 distinct cells that are each one king step apart`() {
        assertEquals(52, Board.loop.size)
        assertEquals(52, Board.loop.toSet().size)
        for (i in Board.loop.indices) {
            val a = Board.loop[i]
            val b = Board.loop[(i + 1) % 52]
            assertTrue("$a -> $b", abs(a.row - b.row) <= 1 && abs(a.col - b.col) <= 1)
            assertTrue(a.row in 0..14 && a.col in 0..14)
        }
    }

    @Test
    fun `every fleet launches on its own colour beside its hangar and enters its lane after 50 cells`() {
        for (team in Team.entries) {
            assertTrue(Board.isOwnColor(team, 0))
            assertEquals(Board.loop[team.ordinal * 13], Board.launchCell(team))
            val entrance = Board.laneEntrance(team)
            val lane0 = Board.laneCell(team, 0)
            assertTrue("$team lane starts next to its entrance", abs(entrance.row - lane0.row) + abs(entrance.col - lane0.col) == 1)
            assertEquals(Board.CENTER, Board.cell(team, Board.FINISH))
            val hangar = Board.hangarCenter(team)
            val launch = Board.launchCell(team)
            assertTrue("$team launch near hangar", abs(hangar.row - launch.row) < 4.5 && abs(hangar.col - launch.col) < 4.5)
        }
    }

    @Test
    fun `own colour repeats every four cells and the fly cells are own colour`() {
        for (team in Team.entries) {
            for (p in 0..50) assertEquals("$team $p", p % 4 == 0, Board.isOwnColor(team, p))
            assertTrue(Board.isOwnColor(team, Board.FLY_FROM))
            assertTrue(Board.isOwnColor(team, Board.FLY_TO))
        }
    }

    @Test
    fun `the fly-over crosses the opposite fleet's lane in a straight line`() {
        for (team in Team.entries) {
            val from = Board.cell(team, Board.FLY_FROM)
            val to = Board.cell(team, Board.FLY_TO)
            val crossing = Board.flyCrossingCell(team)
            assertTrue("$team straight", from.row == to.row || from.col == to.col)
            assertEquals((from.row + to.row) / 2, crossing.row)
            assertEquals((from.col + to.col) / 2, crossing.col)
            assertEquals(crossing, Board.laneCell(Board.opposite(team), Board.FLY_CROSSING - Board.LANE_FIRST))
        }
    }

    @Test
    fun `lanes and hangars do not touch the loop`() {
        val loop = Board.loop.toSet()
        for (team in Team.entries) {
            for (i in 0 until 6) assertFalse(Board.laneCell(team, i) in loop)
            assertFalse(Board.CENTER in loop)
        }
    }
}
