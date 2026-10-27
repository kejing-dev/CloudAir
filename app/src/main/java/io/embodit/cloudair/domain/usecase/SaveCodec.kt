package io.embodit.cloudair.domain.usecase

import io.embodit.cloudair.domain.model.ActionKind
import io.embodit.cloudair.domain.model.Board
import io.embodit.cloudair.domain.model.DecisionMode
import io.embodit.cloudair.domain.model.EventType
import io.embodit.cloudair.domain.model.GameSnapshot
import io.embodit.cloudair.domain.model.LastEvent
import io.embodit.cloudair.domain.model.Phase
import io.embodit.cloudair.domain.model.Seat
import io.embodit.cloudair.domain.model.SeatKind
import io.embodit.cloudair.domain.model.Team

/**
 * A small line-based format for the saved game (`key=value` per line), so no JSON library is needed
 * on the JVM test path. [decode] validates every field and refuses anything inconsistent.
 */
class SaveCodec {
    class InvalidSave(message: String) : Exception(message)

    fun encode(game: GameSnapshot): String = buildString {
        appendLine("version=$VERSION")
        appendLine("seats=${game.seats.size}")
        game.seats.forEachIndexed { i, seat -> appendLine("seat$i=${seat.team.name},${seat.kind.name},${seat.planes.joinToString(",")}") }
        appendLine("turn=${game.turn}")
        appendLine("round=${game.round}")
        appendLine("dice=${game.dice ?: ""}")
        appendLine("phase=${game.phase.name}")
        appendLine("selectedKind=${game.selectedKind?.name ?: ""}")
        appendLine("winner=${game.winner ?: ""}")
        appendLine("decisionMode=${game.decisionMode.name}")
        appendLine("pendingRoll=${game.pendingRoll ?: ""}")
        game.last?.let { e ->
            appendLine("last=${e.type.name},${e.team.name},${e.plane},${e.dice},${e.to},${e.captures},${e.flew},${e.jumped},${e.finished},${e.bounced}")
        }
    }

    fun decode(text: String): GameSnapshot {
        val map = HashMap<String, String>()
        text.lineSequence().forEach { line ->
            val i = line.indexOf('=')
            if (i > 0) map[line.substring(0, i).trim()] = line.substring(i + 1).trim()
        }
        fun need(key: String) = map[key] ?: throw InvalidSave("missing $key")
        if (need("version").toIntOrNull() != VERSION) throw InvalidSave("unsupported version")
        val count = need("seats").toIntOrNull() ?: throw InvalidSave("bad seats")
        if (count !in 2..4) throw InvalidSave("bad seat count")
        val seats = (0 until count).map { i ->
            val parts = need("seat$i").split(",")
            if (parts.size != 6) throw InvalidSave("bad seat $i")
            val team = runCatching { Team.valueOf(parts[0]) }.getOrElse { throw InvalidSave("bad team") }
            val kind = runCatching { SeatKind.valueOf(parts[1]) }.getOrElse { throw InvalidSave("bad kind") }
            val planes = parts.drop(2).map { it.toIntOrNull() ?: throw InvalidSave("bad plane") }
            if (planes.any { it < Board.HANGAR || it > Board.FINISH }) throw InvalidSave("plane out of range")
            Seat(team, kind, planes)
        }
        if (seats.map { it.team }.distinct().size != seats.size) throw InvalidSave("duplicate team")
        if (seats.none { it.kind == SeatKind.HUMAN }) throw InvalidSave("no human")
        val turn = need("turn").toIntOrNull()?.takeIf { it in seats.indices } ?: throw InvalidSave("bad turn")
        val round = need("round").toIntOrNull()?.takeIf { it >= 1 } ?: throw InvalidSave("bad round")
        val dice = need("dice").let { if (it.isEmpty()) null else it.toIntOrNull()?.takeIf { d -> d in 1..6 } ?: throw InvalidSave("bad dice") }
        val phase = runCatching { Phase.valueOf(need("phase")) }.getOrElse { throw InvalidSave("bad phase") }
        if (phase == Phase.ROLLING || phase == Phase.MOVING) throw InvalidSave("unstable phase")
        val selectedKind = need("selectedKind").let { if (it.isEmpty()) null else runCatching { ActionKind.valueOf(it) }.getOrElse { throw InvalidSave("bad kind") } }
        val winner = need("winner").let { if (it.isEmpty()) null else it.toIntOrNull()?.takeIf { w -> w in seats.indices } ?: throw InvalidSave("bad winner") }
        val mode = runCatching { DecisionMode.valueOf(need("decisionMode")) }.getOrElse { throw InvalidSave("bad mode") }
        val pendingRoll = map["pendingRoll"].orEmpty().let { if (it.isEmpty()) null else it.toIntOrNull()?.takeIf { d -> d in 1..6 } ?: throw InvalidSave("bad pending roll") }
        if (pendingRoll != null && phase != Phase.READY) throw InvalidSave("pending roll outside READY")
        val last = map["last"]?.let { raw ->
            val p = raw.split(",")
            if (p.size != 10) throw InvalidSave("bad event")
            LastEvent(
                type = runCatching { EventType.valueOf(p[0]) }.getOrElse { throw InvalidSave("bad event") },
                team = runCatching { Team.valueOf(p[1]) }.getOrElse { throw InvalidSave("bad event") },
                plane = p[2].toIntOrNull() ?: throw InvalidSave("bad event"),
                dice = p[3].toIntOrNull() ?: throw InvalidSave("bad event"),
                to = p[4].toIntOrNull() ?: throw InvalidSave("bad event"),
                captures = p[5].toIntOrNull() ?: throw InvalidSave("bad event"),
                flew = p[6].toBoolean(), jumped = p[7].toBoolean(), finished = p[8].toBoolean(), bounced = p[9].toBoolean(),
            )
        }
        val finishedSeats = seats.count { s -> s.planes.all { it == Board.FINISH } }
        if (phase == Phase.ENDED) {
            if (winner == null || !seats[winner].planes.all { it == Board.FINISH }) throw InvalidSave("winner without four planes home")
        } else if (winner != null || finishedSeats > 0) throw InvalidSave("finished fleet in a running game")
        if ((phase == Phase.DECISION || phase == Phase.SELECT || phase == Phase.NO_MOVES) && dice == null) throw InvalidSave("phase needs a dice")
        return GameSnapshot(seats, turn, round, dice, phase, selectedKind, winner, mode, last, pendingRoll)
    }

    companion object {
        const val VERSION = 1
    }
}
