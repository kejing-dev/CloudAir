package io.embodit.cloudair.ui.board.scene

import androidx.compose.ui.graphics.Color
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.Resource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import io.embodit.cloudair.domain.model.Team
import io.embodit.cloudair.ui.theme.CloudAirColors

internal fun Color.toColor4(): Color4 = Color4(red, green, blue, alpha)

/**
 * Materials, meshes and collision shapes shared by the whole board, created once per scene lifetime
 * off the main thread (creating a resource waits for the renderer) and persisted with toGlobal so
 * they outlive any single entity. [close] releases them.
 */
internal class SceneResources {
    private val all = ArrayList<Resource>()

    private fun pbr(color: Color, roughness: Float = 0.8f, glow: Float = 0.14f, metallic: Float = 0f): PhysicallyBasedMaterial =
        PhysicallyBasedMaterial.create().apply {
            setBaseColor(color.toColor4())
            // A little light of its own keeps the pastel faces readable from every side of the volume,
            // while leaving enough shading for the tiles to read as solid pieces.
            setEmissiveColor(Color4(color.red * glow, color.green * glow, color.blue * glow, 1f))
            setRoughness(roughness)
            setMetallic(metallic)
            toGlobal()
            all += this
        }

    private fun unlit(color: Color, opacity: Float = 1f): UnlitMaterial =
        UnlitMaterial.create(if (opacity < 1f) BlendingMode.TRANSPARENT else BlendingMode.OPAQUE).apply {
            setBaseColor(color.toColor4())
            if (opacity < 1f) setOpacity(opacity)
            setCullingMode(MaterialCullingMode.NONE)
            setApplyToneMapping(false)
            toGlobal()
            all += this
        }

    val mat: Material = pbr(CloudAirColors.BoardIvory, roughness = 0.9f, glow = 0.10f)
    val frame: Material = pbr(CloudAirColors.MatFrame, roughness = 0.9f, glow = 0.12f)
    val neutral: Material = pbr(CloudAirColors.RunwayPale, roughness = 0.85f, glow = 0.12f)
    // Skin v2: tiles and rims are satin lacquer, the circles and spots glazed enamel, the hangar pads matte
    // like the felt in the tray; frame, mat, tower and tray themselves come from board.glb / tray.glb.
    val ivory: Material = pbr(CloudAirColors.BoardIvory, roughness = 0.45f, glow = 0.18f)
    val light: Map<Team, Material> = Team.entries.associateWith { pbr(CloudAirColors.of(it), roughness = 0.92f, glow = 0.14f) }
    val mid: Map<Team, Material> = Team.entries.associateWith { pbr(CloudAirColors.midOf(it), roughness = 0.5f, glow = 0.14f) }
    val lane: Map<Team, Material> = Team.entries.associateWith { pbr(CloudAirColors.laneOf(it), roughness = 0.5f, glow = 0.14f) }
    val deep: Map<Team, Material> = Team.entries.associateWith { pbr(CloudAirColors.deepOf(it), roughness = 0.7f, glow = 0.3f) }
    val glow: Material = pbr(CloudAirColors.RouteGlow, roughness = 0.6f, glow = 0.7f)
    val tray: Material = pbr(CloudAirColors.TrayStone, roughness = 0.9f, glow = 0.16f)
    val trayFloor: Material = pbr(CloudAirColors.TrayFelt, roughness = 1f, glow = 0.05f)
    val ink: Material = unlit(CloudAirColors.Ink)
    val shadow: Material = unlit(CloudAirColors.Shadow, opacity = 0.22f)

    /** Fleet paint for the airframe meshes of the loaded plane model. */
    val airframe: Map<Team, Material> = Team.entries.associateWith { pbr(CloudAirColors.planeOf(it), roughness = 0.45f, glow = 0.12f, metallic = 0.04f) }

