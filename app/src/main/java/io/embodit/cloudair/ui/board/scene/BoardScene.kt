package io.embodit.cloudair.ui.board.scene

import android.util.Log
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import io.embodit.cloudair.domain.model.Board
import io.embodit.cloudair.domain.model.Capture
import io.embodit.cloudair.domain.model.GridPoint
import io.embodit.cloudair.domain.model.MoveSegment
import io.embodit.cloudair.domain.model.SegmentKind
import io.embodit.cloudair.domain.model.Team
import io.embodit.cloudair.domain.usecase.DiceSolver
import io.embodit.cloudair.domain.usecase.DiceThrow
import io.embodit.cloudair.ui.board.BoardUiState
import io.embodit.cloudair.ui.board.PlaneState
import io.embodit.cloudair.ui.board.SceneEvent
import io.embodit.cloudair.ui.board.SoundKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Owns every entity of the cloud airport and plays the moves and throws the game commits.
 *
 * ```
 * SpatialView
 * └ root                     the mat's top surface is y = 0 here; tipped toward the player
 *   ├ board skin (frame, mat, hub + tower; board.glb), loop tiles (+ circles), lanes, hangars (slab, rims, spots), arrows, dashes
 *   ├ fleets: PlaneView per plane of every seat, rings under planes that may be picked
 *   └ tray skin (tray.glb: shell + felt), the die, the die's invitation ring
 * ```
 *
 * All calls come from the main thread: [install]/[apply] from Compose, [onFrame] from the ECS system.
 */
internal class BoardScene(private val onEvent: (SceneEvent) -> Unit) {
    val root: Entity = Entity().apply { setName("boardRoot") }

    private var res: SceneResources? = null
    private var planeTemplate: Entity? = null
    private var dieRoot: Entity? = null
    private var boardModel: Entity? = null
    private var trayModel: Entity? = null
    private var built = false

    private class Tile(val entity: ModelEntity, val material: Material, val position: P3)

    private val loopTiles = ArrayList<Tile>()
    private val laneTiles = HashMap<Team, List<Tile>>()
    private val highlighted = ArrayList<Tile>()

    private class Hangar(val group: Entity, val slab: ModelEntity, val rims: List<ModelEntity>, var lifted: Float = 0f)
    private val beads = HashMap<Team, List<ModelEntity>>()
    private var recommendedTile: Tile? = null

    private val hangars = HashMap<Team, Hangar>()
    private var activeHangar: Team? = null
    private var beacon: ModelEntity? = null

    private var dieRing: ModelEntity? = null
    private var dieRest: Pair<DoubleArray, DoubleArray> = DiceSolver.restPose(6).let { it.position to it.quaternion }
    private val pickRings = ArrayList<ModelEntity>()

    private val planes = HashMap<Pair<Int, Int>, PlaneView>()
    private var fleetsGameId = -1

    private var state = BoardUiState()
    private var regionHalf: P3? = null

    // ---- animation ----
    private var moveTimeline: Timeline? = null
    private var moveId = -1
    private var diceThrow: DiceThrow? = null
    private var diceId = -1
    private var diceClock = 0f
    private var diceHold = 0f
    private var diceLanded = false
    private var clock = 0f

    val hasResources: Boolean get() = res != null

    fun provide(resources: SceneResources) {
        if (res == null) res = resources else resources.close()
        tryBuild()
    }

    /**
     * The loaded models: the airplane used as a template for every fleet, the die, and the skin's board
     * (frame, mat, hub and tower) and dice tray. Without a board or tray model those parts are primitives.
     */
    fun install(plane: Entity, die: Entity, board: Entity? = null, tray: Entity? = null) {
        planeTemplate = plane
        dieRoot = die
        boardModel = board
        trayModel = tray
        tryBuild()
    }

    fun setRegion(half: P3) {
        val known = regionHalf
        if (known != null && abs(known.x - half.x) < 1e-4f && abs(known.y - half.y) < 1e-4f && abs(known.z - half.z) < 1e-4f) return
        regionHalf = half
        // The mat is tipped toward the player like an easel, so the whole airport reads from a seat
        // in front of the volume. The lowest point is the tray's near rim; it rests on the volume floor.
        val tilt = TILT_RAD
        val nearest = BoardLayout.TRAY_CENTER.z + (DiceSolverUnits.TRAY_DEPTH / 2 + DiceSolverUnits.WALL) * BoardLayout.DIE_SCALE
        val lowest = -nearest * sin(tilt) - BoardLayout.MAT_THICKNESS * cos(tilt)
        val y = -half.y - lowest + FLOOR_LIFT
        root.transform().setPosition(Vector3(0f, y, 0f))
        root.transform().setQuaternion(Quat(Vector3(1f, 0f, 0f), tilt))
        Log.i(TAG, "region half=(${half.x}, ${half.y}, ${half.z}) rootY=$y tiltDeg=${Math.toDegrees(tilt.toDouble())}")
    }

