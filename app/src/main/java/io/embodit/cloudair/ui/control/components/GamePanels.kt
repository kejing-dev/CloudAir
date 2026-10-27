package io.embodit.cloudair.ui.control.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import io.embodit.cloudair.R
import io.embodit.cloudair.domain.model.ActionKind
import io.embodit.cloudair.domain.model.Board
import io.embodit.cloudair.domain.model.EventType
import io.embodit.cloudair.domain.model.LastEvent
import io.embodit.cloudair.domain.model.MovePlan
import io.embodit.cloudair.domain.model.Phase
import io.embodit.cloudair.domain.model.SeatKind
import io.embodit.cloudair.ui.control.ControlEvent
import io.embodit.cloudair.ui.control.GameView
import io.embodit.cloudair.ui.theme.CloudAirColors

/** The in-game page: whose turn, the die, the one main action, and the fleets' progress. */
@Composable
internal fun TurnPanel(game: GameView, onEvent: (ControlEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TurnPill(game)
            Box(modifier = Modifier.weight(1f))
            Text(text = stringResource(R.string.turn_round, game.round), style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelTertiary)
            HGap(12.dp)
            SecondaryButton(text = stringResource(R.string.common_menu), onClick = { onEvent(ControlEvent.OpenMenu) })
        }
        Gap(14.dp)
        // The middle takes whatever height is left and scrolls (the plane list), so the strip never overlaps.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (game.phase) {
                Phase.DECISION -> SixChoice(game, onEvent)
                Phase.SELECT -> PlanePick(game, onEvent)
                else -> RollArea(game, onEvent)
            }
        }
        Gap(8.dp)
        FleetStrip(game)
        if (game.saveFailed) {
            Gap(6.dp)
            Text(text = stringResource(R.string.turn_unsaved), style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.alert)
        }
    }
}

