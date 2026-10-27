package io.embodit.cloudair.domain.usecase

import io.embodit.cloudair.data.repository.GameRepository
import io.embodit.cloudair.data.session.GameSessionStore
import io.embodit.cloudair.domain.model.ActionKind
import io.embodit.cloudair.domain.model.DecisionMode
import io.embodit.cloudair.domain.model.DiceThrowSpec
import io.embodit.cloudair.domain.model.GameConfig
import io.embodit.cloudair.domain.model.GameSession
import io.embodit.cloudair.domain.model.GameSnapshot
import io.embodit.cloudair.domain.model.MovePlan
import io.embodit.cloudair.domain.model.MoveSpec
import io.embodit.cloudair.domain.model.Phase
import io.embodit.cloudair.domain.model.SeatKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Runs a game: whose turn it is, when the AI rolls, what a roll turns into, and when the board's
 * animations hand control back. Every rule decision is made here before the board moves anything;
 * the board only reports that it has finished playing a dice throw or a move.
 *
 * All calls happen on the main thread (the one the [scope] dispatches to).
 */
class GameFlowUseCase(
    private val store: GameSessionStore,
    private val repository: GameRepository,
    private val scope: CoroutineScope,
    private val rules: RulesEngine = RulesEngine(),
    private val codec: SaveCodec = SaveCodec(),
    private val random: Random = Random.Default,
    private val timing: Timing = Timing(),
    private val log: (String) -> Unit = {},
) {
    /** Delays in milliseconds. Watchdogs finish an animation the board never reported. */
    data class Timing(
        val aiThinkMs: Long = 700,
        val passMs: Long = 1600,
        val diceWatchdogMs: Long = 5000,
        val moveWatchdogMs: Long = 9000,
    )

    private var nextId = 0
    private var scheduled: Job? = null
    private var watchdog: Job? = null

    /** A throw that settled while paused: resolved on resume. */
    private var pendingSettle: Int? = null

    /** A roll committed before the game was saved: replayed by the next tick. */
    private var resumeRoll: Int? = null

    /** Test hooks (debug builds): the next roll's value, and humans played like the recommending AI. */
    var forcedDice: ArrayDeque<Int> = ArrayDeque()
    var autoPilot: Boolean = false
        set(value) { field = value; tick() }

    val session: GameSession get() = store.state.value
    val game: GameSnapshot? get() = session.game

    fun hasSavedGame(): Boolean = runCatching { repository.load()?.let { codec.decode(it) } }.getOrNull() != null

    /** The saved game, if it can be read; null when there is none or it is damaged. */
    fun peekSavedGame(): GameSnapshot? = runCatching { repository.load()?.let { codec.decode(it) } }.getOrNull()

    /** True when a save exists but cannot be read. */
    fun hasBrokenSave(): Boolean = repository.load() != null && peekSavedGame() == null

    fun newGame(config: GameConfig, decisionMode: DecisionMode) {
        require(config.isValid)
        cancelTimers()
        val snapshot = GameSnapshot(seats = config.seats, decisionMode = decisionMode)
        store.update { GameSession(game = snapshot, gameId = ++nextId) }
        log("GAME new seats=${config.seats.joinToString { "${it.team}:${it.kind}" }} mode=$decisionMode")
        persist()
        tick()
    }

    /** Continues the saved game. Returns false when there is none or it cannot be read. */
    fun continueSaved(): Boolean {
        val snapshot = peekSavedGame() ?: return false
        cancelTimers()
        pendingSettle = null
        val game = snapshot.copy(pendingRoll = null)
        store.update { GameSession(game = game, gameId = ++nextId, legalPlans = restoredPlans(game)) }
        resumeRoll = snapshot.pendingRoll
        log("GAME restored phase=${snapshot.phase} turn=${snapshot.turn} round=${snapshot.round} pendingRoll=${snapshot.pendingRoll}")
        tick()
        return true
    }

    /** The legal moves a restored choice is waiting on. */
    private fun restoredPlans(g: GameSnapshot): List<MovePlan> {
        val dice = g.dice ?: return emptyList()
        return when (g.phase) {
            Phase.DECISION -> rules.legalPlans(g, dice)
            Phase.SELECT -> rules.legalPlans(g, dice).filter { g.selectedKind == null || it.kind == g.selectedKind }
            else -> emptyList()
        }
    }

    /** Finishes a throw or move that is still in flight, so the state is stable right now. */
    private fun resolveNow() {
        if (game?.phase == Phase.ROLLING) session.dice?.let { diceSettled(it.id, force = true) }
        if (game?.phase == Phase.MOVING) session.move?.let { moveFinished(it.id) }
    }

    /** Leaves the current game (it stays saved) and returns to no game. A roll or move in flight is resolved first. */
    fun leave() {
        resolveNow()
        cancelTimers()
        pendingSettle = null
        resumeRoll = null
        store.update { GameSession() }
    }

    fun abandon() {
        cancelTimers()
        pendingSettle = null
        resumeRoll = null
        repository.clear()
        store.update { GameSession() }
    }

    fun pause() {
        if (session.game == null || session.paused) return
        cancelTimers()
        store.update { it.copy(paused = true) }
        log("GAME paused")
    }

    fun resume() {
        if (session.game == null || !session.paused) return
        store.update { it.copy(paused = false) }
        log("GAME resumed")
        pendingSettle?.let { pendingSettle = null; diceSettled(it) }
        tick()
    }

    /** Takes effect from the next roll. */
    fun setDecisionMode(mode: DecisionMode) {
        val g = game ?: return
        if (g.decisionMode == mode) return
        store.update { s -> s.copy(game = s.game?.copy(decisionMode = mode)) }
        persist()
    }

    /** A human asks for a roll (the die on the board, or the button). Ignored unless it is their turn. */
    fun requestRoll(): Boolean {
        val g = game ?: return false
        if (session.paused || g.phase != Phase.READY || g.current.kind != SeatKind.HUMAN) return false
        roll()
        return true
    }

    private fun roll() {
        val g = game ?: return
        val result = resumeRoll ?: forcedDice.removeFirstOrNull()?.coerceIn(1, 6) ?: random.nextInt(1, 7)
        resumeRoll = null
        val spec = DiceThrowSpec(++nextId, result, random.nextInt())
        store.update { s -> s.copy(game = g.copy(phase = Phase.ROLLING, selectedKind = null), dice = spec, legalPlans = emptyList()) }
        // The roll is committed now: a save with the pending value means a kill mid-throw replays it.
        repository.save(codec.encode(g.copy(phase = Phase.READY, selectedKind = null, pendingRoll = result)))
        log("DICE roll id=${spec.id} seat=${g.turn} team=${g.current.team} result=$result")
        watchdog = scope.launch {
            delay(timing.diceWatchdogMs)
            if (session.dice?.id == spec.id && game?.phase == Phase.ROLLING) { log("DICE watchdog id=${spec.id}"); diceSettled(spec.id) }
        }
    }

    /** The board finished playing the throw [id]. While paused the result waits for resume unless [force]. */
    fun diceSettled(id: Int, force: Boolean = false) {
        val spec = session.dice ?: return
        val g = game ?: return
        if (spec.id != id || g.phase != Phase.ROLLING) return
        watchdog?.cancel()
        if (session.paused && !force) { pendingSettle = id; log("DICE settled id=$id deferred=paused"); return }
        val dice = spec.result
        val plans = rules.legalPlans(g, dice)
        log("DICE settled id=$id result=$dice legal=${plans.size}")
        if (plans.isEmpty()) {
            store.update { s -> s.copy(game = g.copy(phase = Phase.NO_MOVES, dice = dice), legalPlans = emptyList()) }
            persist()
            tick()
            return
        }
        val human = g.current.kind == SeatKind.HUMAN && !autoPilot
        val mode = g.decisionMode
        if (human && mode != DecisionMode.FULL_AUTO && dice == 6 && plans.any { it.kind == ActionKind.LAUNCH } && plans.any { it.kind == ActionKind.ADVANCE }) {
            store.update { s -> s.copy(game = g.copy(phase = Phase.DECISION, dice = dice), legalPlans = plans) }
            persist()
            return
        }
        if (human && mode == DecisionMode.MANUAL && plans.size > 1) {
            store.update { s -> s.copy(game = g.copy(phase = Phase.SELECT, dice = dice), legalPlans = plans) }
            persist()
            return
        }
        val picked = when (g.current.kind) {
            SeatKind.AI_EASY -> rules.easyPick(plans, random)
            SeatKind.AI_SMART -> rules.smartPick(g, plans)
            SeatKind.HUMAN -> rules.recommend(plans)
        } ?: return
        beginMove(g.copy(dice = dice), picked)
    }

    /** The human chose launch or advance on a six. */
    fun chooseKind(kind: ActionKind): Boolean {
        val g = game ?: return false
        if (g.phase != Phase.DECISION) return false
        val plans = session.legalPlans.filter { it.kind == kind }
        if (plans.isEmpty()) return false
        if (g.decisionMode == DecisionMode.MANUAL && plans.size > 1) {
            store.update { s -> s.copy(game = g.copy(phase = Phase.SELECT, selectedKind = kind), legalPlans = plans) }
            persist()
            return true
        }
        beginMove(g.copy(selectedKind = kind), rules.recommend(plans) ?: return false)
        return true
    }

    /** The human picked a plane in manual mode. */
    fun choosePlane(plane: Int): Boolean {
        val g = game ?: return false
        if (g.phase != Phase.SELECT) return false
        val plan = session.legalPlans.firstOrNull { it.plane == plane } ?: return false
        beginMove(g, plan)
        return true
    }

    /** Back from plane selection to the six choice, only when that choice was made. */
    fun backToChoice(): Boolean {
        val g = game ?: return false
        if (g.phase != Phase.SELECT || g.selectedKind == null) return false
        val dice = g.dice ?: return false
        store.update { s -> s.copy(game = g.copy(phase = Phase.DECISION, selectedKind = null), legalPlans = rules.legalPlans(g, dice)) }
        persist()
        return true
    }

    private fun beginMove(g: GameSnapshot, plan: MovePlan) {
        val spec = MoveSpec(++nextId, plan)
        store.update { s -> s.copy(game = g.copy(phase = Phase.MOVING), move = spec, legalPlans = emptyList()) }
        log("MOVE begin id=${spec.id} seat=${plan.seat} plane=${plan.plane} ${plan.kind} dice=${plan.dice} ${plan.from}->${plan.to} segs=${plan.segments.joinToString { "${it.kind}:${it.from}>${it.to}" }} captures=${plan.captures.size}")
        watchdog = scope.launch {
            delay(timing.moveWatchdogMs)
            if (session.move?.id == spec.id && game?.phase == Phase.MOVING) { log("MOVE watchdog id=${spec.id}"); moveFinished(spec.id) }
        }
    }

    /** The board finished playing the move [id]. */
    fun moveFinished(id: Int) {
        val spec = session.move ?: return
        val g = game ?: return
        if (spec.id != id || g.phase != Phase.MOVING) return
        watchdog?.cancel()
        val next = rules.apply(g, spec.plan)
        store.update { s -> s.copy(game = next, move = null, dice = null) }
        log("MOVE done id=$id phase=${next.phase} turn=${next.turn} round=${next.round} winner=${next.winner}")
        persist()
        tick()
    }

    /** Runs whatever the current stable phase asks for, unless paused. */
    fun tick() {
        scheduled?.cancel()
        val g = game ?: return
        if (session.paused) return
        when (g.phase) {
            Phase.READY -> if (resumeRoll != null) roll() else if (g.current.kind != SeatKind.HUMAN || autoPilot) scheduled = scope.launch { delay(timing.aiThinkMs); if (!session.paused && game?.phase == Phase.READY) roll() }
            Phase.DECISION -> if (autoPilot) scheduled = scope.launch { delay(timing.aiThinkMs); if (!session.paused && game?.phase == Phase.DECISION) chooseKind(if (session.legalPlans.any { it.kind == ActionKind.ADVANCE }) ActionKind.ADVANCE else ActionKind.LAUNCH) }
            Phase.SELECT -> if (autoPilot) scheduled = scope.launch { delay(timing.aiThinkMs); if (!session.paused && game?.phase == Phase.SELECT) rules.recommend(session.legalPlans)?.let { choosePlane(it.plane) } }
            Phase.NO_MOVES -> scheduled = scope.launch {
                delay(timing.passMs)
                val now = game ?: return@launch
                if (session.paused || now.phase != Phase.NO_MOVES) return@launch
                val dice = now.dice ?: return@launch
                store.update { s -> s.copy(game = rules.pass(now, dice), dice = null) }
                log("PASS seat=${now.turn}")
                persist()
                tick()
            }
            else -> Unit
        }
    }

    private fun cancelTimers() {
        scheduled?.cancel(); scheduled = null
        watchdog?.cancel(); watchdog = null
    }

    private fun persist() {
        val g = game ?: return
        if (!g.isStable) return
        val ok = repository.save(codec.encode(g))
        if (!ok) log("SAVE failed")
        store.update { it.copy(saveFailed = !ok) }
    }
}