    private fun tryBuild() {
        val r = res ?: return
        if (built || dieRoot == null || planeTemplate == null) return
        built = true
        val started = android.os.SystemClock.elapsedRealtime()
        buildMat(r)
        buildTray(r)
        Log.i(TAG, "SCENE built in ${android.os.SystemClock.elapsedRealtime() - started} ms")
        apply(state, force = true)
    }

    // ---- static board ----

    private fun add(mesh: MeshResource, material: Material, at: P3, name: String, yaw: Float = 0f, parent: Entity = root): ModelEntity {
        val e = ModelEntity(mesh, material).apply {
            setName(name)
            transform().setPosition(Vector3(at.x, at.y, at.z))
            if (yaw != 0f) transform().setQuaternion(Quat(Vector3(0f, 1f, 0f), yaw))
        }
        parent.addChild(e)
        return e
    }

    private fun arrow(parent: Entity, shaft: MeshResource, head: MeshResource, material: Material, at: P3, heading: Float, name: String, shaftLength: Float, headLength: Float) {
        val holder = Entity().apply {
            setName(name)
            transform().setPosition(Vector3(at.x, at.y, at.z))
            transform().setQuaternion(Quat(Vector3(0f, 1f, 0f), heading))
        }
        parent.addChild(holder)
        holder.addChild(ModelEntity(shaft, material).apply { setName("$name:shaft"); transform().setPosition(Vector3(0f, 0f, shaftLength * 0.12f)) })
        val headZ = -shaftLength * 0.5f + headLength * 0.1f
        holder.addChild(ModelEntity(head, material).apply {
            setName("$name:l")
            transform().setPosition(Vector3(-headLength * 0.3f, 0f, headZ))
            transform().setQuaternion(Quat(Vector3(0f, 1f, 0f), (-PI / 4).toFloat()))
        })
        holder.addChild(ModelEntity(head, material).apply {
            setName("$name:r")
            transform().setPosition(Vector3(headLength * 0.3f, 0f, headZ))
            transform().setQuaternion(Quat(Vector3(0f, 1f, 0f), (PI / 4).toFloat()))
        })
    }

