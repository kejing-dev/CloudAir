package io.embodit.cloudair.ui.control

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.embodit.cloudair.platform.CloudAirGraph
import io.embodit.cloudair.ui.control.components.HomePanel
import io.embodit.cloudair.ui.control.components.PausePanel
import io.embodit.cloudair.ui.control.components.RulesPanel
import io.embodit.cloudair.ui.control.components.SettingsPanel
import io.embodit.cloudair.ui.control.components.SetupPanel
import io.embodit.cloudair.ui.control.components.TurnPanel
import io.embodit.cloudair.ui.control.components.VictoryPanel

/** The control window: one page at a time, on the system glass. */
@Composable
fun ControlScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val graph = remember(context) { CloudAirGraph.from(context) }
    val viewModel: ControlViewModel = viewModel(factory = ControlViewModel.factory(graph.sessionStore, graph.settings, graph.flow))
    val state by viewModel.state.collectAsStateWithLifecycle()
    ControlContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
internal fun ControlContent(state: ControlUiState, onEvent: (ControlEvent) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        when (state.page) {
            ControlPage.HOME -> HomePanel(state, onEvent)
            ControlPage.SETUP -> SetupPanel(state, onEvent)
            ControlPage.GAME -> state.game?.let { TurnPanel(it, onEvent) } ?: HomePanel(state, onEvent)
            ControlPage.PAUSE -> PausePanel(state, onEvent)
            ControlPage.SETTINGS -> SettingsPanel(state, onEvent)
            ControlPage.RULES -> RulesPanel(onEvent)
            ControlPage.VICTORY -> state.game?.let { VictoryPanel(it, onEvent) } ?: HomePanel(state, onEvent)
        }
    }
}
