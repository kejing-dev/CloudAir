package io.embodit.cloudair.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.embodit.cloudair.domain.model.ActionKind
import io.embodit.cloudair.domain.model.DecisionMode
import io.embodit.cloudair.domain.model.GameConfig
import io.embodit.cloudair.domain.model.Language
import io.embodit.cloudair.domain.model.MotionSpeed
import io.embodit.cloudair.domain.model.Seat
import io.embodit.cloudair.domain.model.SeatKind
import io.embodit.cloudair.domain.model.Team
import io.embodit.cloudair.ui.board.scene.BoardSceneHolder

/**
 * Debug builds only. Drives the game from adb so every state can be reached without a controller:
 *
 *     adb shell am broadcast -a io.embodit.cloudair.DEBUG -p io.embodit.cloudair --es cmd roll
 *
 * Commands (`--es a <argument>`):
 *   new H,E,S      seats by kind (H human, E easy AI, S smart AI; 2–4 entries, teams in order)
 *   roll           roll for the current human seat
 *   dice 6,3       values of the next rolls
 *   kind launch|advance · plane N · back
 *   set S:P:V,...  plane progress (seat:plane:value, -1 hangar, 57 home) of the running game
 *   auto on|off    play human seats automatically (soak test)
 *   pause · resume · leave · continue · abandon
 *   mode recommend|manual|auto · speed gentle|standard|quick · lang system|zh|en · reduced on|off · sound on|off
 *   dump           log the session · evidence  log the scene bounds
 */
class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = CloudAirGraph.from(context)
        val flow = graph.flow
        val a = intent.getStringExtra("a").orEmpty().trim()
        val on = a == "on" || a == "true" || a == "1"
        val cmd = intent.getStringExtra("cmd")
        when (cmd) {
            "new" -> {
                val kinds = a.ifEmpty { "H,E" }.split(",").map {
                    when (it.trim().uppercase()) { "H" -> SeatKind.HUMAN; "S" -> SeatKind.AI_SMART; else -> SeatKind.AI_EASY }
                }
                val teams = GameConfig.teamsFor(kinds.size)
                val config = GameConfig(kinds.mapIndexed { i, k -> Seat(teams[i], k) })
                if (config.isValid) flow.newGame(config, graph.settings.settings.value.decisionMode) else Log.w(TAG, "invalid config $a")
            }
            "roll" -> Log.i(TAG, "roll accepted=${flow.requestRoll()}")
            "dice" -> { flow.forcedDice.clear(); a.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach { flow.forcedDice.addLast(it) } }
            "kind" -> flow.chooseKind(if (a.startsWith("l")) ActionKind.LAUNCH else ActionKind.ADVANCE)
            "plane" -> a.toIntOrNull()?.let { flow.choosePlane(it) }
            "back" -> flow.backToChoice()
            "set" -> {
                val g = flow.game ?: return
                val planes = g.seats.map { it.planes.toMutableList() }
                a.split(",").forEach { triple ->
                    val p = triple.split(":").mapNotNull { it.trim().toIntOrNull() }
                    if (p.size == 3 && p[0] in planes.indices && p[1] in 0..3) planes[p[0]][p[1]] = p[2].coerceIn(-1, 57)
                }
                graph.sessionStore.update { s -> s.copy(game = s.game?.copy(seats = g.seats.mapIndexed { i, seat -> seat.copy(planes = planes[i]) })) }
            }
            "auto" -> flow.autoPilot = on
            "pause" -> flow.pause()
            "resume" -> flow.resume()
            "leave" -> flow.leave()
            "continue" -> Log.i(TAG, "continue restored=${flow.continueSaved()} plans=${graph.sessionStore.state.value.legalPlans.size}")
            "abandon" -> flow.abandon()
            "mode" -> {
                val mode = when (a) { "manual" -> DecisionMode.MANUAL; "auto" -> DecisionMode.FULL_AUTO; else -> DecisionMode.RECOMMEND }
                graph.settings.update { it.copy(decisionMode = mode) }
                flow.setDecisionMode(mode)
            }
            "speed" -> graph.settings.update { it.copy(speed = when (a) { "gentle" -> MotionSpeed.GENTLE; "quick" -> MotionSpeed.QUICK; else -> MotionSpeed.STANDARD }) }
            "lang" -> graph.settings.update { it.copy(language = when (a) { "zh" -> Language.ZH; "en" -> Language.EN; else -> Language.SYSTEM }) }
            "reduced" -> graph.settings.update { it.copy(reducedMotion = on) }
            "sound" -> graph.settings.update { it.copy(sound = on) }
            "dump" -> Log.i(TAG, "DUMP ${graph.sessionStore.state.value}")
            "evidence" -> Log.i(TAG, "EVIDENCE ${BoardSceneHolder.scene?.evidence() ?: "no scene"}")
            else -> Log.w(TAG, "unknown debug command: $cmd")
        }
        Log.i(TAG, "CMD $cmd a=$a phase=${flow.game?.phase} turn=${flow.game?.turn} dice=${flow.game?.dice}")
    }

    private companion object { const val TAG = CloudAirGraph.TAG }
}