    private fun buildMat(r: SceneResources) {
        // board.glb: bent-wood frame, linen mat with its binding, the hub and the turned tower, origin on the mat's top centre.
        val board = boardModel
        if (board != null) {
            board.setName("boardSkin")
            root.addChild(board)
        } else {
            add(r.frameSlab, r.frame, P3(0f, -BoardLayout.MAT_THICKNESS * 1.3f, 0f), "frame")
            add(r.matSlab, r.mat, P3(0f, -BoardLayout.MAT_THICKNESS / 2, 0f), "mat")
        }
        val proud = BoardLayout.TILE_HEIGHT + 0.0009f
        // Loop tiles, coloured by the cycle of four; the notch cells as diamonds; a white circle on every cell.
        for (i in Board.loop.indices) {
            val cell = Board.loop[i]
            val material = r.mid.getValue(Board.colorOf(i))
            val notch = i in Board.notchIndices
            val at = BoardLayout.of(cell, BoardLayout.TILE_HEIGHT / 2)
            val tile = add(if (notch) r.notchTile else r.tile, material, at, "loop:$i", if (notch) (PI / 4).toFloat() else 0f)
            loopTiles += Tile(tile, material, at)
            add(r.circle, r.ivory, BoardLayout.of(cell, BoardLayout.TILE_HEIGHT + BoardLayout.CIRCLE_HEIGHT / 2), "circle:$i")
        }
        for (team in Team.entries) {
            val laneMaterial = r.lane.getValue(team)
            laneTiles[team] = (0 until Board.LANE_CELLS).map { i ->
                val at = BoardLayout.of(Board.laneCell(team, i), BoardLayout.LANE_TILE_HEIGHT / 2)
                val tile = add(r.laneTile, laneMaterial, at, "lane:$team:$i")
                add(r.circle, r.ivory, BoardLayout.of(Board.laneCell(team, i), BoardLayout.LANE_TILE_HEIGHT + BoardLayout.CIRCLE_HEIGHT / 2), "laneCircle:$team:$i")
                Tile(tile, laneMaterial, at)
            }
            // An inward arrow on the lane entrance.
            val entrance = Board.laneEntrance(team)
            val firstLane = Board.laneCell(team, 0)
            arrow(root, r.laneArrow, r.laneArrowHead, r.deep.getValue(team), BoardLayout.of(firstLane, BoardLayout.LANE_TILE_HEIGHT + BoardLayout.CIRCLE_HEIGHT + 0.0012f), BoardLayout.headingBetween(BoardLayout.of(entrance), BoardLayout.of(firstLane)), "laneArrow:$team", BoardLayout.TILE * 0.5f, BoardLayout.TILE * 0.34f)
            // Hangar: a slab with a rim and four parking spots, grouped so it can lift when it is this fleet's turn.
            val center = BoardLayout.of(Board.hangarCenter(team))
            val group = Entity().apply { setName("hangar:$team"); transform().setPosition(Vector3(center.x, 0f, center.z)) }
            root.addChild(group)
            val slab = add(r.hangarSlab, r.light.getValue(team), P3(0f, BoardLayout.HANGAR_HEIGHT / 2, 0f), "hangarSlab:$team", parent = group)
            val half = BoardLayout.HANGAR_SIZE / 2 - BoardLayout.RIM_THICKNESS / 2
            val rimY = BoardLayout.HANGAR_HEIGHT + BoardLayout.RIM_HEIGHT / 2
            val rims = listOf(
                add(r.rimX, r.mid.getValue(team), P3(-half, rimY, 0f), "rim:$team:l", parent = group),
                add(r.rimX, r.mid.getValue(team), P3(half, rimY, 0f), "rim:$team:r", parent = group),
                add(r.rimZ, r.mid.getValue(team), P3(0f, rimY, -half), "rim:$team:b", parent = group),
                add(r.rimZ, r.mid.getValue(team), P3(0f, rimY, half), "rim:$team:f", parent = group),
            )
            for (p in 0 until 4) {
                val spot = BoardLayout.of(Board.hangarSpot(team, p), BoardLayout.HANGAR_HEIGHT + BoardLayout.SPOT_HEIGHT / 2) - P3(center.x, 0f, center.z)
                add(r.spot, r.ivory, spot, "spot:$team:$p", parent = group)
            }
            hangars[team] = Hangar(group, slab, rims)
            // Launch arrow: on the launch cell, pointing along the loop.
            val launch = Board.launchCell(team)
            val next = Board.cell(team, 1)
            arrow(root, r.arrow, r.arrowHead, r.deep.getValue(team), BoardLayout.of(launch, proud + 0.0012f), BoardLayout.headingBetween(BoardLayout.of(launch), BoardLayout.of(next)), "arrow:$team", BoardLayout.TILE * 0.7f, BoardLayout.TILE * 0.44f)
            // Fly-over: three dashes across the opposite arm and a ring on the entry cell.
            val from = BoardLayout.of(Board.cell(team, Board.FLY_FROM), proud + 0.0012f)
            val to = BoardLayout.of(Board.cell(team, Board.FLY_TO), proud + 0.0012f)
            val dashHeading = BoardLayout.headingBetween(from, to)
            for (k in 1..3) {
                val at = from.lerp(to, k / 4f)
                // The middle dash crosses the taller lane tile and its circle.
                val y = if (k == 2) BoardLayout.LANE_TILE_HEIGHT + BoardLayout.CIRCLE_HEIGHT + 0.0013f else at.y
                add(r.dash, r.deep.getValue(team), P3(at.x, y, at.z), "dash:$team:$k", dashHeading)
            }
            add(r.flyMark, r.deep.getValue(team), BoardLayout.of(Board.cell(team, Board.FLY_FROM), proud + 0.0006f), "flyMark:$team")
            // Beads floating along the real flight arc: the sky bridge the plane will follow.
            val arcFrom = BoardLayout.of(Board.cell(team, Board.FLY_FROM), BoardLayout.PLANE_REST)
            val arcTo = BoardLayout.of(Board.cell(team, Board.FLY_TO), BoardLayout.PLANE_REST)
            beads[team] = (1..5).map { k ->
                val u = k / 6f
                val at = arcFrom.lerp(arcTo, u) + P3(0f, FLY_LIFT * Easing.hump(u), 0f)
                add(r.bead, r.deep.getValue(team), at, "bead:$team:$k").apply { enabled = false }
            }
            // Centre pinwheel petal toward this fleet's lane.
            val wedgeAt = BoardLayout.of(Board.rotate(GridPoint(6.4, 7.0), team.ordinal), BoardLayout.TILE_HEIGHT * 0.55f)
            add(r.centerWedge, r.deep.getValue(team), wedgeAt, "wedge:$team", (PI / 4).toFloat())
        }
        // The hub and the control tower (in board.glb when it loaded).
        if (board == null) {
            add(r.hub, r.ivory, P3(0f, BoardLayout.HUB_HEIGHT / 2, 0f), "hub")
            add(r.tower, r.ivory, P3(0f, BoardLayout.HUB_HEIGHT + BoardLayout.TOWER_HEIGHT / 2, 0f), "tower")
            add(r.cab, r.frame, P3(0f, BoardLayout.HUB_HEIGHT + BoardLayout.TOWER_HEIGHT + BoardLayout.CAB_HEIGHT / 2, 0f), "cab")
        }
        beacon = add(r.beacon, r.deep.getValue(Team.MINT), P3(0f, BoardLayout.HUB_HEIGHT + BoardLayout.TOWER_HEIGHT + BoardLayout.CAB_HEIGHT + 0.002f, 0f), "beacon")
        // Rings for planes the player may pick: a pool of four, hidden until needed.
        repeat(4) { i ->
            pickRings += ModelEntity(r.ring, r.deep.getValue(Team.MINT)).apply { setName("pickRing:$i"); enabled = false }.also { root.addChild(it) }
        }
    }

