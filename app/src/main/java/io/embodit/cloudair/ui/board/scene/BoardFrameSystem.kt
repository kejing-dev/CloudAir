package io.embodit.cloudair.ui.board.scene

import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.System

/**
 * Drives the board's animations once per rendered frame inside the ECS, not through Compose.
 * The SDK instantiates the system itself, so it finds the scene through [BoardSceneHolder].
 */
class BoardFrameSystem : System() {
    override fun update(context: SceneUpdateContext) {
        BoardSceneHolder.scene?.onFrame(context.deltaTime)
    }
}

/** The board currently on screen. There is at most one. */
internal object BoardSceneHolder {
    @Volatile
    var scene: BoardScene? = null
}
