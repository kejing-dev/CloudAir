package io.embodit.cloudair.ui.control.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import io.embodit.cloudair.R
import io.embodit.cloudair.domain.model.Team
import io.embodit.cloudair.ui.theme.CloudAirColors

@Composable
internal fun teamName(team: Team): String = stringResource(
    when (team) {
        Team.MINT -> R.string.team_mint
        Team.SKY -> R.string.team_sky
        Team.APRICOT -> R.string.team_apricot
        Team.LAVENDER -> R.string.team_lavender
    },
)

/** The fleet's colour as a small disc with its darker rim. */
@Composable
internal fun TeamDot(team: Team, size: androidx.compose.ui.unit.Dp = 16.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(CloudAirColors.deepOf(team))
            .padding(2.dp)
            .clip(CircleShape)
            .background(CloudAirColors.of(team)),
    )
}

/** The main action of a page: 56 dp tall, the interaction colour. */
@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        size = ButtonDefaults.Regular.copy(height = 56.dp, minWidth = 200.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PicoTheme.colorScheme.interaction,
            contentColor = PicoTheme.colorScheme.labelPrimaryLight,
        ),
    ) {
        // design-style: inherited-content-color Button
        Text(text = text, style = PicoTheme.typography.titleMedium, maxLines = 1, softWrap = false)
    }
}

/** A secondary action on glass. */
@Composable
internal fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, tall: Boolean = false) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        size = ButtonDefaults.Regular.copy(height = if (tall) 56.dp else 48.dp, minWidth = 140.dp),
        shape = RoundedCornerShape(if (tall) 28.dp else 24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PicoTheme.colorScheme.fillSecondary,
            contentColor = PicoTheme.colorScheme.labelPrimary,
        ),
    ) {
        // design-style: inherited-content-color Button
        Text(text = text, style = PicoTheme.typography.labelLarge, maxLines = 1, softWrap = false)
    }
}

/** A choice chip-like button: selected ones take the interaction colour. */
@Composable
internal fun ChoiceButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        size = ButtonDefaults.Regular.copy(height = 44.dp, minWidth = 96.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) PicoTheme.colorScheme.interaction else PicoTheme.colorScheme.fillTertiary,
            contentColor = if (selected) PicoTheme.colorScheme.labelPrimaryLight else PicoTheme.colorScheme.labelPrimary,
        ),
    ) {
        // design-style: inherited-content-color Button
        Text(text = text, style = PicoTheme.typography.labelLarge)
    }
}

/** Page header: a title on the left, up to two actions on the right. */
@Composable
internal fun PageHeader(title: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, style = PicoTheme.typography.titleLarge, color = PicoTheme.colorScheme.labelPrimary, modifier = Modifier.weight(1f))
        actions()
    }
}

/** A die face drawn with pips, like the real die on the board. A null value shows every pip faintly. */
@Composable
internal fun DiceFace(value: Int?, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 64.dp, pipColor: Color = CloudAirColors.MintDeep) {
    val pip = size / 6
    val face = CloudAirColors.BoardIvory
    val faint = pipColor.copy(alpha = 0.2f)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 5))
            .background(face)
            .padding(size / 8),
    ) {
        val v = value ?: 6
        val grid = listOf(
            listOf(v in listOf(2, 3, 4, 5, 6), false, v in listOf(4, 5, 6)),
            listOf(v == 6, v in listOf(1, 3, 5), v == 6),
            listOf(v in listOf(4, 5, 6), false, v in listOf(2, 3, 4, 5, 6)),
        )
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.SpaceBetween) {
            grid.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { lit ->
                        Box(modifier = Modifier.size(pip).clip(CircleShape).background(if (!lit) Color.Transparent else if (value == null) faint else pipColor))
                    }
                }
                if (row !== grid.last()) Spacer(Modifier.height(pip / 2))
            }
        }
    }
}

/** Four small marks for one fleet's planes: hollow = hangar, pastel = flying, deep = home. */
@Composable
internal fun PlanePips(team: Team, planes: List<Int>) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        planes.forEach { progress ->
            val fill = when {
                progress == io.embodit.cloudair.domain.model.Board.FINISH -> CloudAirColors.deepOf(team)
                progress >= 0 -> CloudAirColors.of(team)
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(CloudAirColors.deepOf(team).copy(alpha = 0.5f))
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(if (progress < 0) CloudAirColors.BoardIvory else fill),
            )
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text = text, style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.labelSecondary, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
internal fun Gap(height: androidx.compose.ui.unit.Dp = 12.dp) = Spacer(Modifier.height(height))

@Composable
internal fun HGap(width: androidx.compose.ui.unit.Dp = 12.dp) = Spacer(Modifier.width(width))
