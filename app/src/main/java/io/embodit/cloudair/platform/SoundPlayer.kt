package io.embodit.cloudair.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import io.embodit.cloudair.ui.board.SoundKind

/**
 * Short sound effects from `assets/sounds/<kind>.wav` (plus `<kind>_2.wav`, `<kind>_3.wav` variants where
 * present), played through a SoundPool. The die and plane-move sounds get a random pitch, volume and
 * variant each time ([SoundVariation]). A missing file is simply silent, and [enabled] mutes everything
 * without unloading.
 */
class SoundPlayer(private val context: Context) {
    @Volatile var enabled: Boolean = true

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val ids = HashMap<SoundKind, List<Int>>()
    private val loaded = HashSet<Int>()
    private val variation = SoundVariation()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) synchronized(loaded) { loaded += sampleId } }
        for (kind in SoundKind.entries) {
            val base = kind.name.lowercase()
            val found = ArrayList<Int>()
            for (name in listOf("$base.wav", "${base}_2.wav", "${base}_3.wav")) {
                runCatching {
                    context.assets.openFd("sounds/$name").use { fd -> found += pool.load(fd, 1) }
                }.onFailure { if (name == "$base.wav") Log.i(CloudAirGraph.TAG, "no sound asset sounds/$name") }
            }
            if (found.isNotEmpty()) ids[kind] = found
        }
    }

    fun play(kind: SoundKind) {
        if (!enabled) return
        val variants = ids[kind] ?: return
        val base = when (kind) {
            SoundKind.HOP -> 0.45f
            SoundKind.TAP -> 0.5f
            else -> 0.8f
        }
        val p = synchronized(variation) { variation.next(kind, base, variants.size) }
        val id = variants[p.variant]
        if (synchronized(loaded) { id !in loaded }) return
        pool.play(id, p.volume, p.volume, 1, 0, p.rate)
    }
}
