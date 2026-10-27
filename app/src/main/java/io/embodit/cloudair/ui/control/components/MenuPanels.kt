package io.embodit.cloudair.ui.control.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Switch
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.AlertDialog
import io.embodit.cloudair.R
import io.embodit.cloudair.domain.model.DecisionMode
import io.embodit.cloudair.domain.model.Language
import io.embodit.cloudair.domain.model.MotionSpeed
import io.embodit.cloudair.domain.model.SeatKind
import io.embodit.cloudair.ui.control.ControlEvent
import io.embodit.cloudair.ui.control.ControlUiState

@Composable
internal fun HomePanel(state: ControlUiState, onEvent: (ControlEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(text = stringResource(R.string.app_name), style = PicoTheme.typography.displaySmall, color = PicoTheme.colorScheme.labelPrimary)
        Gap(4.dp)
        Text(text = stringResource(R.string.home_hero), style = PicoTheme.typography.bodyLarge, color = PicoTheme.colorScheme.labelSecondary)
        Gap(20.dp)
        val saved = state.saved
        if (saved != null) {
            PrimaryButton(text = stringResource(R.string.home_continue), onClick = { onEvent(ControlEvent.ContinueGame) }, modifier = Modifier.fillMaxWidth())
            Gap(6.dp)
            Text(text = stringResource(R.string.home_saved, saved.seats, saved.round), style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelTertiary)
            Gap(10.dp)
            SecondaryButton(text = stringResource(R.string.home_new), onClick = { onEvent(ControlEvent.NewGame) }, modifier = Modifier.fillMaxWidth(), tall = true)
        } else {
            PrimaryButton(text = stringResource(R.string.home_new), onClick = { onEvent(ControlEvent.NewGame) }, modifier = Modifier.fillMaxWidth())
            if (state.brokenSave) {
                Gap(6.dp)
                Text(text = stringResource(R.string.home_broken_save), style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.alert)
            }
        }
        Gap(16.dp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(text = stringResource(R.string.common_settings), onClick = { onEvent(ControlEvent.OpenSettings) }, modifier = Modifier.weight(1f))
            SecondaryButton(text = stringResource(R.string.common_rules), onClick = { onEvent(ControlEvent.OpenRules) }, modifier = Modifier.weight(1f))
        }
        Gap(16.dp)
        Text(text = stringResource(R.string.home_context), style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelTertiary)
    }
}

@Composable
internal fun SetupPanel(state: ControlUiState, onEvent: (ControlEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        PageHeader(title = stringResource(R.string.setup_title)) {
            SecondaryButton(text = stringResource(R.string.common_back), onClick = { onEvent(ControlEvent.Back) })
        }
        Gap(12.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.setup_seats), style = PicoTheme.typography.labelLarge, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.width(72.dp))
            SegmentControl(modifier = Modifier.width(240.dp)) {
                for (count in 2..4) {
                    SegmentItem(
                        selected = state.draft.size == count,
                        onClick = { onEvent(ControlEvent.SetSeatCount(count)) },
                        modifier = Modifier.weight(1f),
                        title = {
                            // design-style: inherited-content-color SegmentItem
                            Text(text = stringResource(R.string.setup_seat_count, count), style = PicoTheme.typography.labelLarge)
                        },
                    )
                }
            }
        }
        Gap(12.dp)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.draft.forEachIndexed { index, seat ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TeamDot(seat.team, 18.dp)
                    HGap(8.dp)
                    Text(text = teamName(seat.team), style = PicoTheme.typography.labelLarge, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.width(96.dp))
                    SegmentControl(modifier = Modifier.weight(1f)) {
                        listOf(SeatKind.HUMAN to R.string.setup_human, SeatKind.AI_EASY to R.string.setup_ai_easy, SeatKind.AI_SMART to R.string.setup_ai_smart).forEach { (kind, label) ->
                            SegmentItem(
                                selected = seat.kind == kind,
                                onClick = { onEvent(ControlEvent.SetSeatKind(index, kind)) },
                                modifier = Modifier.weight(1f),
                                title = {
                                    // design-style: inherited-content-color SegmentItem
                                    Text(text = stringResource(label), style = PicoTheme.typography.labelMedium)
                                },
                            )
                        }
                    }
                }
            }
        }
        if (!state.draftValid) {
            Text(text = stringResource(R.string.setup_human_required), style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.alert)
            Gap(6.dp)
        }
        PrimaryButton(text = stringResource(R.string.setup_start), onClick = { onEvent(ControlEvent.StartGame) }, modifier = Modifier.fillMaxWidth(), enabled = state.draftValid)
    }
    if (state.confirmReplace) {
        ConfirmDialog(
            title = stringResource(R.string.setup_replace_title),
            body = stringResource(R.string.setup_replace_body),
            action = stringResource(R.string.setup_replace_action),
            onConfirm = { onEvent(ControlEvent.ConfirmReplace) },
            onCancel = { onEvent(ControlEvent.CancelConfirm) },
        )
    }
}

