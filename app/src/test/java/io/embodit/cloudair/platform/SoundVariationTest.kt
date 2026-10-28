package io.embodit.cloudair.platform

import io.embodit.cloudair.ui.board.SoundKind
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundVariationTest {
    @Test
    fun variedSoundsStayInRangeAndNeverRepeatBackToBack() {
        for (seed in 0 until 20) {
            val v = SoundVariation(Random(seed))
            for (kind in SoundVariation.VARIED) {
                var last: SoundVariation.Play? = null
                repeat(200) {
                    val p = v.next(kind, 0.8f, variants = 3)
                    assertTrue("rate ${p.rate}", p.rate in SoundVariation.MIN_RATE..SoundVariation.MAX_RATE)
                    assertTrue("volume ${p.volume}", p.volume in 0.72f..0.88f)
                    assertTrue(p.variant in 0..2)
                    last?.let {
                        assertTrue("rates ${it.rate} -> ${p.rate}", abs(p.rate - it.rate) >= SoundVariation.MIN_STEP - 1e-6f)
                        assertNotEquals(it.variant, p.variant)
                    }
                    last = p
                }
            }
        }
    }

    @Test
    fun ratesSpreadAcrossTheRange() {
        val v = SoundVariation(Random(7))
        val rates = (0 until 500).map { v.next(SoundKind.HOP, 0.45f, variants = 3).rate }
        assertTrue(rates.min() < 0.95f && rates.max() > 1.05f)
    }

    @Test
    fun otherSoundsPlayUnchanged() {
        val v = SoundVariation(Random(1))
        for (kind in SoundKind.entries - SoundVariation.VARIED) {
            assertEquals(SoundVariation.Play(0, 1f, 0.5f), v.next(kind, 0.5f, variants = 1))
        }
    }

    @Test
    fun loudVolumeIsClamped() {
        val v = SoundVariation(Random(3))
        repeat(100) { assertTrue(v.next(SoundKind.DICE_LAND, 1f).volume <= 1f) }
    }
}
