package io.embodit.cloudair.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import io.embodit.cloudair.ui.board.SoundKind

/**
 * Short sound effects from `assets/sounds/<kind>.wav`, played through a SoundPool. A missing file
 * is simply silent, and [enabled] mutes everything without unloading.
 */
class SoundPlayer(private val context: Context) {
    @Volatile var enabled: Boolean = true

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val ids = HashMap<SoundKind, Int>()
    private val loaded = HashSet<Int>()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loaded += sampleId }
        for (kind in SoundKind.entries) {
            val name = "sounds/${kind.name.lowercase()}.wav"
            runCatching {
                context.assets.openFd(name).use { fd -> ids[kind] = pool.load(fd, 1) }
            }.onFailure { Log.i(CloudAirGraph.TAG, "no sound asset $name") }
        }
    }

    fun play(kind: SoundKind) {
        if (!enabled) return
        val id = ids[kind] ?: return
        if (id !in loaded) return
        val volume = when (kind) {
            SoundKind.HOP -> 0.45f
            SoundKind.TAP -> 0.5f
            else -> 0.8f
        }
        pool.play(id, volume, volume, 1, 0, 1f)
    }
}