    // ---- meshes ----
    val tile: MeshResource = MeshResource.createBox(Vector3(BoardLayout.TILE, BoardLayout.TILE_HEIGHT, BoardLayout.TILE), cornerRadius = BoardLayout.TILE * 0.22f).keep()
    val notchTile: MeshResource = MeshResource.createBox(Vector3(BoardLayout.TILE * 0.74f, BoardLayout.TILE_HEIGHT, BoardLayout.TILE * 0.74f), cornerRadius = BoardLayout.TILE * 0.16f).keep()
    val laneTile: MeshResource = MeshResource.createBox(Vector3(BoardLayout.TILE, BoardLayout.LANE_TILE_HEIGHT, BoardLayout.TILE), cornerRadius = BoardLayout.TILE * 0.2f).keep()
    val circle: MeshResource = MeshResource.createCylinder(radius = BoardLayout.CIRCLE_RADIUS, height = BoardLayout.CIRCLE_HEIGHT).keep()
    val matSlab: MeshResource = MeshResource.createBox(Vector3(BoardLayout.MAT, BoardLayout.MAT_THICKNESS, BoardLayout.MAT), cornerRadius = 0.012f).keep()
    val frameSlab: MeshResource = MeshResource.createBox(Vector3(BoardLayout.MAT + 2 * BoardLayout.FRAME_MARGIN, BoardLayout.MAT_THICKNESS * 0.6f, BoardLayout.MAT + 2 * BoardLayout.FRAME_MARGIN), cornerRadius = 0.016f).keep()
    val hangarSlab: MeshResource = MeshResource.createBox(Vector3(BoardLayout.HANGAR_SIZE, BoardLayout.HANGAR_HEIGHT, BoardLayout.HANGAR_SIZE), cornerRadius = BoardLayout.CELL * 0.4f).keep()
    val rimX: MeshResource = MeshResource.createBox(Vector3(BoardLayout.RIM_THICKNESS, BoardLayout.RIM_HEIGHT, BoardLayout.HANGAR_SIZE), cornerRadius = BoardLayout.RIM_THICKNESS * 0.4f).keep()
    val rimZ: MeshResource = MeshResource.createBox(Vector3(BoardLayout.HANGAR_SIZE - 2 * BoardLayout.RIM_THICKNESS, BoardLayout.RIM_HEIGHT, BoardLayout.RIM_THICKNESS), cornerRadius = BoardLayout.RIM_THICKNESS * 0.4f).keep()
    val spot: MeshResource = MeshResource.createCylinder(radius = BoardLayout.SPOT_RADIUS, height = BoardLayout.SPOT_HEIGHT).keep()
    val hub: MeshResource = MeshResource.createCylinder(radius = BoardLayout.HUB_RADIUS, height = BoardLayout.HUB_HEIGHT).keep()
    val tower: MeshResource = MeshResource.createCylinder(radius = BoardLayout.TOWER_RADIUS, height = BoardLayout.TOWER_HEIGHT).keep()
    val cab: MeshResource = MeshResource.createCylinder(radius = BoardLayout.CAB_RADIUS, height = BoardLayout.CAB_HEIGHT).keep()
    val beacon: MeshResource = MeshResource.createTorus(outerRingRadius = BoardLayout.CAB_RADIUS * 0.9f, innerRingRadius = BoardLayout.CAB_RADIUS * 0.55f).keep()
    val centerWedge: MeshResource = MeshResource.createBox(Vector3(BoardLayout.CELL * 0.95f, BoardLayout.TILE_HEIGHT * 1.1f, BoardLayout.CELL * 0.95f), cornerRadius = BoardLayout.CELL * 0.12f).keep()
    val arrow: MeshResource = MeshResource.createBox(Vector3(BoardLayout.TILE * 0.26f, 0.0025f, BoardLayout.TILE * 0.7f), cornerRadius = 0.001f).keep()
    val arrowHead: MeshResource = MeshResource.createBox(Vector3(BoardLayout.TILE * 0.26f, 0.0025f, BoardLayout.TILE * 0.44f), cornerRadius = 0.001f).keep()
    val laneArrow: MeshResource = MeshResource.createBox(Vector3(BoardLayout.TILE * 0.2f, 0.002f, BoardLayout.TILE * 0.5f), cornerRadius = 0.001f).keep()
    val laneArrowHead: MeshResource = MeshResource.createBox(Vector3(BoardLayout.TILE * 0.2f, 0.002f, BoardLayout.TILE * 0.34f), cornerRadius = 0.001f).keep()
    val dash: MeshResource = MeshResource.createBox(Vector3(BoardLayout.CELL * 0.12f, 0.0025f, BoardLayout.CELL * 0.22f), cornerRadius = 0.0008f).keep()
    val shadowDisc: MeshResource = MeshResource.createCylinder(radius = BoardLayout.CELL * 0.4f, height = 0.0006f).keep()
    val ring: MeshResource = MeshResource.createTorus(outerRingRadius = BoardLayout.CELL * 0.78f, innerRingRadius = BoardLayout.CELL * 0.66f).keep()
    val flyMark: MeshResource = MeshResource.createTorus(outerRingRadius = BoardLayout.CELL * 0.36f, innerRingRadius = BoardLayout.CELL * 0.27f).keep()
    val bead: MeshResource = MeshResource.createSphere(radius = 0.0035f).keep()
    val trayFloorMesh: MeshResource = MeshResource.createBox(
        Vector3((DiceSolverUnits.TRAY_WIDTH + 2 * DiceSolverUnits.WALL) * BoardLayout.DIE_SCALE, BoardLayout.TRAY_FLOOR_THICKNESS, (DiceSolverUnits.TRAY_DEPTH + 2 * DiceSolverUnits.WALL) * BoardLayout.DIE_SCALE),
        cornerRadius = 0.004f,
    ).keep()
    val trayWallX: MeshResource = MeshResource.createBox(
        Vector3(DiceSolverUnits.WALL * BoardLayout.DIE_SCALE, DiceSolverUnits.WALL_HEIGHT * BoardLayout.DIE_SCALE, (DiceSolverUnits.TRAY_DEPTH + 2 * DiceSolverUnits.WALL) * BoardLayout.DIE_SCALE),
        cornerRadius = 0.0015f,
    ).keep()
    val trayWallZ: MeshResource = MeshResource.createBox(
        Vector3(DiceSolverUnits.TRAY_WIDTH * BoardLayout.DIE_SCALE, DiceSolverUnits.WALL_HEIGHT * BoardLayout.DIE_SCALE, DiceSolverUnits.WALL * BoardLayout.DIE_SCALE),
        cornerRadius = 0.0015f,
    ).keep()

