package io.embodit.cloudair.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DiceSolverTest {
    @Test
    fun `every result settles face up inside the tray within two and a half seconds`() {
        for (seed in 1..40) for (result in 1..6) {
            val throwObj = DiceSolver.createThrow(result, seed * 7919)
            val last = throwObj.frames.last()
            assertTrue("seed $seed result $result settled", last.settled)
            val (face, alignment) = DiceSolver.topFace(last.quaternion)
            assertEquals("seed $seed", result, face)
            assertTrue(alignment > 0.999)
            assertTrue("duration ${throwObj.duration}", throwObj.duration in 0.4..2.6)
            assertTrue(abs(last.position[0]) < DiceSolver.TRAY_WIDTH / 2 && abs(last.position[2]) < DiceSolver.TRAY_DEPTH / 2)
            assertTrue(abs(last.position[1] - DiceSolver.HALF) < 0.02)
        }
    }

    @Test
    fun `the same seed gives the same motion and the symmetry is applied from the first frame`() {
        val a = DiceSolver.createThrow(3, 42)
        val b = DiceSolver.createThrow(3, 42)
        assertEquals(a.frames.size, b.frames.size)
        for (i in a.frames.indices) for (k in 0..2) assertEquals(a.frames[i].position[k], b.frames[i].position[k], 0.0)
        val c = DiceSolver.createThrow(5, 42)
        // Same physical path, different numbering: positions identical, orientations differ by a constant symmetry.
        for (i in a.frames.indices) for (k in 0..2) assertEquals(a.frames[i].position[k], c.frames[i].position[k], 0.0)
    }

    @Test
    fun `sampling interpolates between frames and clamps at the ends`() {
        val t = DiceSolver.createThrow(2, 9)
        val start = t.sample(-1.0)
        for (k in 0..2) assertEquals(t.frames.first().position[k], start.position[k], 1e-9)
        val end = t.sample(t.duration + 5)
        for (k in 0..2) assertEquals(t.frames.last().position[k], end.position[k], 1e-9)
        assertTrue(end.settled)
        val mid = t.sample(t.dt * 1.5)
        for (k in 0..2) assertEquals((t.frames[1].position[k] + t.frames[2].position[k]) / 2, mid.position[k], 1e-9)
    }

    @Test
    fun `rest poses show the requested face`() {
        for (r in 1..6) assertEquals(r, DiceSolver.topFace(DiceSolver.restPose(r).quaternion).first)
    }
}
