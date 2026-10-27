package io.embodit.cloudair.domain.model

/** A cell of the 15 x 15 board grid. Row 0 is the far edge (away from the player), column 0 the left edge. */
data class Cell(val row: Int, val col: Int)

/** A point in grid units (cell centres are at integer coordinates). */
data class GridPoint(val row: Double, val col: Double)

/**
 * The classic aeroplane-chess mat as a 15 x 15 grid: four corner hangars, a cross of four 3-wide arms,
 * a 52-cell loop running round the cross, and a 6-cell home lane down the middle of each arm to the centre.
 *
 * Progress of a plane, per fleet: [HANGAR] = -1; 0..50 on the loop (0 is the launch cell, 50 the lane
 * entrance at the tip of the fleet's arm); 51..56 the home lane; [FINISH] = 57 the centre.
 *
 * Loop cell `i` belongs to colour `i % 4`; every fleet's launch cell is its own colour and so is every
 * fourth cell after it (the "connected jump" cells). The loop runs counter-clockwise seen from above:
 * mint launches beside the far-left hangar and flies toward the player along the top arm's left column.
 */
object Board {
    const val SIZE = 15
    const val LOOP_CELLS = 52
    const val QUARTER = 13
    const val HANGAR = -1
    const val LAST_LOOP = 50
    const val LANE_FIRST = 51
    const val LANE_CELLS = 6
    const val FINISH = 57

    /** Landing on your own colour jumps this many cells further (once per move). */
    const val JUMP = 4

    /** Landing exactly here (own colour) flies straight across the opposite arm to [FLY_TO]. */
    const val FLY_FROM = 20
    const val FLY_TO = 28

    /** The lane cell of the fleet two seats on that the fly-over crosses: a plane parked there is sent home. */
    const val FLY_CROSSING = 53

    val CENTER = Cell(7, 7)

    /** Mint's quarter: down the top arm's left column, through the notch, along the left arm's far row, round its tip. */
    private val quarter = listOf(
        Cell(1, 6), Cell(2, 6), Cell(3, 6), Cell(4, 6), Cell(5, 6),
        Cell(6, 5), Cell(6, 4), Cell(6, 3), Cell(6, 2), Cell(6, 1), Cell(6, 0),
        Cell(7, 0), Cell(8, 0),
    )

    /** Rotates a cell a quarter turn in the loop direction. */
    fun rotate(cell: Cell, quarters: Int): Cell {
        var r = cell
        repeat(Math.floorMod(quarters, 4)) { r = Cell(SIZE - 1 - r.col, r.row) }
        return r
    }

    fun rotate(point: GridPoint, quarters: Int): GridPoint {
        var p = point
        repeat(Math.floorMod(quarters, 4)) { p = GridPoint((SIZE - 1) - p.col, p.row) }
        return p
    }

    /** The 52 loop cells, absolute index 0 = mint's launch cell. */
    val loop: List<Cell> = (0 until 4).flatMap { t -> quarter.map { rotate(it, t) } }

    /** The loop cell reached diagonally in each quarter; drawn as a diamond like the mat's corner triangles. */
    val notchIndices: List<Int> = (0 until 4).map { it * QUARTER + 5 }

    fun absoluteIndex(team: Team, progress: Int): Int {
        require(progress in 0..LAST_LOOP) { "not a loop progress: $progress" }
        return (team.ordinal * QUARTER + progress) % LOOP_CELLS
    }

    fun colorOf(absoluteIndex: Int): Team = Team.entries[Math.floorMod(absoluteIndex, 4)]

    fun isOwnColor(team: Team, progress: Int): Boolean =
        progress in 0..LAST_LOOP && colorOf(absoluteIndex(team, progress)) == team

    fun laneCell(team: Team, index: Int): Cell {
        require(index in 0 until LANE_CELLS)
        return rotate(Cell(1 + index, 7), team.ordinal)
    }

    /** The cell a plane of [team] with [progress] stands on. The hangar has no cell; use [hangarSpot]. */
    fun cell(team: Team, progress: Int): Cell = when (progress) {
        in 0..LAST_LOOP -> loop[absoluteIndex(team, progress)]
        in LANE_FIRST..(LANE_FIRST + LANE_CELLS - 1) -> laneCell(team, progress - LANE_FIRST)
        FINISH -> CENTER
        else -> throw IllegalArgumentException("no cell for progress $progress")
    }

    fun launchCell(team: Team): Cell = loop[team.ordinal * QUARTER]

    fun laneEntrance(team: Team): Cell = cell(team, LAST_LOOP)

    fun opposite(team: Team): Team = Team.entries[(team.ordinal + 2) % 4]

    /** The lane cell the fleet's fly-over crosses. */
    fun flyCrossingCell(team: Team): Cell = laneCell(opposite(team), FLY_CROSSING - LANE_FIRST)

    /** Hangar block: 6 x 6 cells in the fleet's corner, drawn as a 5-cell square round this centre. */
    fun hangarCenter(team: Team): GridPoint = rotate(GridPoint(2.5, 2.5), team.ordinal)

    /** The four parking spots of a hangar, 2 x 2. */
    fun hangarSpot(team: Team, plane: Int): GridPoint {
        val dr = if (plane < 2) -1.0 else 1.0
        val dc = if (plane % 2 == 0) -1.0 else 1.0
        return rotate(GridPoint(2.5 + dr, 2.5 + dc), team.ordinal)
    }

    /** Where a plane that reached the centre rests: beside the tower on its fleet's side; planes 2 and 3 stack on 0 and 1. */
    fun homeSpot(team: Team, plane: Int): GridPoint {
        val side = if (plane % 2 == 0) -0.36 else 0.36
        return rotate(GridPoint(7.0 - 0.62, 7.0 + side), team.ordinal)
    }


    /** The cell of every plane in play, for the capture rules. */
    fun loopIndexOf(team: Team, progress: Int): Int? = if (progress in 0..LAST_LOOP) absoluteIndex(team, progress) else null
}
