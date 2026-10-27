package io.embodit.cloudair.ui.board.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.dsl.registerSystem
import com.pico.spatial.ui.foundation.dsl.unregisterSystem
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import io.embodit.cloudair.ui.board.BoardUiState
import io.embodit.cloudair.ui.board.SceneEvent
import io.embodit.cloudair.ui.board.scene.BoardFrameSystem
import io.embodit.cloudair.ui.board.scene.BoardScene
import io.embodit.cloudair.ui.board.scene.BoardSceneHolder
import io.embodit.cloudair.ui.board.scene.P3
import io.embodit.cloudair.ui.board.scene.SceneResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * The cloud airport as 3D content. Compose only hands over state; the entities live in [BoardScene]
 * and the motion runs in [BoardFrameSystem]. A pinch on the die (or the tray) throws it; a pinch on
 * a plane picks it when the game is asking for a plane.
 */
@Composable
internal fun BoardView(state: BoardUiState, onEvent: (SceneEvent) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    val scene = remember { BoardScene { currentOnEvent(it) } }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    DisposableEffect(scene) {
        BoardSceneHolder.scene = scene
        registerSystem<BoardFrameSystem>()
        onDispose {
            unregisterSystem<BoardFrameSystem>()
            if (BoardSceneHolder.scene === scene) BoardSceneHolder.scene = null
            scene.dispose()
        }
    }

    // Materials, meshes and collision shapes wait for the renderer: create them off the main thread.
    LaunchedEffect(scene) {
        if (scene.hasResources) return@LaunchedEffect
        val resources = withContext(NonCancellable + Dispatchers.Default) { SceneResources() }
        scene.provide(resources)
    }

    SpatialView(
        modifier = modifier
            .onSizeChanged { viewSize = it }
            .pointerInput(Unit) {
                detectSpatialTapGesture(context = context, targetedToEntity = null) { tap ->
                    val name = tap.targetEntity?.getName()
                    Log.i(BoardScene.TAG, "TAP target=$name")
                    when {
                        name == BoardScene.DIE_NAME || name == BoardScene.TRAY_NAME -> currentOnEvent(SceneEvent.DieTapped)
                        else -> scene.planeOf(name)?.let { (seat, plane) -> currentOnEvent(SceneEvent.PlaneTapped(seat, plane)) }
                    }
                }
            },
        initial = { content, _ ->
            content.addEntity(scene.root)
            val plane = runCatching { Entity.loadSuspend("asset://models/plane.glb") }
                .onFailure { Log.e(BoardScene.TAG, "plane model failed", it) }.getOrNull()
            val die = runCatching { Entity.loadSuspend("asset://models/die.glb") }
                .onFailure { Log.e(BoardScene.TAG, "die model failed", it) }.getOrNull()
            if (plane != null && die != null) {
                plane.setName("planeTemplate")
                plane.enabled = false
                content.addEntity(plane)
                Log.i(BoardScene.TAG, "MODELS loaded plane children=${plane.getChildrenCount()} die children=${die.getChildrenCount()}")
                scene.install(plane, die)
            }
        },
        update = { content, _ ->
            if (viewSize != IntSize.Zero) {
                val corner = content.convertPosition(Vector3.ZERO, ViewCoordinateSpace.Local, content.localSpatialCoordinateSpace)
                scene.setRegion(P3(abs(corner.x), abs(corner.y), abs(corner.z)))
            }
            scene.apply(state)
        },
    )
}
