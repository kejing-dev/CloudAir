package io.embodit.cloudair.ui.board

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import io.embodit.cloudair.platform.CloudAirGraph
import io.embodit.cloudair.ui.board.components.BoardView
import io.embodit.cloudair.ui.navigation.Containers

/**
 * The cloud airport: the main window of the app, a volume in Shared Space. It opens the control
 * window to its right at launch and takes it along when it closes.
 */
@Composable
fun BoardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val graph = remember(context) { CloudAirGraph.from(context) }
    val viewModel: BoardViewModel = viewModel(factory = BoardViewModel.factory(graph.sessionStore, graph.settings, graph.flow, graph.sounds::play))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalSpatialNavigator.current

    DisposableEffect(navigator) {
        navigator.openWindowContainer(Containers.CONTROL, Containers.CONTROL_TAG)
        onDispose { navigator.closeWindowContainer(Containers.CONTROL, Containers.CONTROL_TAG) }
    }

    BoardContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
internal fun BoardContent(state: BoardUiState, onEvent: (SceneEvent) -> Unit, modifier: Modifier = Modifier) {
    BoardView(state = state, onEvent = onEvent, modifier = modifier.fillMaxSize())
}
