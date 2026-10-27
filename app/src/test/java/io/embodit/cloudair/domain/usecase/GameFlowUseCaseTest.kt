package io.embodit.cloudair.domain.usecase

import io.embodit.cloudair.data.repository.FakeGameRepository
import io.embodit.cloudair.data.session.GameSessionStore
import io.embodit.cloudair.domain.model.ActionKind
import io.embodit.cloudair.domain.model.Board
import io.embodit.cloudair.domain.model.DecisionMode
import io.embodit.cloudair.domain.model.GameConfig
import io.embodit.cloudair.domain.model.GameSnapshot
import io.embodit.cloudair.domain.model.Phase
import io.embodit.cloudair.domain.model.Seat
import io.embodit.cloudair.domain.model.SeatKind
import io.embodit.cloudair.domain.model.Team
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class GameFlowUseCaseTest {
    private val store = GameSessionStore()
    private val repository = FakeGameRepository()

    /** A random source that always rolls the next number of [dice]. */
    private class ScriptedRandom(private val dice: ArrayDeque<Int>) : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextInt(from: Int, until: Int): Int = if (from == 1 && until == 7) dice.removeFirstOrNull() ?: 1 else from
        override fun nextInt(): Int = 7
    }

    private fun flow(scope: TestScope, vararg dice: Int, log: MutableList<String> = ArrayList()) =
        GameFlowUseCase(store, repository, scope, random = ScriptedRandom(ArrayDeque(dice.toList())), timing = GameFlowUseCase.Timing(aiThinkMs = 100, passMs = 100), log = { log += it })

    private val human2 = GameConfig(listOf(Seat(Team.MINT, SeatKind.HUMAN), Seat(Team.APRICOT, SeatKind.HUMAN)))

    @Test
    fun `a human roll goes through the board before the rules see it`() = runTest {
        val flow = flow(this, 6, 2)
        flow.newGame(human2, DecisionMode.RECOMMEND)
        assertTrue(flow.requestRoll())
        assertEquals(Phase.ROLLING, flow.game!!.phase)
        val dice = store.state.value.dice!!
        assertEquals(6, dice.result)
        assertFalse(flow.requestRoll())
        flow.diceSettled(dice.id)
        // Only launches are legal: no choice, the move starts at once.
        assertEquals(Phase.MOVING, flow.game!!.phase)
        val move = store.state.value.move!!
        assertEquals(ActionKind.LAUNCH, move.plan.kind)
        flow.moveFinished(move.id)
        assertEquals(Phase.READY, flow.game!!.phase)
        assertEquals(0, flow.game!!.turn) // a six rolls again
        assertEquals(0, flow.game!!.seats[0].planes[0])
        assertTrue(repository.writes >= 2)
    }

    @Test
    fun `a six with both options asks the human, and manual mode asks for the plane`() = runTest {
        val flow = flow(this, 6)
        flow.newGame(human2, DecisionMode.MANUAL)
        store.update { s -> s.copy(game = s.game!!.copy(seats = listOf(s.game.seats[0].copy(planes = listOf(3, 9, -1, -1)), s.game.seats[1]))) }
        flow.requestRoll()
        flow.diceSettled(store.state.value.dice!!.id)
        assertEquals(Phase.DECISION, flow.game!!.phase)
        assertTrue(flow.chooseKind(ActionKind.ADVANCE))
        assertEquals(Phase.SELECT, flow.game!!.phase)
        assertEquals(2, store.state.value.legalPlans.size)
        assertTrue(flow.backToChoice())
        assertEquals(Phase.DECISION, flow.game!!.phase)
        assertTrue(flow.chooseKind(ActionKind.LAUNCH))
        assertEquals(Phase.SELECT, flow.game!!.phase)
        assertFalse(flow.choosePlane(0))
        assertTrue(flow.choosePlane(2))
        assertEquals(Phase.MOVING, flow.game!!.phase)
        assertEquals(2, store.state.value.move!!.plan.plane)
    }

    @Test
    fun `an AI seat rolls by itself after thinking and a no-move roll passes after a pause`() = runTest {
        val config = GameConfig(listOf(Seat(Team.MINT, SeatKind.HUMAN), Seat(Team.SKY, SeatKind.AI_EASY)))
        val flow = flow(this, 2, 3)
        flow.newGame(config, DecisionMode.RECOMMEND)
        flow.requestRoll()
        flow.diceSettled(store.state.value.dice!!.id)
        assertEquals(Phase.NO_MOVES, flow.game!!.phase)
        advanceTimeBy(150); runCurrent()
        assertEquals(Phase.READY, flow.game!!.phase)
        assertEquals(1, flow.game!!.turn)
        advanceTimeBy(150); runCurrent()
        assertEquals(Phase.ROLLING, flow.game!!.phase)
        assertEquals(3, store.state.value.dice!!.result)
    }

    @Test
    fun `pausing stops the AI and resuming lets it continue`() = runTest {
        val config = GameConfig(listOf(Seat(Team.SKY, SeatKind.AI_EASY), Seat(Team.MINT, SeatKind.HUMAN)))
        val flow = flow(this, 4)
        flow.newGame(config, DecisionMode.RECOMMEND)
        flow.pause()
        advanceTimeBy(500); runCurrent()
        assertEquals(Phase.READY, flow.game!!.phase)
        flow.resume()
        advanceTimeBy(150); runCurrent()
        assertEquals(Phase.ROLLING, flow.game!!.phase)
    }

    @Test
    fun `watchdogs finish animations the board never reported`() = runTest {
        val flow = flow(this, 6)
        flow.newGame(human2, DecisionMode.RECOMMEND)
        flow.requestRoll()
        advanceTimeBy(5100); runCurrent()
        assertEquals(Phase.MOVING, flow.game!!.phase)
        advanceTimeBy(9100); runCurrent()
        assertEquals(Phase.READY, flow.game!!.phase)
        assertEquals(0, flow.game!!.seats[0].planes[0])
    }

    @Test
    fun `a saved game continues from its stable phase and a damaged save is refused`() = runTest {
        val flow = flow(this, 6)
        flow.newGame(human2, DecisionMode.RECOMMEND)
        flow.requestRoll()
        flow.diceSettled(store.state.value.dice!!.id)
        flow.moveFinished(store.state.value.move!!.id)
        val saved = flow.game!!
        flow.leave()
        assertNull(flow.game)
        assertTrue(flow.hasSavedGame())
        assertTrue(flow.continueSaved())
        assertEquals(saved, flow.game)
        repository.save("version=1\nseats=9\n")
        assertFalse(flow.hasSavedGame())
        assertFalse(flow.continueSaved())
    }

    @Test
    fun `a failed save is reported but the game goes on`() = runTest {
        repository.failWrites = true
        val flow = flow(this, 6)
        flow.newGame(human2, DecisionMode.RECOMMEND)
        assertTrue(store.state.value.saveFailed)
        assertTrue(flow.requestRoll())
    }

    @Test
    fun `the winner ends the game`() = runTest {
        val flow = flow(this, 2)
        flow.newGame(human2, DecisionMode.RECOMMEND)
        store.update { s -> s.copy(game = s.game!!.copy(seats = listOf(s.game.seats[0].copy(planes = listOf(55, 57, 57, 57)), s.game.seats[1]))) }
        flow.requestRoll()
        flow.diceSettled(store.state.value.dice!!.id)
        flow.moveFinished(store.state.value.move!!.id)
        val g: GameSnapshot = flow.game!!
        assertEquals(Phase.ENDED, g.phase)
        assertEquals(0, g.winner)
        assertEquals(Board.FINISH, g.seats[0].planes[0])
        assertNotNull(g.last)
        assertTrue(g.last!!.finished)
    }
}
