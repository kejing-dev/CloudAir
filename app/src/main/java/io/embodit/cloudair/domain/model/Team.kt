package io.embodit.cloudair.domain.model

/**
 * The four fleets, in the order they sit around the loop (counter-clockwise seen from above):
 * mint in the far-left corner, sky near-left, apricot near-right, lavender far-right.
 * The ordinal is also the fleet's quarter of the 52-cell loop (see [Board]).
 */
enum class Team { MINT, SKY, APRICOT, LAVENDER }

enum class SeatKind { HUMAN, AI_EASY, AI_SMART }

/** One seat at the table: a fleet, who flies it, and where its four planes are (see [Board] for progress values). */
data class Seat(val team: Team, val kind: SeatKind, val planes: List<Int> = List(4) { Board.HANGAR }) {
    val homeCount: Int get() = planes.count { it == Board.FINISH }
    val isAi: Boolean get() = kind != SeatKind.HUMAN
}

/** Which seats take part in a new game. 2 seats sit opposite each other, 3 leave one corner empty. */
data class GameConfig(val seats: List<Seat>) {
    val isValid: Boolean
        get() = seats.size in 2..4 && seats.any { it.kind == SeatKind.HUMAN } && seats.map { it.team }.distinct().size == seats.size

    companion object {
        fun teamsFor(count: Int): List<Team> = when (count) {
            2 -> listOf(Team.MINT, Team.APRICOT)
            3 -> listOf(Team.MINT, Team.SKY, Team.APRICOT)
            else -> Team.entries
        }

        fun default(count: Int = 2): GameConfig =
            GameConfig(teamsFor(count).mapIndexed { i, team -> Seat(team, if (i == 0) SeatKind.HUMAN else SeatKind.AI_EASY) })
    }
}

enum class Language { SYSTEM, ZH, EN }

enum class MotionSpeed(val factor: Float) { GENTLE(1.25f), STANDARD(1f), QUICK(0.65f) }

/** How a human's roll turns into a move: recommended automatically, chosen by hand, or fully automatic (no six choice). */
enum class DecisionMode { RECOMMEND, MANUAL, FULL_AUTO }

data class Settings(
    val language: Language = Language.SYSTEM,
    val sound: Boolean = true,
    val reducedMotion: Boolean = false,
    val speed: MotionSpeed = MotionSpeed.STANDARD,
    val decisionMode: DecisionMode = DecisionMode.RECOMMEND,
)
