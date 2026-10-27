package io.embodit.cloudair

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.Placement
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import io.embodit.cloudair.ui.AppRoot
import io.embodit.cloudair.ui.board.BoardScreen
import io.embodit.cloudair.ui.control.ControlScreen
import io.embodit.cloudair.ui.navigation.Containers

/**
 * Two surfaces in Shared Space: the cloud airport (the default Volumetric container, 0.78 x 0.5 x
 * 1.0 m from the manifest) and the control window (Planar, 560 x 400 dp) placed to its right.
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultWindowContainer {
            AppRoot { BoardScreen() }
        }
        WindowContainer(
            Containers.CONTROL,
            form = Form.Planar,
            defaultSize = WindowContainerSize(width = 560.dp, height = 400.dp),
            placement = { containers.firstOrNull { it.id == Containers.BOARD }?.let { Placement.right(it) } ?: Placement.none() },
        ) {
            AppRoot {
                ControlScreen(Modifier.windowConstraints(minWidth = 520.dp, minHeight = 320.dp, maxWidth = 900.dp, maxHeight = 640.dp))
            }
        }
    }