@Composable
internal fun PausePanel(state: ControlUiState, onEvent: (ControlEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(text = stringResource(R.string.pause_title), style = PicoTheme.typography.headlineSmall, color = PicoTheme.colorScheme.labelPrimary)
        Gap(4.dp)
        Text(text = stringResource(R.string.pause_copy), style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelSecondary)
        Gap(20.dp)
        PrimaryButton(text = stringResource(R.string.pause_resume), onClick = { onEvent(ControlEvent.Resume) }, modifier = Modifier.fillMaxWidth())
        Gap(10.dp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(text = stringResource(R.string.common_settings), onClick = { onEvent(ControlEvent.OpenSettings) }, modifier = Modifier.weight(1f))
            SecondaryButton(text = stringResource(R.string.common_rules), onClick = { onEvent(ControlEvent.OpenRules) }, modifier = Modifier.weight(1f))
        }
        Gap(10.dp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(text = stringResource(R.string.pause_restart), onClick = { onEvent(ControlEvent.Restart) }, modifier = Modifier.weight(1f))
            SecondaryButton(text = stringResource(R.string.pause_leave), onClick = { onEvent(ControlEvent.LeaveGame) }, modifier = Modifier.weight(1f))
        }
    }
    if (state.confirmRestart) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_restart_title),
            body = stringResource(R.string.confirm_restart_body),
            action = stringResource(R.string.confirm_restart_action),
            onConfirm = { onEvent(ControlEvent.ConfirmRestart) },
            onCancel = { onEvent(ControlEvent.CancelConfirm) },
        )
    }
}

@Composable
internal fun SettingsPanel(state: ControlUiState, onEvent: (ControlEvent) -> Unit) {
    val s = state.settings
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        PageHeader(title = stringResource(R.string.common_settings)) {
            SecondaryButton(text = stringResource(R.string.common_done), onClick = { onEvent(ControlEvent.Back) })
        }
        Gap(12.dp)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                SectionLabel(stringResource(R.string.settings_language))
                SegmentControl(modifier = Modifier.fillMaxWidth()) {
                    listOf(Language.SYSTEM to R.string.settings_language_system, Language.ZH to R.string.settings_language_zh, Language.EN to R.string.settings_language_en).forEach { (lang, label) ->
                        SegmentItem(
                            selected = s.language == lang,
                            onClick = { onEvent(ControlEvent.SetLanguage(lang)) },
                            modifier = Modifier.weight(1f),
                            title = {
                                // design-style: inherited-content-color SegmentItem
                                Text(text = stringResource(label), style = PicoTheme.typography.labelLarge)
                            },
                        )
                    }
                }
            }
            Column {
                SectionLabel(stringResource(R.string.settings_mode))
                SegmentControl(modifier = Modifier.fillMaxWidth()) {
                    listOf(DecisionMode.RECOMMEND to R.string.settings_mode_recommend, DecisionMode.MANUAL to R.string.settings_mode_manual, DecisionMode.FULL_AUTO to R.string.settings_mode_auto).forEach { (mode, label) ->
                        SegmentItem(
                            selected = s.decisionMode == mode,
                            onClick = { onEvent(ControlEvent.SetDecisionMode(mode)) },
                            modifier = Modifier.weight(1f),
                            title = {
                                // design-style: inherited-content-color SegmentItem
                                Text(text = stringResource(label), style = PicoTheme.typography.labelMedium)
                            },
                        )
                    }
                }
                Text(text = stringResource(R.string.settings_mode_hint), style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelTertiary, modifier = Modifier.padding(top = 4.dp))
            }
            Column {
                SectionLabel(stringResource(R.string.settings_speed))
                SegmentControl(modifier = Modifier.fillMaxWidth()) {
                    listOf(MotionSpeed.GENTLE to R.string.settings_speed_gentle, MotionSpeed.STANDARD to R.string.settings_speed_standard, MotionSpeed.QUICK to R.string.settings_speed_quick).forEach { (speed, label) ->
                        SegmentItem(
                            selected = s.speed == speed,
                            onClick = { onEvent(ControlEvent.SetSpeed(speed)) },
                            modifier = Modifier.weight(1f),
                            title = {
                                // design-style: inherited-content-color SegmentItem
                                Text(text = stringResource(label), style = PicoTheme.typography.labelLarge)
                            },
                        )
                    }
                }
            }
            SwitchRow(title = stringResource(R.string.settings_sound), hint = stringResource(R.string.settings_sound_hint), checked = s.sound) { onEvent(ControlEvent.SetSound(it)) }
            SwitchRow(title = stringResource(R.string.settings_reduced_motion), hint = stringResource(R.string.settings_reduced_hint), checked = s.reducedMotion) { onEvent(ControlEvent.SetReducedMotion(it)) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = PicoTheme.typography.labelLarge, color = PicoTheme.colorScheme.labelPrimary)
            Text(text = hint, style = PicoTheme.typography.bodySmall, color = PicoTheme.colorScheme.labelTertiary)
        }
        HGap(12.dp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun RulesPanel(onEvent: (ControlEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        PageHeader(title = stringResource(R.string.rules_title)) {
            SecondaryButton(text = stringResource(R.string.common_back), onClick = { onEvent(ControlEvent.Back) })
        }
        Gap(12.dp)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(R.string.rules_1, R.string.rules_2, R.string.rules_3, R.string.rules_4, R.string.rules_5, R.string.rules_6, R.string.rules_7).forEachIndexed { i, id ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(text = "${i + 1}", style = PicoTheme.typography.titleMedium, color = PicoTheme.colorScheme.interaction, modifier = Modifier.width(28.dp))
                    Text(text = stringResource(id), style = PicoTheme.typography.bodyMediumMultiline, color = PicoTheme.colorScheme.labelPrimary)
                }
            }
        }
    }
}

@Composable
internal fun ConfirmDialog(title: String, body: String, action: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    // Resolved here, outside the dialog's own composition, so the in-app language applies to it as well.
    val cancel = stringResource(R.string.common_cancel)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = title, style = PicoTheme.typography.headlineSmall, color = PicoTheme.colorScheme.labelPrimary) },
        content = { Text(text = body, style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelSecondary) },
        buttons = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(text = cancel, onClick = onCancel, modifier = Modifier.weight(1f), tall = true)
                PrimaryButton(text = action, onClick = onConfirm, modifier = Modifier.weight(1f))
            }
        },
    )
}