    private fun buildTray(r: SceneResources) {
        val c = BoardLayout.TRAY_CENTER
        val k = BoardLayout.DIE_SCALE
        // tray.glb: wooden shell round the solver's inner box, felt top at the floor (origin = TRAY_CENTER).
        val tray = trayModel
        if (tray != null) {
            tray.setName("traySkin")
            tray.transform().setPosition(Vector3(c.x, c.y, c.z))
            root.addChild(tray)
        } else {
            add(r.trayFloorMesh, r.trayFloor, P3(c.x, c.y - BoardLayout.TRAY_FLOOR_THICKNESS / 2, c.z), "trayFloor")
            val wallY = c.y + DiceSolverUnits.WALL_HEIGHT * k / 2
            val dx = (DiceSolverUnits.TRAY_WIDTH / 2 + DiceSolverUnits.WALL / 2) * k
            val dz = (DiceSolverUnits.TRAY_DEPTH / 2 + DiceSolverUnits.WALL / 2) * k
            add(r.trayWallX, r.tray, P3(c.x - dx, wallY, c.z), "trayWallL")
            add(r.trayWallX, r.tray, P3(c.x + dx, wallY, c.z), "trayWallR")
            add(r.trayWallZ, r.tray, P3(c.x, wallY, c.z - dz), "trayWallB")
            add(r.trayWallZ, r.tray, P3(c.x, wallY, c.z + dz), "trayWallF")
        }
        // The tray itself is a tap target too: a pinch anywhere in it throws the die.
        val trayTarget = Entity().apply {
            setName(TRAY_NAME)
            // A thin slab at floor level, so a gaze on the die meets the die's own collider first.
            transform().setPosition(Vector3(c.x, c.y + 0.006f, c.z))
            components.set(CollisionComponent(collisionShape = listOf(r.trayShape), physicsMaterial = r.physics))
            components.set(InteractableComponent())
        }
        root.addChild(trayTarget)
        dieRing = ModelEntity(r.ring, r.deep.getValue(Team.MINT)).apply {
            setName("dieRing")
            val s = BoardLayout.DIE_SIDE * 0.95f / (BoardLayout.CELL * 0.78f)
            transform().setScaleVector(Vector3(s, 1f, s))
            enabled = false
        }
        root.addChild(dieRing!!)
        val die = dieRoot ?: return
        die.setName(DIE_NAME)
        die.transform().setScaleVector(Vector3(k, k, k))
        die.components.set(CollisionComponent(collisionShape = listOf(r.dieShape), physicsMaterial = r.physics))
        die.components.set(InteractableComponent())
        die.components.set(HoverEffectComponent())
        root.addChild(die)
        placeDie(dieRest)
    }

    private fun diePosition(p: DoubleArray, bob: Float = 0f): Vector3 {
        val k = BoardLayout.DIE_SCALE
        val c = BoardLayout.TRAY_CENTER
        return Vector3(c.x + (p[0] * k).toFloat(), c.y + (p[1] * k).toFloat() + bob, c.z + (p[2] * k).toFloat())
    }

    private fun placeDie(pose: Pair<DoubleArray, DoubleArray>, bob: Float = 0f, scale: Float = 1f) {
        val die = dieRoot ?: return
        val q = pose.second
        die.transform().setPosition(diePosition(pose.first, bob))
        die.transform().setQuaternion(Quat(q[0].toFloat(), q[1].toFloat(), q[2].toFloat(), q[3].toFloat()))
        val k = BoardLayout.DIE_SCALE * scale
        die.transform().setScaleVector(Vector3(k, k, k))
    }

    // ---- fleets ----

    private fun rebuildFleets(planesState: List<PlaneState>) {
        val r = res ?: return
        val template = planeTemplate ?: return
        planes.values.forEach { it.destroy() }
        planes.clear()
        for (ps in planesState) {
            val model = template.clone(Entity.CloneOptions(recursive = true, shouldShareMaterialInstance = true))
            if (model == null) { Log.w(TAG, "plane clone failed for ${ps.seat}:${ps.plane}"); continue }
            model.enabled = true
            val view = PlaneView(ps.seat, ps.plane, ps.team, model, r)
            planes[ps.seat to ps.plane] = view
            root.addChild(view.holder)
            rest(view, ps.progress)
        }
        Log.i(TAG, "FLEETS built planes=${planes.size}")
    }

