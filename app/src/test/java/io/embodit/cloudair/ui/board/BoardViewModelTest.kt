package io.embodit.cloudair.ui.board

import io.embodit.cloudair.data.repository.FakeGameRepository
import io.embodit.cloudair.data.repository.FakeSettingsRepository
import io.embodit.cloudair.data.session.GameSessionStore
import io.embodit.cloudair.domain.model.DecisionMode
import io.embodit.cloudair.domain.model.GameConfig
import io.embodit.cloudair.domain.model.Phase
import io.embodit.cloudair.domain.model.Seat
import io.embodit.cloudair.domain.model.SeatKind
import io.embodit.cloudair.domain.model.Settings
import io.embodit.cloudair.domain.model.Team
import io.embodit.cloudair.domain.usecase.GameFlowUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = GameSessionStore()
    private val settings = FakeSettingsRepository(Settings(reducedMotion = true))
    private val sounds = ArrayList<SoundKind>()
    private lateinit var flow: GameFlowUseCase

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(scope: kotlinx.coroutines.CoroutineScope, vararg dice: Int): BoardViewModel {
        flow = GameFlowUseCase(store, FakeGameRepository(), scope, random = Random(5), timing = GameFlowUseCase.Timing(aiThinkMs = 50, passMs = 50))
        dice.forEach { flow.forcedDice.addLast(it) }
        return BoardViewModel(store, settings, flow, sounds = { sounds += it }, computeDispatcher = dispatcher)
    }

    private val human2 = GameConfig(listOf(Seat(Team.MINT, SeatKind.HUMAN), Seat(Team.APRICOT, SeatKind.HUMAN)))

    @Test
    fun `without a game the board is empty but follows the settings`() = runTest(dispatcher) {
        val vm = viewModel(this)
        runCurrent()
        val s = vm.state.value
        assertTrue(s.planes.isEmpty())
        assertTrue(s.reducedMotion)
        assertFalse(s.canRoll)
    }

    @Test
    fun `a new game lays out sixteen planes in the hangars and lets the human roll`() = runTest(dispatcher) {
        val vm = viewModel(this)
        flow.newGame(GameConfig.default(4), DecisionMode.RECOMMEND)
        runCurrent()
        val s = vm.state.value
        assertEquals(16, s.planes.size)
        assertTrue(s.planes.all { it.progress == -1 })
        assertEquals(0, s.activeSeat)
        assertTrue(s.canRoll)
    }

    @Test
    fun `tapping the die rolls, the throw is computed, and settling it starts the move`() = runTest(dispatcher) {
        val vm = viewModel(this, 6)
        flow.newGame(human2, DecisionMode.RECOMMEND)
        runCurrent()
        vm.onEvent(SceneEvent.DieTapped)
        runCurrent()
        val s = vm.state.value
        assertEquals(Phase.ROLLING, s.phase)
        assertNotNull(s.dice)
        assertNotNull(s.diceThrow)
        assertEquals(6, s.diceThrow!!.result)
        assertTrue(sounds.contains(SoundKind.TAP))
        vm.onEvent(SceneEvent.DiceSettled(s.dice!!.id))
        runCurrent()
        assertEquals(Phase.MOVING, vm.state.value.phase)
        assertNotNull(vm.state.value.move)
        vm.onEvent(SceneEvent.MoveFinished(vm.state.value.move!!.id))
        runCurrent()
        assertEquals(Phase.READY, vm.state.value.phase)
        assertEquals(0, vm.state.value.planes.first { it.seat == 0 && it.plane == 0 }.progress)
    }

    @Test
    fun `a tapped plane only counts while the game asks for one`() = runTest(dispatcher) {
        val vm = viewModel(this, 6)
        flow.newGame(human2, DecisionMode.MANUAL)
        runCurrent()
        vm.onEvent(SceneEvent.PlaneTapped(0, 1))
        runCurrent()
        assertEquals(Phase.READY, vm.state.value.phase)
        vm.onEvent(SceneEvent.DieTapped)
        runCurrent()
        vm.onEvent(SceneEvent.DiceSettled(vm.state.value.dice!!.id))
        runCurrent()
        assertEquals(Phase.SELECT, vm.state.value.phase)
        assertEquals(setOf(0, 1, 2, 3), vm.state.value.selectable)
        vm.onEvent(SceneEvent.PlaneTapped(1, 2))
        runCurrent()
        assertEquals(Phase.SELECT, vm.state.value.phase)
        vm.onEvent(SceneEvent.PlaneTapped(0, 2))
        runCurrent()
        assertEquals(Phase.MOVING, vm.state.value.phase)
        assertEquals(2, vm.state.value.move!!.plan.plane)
    }
}
