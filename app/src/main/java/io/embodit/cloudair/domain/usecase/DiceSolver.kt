package io.embodit.cloudair.domain.usecase

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Pose of the die at one instant: position (solver units) and orientation as a quaternion (x, y, z, w). */
class DicePose(val position: DoubleArray, val quaternion: DoubleArray, val settled: Boolean)

/**
 * A finished throw: 240 Hz poses from release to rest, and the face that ends up on top.
 * Positions are in solver units (die side [DiceSolver.SIDE]); the tray floor is y = 0, +Y up.
 */
class DiceThrow(val result: Int, val frames: List<DicePose>, val dt: Double) {
    val duration: Double get() = (frames.size - 1) * dt

    fun sample(time: Double): DicePose {
        val t = time.coerceIn(0.0, duration)
        val index = if (t >= duration) frames.size - 1 else (t / dt).toInt()
        if (index >= frames.size - 1) return frames.last()
        val a = frames[index]
        val b = frames[index + 1]
        val u = (t - index * dt) / dt
        val p = DoubleArray(3) { a.position[it] + (b.position[it] - a.position[it]) * u }
        return DicePose(p, DiceSolver.slerp(a.quaternion, b.quaternion, u), false)
    }
}

/**
 * Deterministic rigid-cube dice trajectories, ported from the Cloud Air V4 prototype's solver.
 *
 * The game decides the result first. A result-independent cube is simulated (gravity, vertex and
 * rim-edge contacts against the tray floor and walls, restitution, Coulomb friction, sleeping); then
 * every frame's orientation is right-multiplied by one constant cube symmetry so the face that
 * naturally comes to rest on top is the committed result. Nothing is steered toward the result.
 */
object DiceSolver {
    const val DT = 1.0 / 240
    const val SIDE = 0.6
    const val HALF = SIDE / 2
    private const val INV_I = 6 / (SIDE * SIDE)

    /** Inner floor of the tray, solver units; walls stand at +-width/2 and +-depth/2. */
    const val TRAY_WIDTH = 2.8
    const val TRAY_DEPTH = 1.8
    const val TRAY_WALL_HEIGHT = 0.5
    const val TRAY_WALL_THICKNESS = 0.08

    /**
     * Outward normal of each face in the die model's own frame (the Blender export: pip 1 on -Z,
     * 6 on +Z, 2 on +X, 5 on -X, 3 on +Y, 4 on -Y).
     */
    val FACE_NORMALS: Map<Int, DoubleArray> = mapOf(
        1 to doubleArrayOf(0.0, 0.0, -1.0),
        2 to doubleArrayOf(1.0, 0.0, 0.0),
        3 to doubleArrayOf(0.0, 1.0, 0.0),
        4 to doubleArrayOf(0.0, -1.0, 0.0),
        5 to doubleArrayOf(-1.0, 0.0, 0.0),
        6 to doubleArrayOf(0.0, 0.0, 1.0),
    )

    private val vertices: List<DoubleArray> = buildList {
        for (x in doubleArrayOf(-HALF, HALF)) for (y in doubleArrayOf(-HALF, HALF)) for (z in doubleArrayOf(-HALF, HALF)) add(doubleArrayOf(x, y, z))
    }
    private val edges: List<Pair<Int, Int>> = buildList {
        for (a in 0 until 8) for (b in a + 1 until 8) if ((0..2).count { vertices[a][it] != vertices[b][it] } == 1) add(a to b)
    }

    private class Plane(val n: DoubleArray, val d: Double, val t1: DoubleArray, val t2: DoubleArray, val floor: Boolean)

