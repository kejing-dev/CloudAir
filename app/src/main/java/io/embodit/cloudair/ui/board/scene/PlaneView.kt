package io.embodit.cloudair.ui.board.scene

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import io.embodit.cloudair.domain.model.Team

internal fun Entity.transform(): TransformComponent = components[TransformComponent::class.java]!!

/**
 * One plane on the board.
 *
 * ```
 * holder (position on the mat, heading, tap target)
 * ├ attitude (pitch and bank of the airframe)
 * │ └ lift (height above the mat, scale)
 * │   └ model (the Blender airplane, rotated so its nose points along -Z and its canopy up)
 * └ shadow (a soft disc that stays on the mat and shrinks as the plane climbs)
 * ```
 */
internal class PlaneView(val seat: Int, val plane: Int, val team: Team, model: Entity, res: SceneResources) {
    val holder: Entity = Entity().apply { setName("plane:$seat:$plane") }
    private val attitude: Entity = Entity().apply { setName("attitude:$seat:$plane") }
    private val lift: Entity = Entity().apply { setName("lift:$seat:$plane") }
    private val shadow: Entity = ModelEntity(res.shadowDisc, res.shadow).apply { setName("shadow:$seat:$plane") }
    private val model: Entity = model

    var scale: Float = BoardLayout.PLANE_SCALE
        private set

    /** Where the plane rests when nothing moves it. */
    var restPosition: P3 = P3(0f, 0f, 0f)
        private set
    var restHeading: Float = 0f
        private set

    init {
        // The export keeps Blender's axes: length along Y (nose at -Y), canopy toward -Z. A quarter
        // turn about X stands it up with the nose along -Z of the holder.
        model.transform().setEulerAngles(EulerAngles(pitch = MODEL_PITCH))
        paint(res)
        lift.addChild(model)
        attitude.addChild(lift)
        holder.addChild(attitude)
        holder.addChild(shadow)
        shadow.transform().setPosition(Vector3(0f, 0.0004f, 0f))
        holder.components.set(CollisionComponent(collisionShape = listOf(res.planeShape), physicsMaterial = res.physics))
        holder.components.set(InteractableComponent())
        holder.components.set(HoverEffectComponent())
        setScale(BoardLayout.PLANE_SCALE)
    }

    /** Replaces the airframe material of the loaded model with the fleet's paint. */
    private fun paint(res: SceneResources) {
        val paint = res.airframe.getValue(team)
        fun visit(e: Entity) {
            if (e.getName().startsWith("Airframe")) {
                e.components[ModelComponent::class.java]?.materials?.let { if (it.iterator().hasNext()) it[0] = paint }
            }
            e.getChildren().forEach { visit(it) }
        }
        visit(model)
    }

    /** Places the plane; [pitch] (nose up positive) and [roll] (bank) are radians. */
    fun place(position: P3, heading: Float, height: Float = 0f, pitch: Float = 0f, roll: Float = 0f) {
        holder.transform().setPosition(Vector3(position.x, position.y, position.z))
        holder.transform().setQuaternion(Quat(Vector3(0f, 1f, 0f), heading))
        if (pitch != 0f || roll != 0f) {
            attitude.transform().setQuaternion(Quat(Vector3(1f, 0f, 0f), pitch) * Quat(Vector3(0f, 0f, 1f), roll))
        } else {
            attitude.transform().setQuaternion(Quat())
        }
        setHeight(height)
    }

    /** Records and applies the resting pose. */
    fun rest(position: P3, heading: Float) {
        restPosition = position
        restHeading = heading
        place(position, heading)
    }

    fun setHeight(height: Float) {
        lift.transform().setPosition(Vector3(0f, height, 0f))
        // The shadow softens and shrinks with altitude.
        val k = (1f - height / 0.16f).coerceIn(0.35f, 1f) * scale / BoardLayout.PLANE_SCALE
        shadow.transform().setScaleVector(Vector3(k, 1f, k))
    }

    fun setScale(k: Float) {
        scale = k
        lift.transform().setScaleVector(Vector3(k, k, k))
    }

    var enabled: Boolean
        get() = holder.enabled
        set(value) { holder.enabled = value }

    fun destroy() { holder.destroy() }

    companion object {
        const val MODEL_PITCH = 90f
    }
}
