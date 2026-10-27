package io.embodit.cloudair.domain.usecase

import io.embodit.cloudair.domain.model.ActionKind
import io.embodit.cloudair.domain.model.DecisionMode
import io.embodit.cloudair.domain.model.EventType
import io.embodit.cloudair.domain.model.GameSnapshot
import io.embodit.cloudair.domain.model.LastEvent
import io.embodit.cloudair.domain.model.Phase
import io.embodit.cloudair.domain.model.Seat
import io.embodit.cloudair.domain.model.SeatKind
import io.embodit.cloudair.domain.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SaveCodecTest {
    private val codec = SaveCodec()

    private val game = GameSnapshot(
        seats = listOf(Seat(Team.MINT, SeatKind.HUMAN, listOf(3, -1, 57, 52)), Seat(Team.APRICOT, SeatKind.AI_SMART, listOf(-1, 10, 10, 20))),
        turn = 1, round = 7, dice = 6, phase = Phase.DECISION, selectedKind = null, winner = null,
        decisionMode = DecisionMode.MANUAL,
        last = LastEvent(EventType.MOVE, Team.MINT, 0, 4, 3, captures = 1, flew = false, jumped = true, finished = false, bounced = false),
    )

    @Test
    fun `a snapshot survives a round trip`() {
        assertEquals(game, codec.decode(codec.encode(game)))
        val ended = game.copy(seats = listOf(game.seats[0].copy(planes = listOf(57, 57, 57, 57)), game.seats[1]), phase = Phase.ENDED, winner = 0, selectedKind = ActionKind.ADVANCE)
        assertEquals(ended, codec.decode(codec.encode(ended)))
    }

    private fun refuses(text: String, why: String) {
        try { codec.decode(text); fail("accepted $why") } catch (e: SaveCodec.InvalidSave) { /* expected */ }
    }

    @Test
    fun `damaged or inconsistent saves are refused`() {
        refuses("", "empty")
        refuses(codec.encode(game).replace("version=1", "version=9"), "version")
        refuses(codec.encode(game).replace("turn=1", "turn=5"), "turn")
        refuses(codec.encode(game).replace("phase=DECISION", "phase=MOVING"), "unstable phase")
        refuses(codec.encode(game).replace("3,-1,57,52", "3,-1,58,52"), "plane range")
        refuses(codec.encode(game).replace("MINT,HUMAN", "MINT,AI_EASY"), "no human")
        refuses(codec.encode(game).replace("phase=DECISION", "phase=ENDED"), "ended without winner")
        refuses(codec.encode(game.copy(seats = listOf(game.seats[0].copy(planes = listOf(57, 57, 57, 57)), game.seats[1]))), "finished fleet in a running game")
    }
}