    private fun rest(view: PlaneView, progress: Int) {
        view.setScale(
            when (progress) {
                Board.HANGAR -> BoardLayout.PLANE_SCALE_PARKED
                Board.FINISH -> BoardLayout.PLANE_SCALE_HOME
                else -> BoardLayout.PLANE_SCALE
            },
        )
        view.rest(BoardLayout.planeRest(view.team, view.plane, progress), BoardLayout.restHeading(view.team, view.plane, progress))
    }

    // ---- state ----

    fun apply(next: BoardUiState, force: Boolean = false) {
        val previous = state
        state = next
        if (!built) return
        if (force || next.gameId != fleetsGameId) {
            fleetsGameId = next.gameId
            moveTimeline = null
            moveId = -1
            clearHighlights()
            rebuildFleets(next.planes)
            // A restored game shows its last result on the die.
            dieRest = DiceSolver.restPose(next.lastDice ?: 6).let { it.position to it.quaternion }
            placeDie(dieRest)
        } else if (moveTimeline == null) {
            // Nothing in flight: every plane sits where the game says.
            for (ps in next.planes) planes[ps.seat to ps.plane]?.let { rest(it, ps.progress) }
        }
        updateTurnMarks(next)
        updatePickRings(next)
        val move = next.move
        if (move != null && move.id != moveId) {
            moveId = move.id
            beginMove(move.id, move.plan.segments, move.plan.seat, move.plan.plane, next)
        }
        val throwSpec = next.dice
        val throwData = next.diceThrow
        if (throwSpec != null && throwData != null && throwSpec.id != diceId && throwData.result == throwSpec.result) {
            diceId = throwSpec.id
            beginDice(throwData, next)
        }
        if (throwSpec == null && previous.dice != null && next.lastDice != null && diceThrow == null) {
            // A restored game: show the last result at rest.
            dieRest = DiceSolver.restPose(next.lastDice).let { it.position to it.quaternion }
            placeDie(dieRest)
        }
    }

    private fun updateTurnMarks(next: BoardUiState) {
        val r = res ?: return
        val seat = next.activeSeat
        val team = seat?.let { next.teams.getOrNull(it) }
        if (team != activeHangar) {
            activeHangar?.let { old ->
                hangars[old]?.rims?.forEach { rim -> rim.components[ModelComponent::class.java]?.materials?.set(0, r.mid.getValue(old)) }
                beads[old]?.forEach { it.enabled = false }
            }
            team?.let { t ->
                hangars[t]?.rims?.forEach { rim -> rim.components[ModelComponent::class.java]?.materials?.set(0, r.deep.getValue(t)) }
                beacon?.components?.get(ModelComponent::class.java)?.materials?.set(0, r.deep.getValue(t))
                dieRing?.components?.get(ModelComponent::class.java)?.materials?.set(0, r.deep.getValue(t))
                beads[t]?.forEach { it.enabled = true }
            }
            activeHangar = team
        }
        // Seats that are not playing keep a pale hangar.
        for ((t, hangar) in hangars) {
            val playing = t in next.teams || next.teams.isEmpty()
            hangar.slab.components[ModelComponent::class.java]?.materials?.set(0, if (playing) r.light.getValue(t) else r.neutral)
        }
        dieRing?.enabled = next.canRoll && diceThrow == null
        // The recommended plane's destination glows while the player chooses.
        val rec = next.recommended
        val recTile = if (rec != null && team != null && next.phase == io.embodit.cloudair.domain.model.Phase.SELECT) tileOf(team, rec.to) else null
        if (recTile !== recommendedTile) {
            recommendedTile?.let { restore(it) }
            recommendedTile = recTile
            recTile?.let { raise(it, r.glow) }
        }
    }

    private fun updatePickRings(next: BoardUiState) {
        val r = res ?: return
        val team = next.activeSeat?.let { next.teams.getOrNull(it) }
        val selectable = if (team == null) emptyList() else next.selectable.sorted()
        pickRings.forEachIndexed { i, ring ->
            val plane = selectable.getOrNull(i)
            val view = plane?.let { planes[next.activeSeat!! to it] }
            if (view == null) { ring.enabled = false; return@forEachIndexed }
            ring.components[ModelComponent::class.java]?.materials?.set(0, r.deep.getValue(team!!))
            val p = view.restPosition
            ring.transform().setPosition(Vector3(p.x, p.y + 0.0008f, p.z))
            ring.enabled = true
        }
    }

    // ---- dice ----

    private fun beginDice(data: DiceThrow, s: BoardUiState) {
        diceThrow = data
        diceClock = 0f
        diceLanded = false
        diceHold = if (s.reducedMotion) 0.35f else 0.3f * s.speed.factor
        dieRing?.enabled = false
        val last = data.frames.last().let { it.position to it.quaternion }
        dieRest = last
        if (s.reducedMotion) {
            placeDie(last)
            diceLanded = true
        } else {
            onEvent(SceneEvent.Sound(SoundKind.DICE_THROW))
            placeDie(data.frames.first().let { it.position to it.quaternion })
        }
        Log.i(TAG, "DICE play id=$diceId result=${data.result} duration=${"%.2f".format(data.duration)} reduced=${s.reducedMotion}")
    }