    private val planes = listOf(
        Plane(doubleArrayOf(0.0, 1.0, 0.0), 0.0, doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0), true),
        Plane(doubleArrayOf(1.0, 0.0, 0.0), -TRAY_WIDTH / 2, doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0), false),
        Plane(doubleArrayOf(-1.0, 0.0, 0.0), -TRAY_WIDTH / 2, doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0), false),
        Plane(doubleArrayOf(0.0, 0.0, 1.0), -TRAY_DEPTH / 2, doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 1.0, 0.0), false),
        Plane(doubleArrayOf(0.0, 0.0, -1.0), -TRAY_DEPTH / 2, doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 1.0, 0.0), false),
    )
    private val rim = Plane(doubleArrayOf(0.0, 1.0, 0.0), TRAY_WALL_HEIGHT, doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0), false)

    // ---- vector helpers ----
    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    private fun length(a: DoubleArray): Double { var s = 0.0; for (v in a) s += v * v; return sqrt(s) }
    private fun normalize(a: DoubleArray): DoubleArray { val l = length(a); return DoubleArray(a.size) { a[it] / l } }

    fun multiply(a: DoubleArray, b: DoubleArray): DoubleArray = doubleArrayOf(
        a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
        a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
        a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
        a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2],
    )

    fun rotateVector(q: DoubleArray, v: DoubleArray): DoubleArray {
        val qv = doubleArrayOf(q[0], q[1], q[2])
        val t = cross(qv, v).also { for (i in 0..2) it[i] *= 2 }
        val c = cross(qv, t)
        return DoubleArray(3) { v[it] + q[3] * t[it] + c[it] }
    }

    /** The face whose normal points most upward, and how well it is aligned (1 = flat). */
    fun topFace(q: DoubleArray): Pair<Int, Double> {
        var face = 1
        var best = Double.NEGATIVE_INFINITY
        for (f in 1..6) {
            val up = rotateVector(q, FACE_NORMALS.getValue(f))[1]
            if (up > best) { best = up; face = f }
        }
        return face to best
    }

    /** The shortest rotation taking unit axis [from] onto unit axis [to]. */
    fun betweenAxes(from: DoubleArray, to: DoubleArray): DoubleArray {
        val d = dot(from, to)
        if (d > 0.99) return doubleArrayOf(0.0, 0.0, 0.0, 1.0)
        if (d < -0.99) {
            val axis = normalize(cross(from, if (abs(from[0]) < 0.5) doubleArrayOf(1.0, 0.0, 0.0) else doubleArrayOf(0.0, 1.0, 0.0)))
            return doubleArrayOf(axis[0], axis[1], axis[2], 0.0)
        }
        val c = cross(from, to)
        return normalize(doubleArrayOf(c[0], c[1], c[2], 1 + d))
    }

    fun slerp(a: DoubleArray, b: DoubleArray, t: Double): DoubleArray {
        var cosine = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3]
        var end = b
        if (cosine < 0) { cosine = -cosine; end = DoubleArray(4) { -b[it] } }
        if (cosine > 0.9995) return normalize(DoubleArray(4) { a[it] + (end[it] - a[it]) * t })
        val angle = acos(min(1.0, cosine))
        val s = sin(angle)
        return DoubleArray(4) { (a[it] * sin((1 - t) * angle) + end[it] * sin(t * angle)) / s }
    }

    private class Rng(seed: Int) {
        private var state = seed.toLong() and 0xFFFFFFFFL
        fun next(): Double {
            state = (state + 0x6D2B79F5L) and 0xFFFFFFFFL
            var t = state.toInt()
            t = (t xor (t ushr 15)) * (t or 1)
            t = t xor (t + ((t xor (t ushr 7)) * (t or 61)))
            return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
        }
    }

    private class Body(val p: DoubleArray, val v: DoubleArray, val w: DoubleArray, var q: DoubleArray)

    private fun velocityAt(b: Body, r: DoubleArray): DoubleArray { val wr = cross(b.w, r); return DoubleArray(3) { b.v[it] + wr[it] } }
    private fun effectiveMass(r: DoubleArray, n: DoubleArray): Double { val rn = cross(r, n); return 1 / (1 + INV_I * dot(rn, rn)) }
    private fun impulse(b: Body, r: DoubleArray, n: DoubleArray, amount: Double) {
        val torque = cross(r, n)
        for (i in 0..2) { b.v[i] += n[i] * amount; b.w[i] += torque[i] * amount * INV_I }
    }

    private class Contact(val plane: Plane, val r: DoubleArray, val target: Double, val mn: Double, val mt1: Double, val mt2: Double) {
        var normal = 0.0
        var friction1 = 0.0
        var friction2 = 0.0
    }

    private class Simulation(val frames: List<DicePose>, val slept: Boolean, val maxPenetration: Double)

    private fun pose(b: Body, settled: Boolean) = DicePose(b.p.copyOf(), b.q.copyOf(), settled)

    private fun isSettled(b: Body): Boolean = length(b.v) < 0.05 && length(b.w) < 0.07 && topFace(b.q).second > 0.9995

    private fun simulate(seed: Int): Simulation {
        val rng = Rng(seed)
        val axis = normalize(doubleArrayOf(rng.next() - 0.5, rng.next() - 0.5, rng.next() - 0.5))
        val angle = rng.next() * Math.PI * 2
        val b = Body(
            p = doubleArrayOf(-0.48 + rng.next() * 0.22, 1.05 + rng.next() * 0.18, -0.22 + rng.next() * 0.3),
            v = doubleArrayOf(0.6 + rng.next() * 0.35, 0.55 + rng.next() * 0.45, (rng.next() - 0.5) * 0.55),
            w = doubleArrayOf(7 + rng.next() * 6, (rng.next() - 0.5) * 12, -7 - rng.next() * 6),
            q = doubleArrayOf(axis[0] * sin(angle / 2), axis[1] * sin(angle / 2), axis[2] * sin(angle / 2), cos(angle / 2)),
        )
        val frames = ArrayList<DicePose>(700)
        frames += pose(b, false)
        var quiet = 0
        var hadContact = false
        var maxPenetration = 0.0
        for (step in 1..600) {
            b.v[1] -= 9.81 * DT
            for (i in 0..2) { b.p[i] += b.v[i] * DT; b.w[i] *= exp(-0.025 * DT) }
            val dq = multiply(doubleArrayOf(b.w[0], b.w[1], b.w[2], 0.0), b.q)
            b.q = normalize(DoubleArray(4) { b.q[it] + 0.5 * DT * dq[it] })
            val contacts = ArrayList<Contact>()
            class Candidate(val r: DoubleArray, val world: DoubleArray, val clipped: Boolean)
            val corners = vertices.map { local -> val r = rotateVector(b.q, local); Candidate(r, DoubleArray(3) { r[it] + b.p[it] }, false) }
            val candidates = ArrayList(corners)
            for ((ia, ic) in edges) {
                val va = corners[ia]; val vc = corners[ic]
                val ya = va.world[1] - TRAY_WALL_HEIGHT; val yc = vc.world[1] - TRAY_WALL_HEIGHT
                if (ya * yc >= 0) continue
                val ratio = ya / (ya - yc)
                val r = DoubleArray(3) { va.r[it] + (vc.r[it] - va.r[it]) * ratio }
                val world = DoubleArray(3) { r[it] + b.p[it] }
                world[1] = TRAY_WALL_HEIGHT
                candidates += Candidate(r, world, true)
            }
            for (c in candidates) {
                val world = c.world
                val overRim = abs(world[0]) >= TRAY_WIDTH / 2 || abs(world[2]) >= TRAY_DEPTH / 2
                val withinRim = abs(world[0]) <= TRAY_WIDTH / 2 + TRAY_WALL_THICKNESS && abs(world[2]) <= TRAY_DEPTH / 2 + TRAY_WALL_THICKNESS
                val set = if (!c.clipped && overRim && withinRim && world[1] >= TRAY_WALL_HEIGHT - 0.05) planes + rim else planes
                for (plane in set) {
                    if (c.clipped && plane.floor) continue
                    if (!plane.floor && plane !== rim && world[1] > TRAY_WALL_HEIGHT) continue
                    val distance = dot(world, plane.n) - plane.d
                    if (distance > 0.0015) continue
                    val vn = dot(velocityAt(b, c.r), plane.n)
                    maxPenetration = max(maxPenetration, -distance)
                    val bounce = if (vn < -0.7) -vn * (if (plane.floor) 0.29 else 0.2) else 0.0
                    val bias = min(1.2, 0.18 * max(0.0, -distance - 0.00035) / DT)
                    contacts += Contact(plane, c.r, max(bounce, bias), effectiveMass(c.r, plane.n), effectiveMass(c.r, plane.t1), effectiveMass(c.r, plane.t2))
                }
            }
            repeat(14) {
                for (c in contacts) {
                    val next = max(0.0, c.normal + (c.target - dot(velocityAt(b, c.r), c.plane.n)) * c.mn)
                    impulse(b, c.r, c.plane.n, next - c.normal); c.normal = next
                    val v = velocityAt(b, c.r)
                    var f1 = c.friction1 - dot(v, c.plane.t1) * c.mt1
                    var f2 = c.friction2 - dot(v, c.plane.t2) * c.mt2
                    val magnitude = sqrt(f1 * f1 + f2 * f2)
                    val limit = 0.58 * c.normal
                    if (magnitude > limit && magnitude > 0) { f1 *= limit / magnitude; f2 *= limit / magnitude }
                    impulse(b, c.r, c.plane.t1, f1 - c.friction1); impulse(b, c.r, c.plane.t2, f2 - c.friction2)
                    c.friction1 = f1; c.friction2 = f2
                }
            }
            hadContact = contacts.any { it.plane.floor }
            if (hadContact) for (i in 0..2) b.w[i] *= exp(-0.8 * DT)
            quiet = if (hadContact && isSettled(b)) quiet + 1 else 0
            if (quiet >= 30) {
                b.v.fill(0.0); b.w.fill(0.0)
                frames += pose(b, true)
                return Simulation(frames, true, maxPenetration)
            }
            frames += pose(b, false)
        }
        return Simulation(frames, false, maxPenetration)
    }

    private fun naturalThrow(seed: Int): Simulation {
        for (attempt in 0 until 9) {
            val s = if (attempt == 8) 1 else (seed + attempt * 0x9e3779b9.toInt())
            val simulation = simulate(s)
            if (simulation.slept && simulation.maxPenetration < 0.04) return simulation
        }
        throw IllegalStateException("dice did not settle")
    }

    /** The pose of a die lying flat with [result] on top. */
    fun restPose(result: Int): DicePose {
        require(result in 1..6)
        return DicePose(doubleArrayOf(0.0, HALF, 0.0), betweenAxes(FACE_NORMALS.getValue(result), FACE_NORMALS.getValue(3)), true)
    }

    /** A throw ending with [result] on top. The same [seed] always gives the same motion. */
    fun createThrow(result: Int, seed: Int): DiceThrow {
        require(result in 1..6) { "result must be a committed 1..6" }
        val natural = naturalThrow(seed)
        val landed = topFace(natural.frames.last().quaternion).first
        val symmetry = betweenAxes(FACE_NORMALS.getValue(result), FACE_NORMALS.getValue(landed))
        val frames = natural.frames.map { DicePose(it.position, normalize(multiply(it.quaternion, symmetry)), it.settled) }
        return DiceThrow(result, frames, DT)
    }
}