/** "Your turn · Mint fleet" on a pastel pill of the fleet's colour. */
@Composable
private fun TurnPill(game: GameView) {
    val shape = RoundedCornerShape(18.dp)
    val humans = game.kinds.count { it == SeatKind.HUMAN }
    val title = when {
        game.phase == Phase.ENDED -> stringResource(R.string.finish_title)
        game.isHumanTurn && humans == 1 -> stringResource(R.string.turn_yours)
        game.isHumanTurn -> stringResource(R.string.turn_other_human, teamName(game.currentTeam))
        else -> stringResource(R.string.turn_ai, teamName(game.currentTeam))
    }
    Row(
        modifier = Modifier.clip(shape).background(CloudAirColors.of(game.currentTeam)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamDot(game.currentTeam, 14.dp)
        HGap(8.dp)
        Text(text = title, style = PicoTheme.typography.titleMedium, color = CloudAirColors.Ink)
        HGap(8.dp)
        Text(text = stringResource(R.string.seat_label, teamName(game.currentTeam)), style = PicoTheme.typography.bodySmall, color = CloudAirColors.deepOf(game.currentTeam))
    }
}

@Composable
private fun RollArea(game: GameView, onEvent: (ControlEvent) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DiceFace(value = if (game.phase == Phase.ROLLING) null else game.dice, size = 72.dp, pipColor = CloudAirColors.deepOf(game.currentTeam))
        HGap(20.dp)
        Column(modifier = Modifier.weight(1f)) {
            if (game.isHumanTurn || game.phase == Phase.ENDED) {
                PrimaryButton(
                    text = stringResource(
                        when (game.phase) {
                            Phase.ROLLING -> R.string.turn_rolling
                            Phase.MOVING -> R.string.turn_moving
                            else -> R.string.turn_roll
                        },
                    ),
                    onClick = { onEvent(ControlEvent.Roll) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = game.canRoll,
                )
            } else {
                // An AI seat: no button to press, just what is going on.
                Box(modifier = Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.CenterStart) {
                    Text(
                        text = stringResource(
                            when (game.phase) {
                                Phase.ROLLING -> R.string.turn_rolling
                                Phase.MOVING -> R.string.turn_moving
                                else -> R.string.turn_ai
                            },
                            teamName(game.currentTeam),
                        ),
                        style = PicoTheme.typography.titleMedium,
                        color = PicoTheme.colorScheme.labelSecondary,
                    )
                }
            }
            Gap(6.dp)
            Text(text = statusText(game), style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelSecondary)
        }
    }
}

/** One line that explains what just happened or what to do now. */
@Composable
internal fun statusText(game: GameView): String {
    val last = game.last
    return when (game.phase) {
        Phase.NO_MOVES -> when {
            game.dice == 6 -> stringResource(R.string.turn_six_no_move)
            game.allInHangar -> stringResource(R.string.turn_need_six)
            else -> stringResource(R.string.turn_no_move)
        }
        Phase.READY -> when {
            last != null && last.team == game.currentTeam && last.dice == 6 && last.type != EventType.PASS -> stringResource(R.string.turn_extra)
            last != null -> eventText(last)
            game.canRoll -> stringResource(R.string.turn_roll_hint)
            else -> ""
        }
        Phase.ROLLING -> last?.let { eventText(it) } ?: ""
        Phase.MOVING -> game.dice?.let { stringResource(R.string.turn_result, it) } ?: stringResource(R.string.turn_moving)
        else -> last?.let { eventText(it) } ?: ""
    }
}

@Composable
internal fun eventText(e: LastEvent): String {
    val name = teamName(e.team)
    val plane = e.plane + 1
    val base = when {
        e.type == EventType.PASS -> stringResource(R.string.event_pass, name)
        e.type == EventType.LAUNCH -> stringResource(R.string.event_launch, name, plane)
        e.finished -> stringResource(R.string.event_home, name, plane)
        e.flew -> stringResource(R.string.event_fly, name, plane)
        e.jumped -> stringResource(R.string.event_jump, name, plane)
        e.bounced -> stringResource(R.string.event_bounce, name, plane)
        else -> stringResource(R.string.event_move, name, plane, e.dice)
    }
    return if (e.captures > 0) base + " · " + stringResource(R.string.event_capture, e.captures) else base
}

@Composable
private fun SixChoice(game: GameView, onEvent: (ControlEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DiceFace(value = 6, size = 48.dp, pipColor = CloudAirColors.deepOf(game.currentTeam))
            HGap(12.dp)
            Text(text = stringResource(R.string.choice_title), style = PicoTheme.typography.titleMedium, color = PicoTheme.colorScheme.labelPrimary)
        }
        Gap(10.dp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                PrimaryButton(text = stringResource(R.string.choice_launch), onClick = { onEvent(ControlEvent.ChooseKind(ActionKind.LAUNCH)) }, modifier = Modifier.fillMaxWidth())
                Text(text = stringResource(if (game.manual) R.string.choice_launch_hint_manual else R.string.choice_launch_hint), style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelTertiary, modifier = Modifier.padding(top = 4.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                PrimaryButton(text = stringResource(R.string.choice_advance), onClick = { onEvent(ControlEvent.ChooseKind(ActionKind.ADVANCE)) }, modifier = Modifier.fillMaxWidth())
                Text(text = stringResource(if (game.manual) R.string.choice_advance_hint_manual else R.string.choice_advance_hint), style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelTertiary, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun PlanePick(game: GameView, onEvent: (ControlEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DiceFace(value = game.dice, size = 48.dp, pipColor = CloudAirColors.deepOf(game.currentTeam))
            HGap(12.dp)
            Text(text = stringResource(R.string.choice_plane), style = PicoTheme.typography.titleMedium, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.weight(1f))
            if (game.selectedKind != null) SecondaryButton(text = stringResource(R.string.choice_back), onClick = { onEvent(ControlEvent.BackToChoice) })
        }
        Gap(10.dp)
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            game.legalPlans.forEach { plan -> PlaneOption(plan, recommended = plan.plane == game.recommended, onClick = { onEvent(ControlEvent.ChoosePlane(plan.plane)) }) }
        }
    }
}

@Composable
private fun PlaneOption(plan: MovePlan, recommended: Boolean, onClick: () -> Unit) {
    val detail = when {
        plan.kind == ActionKind.LAUNCH -> stringResource(R.string.choice_in_hangar)
        plan.to == Board.FINISH -> stringResource(R.string.choice_progress_finish, plan.from)
        else -> stringResource(R.string.choice_progress, plan.from, plan.to)
    }
    val label = stringResource(R.string.choice_plane_number, plan.plane + 1) + "  ·  " + detail + if (recommended) "  ·  " + stringResource(R.string.choice_recommended) else ""
    ChoiceButton(text = label, selected = recommended, onClick = onClick, modifier = Modifier.fillMaxWidth())
}

/** Every fleet with its four plane marks and the count of planes home. */
@Composable
private fun FleetStrip(game: GameView) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        game.teams.forEachIndexed { i, team ->
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                TeamDot(team, 16.dp)
                HGap(4.dp)
                PlanePips(team, game.planes[i])
                HGap(4.dp)
                Text(
                    text = stringResource(R.string.turn_home_count, game.homeCounts[i]),
                    style = PicoTheme.typography.bodySmall,
                    color = if (i == game.turn) PicoTheme.colorScheme.labelPrimary else PicoTheme.colorScheme.labelTertiary,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
internal fun VictoryPanel(game: GameView, onEvent: (ControlEvent) -> Unit) {
    val winner = game.winner ?: game.turn
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamDot(game.teams[winner], 24.dp)
            HGap(10.dp)
            Text(text = stringResource(R.string.finish_winner, teamName(game.teams[winner])), style = PicoTheme.typography.headlineSmall, color = PicoTheme.colorScheme.labelPrimary)
        }
        Gap(4.dp)
        Text(text = stringResource(R.string.finish_title), style = PicoTheme.typography.bodyLarge, color = PicoTheme.colorScheme.passable)
        Text(text = stringResource(R.string.finish_copy, game.round), style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelSecondary)
        Gap(20.dp)
        PrimaryButton(text = stringResource(R.string.finish_again), onClick = { onEvent(ControlEvent.PlayAgain) }, modifier = Modifier.fillMaxWidth())
        Gap(10.dp)
        SecondaryButton(text = stringResource(R.string.finish_home), onClick = { onEvent(ControlEvent.ToHome) }, modifier = Modifier.fillMaxWidth(), tall = true)
    }
}
