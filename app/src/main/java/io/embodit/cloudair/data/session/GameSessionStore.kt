package io.embodit.cloudair.data.session

import io.embodit.cloudair.domain.model.GameSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** The one process-wide live session, read by the board window and the control window alike. */
class GameSessionStore {
    private val _state = MutableStateFlow(GameSession())
    val state: StateFlow<GameSession> = _state

    fun update(transform: (GameSession) -> GameSession) = _state.update(transform)
}
