package io.embodit.cloudair.ui.board.scene

import io.embodit.cloudair.domain.model.Board
import io.embodit.cloudair.domain.model.Cell
import io.embodit.cloudair.domain.model.GridPoint
import io.embodit.cloudair.domain.model.Team

/** A point in the board's own metres: +X right, +Y up, +Z toward the player. The mat's centre is the origin. */
data class P3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: P3) = P3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: P3) = P3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = P3(x * k, y * k, z * k)
    fun lerp(o: P3, t: Float) = P3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t)
    fun length(): Float = kotlin.math.sqrt(x * x + y * y + z * z)
}

/**
 * The board in metres. Grid row 0 is the far edge (-Z), column 0 the left edge (-X); the mat's top
 * surface is y = 0 in board space. The scene root places the whole board inside the volume.
 */
object BoardLayout {
    /** Mat width and depth. */
    const val MAT = 0.72f
    const val CELL = MAT / Board.SIZE
    const val TILE = CELL * 0.82f
    const val TILE_HEIGHT = 0.0055f
    const val MAT_THICKNESS = 0.018f
    const val HANGAR_SIZE = CELL * 4.6f
    const val HANGAR_HEIGHT = 0.004f
    const val RIM_HEIGHT = 0.009f
    const val RIM_THICKNESS = CELL * 0.18f
    const val LANE_TILE_HEIGHT = TILE_HEIGHT * 1.5f
    const val CIRCLE_RADIUS = CELL * 0.2f
    const val CIRCLE_HEIGHT = 0.0008f
    const val HUB_RADIUS = CELL * 0.42f
    const val HUB_HEIGHT = 0.008f
    const val TOWER_RADIUS = CELL * 0.11f
    const val TOWER_HEIGHT = 0.022f
    const val CAB_RADIUS = CELL * 0.2f
    const val CAB_HEIGHT = 0.008f
    const val FRAME_MARGIN = 0.012f
    const val SPOT_RADIUS = CELL * 0.62f
    const val SPOT_HEIGHT = 0.0035f

    /** Planes rest this far above the tile they stand on (the model's origin is below the belly). */
    const val PLANE_REST = TILE_HEIGHT + 0.001f
    const val PLANE_SCALE = 0.074f
    const val PLANE_SCALE_PARKED = 0.074f
    const val PLANE_SCALE_HOME = 0.052f

    /** Die edge in metres and the solver-unit to metre factor. */
    const val DIE_SIDE = 0.042f
    const val DIE_SCALE = DIE_SIDE / 0.6f

    /** Tray floor centre, in board metres (in front of the mat's near edge). */
    val TRAY_CENTER = P3(0f, 0f, MAT / 2 + FRAME_MARGIN + 0.004f + 1.8f * DIE_SCALE / 2 + 0.08f * DIE_SCALE)
    const val TRAY_FLOOR_THICKNESS = 0.006f

    fun of(point: GridPoint, y: Float = 0f): P3 =
        P3(((point.col - 7.0) * CELL).toFloat(), y, ((point.row - 7.0) * CELL).toFloat())

    fun of(cell: Cell, y: Float = 0f): P3 = of(GridPoint(cell.row.toDouble(), cell.col.toDouble()), y)

    /** Where a plane of [team] with [progress] rests, including the hangar and the centre. */
    fun planeRest(team: Team, plane: Int, progress: Int): P3 = when (progress) {
        Board.HANGAR -> of(Board.hangarSpot(team, plane), HANGAR_HEIGHT + SPOT_HEIGHT + 0.001f)
        // Planes home park in pairs beside the tower, the second pair stacked on the first.
        Board.FINISH -> of(Board.homeSpot(team, plane), HUB_HEIGHT + 0.001f + if (plane >= 2) 0.013f else 0f)
        else -> of(Board.cell(team, progress), PLANE_REST)
    }

    /**
     * Heading (radians about +Y, 0 = nose toward -Z) that points from [from] to [to] on the mat.
     * A rotation of theta about +Y turns the nose direction (0, 0, -1) into (-sin theta, 0, -cos theta).
     */
    fun headingBetween(from: P3, to: P3): Float {
        val dx = to.x - from.x
        val dz = to.z - from.z
        if (dx * dx + dz * dz < 1e-10f) return 0f
        return kotlin.math.atan2(-dx, -dz)
    }

    /** Parked planes face the launch cell; home planes face the centre. */
    fun restHeading(team: Team, plane: Int, progress: Int): Float = when (progress) {
        Board.HANGAR -> headingBetween(of(Board.hangarCenter(team)), of(Board.launchCell(team)))
        Board.FINISH -> headingBetween(of(Board.homeSpot(team, plane)), of(Board.CENTER))
        else -> {
            val next = if (progress < Board.FINISH) progress + 1 else progress
            if (next == progress) 0f else headingBetween(of(Board.cell(team, progress)), of(Board.cell(team, next)))
        }
    }
}
