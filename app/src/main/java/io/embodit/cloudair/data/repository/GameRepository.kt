package io.embodit.cloudair.data.repository

/** Where the saved game lives. One slot; a write replaces the previous save only when it succeeds. */
interface GameRepository {
    /** The saved text, or null when there is none. */
    fun load(): String?

    /** Returns false when the save could not be written; the previous save is kept. */
    fun save(text: String): Boolean

    fun clear()
}

class FakeGameRepository(private var text: String? = null, var failWrites: Boolean = false) : GameRepository {
    var writes = 0
        private set

    override fun load(): String? = text

    override fun save(text: String): Boolean {
        writes++
        if (failWrites) return false
        this.text = text
        return true
    }

    override fun clear() { text = null }
}
