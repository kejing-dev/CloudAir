package io.embodit.cloudair.domain.usecase

import io.embodit.cloudair.data.repository.FakeGameRepository
import io.embodit.cloudair.data.session.GameSessionStore
import io.embodit.cloudair.domain.model.ActionKind
import io.embodit.cloudair.domain.model.DecisionMode
import io.embodit.cloudair.domain.model.GameConfig
import io.embodit.cloudair.domain.model.Phase
import io.embodit.cloudair.domain.model.Seat
import io.embodit.cloudair.domain.model.SeatKind
import io.embodit.cloudair.domain.model.Team
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The QA round-1 defects D1–D3: restoring a choice, pausing mid-throw, leaving mid-throw. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameFlowRestoreTest {
    private val repository = FakeGameRepository()

    private fun flow(scope: TestScope, store: GameSessionStore, vararg dice: Int): GameFlowUseCase {
        val f = GameFlowUseCase(store, repository, scope, random = Random(9), timing = GameFlowUseCase.Timing(aiThinkMs = 50, passMs = 50))
        dice.forEach { f.forcedDice.addLast(it) }
        return f
    }

    private val human2 = GameConfig(listOf(Seat(Team.MINT, SeatKind.HUMAN), Seat(Team.APRICOT, SeatKind.HUMAN)))
    private val humanAi = GameConfig(listOf(Seat(Team.MINT, SeatKind.HUMAN), Seat(Team.APRICOT, SeatKind.AI_EASY)))

    @Test
    fun `a game saved in the six choice continues with its choices working (D1)`() = runTest {
        val store = GameSessionStore()
        val flow = flow(this, store, 6)
        flow.newGame(human2, DecisionMode.RECOMMEND)
        store.update { s -> s.copy(game = s.game!!.copy(seats = listOf(s.game.seats[0].copy(planes = listOf(3, -1, -1, -1)), s.game.seats[1]))) }
        flow.requestRoll()
        flow.diceSettled(store.state.value.dice!!.id)
        assertEquals(Phase.DECISION, flow.game!!.phase)
        flow.leave()
        // A fresh process: a new store and flow over the same saved game.
        val store2 = GameSessionStore()
        val flow2 = flow(this, store2)
        assertTrue(flow2.continueSaved())
        assertEquals(Phase.DECISION, flow2.game!!.phase)
        assertEquals(4, store2.state.value.legalPlans.size)
        assertTrue(flow2.chooseKind(ActionKind.ADVANCE))
        assertEquals(Phase.MOVING, flow2.game!!.phase)
        assertEquals(9, store2.state.value.move!!.plan.to)
    }

    @Test
    fun `a game saved in the plane pick continues with its list (D1)`() = runTest {
        val store = GameSessionStore()
        val flow = flow(this, store, 2)
        flow.newGame(human2, DecisionMode.MANUAL)
        store.update { s -> s.copy(game = s.game!!.copy(seats = listOf(s.game.seats[0].copy(planes = listOf(3, 9, -1, -1)), s.game.seats[1]))) }
        flow.requestRoll()
        flow.diceSettled(store.state.value.dice!!.id)
        assertEquals(Phase.SELECT, flow.game!!.phase)
        flow.leave()
        val store2 = GameSessionStore()
        val flow2 = flow(this, store2)
        assertTrue(flow2.continueSaved())
        assertEquals(Phase.SELECT, flow2.game!!.phase)
        assertEquals(listOf(0, 1), store2.state.value.legalPlans.map { it.plane })
        assertTrue(flow2.choosePlane(1))
        assertEquals(Phase.MOVING, flow2.game!!.phase)
    }

    @Test
    fun `pausing while the die is in the air holds the result until resume (D2)`() = runTest {
        val store = GameSessionStore()
        val flow = flow(this, store, 6)
        flow.newGame(humanAi.copy(seats = humanAi.seats.reversed()), DecisionMode.RECOMMEND)
        advanceTimeBy(100); runCurrent()
        assertEquals(Phase.ROLLING, flow.game!!.phase)
        flow.pause()
        flow.diceSettled(store.state.value.dice!!.id)
        // Still rolling: nothing was chosen or moved behind the pause page.
        assertEquals(Phase.ROLLING, flow.game!!.phase)
        assertEquals(-1, flow.game!!.seats[0].planes[0])
        flow.resume()
        assertEquals(Phase.MOVING, flow.game!!.phase)
        assertEquals(ActionKind.LAUNCH, store.state.value.move!!.plan.kind)
    }

    @Test
    fun `leaving mid-throw resolves the committed roll instead of granting a new one (D3)`() = runTest {
        val store = GameSessionStore()
        val flow = flow(this, store, 6)
        flow.newGame(human2, DecisionMode.RECOMMEND)
        flow.requestRoll()
        assertEquals(Phase.ROLLING, flow.game!!.phase)
        flow.pause()
        flow.leave()
        val restored = flow.peekSavedGame()!!
        assertEquals(Phase.READY, restored.phase)
        assertEquals(0, restored.seats[0].planes[0]) // the six launched plane 0 before the game was put away
        assertEquals(0, restored.turn) // and a six keeps the turn
    }

    @Test
    fun `a kill mid-throw replays the committed roll on restore (D3)`() = runTest {
        val store = GameSessionStore()
        val flow = flow(this, store, 4)
        flow.newGame(human2, DecisionMode.RECOMMEND)
        store.update { s -> s.copy(game = s.game!!.copy(seats = listOf(s.game.seats[0].copy(planes = listOf(3, -1, -1, -1)), s.game.seats[1]))) }
        flow.requestRoll()
        // The process dies here: nothing else is saved. The save carries the pending roll.
        val saved = flow.peekSavedGame()!!
        assertEquals(4, saved.pendingRoll)
        assertEquals(Phase.READY, saved.phase)
        val store2 = GameSessionStore()
        val flow2 = flow(this, store2, 1) // a different forced value must NOT be used
        assertTrue(flow2.continueSaved())
        runCurrent()
        assertEquals(Phase.ROLLING, flow2.game!!.phase)
        assertEquals(4, store2.state.value.dice!!.result)
        flow2.diceSettled(store2.state.value.dice!!.id)
        assertNotNull(store2.state.value.move)
        assertEquals(7, store2.state.value.move!!.plan.to)
        flow2.moveFinished(store2.state.value.move!!.id)
        assertFalse(flow2.peekSavedGame()!!.pendingRoll != null)
    }
}
