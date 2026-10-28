package io.embodit.cloudair.platform

import io.embodit.cloudair.ui.board.SoundKind
import kotlin.math.abs
import kotlin.random.Random

/**
 * Per-play variation for the sounds that repeat a lot (the die and the plane moves), so a run of hops
 * or a throw never sounds like the same sample twice: a random pitch (SoundPool rate) within
 * [MIN_RATE, MAX_RATE], a volume within ±[VOLUME_SPREAD], and a rotation through the variant files.
 * Every other sound plays as it is.
 */
class SoundVariation(private val random: Random = Random.Default) {
    data class Play(val variant: Int, val rate: Float, val volume: Float)

    private val lastRate = HashMap<SoundKind, Float>()
    private val lastVariant = HashMap<SoundKind, Int>()

    /** How to play [kind] at [baseVolume] this time, given it has [variants] files (at least 1). */
    fun next(kind: SoundKind, baseVolume: Float, variants: Int = 1): Play {
        if (kind !in VARIED) return Play(0, 1f, baseVolume)
        // A new rate that differs audibly from the last one of this kind: back-to-back hops never match.
        val previous = lastRate[kind]
        var rate = MIN_RATE + random.nextFloat() * (MAX_RATE - MIN_RATE)
        if (previous != null && abs(rate - previous) < MIN_STEP) {
            rate = if (previous + MIN_STEP <= MAX_RATE && (previous - MIN_STEP < MIN_RATE || random.nextBoolean())) previous + MIN_STEP else previous - MIN_STEP
        }
        lastRate[kind] = rate
        val volume = (baseVolume * (1f + (random.nextFloat() * 2f - 1f) * VOLUME_SPREAD)).coerceIn(0f, 1f)
        // Variants rotate in a random order that never repeats the last one.
        val variant = if (variants <= 1) 0 else {
            val last = lastVariant[kind]
            var v = random.nextInt(variants)
            if (v == last) v = (v + 1 + random.nextInt(variants - 1)) % variants
            v
        }
        lastVariant[kind] = variant
        return Play(variant, rate, volume)
    }

    companion object {
        val VARIED: Set<SoundKind> = setOf(SoundKind.DICE_THROW, SoundKind.DICE_LAND, SoundKind.HOP, SoundKind.JUMP, SoundKind.FLY)
        const val MIN_RATE = 0.93f
        const val MAX_RATE = 1.07f
        const val MIN_STEP = 0.015f
        const val VOLUME_SPREAD = 0.1f
    }
}
