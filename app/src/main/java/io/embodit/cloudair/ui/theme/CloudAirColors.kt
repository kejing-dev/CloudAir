package io.embodit.cloudair.ui.theme

import androidx.compose.ui.graphics.Color
import io.embodit.cloudair.domain.model.Team

/**
 * Design tokens of Cloud Air (design/design-doc.md §4): four low-saturation fleet colours on an
 * ivory mat, a deep teal for the primary action. Roles that map onto a PicoTheme ColorScheme slot
 * are injected in [CloudAirTheme]; the rest are brand tokens used on the board and in the window.
 */
object CloudAirColors {
    // ---- ColorScheme role overrides ----
    val Interaction = Color(0xFF246F66) // design-style: fixed-figma-color design-doc §4 interaction
    val Passable = Color(0xFF428C71) // design-style: fixed-figma-color design-doc §4 passable
    val Alert = Color(0xFFAD7A36) // design-style: fixed-figma-color design-doc §4 alert
    val Error = Color(0xFFAF514D) // design-style: fixed-figma-color design-doc §4 error
    val OnInteraction = Color(0xFFFFFFFF) // design-style: fixed-figma-color design-doc §4 labelPrimaryLight
    val FillTertiary = Color(0xFFDFE9E2) // design-style: fixed-figma-color design-doc §4 fillTertiary
    val DividerLine = Color(0xFFC9D7CF) // design-style: fixed-figma-color design-doc §4 dividerLine

    // ---- Fleet colours (brand tokens, no ColorScheme role) ----
    val Mint = Color(0xFFA8DCCB) // design-style: fixed-figma-color design-doc §4 teamMint
    val Sky = Color(0xFFA9D3F2) // design-style: fixed-figma-color design-doc §4 teamSky
    val Apricot = Color(0xFFF6C9A8) // design-style: fixed-figma-color design-doc §4 teamApricot
    val Lavender = Color(0xFFC9BDE8) // design-style: fixed-figma-color design-doc §4 teamLavender
    val MintDeep = Color(0xFF277661) // design-style: fixed-figma-color design-doc §4 teamMintDeep
    val SkyDeep = Color(0xFF386E9B) // design-style: fixed-figma-color design-doc §4 teamSkyDeep
    val ApricotDeep = Color(0xFF93643B) // design-style: fixed-figma-color design-doc §4 teamApricotDeep
    val LavenderDeep = Color(0xFF70608F) // design-style: fixed-figma-color design-doc §4 teamLavenderDeep

    // ---- Board tokens ----
    val BoardIvory = Color(0xFFF4F4E9) // design-style: fixed-figma-color design-doc §4 boardIvory
    val RunwayPale = Color(0xFFE6ECE5) // design-style: fixed-figma-color design-doc §4 runwayPale
    val RouteGlow = Color(0xFFFFF1C7) // design-style: fixed-figma-color design-doc §4 routeGlow
    val TrayStone = Color(0xFFDCE6DF) // design-style: fixed-figma-color design-doc §4 trayStone
    val Ink = Color(0xFF203C39) // design-style: fixed-figma-color design-doc §4 ink
    val MatFrame = Color(0xFFA9C2B7) // design-style: fixed-figma-color design-doc §4 matFrame (sage piping)
    val TrayFelt = Color(0xFF3F5E58) // design-style: fixed-figma-color design-doc §4 trayFelt
    val Shadow = Color(0xFF203C39) // design-style: fixed-figma-color design-doc §4 shadow (drawn at 22 % opacity)

    fun of(team: Team): Color = when (team) {
        Team.MINT -> Mint
        Team.SKY -> Sky
        Team.APRICOT -> Apricot
        Team.LAVENDER -> Lavender
    }

    private fun mix(team: Team, toward: Float): Color {
        val a = of(team)
        val b = deepOf(team)
        return Color(a.red + (b.red - a.red) * toward, a.green + (b.green - a.green) * toward, a.blue + (b.blue - a.blue) * toward)
    }

    /** Loop tile tone: between the pastel and the deep colour. */
    fun midOf(team: Team): Color = mix(team, 0.40f)

    /** Home lane tone: deeper than the loop so the runways read. */
    fun laneOf(team: Team): Color = mix(team, 0.55f)

    /** Airframe paint: a real fleet colour, not a pastel. */
    fun planeOf(team: Team): Color = mix(team, 0.6f)

    fun deepOf(team: Team): Color = when (team) {
        Team.MINT -> MintDeep
        Team.SKY -> SkyDeep
        Team.APRICOT -> ApricotDeep
        Team.LAVENDER -> LavenderDeep
    }
}
