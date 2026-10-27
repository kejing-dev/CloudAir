package io.embodit.cloudair.ui.board.scene

import kotlin.math.PI
import kotlin.math.sin

internal object Easing {
    fun inOut(t: Float): Float = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f
    fun out(t: Float): Float = 1f - (1f - t).let { it * it * it }
    fun hump(t: Float): Float = sin((PI * t).toFloat())
}

/**
 * A smooth path through a few points on the mat: Catmull-Rom between the points, sampled into an
 * arc-length table so that `at(u)` moves at a steady pace. Two points make a straight line.
 */
internal class Path(private val points: List<P3>) {
    private val samples: List<P3>
    private val cumulative: FloatArray
    val length: Float

    init {
        require(points.isNotEmpty())
        val list = ArrayList<P3>()
        if (points.size < 3) {
            list += points.first()
            list += points.last()
        } else {
            val n = points.size
            for (i in 0 until n - 1) {
                val p0 = points[maxOf(0, i - 1)]
                val p1 = points[i]
                val p2 = points[i + 1]
                val p3 = points[minOf(n - 1, i + 2)]
                for (s in 0 until SEGMENT_SAMPLES) list += catmull(p0, p1, p2, p3, s / SEGMENT_SAMPLES.toFloat())
            }
            list += points.last()
        }
        samples = list
        cumulative = FloatArray(samples.size)
        var acc = 0f
        for (i in 1 until samples.size) { acc += (samples[i] - samples[i - 1]).length(); cumulative[i] = acc }
        length = acc
    }

    fun at(u: Float): P3 {
        if (samples.size < 2 || length <= 0f) return samples.first()
        val target = u.coerceIn(0f, 1f) * length
        var lo = 0
        var hi = samples.size - 1
        while (hi - lo > 1) { val mid = (lo + hi) / 2; if (cumulative[mid] <= target) lo = mid else hi = mid }
        val span = cumulative[hi] - cumulative[lo]
        val t = if (span <= 0f) 0f else (target - cumulative[lo]) / span
        return samples[lo].lerp(samples[hi], t)
    }

    /** Direction of travel at [u] (unit length in the mat plane when possible). */
    fun tangent(u: Float): P3 {
        val a = at((u - 0.02f).coerceIn(0f, 1f))
        val b = at((u + 0.02f).coerceIn(0f, 1f))
        return b - a
    }

    val start: P3 get() = points.first()
    val end: P3 get() = points.last()

    private fun catmull(p0: P3, p1: P3, p2: P3, p3: P3, t: Float): P3 {
        val t2 = t * t
        val t3 = t2 * t
        fun c(a: Float, b: Float, c: Float, d: Float) = 0.5f * ((2 * b) + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3)
        return P3(c(p0.x, p1.x, p2.x, p3.x), c(p0.y, p1.y, p2.y, p3.y), c(p0.z, p1.z, p2.z, p3.z))
    }

    private companion object { const val SEGMENT_SAMPLES = 12 }
}

/** One animated stretch on the timeline. [update] receives local progress 0..1. */
internal class Clip(
    val start: Float,
    val duration: Float,
    val onStart: () -> Unit = {},
    val update: (Float) -> Unit,
    val onEnd: () -> Unit = {},
) {
    var started = false
    var ended = false
    val end: Float get() = start + duration
}

/** Runs clips against a clock in seconds; clips may overlap. [onComplete] fires once after the last one. */
internal class Timeline(private val clips: List<Clip>, private val tail: Float = 0f, private val onComplete: () -> Unit) {
    private var time = 0f
    private var completed = false
    val total: Float = (clips.maxOfOrNull { it.end } ?: 0f) + tail

    fun advance(dt: Float): Boolean {
        if (completed) return true
        time += dt
        for (clip in clips) {
            if (clip.ended) continue
            if (time < clip.start) continue
            if (!clip.started) { clip.started = true; clip.onStart() }
            val u = if (clip.duration <= 0f) 1f else ((time - clip.start) / clip.duration).coerceIn(0f, 1f)
            clip.update(u)
            if (time >= clip.end) { clip.ended = true; clip.onEnd() }
        }
        if (time >= total) { completed = true; onComplete() }
        return completed
    }

    /** Jumps to the end: every clip lands in its final state. */
    fun finish() {
        if (completed) return
        for (clip in clips) {
            if (!clip.started) { clip.started = true; clip.onStart() }
            clip.update(1f)
            if (!clip.ended) { clip.ended = true; clip.onEnd() }
        }
        completed = true
        onComplete()
    }
}