    private fun stepDice(dt: Float) {
        val data = diceThrow ?: return
        val s = state
        val speed = if (s.reducedMotion) 1f else 1f / s.speed.factor
        diceClock += dt * speed
        val duration = if (s.reducedMotion) 0f else data.duration.toFloat()
        if (!s.reducedMotion && diceClock < duration) {
            val pose = data.sample(diceClock.toDouble())
            placeDie(pose.position to pose.quaternion)
            if (!diceLanded && pose.position[1] < DiceSolver.HALF + 0.02) {
                diceLanded = true
                onEvent(SceneEvent.Sound(SoundKind.DICE_LAND))
            }
        } else {
            // At rest: a small pulse says "this is the result".
            val p = ((diceClock - duration) / 0.28f).coerceIn(0f, 1f)
            placeDie(dieRest, scale = 1f + 0.12f * sin((PI * p).toFloat()))
            if (!diceLanded) { diceLanded = true; if (!s.reducedMotion) onEvent(SceneEvent.Sound(SoundKind.DICE_LAND)) }
        }
        if (diceClock >= duration + diceHold) {
            placeDie(dieRest)
            diceThrow = null
            Log.i(TAG, "DICE settled id=$diceId face=${data.result}")
            onEvent(SceneEvent.DiceSettled(diceId))
        }
    }

    // ---- moves ----

    private data class Leg(val path: Path, val lift: Float, val duration: Float, val sound: SoundKind?, val kind: SegmentKind, val restProgress: Int, val roll: Boolean = false)

    private fun beginMove(id: Int, segments: List<MoveSegment>, seat: Int, plane: Int, s: BoardUiState) {
        val view = planes[seat to plane]
        if (view == null) { onEvent(SceneEvent.MoveFinished(id)); return }
        val reduced = s.reducedMotion
        val factor = if (reduced) 0.2f else s.speed.factor
        val clips = ArrayList<Clip>()
        var t = 0f
        for (seg in segments) {
            val legs = legsOf(view, seg, reduced)
            var lastLegEnd = t
            legs.forEachIndexed { index, leg ->
                val dur = leg.duration * factor
                val start = lastLegEnd
                val nextHeading = legs.getOrNull(index + 1)?.let { BoardLayout.headingBetween(it.path.start, it.path.end) }
                val thisHeading = BoardLayout.headingBetween(leg.path.start, leg.path.end)
                val restHeading = BoardLayout.restHeading(view.team, view.plane, leg.restProgress)
                val turn = nextHeading?.let { wrap(it - thisHeading) } ?: 0f
                val bank = (turn * 0.4f).coerceIn(-0.26f, 0.26f)
                val finalScale = if (leg.kind == SegmentKind.FINISH && index == legs.lastIndex) BoardLayout.PLANE_SCALE_HOME else BoardLayout.PLANE_SCALE
                val startScale = view.scale
                clips += Clip(
                    start = start, duration = dur,
                    onStart = {
                        if (index == 0) highlight(seg, view.team)
                        leg.sound?.let { onEvent(SceneEvent.Sound(it)) }
                    },
                    update = { u ->
                        val e = if (leg.kind == SegmentKind.LAUNCH) Easing.out(u) else Easing.inOut(u)
                        val pos = leg.path.at(e)
                        val tangent = leg.path.tangent(e)
                        val heading = if (tangent.x * tangent.x + tangent.z * tangent.z > 1e-9f) BoardLayout.headingBetween(P3(0f, 0f, 0f), tangent) else thisHeading
                        val hump = Easing.hump(u)
                        val pitch = if (reduced) 0f else (if (leg.lift > 0.03f) 0.35f else 0.25f) * cos((PI * u).toFloat())
                        val roll = when {
                            reduced -> 0f
                            leg.roll && u in 0.3f..0.7f -> (2 * PI * Easing.inOut((u - 0.3f) / 0.4f)).toFloat()
                            else -> bank * hump
                        }
                        if (finalScale != startScale) view.setScale(startScale + (finalScale - startScale) * e)
                        view.place(pos, if (u >= 0.999f) restHeading else heading, leg.lift * hump, pitch, roll)
                    },
                    onEnd = {
                        view.setScale(finalScale)
                        view.rest(leg.path.end, restHeading)
                    },
                )
                lastLegEnd = start + dur
            }
            if (seg.kind == SegmentKind.FINISH) {
                // A victory spin on the hub.
                val spinStart = lastLegEnd
                val spinDur = 0.4f * factor
                clips += Clip(
                    start = spinStart, duration = spinDur,
                    onStart = { onEvent(SceneEvent.Sound(SoundKind.HOME)) },
                    update = { u -> view.place(view.restPosition, view.restHeading + (2 * PI * Easing.inOut(u)).toFloat(), 0.012f * Easing.hump(u)) },
                    onEnd = { view.rest(view.restPosition, view.restHeading) },
                )
                lastLegEnd = spinStart + spinDur
            }
            // Captured planes fly home just after the attacker lands.
            for (capture in seg.captures) clips += captureClip(capture, lastLegEnd + 0.1f * factor, reduced, factor)
            t = lastLegEnd
        }
        moveTimeline = Timeline(clips, tail = 0.1f) {
            clearHighlights()
            moveTimeline = null
            Log.i(TAG, "MOVE played id=$id")
            onEvent(SceneEvent.MoveFinished(id))
        }
        Log.i(TAG, "MOVE play id=$id seat=$seat plane=$plane total=${"%.2f".format(moveTimeline?.total ?: 0f)} reduced=$reduced")
    }

