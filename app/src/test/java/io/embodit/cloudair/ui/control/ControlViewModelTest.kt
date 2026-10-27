package io.embodit.cloudair.ui.control

import io.embodit.cloudair.data.repository.FakeGameRepository
import io.embodit.cloudair.data.repository.FakeSettingsRepository
import io.embodit.cloudair.data.session.GameSessionStore
import io.embodit.cloudair.domain.model.ActionKind
import io.embodit.cloudair.domain.model.DecisionMode
import io.embodit.cloudair.domain.model.Language
import io.embodit.cloudair.domain.model.Phase
import io.embodit.cloudair.domain.model.SeatKind
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class ControlViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = GameSessionStore()
    private val repository = FakeGameRepository()
    private val settings = FakeSettingsRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private lateinit var flow: GameFlowUseCase

    private fun viewModel(scope: kotlinx.coroutines.CoroutineScope, vararg dice: Int): ControlViewModel {
        flow = GameFlowUseCase(store, repository, scope, random = Random(1), timing = GameFlowUseCase.Timing(aiThinkMs = 50, passMs = 50))
        dice.forEach { flow.forcedDice.addLast(it) }
        return ControlViewModel(store, settings, flow)
    }

    @Test
    fun `starts on the home page with a two-seat draft and no saved game`() = runTest(dispatcher) {
        val vm = viewModel(this)
        runCurrent()
        val s = vm.state.value
        assertEquals(ControlPage.HOME, s.page)
        assertNull(s.saved)
        assertEquals(listOf(Team.MINT, Team.APRICOT), s.draft.map { it.team })
        assertTrue(s.draftValid)
    }

    @Test
    fun `setup edits seats and starting a game shows the turn page`() = runTest(dispatcher) {
        val vm = viewModel(this)
        vm.onEvent(ControlEvent.NewGame)
        vm.onEvent(ControlEvent.SetSeatCount(4))
        vm.onEvent(ControlEvent.SetSeatKind(0, SeatKind.AI_EASY))
        runCurrent()
        assertEquals(4, vm.state.value.draft.size)
        assertFalse(vm.state.value.draftValid)
        vm.onEvent(ControlEvent.SetSeatKind(1, SeatKind.HUMAN))
        vm.onEvent(ControlEvent.StartGame)
        runCurrent()
        val s = vm.state.value
        assertEquals(ControlPage.GAME, s.page)
        assertNotNull(s.game)
        assertEquals(4, s.game!!.teams.size)
    }

    @Test
    fun `a six with both options shows the choice and the choice starts a move`() = runTest(dispatcher) {
        val vm = viewModel(this, 6)
        vm.onEvent(ControlEvent.NewGame)
        vm.onEvent(ControlEvent.StartGame)
        runCurrent()
        store.update { s -> s.copy(game = s.game!!.copy(seats = listOf(s.game.seats[0].copy(planes = listOf(4, -1, -1, -1)), s.game.seats[1]))) }
        vm.onEvent(ControlEvent.Roll)
        runCurrent()
        // The board would report the throw; here the test does.
        flow.diceSettled(store.state.value.dice!!.id)
        runCurrent()
        assertEquals(Phase.DECISION, vm.state.value.game!!.phase)
        vm.onEvent(ControlEvent.ChooseKind(ActionKind.LAUNCH))
        runCurrent()
        assertEquals(Phase.MOVING, vm.state.value.game!!.phase)
    }

    @Test
    fun `the menu pauses, settings return to the menu, and leaving keeps the save`() = runTest(dispatcher) {
        val vm = viewModel(this)
        vm.onEvent(ControlEvent.NewGame)
        vm.onEvent(ControlEvent.StartGame)
        runCurrent()
        vm.onEvent(ControlEvent.OpenMenu)
        runCurrent()
        assertEquals(ControlPage.PAUSE, vm.state.value.page)
        assertTrue(vm.state.value.game!!.paused)
        vm.onEvent(ControlEvent.OpenSettings)
        vm.onEvent(ControlEvent.SetLanguage(Language.EN))
        runCurrent()
        assertEquals(ControlPage.SETTINGS, vm.state.value.page)
        assertEquals(Language.EN, vm.state.value.settings.language)
        vm.onEvent(ControlEvent.Back)
        runCurrent()
        assertEquals(ControlPage.PAUSE, vm.state.value.page)
        vm.onEvent(ControlEvent.LeaveGame)
        runCurrent()
        assertEquals(ControlPage.HOME, vm.state.value.page)
        assertNotNull(vm.state.value.saved)
        vm.onEvent(ControlEvent.ContinueGame)
        runCurrent()
        assertEquals(ControlPage.GAME, vm.state.value.page)
    }

    @Test
    fun `starting over an existing save asks first, and the decision mode reaches the game`() = runTest(dispatcher) {
        val vm = viewModel(this)
        vm.onEvent(ControlEvent.NewGame)
        vm.onEvent(ControlEvent.StartGame)
        vm.onEvent(ControlEvent.OpenMenu)
        vm.onEvent(ControlEvent.LeaveGame)
        vm.onEvent(ControlEvent.NewGame)
        vm.onEvent(ControlEvent.StartGame)
        runCurrent()
        assertTrue(vm.state.value.confirmReplace)
        assertEquals(ControlPage.SETUP, vm.state.value.page)
        vm.onEvent(ControlEvent.SetDecisionMode(DecisionMode.MANUAL))
        vm.onEvent(ControlEvent.ConfirmReplace)
        runCurrent()
        assertEquals(ControlPage.GAME, vm.state.value.page)
        assertEquals(DecisionMode.MANUAL, store.state.value.game!!.decisionMode)
    }
}