    // ---- collision ----
    val physics: PhysicsMaterialResource = PhysicsMaterialResource().keep()
    val dieShape: ShapeResource = ShapeResource.createBox(Vector3(BoardLayout.DIE_SIDE * 1.25f, BoardLayout.DIE_SIDE * 1.25f, BoardLayout.DIE_SIDE * 1.25f)).keep()
    val trayShape: ShapeResource = ShapeResource.createBox(Vector3(DiceSolverUnits.TRAY_WIDTH * BoardLayout.DIE_SCALE, 0.012f, DiceSolverUnits.TRAY_DEPTH * BoardLayout.DIE_SCALE)).keep()
    val planeShape: ShapeResource = ShapeResource.createBox(Vector3(BoardLayout.CELL * 1.1f, BoardLayout.CELL * 0.6f, BoardLayout.CELL * 1.1f)).keep()

    private fun <T : Resource> T.keep(): T {
        toGlobal()
        all += this
        return this
    }

    fun close() {
        all.forEach { runCatching { it.close() } }
        all.clear()
    }
}

/** The dice solver's tray in its own units (die side 0.6). Mirrors DiceSolver's constants for the tray meshes. */
internal object DiceSolverUnits {
    const val TRAY_WIDTH = 2.8f
    const val TRAY_DEPTH = 1.8f
    const val WALL = 0.08f
    const val WALL_HEIGHT = 0.5f
}