    private fun wrap(angle: Float): Float {
        var a = angle
        while (a > PI) a -= (2 * PI).toFloat()
        while (a < -PI) a += (2 * PI).toFloat()
        return a
    }

    /** The legs of one segment: a hop per cell for walks, one arc for launches, jumps and flights. */
    private fun legsOf(view: PlaneView, seg: MoveSegment, reduced: Boolean): List<Leg> {
        val team = view.team
        val y = BoardLayout.PLANE_REST
        fun cell(p: Int) = BoardLayout.of(Board.cell(team, p), y)
        fun hops(from: Int, to: Int, lift: Float, lastTo: P3? = null): List<Leg> {
            val step = if (to >= from) 1 else -1
            val n = abs(to - from)
            val hopDur = 0.17f + 0.12f / maxOf(1, n)
            val legs = ArrayList<Leg>()
            var p = from
            while (p != to) {
                val next = p + step
                val end = if (next == to && lastTo != null) lastTo else cell(next)
                legs += Leg(Path(listOf(if (p == from) view.restPosition else cell(p), end)), if (reduced) 0f else lift, hopDur, SoundKind.HOP, seg.kind, next)
                p = next
            }
            return legs
        }
        return when (seg.kind) {
            SegmentKind.LAUNCH -> listOf(Leg(Path(listOf(view.restPosition, cell(0))), if (reduced) 0f else 0.04f, 0.8f, SoundKind.TAKEOFF, seg.kind, 0))
            SegmentKind.WALK -> hops(seg.from, seg.to, 0.012f)
            SegmentKind.BOUNCE -> hops(seg.from, seg.to, 0.012f)
            SegmentKind.FINISH -> hops(seg.from, Board.FINISH, 0.014f, lastTo = BoardLayout.planeRest(team, view.plane, Board.FINISH)).mapIndexed { i, leg ->
                if (i == abs(Board.FINISH - seg.from) - 1) leg.copy(lift = if (reduced) 0f else 0.04f, duration = 0.45f) else leg
            }
            SegmentKind.JUMP -> listOf(Leg(Path(listOf(view.restPosition, cell(seg.to))), if (reduced) 0f else 0.05f, 0.55f, SoundKind.JUMP, seg.kind, seg.to))
            SegmentKind.FLY -> listOf(Leg(Path(listOf(view.restPosition, cell(seg.to))), if (reduced) 0f else FLY_LIFT, 1.1f, SoundKind.FLY, seg.kind, seg.to, roll = !reduced))
        }
    }

    private fun captureClip(capture: Capture, start: Float, reduced: Boolean, factor: Float): Clip {
        val victim = planes[capture.seat to capture.plane]
        val to = victim?.let { BoardLayout.planeRest(it.team, it.plane, Board.HANGAR) }
        val from = victim?.restPosition
        val path = if (from != null && to != null) Path(listOf(from, to)) else null
        val heading = victim?.let { BoardLayout.restHeading(it.team, it.plane, Board.HANGAR) } ?: 0f
        val startHeading = victim?.restHeading ?: 0f
        return Clip(
            start = start, duration = 0.8f * factor,
            onStart = { onEvent(SceneEvent.Sound(SoundKind.CAPTURE)) },
            update = { u ->
                if (victim != null && path != null) {
                    val e = Easing.inOut(u)
                    victim.setScale(BoardLayout.PLANE_SCALE + (BoardLayout.PLANE_SCALE_PARKED - BoardLayout.PLANE_SCALE) * e)
                    val spin = if (reduced) 0f else (2 * PI * e).toFloat()
                    victim.place(path.at(e), startHeading + spin + wrap(heading - startHeading) * e, if (reduced) 0f else 0.06f * Easing.hump(u))
                }
            },
            onEnd = { victim?.let { rest(it, Board.HANGAR) } },
        )
    }

    private fun tileOf(team: Team, progress: Int): Tile? = when (progress) {
        in 0..Board.LAST_LOOP -> loopTiles[Board.absoluteIndex(team, progress)]
        in Board.LANE_FIRST until Board.FINISH -> laneTiles[team]?.get(progress - Board.LANE_FIRST)
        else -> null
    }

