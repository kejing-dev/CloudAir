package io.embodit.cloudair.ui.control

import io.embodit.cloudair.data.repository.FakeGameRepository
import io.embodit.cloudair.data.repository.FakeSettingsRepository
import io.embodit.cloudair.data.session.GameSessionStore
import io.embodit.cloudair.domain.model.SeatKind
import io.embodit.cloudair.domain.model.Team
import io.embodit.cloudair.domain.usecase.GameFlowUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** QA D16: a fleet keeps the kind it was given even while it is not seated. */
@OptIn(ExperimentalCoroutinesApi::class)
class SetupDraftTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `seat kinds follow the fleet across seat-count changes`() = runTest(dispatcher) {
        val store = GameSessionStore()
        val vm = ControlViewModel(store, FakeSettingsRepository(), GameFlowUseCase(store, FakeGameRepository(), this))
        vm.onEvent(ControlEvent.NewGame)
        vm.onEvent(ControlEvent.SetSeatCount(3))
        vm.onEvent(ControlEvent.SetSeatKind(1, SeatKind.AI_SMART)) // Sky
        vm.onEvent(ControlEvent.SetSeatCount(2))
        runCurrent()
        assertEquals(listOf(Team.MINT, Team.APRICOT), vm.state.value.draft.map { it.team })
        assertEquals(SeatKind.AI_EASY, vm.state.value.draft[1].kind) // Apricot keeps its own default, not Sky's choice
        vm.onEvent(ControlEvent.SetSeatCount(3))
        runCurrent()
        assertEquals(SeatKind.AI_SMART, vm.state.value.draft[1].kind) // Sky is back with its choice
    }
}