    private fun raise(tile: Tile, material: Material) {
        tile.entity.components[ModelComponent::class.java]?.materials?.set(0, material)
        tile.entity.transform().setPosition(Vector3(tile.position.x, tile.position.y + 0.003f, tile.position.z))
    }

    private fun restore(tile: Tile) {
        tile.entity.components[ModelComponent::class.java]?.materials?.set(0, tile.material)
        tile.entity.transform().setPosition(Vector3(tile.position.x, tile.position.y, tile.position.z))
    }

    private fun highlight(seg: MoveSegment, team: Team) {
        val r = res ?: return
        clearHighlights()
        recommendedTile?.let { restore(it) }
        recommendedTile = null
        val cells = when (seg.kind) {
            SegmentKind.WALK, SegmentKind.BOUNCE, SegmentKind.FINISH -> {
                val step = if (seg.to >= seg.from) 1 else -1
                generateSequence(seg.from) { if (it == seg.to) null else it + step }.toList()
            }
            else -> listOf(seg.from, seg.to)
        }
        for (p in cells) {
            val tile = tileOf(team, p) ?: continue
            raise(tile, r.glow)
            highlighted += tile
        }
    }

    private fun clearHighlights() {
        for (tile in highlighted) restore(tile)
        highlighted.clear()
    }

    // ---- per frame ----

    fun onFrame(deltaSeconds: Float) {
        if (!built) return
        val dt = deltaSeconds.coerceIn(0f, 0.1f)
        clock += dt
        if (diceThrow != null) stepDice(dt)
        moveTimeline?.advance(dt)
        // The active hangar's rim lifts a touch; the others settle back. (Only the rim: the parked planes
        // are not children of the hangar group.)
        for ((team, hangar) in hangars) {
            val target = if (team == activeHangar) 0.003f else 0f
            if (hangar.lifted != target) {
                hangar.lifted += (target - hangar.lifted) * minOf(1f, dt * 8f)
                if (abs(hangar.lifted - target) < 0.0001f) hangar.lifted = target
                val rimY = BoardLayout.HANGAR_HEIGHT + BoardLayout.RIM_HEIGHT / 2 + hangar.lifted
                for (rim in hangar.rims) { val p = rim.transform().position; rim.transform().setPosition(Vector3(p.x, rimY, p.z)) }
            }
        }
        // The die invites a pinch: a slow bob and its ring breathing underneath.
        val ring = dieRing
        if (ring != null && ring.enabled && diceThrow == null) {
            val bob = 0.003f * (0.5f + 0.5f * sin(clock * 5.0f))
            placeDie(dieRest, bob)
            val p = diePosition(dieRest.first)
            ring.transform().setPosition(Vector3(p.x, BoardLayout.TRAY_CENTER.y + 0.0008f, p.z))
            val k = BoardLayout.DIE_SIDE * 0.95f / (BoardLayout.CELL * 0.78f) * (1f + 0.04f * sin(clock * 1.8f))
            ring.transform().setScaleVector(Vector3(k, 1f, k))
        }
        for (r in pickRings) if (r.enabled) { val k = 1f + 0.05f * sin(clock * 2.2f); r.transform().setScaleVector(Vector3(k, 1f, k)) }
        beacon?.let { it.transform().setQuaternion(Quat(Vector3(0f, 1f, 0f), clock * 1.2f)) }
    }

    /** Which fleet plane, if any, an entity name denotes. */
    fun planeOf(name: String?): Pair<Int, Int>? {
        if (name == null || !name.startsWith("plane:")) return null
        val parts = name.split(":")
        if (parts.size != 3) return null
        val seat = parts[1].toIntOrNull() ?: return null
        val plane = parts[2].toIntOrNull() ?: return null
        return seat to plane
    }

    /** Planned and measured bounds, for the scene report. */
    fun evidence(): String {
        val half = regionHalf ?: return "no region"
        val visual = root.getVisualBounds(root.getParent(), true, true)
        return "regionHalf=(${half.x}, ${half.y}, ${half.z}) visualMin=${visual.min} visualMax=${visual.max} planes=${planes.size} built=$built"
    }

    fun dispose() {
        moveTimeline = null
        diceThrow = null
        planes.clear()
        root.destroy()
        res?.close()
        res = null
    }

    companion object {
        const val TAG = "CloudAir"
        const val DIE_NAME = "die"
        const val TRAY_NAME = "tray"

        /** Clearance between the lowest point of the tilted board and the volume floor. */
        const val FLOOR_LIFT = 0.012f

        /** Tip of the whole board toward the player, radians (28 degrees). */
        const val TILT_RAD = 0.489f

        /** Height of the fly-over arc above the mat. */
        const val FLY_LIFT = 0.09f
    }
}
