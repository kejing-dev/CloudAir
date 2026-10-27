package io.embodit.cloudair.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.systemColorScheme
import io.embodit.cloudair.domain.model.Language
import java.util.Locale

/**
 * The app's PicoTheme: the system Vibrant scheme with the design's semantic colours swapped in and
 * the type scale rendered in Noto Sans. All 16 roles are assigned explicitly.
 *
 * [language] localises the resources below this point without restarting anything: a context with
 * the chosen locale is installed as [LocalContext], so `stringResource` follows the in-app setting.
 */
@Composable
fun CloudAirTheme(language: Language = Language.SYSTEM, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val localized = remember(base, language) { base.localized(language) }
    val system = systemColorScheme(localized)
    val scheme =
        system.copy(
            fillPrimary = system.fillPrimary,
            fillSecondary = system.fillSecondary,
            fillTertiary = CloudAirColors.FillTertiary,
            fillLight = system.fillLight,
            labelPrimaryLight = CloudAirColors.OnInteraction,
            labelPrimary = system.labelPrimary,
            labelSecondary = system.labelSecondary,
            labelTertiary = system.labelTertiary,
            labelQuaternary = system.labelQuaternary,
            lightenHover = system.lightenHover,
            lightenPressed = system.lightenPressed,
            error = CloudAirColors.Error,
            alert = CloudAirColors.Alert,
            passable = CloudAirColors.Passable,
            interaction = CloudAirColors.Interaction,
            dividerLine = CloudAirColors.DividerLine,
        )
    CompositionLocalProvider(LocalContext provides localized) {
        PicoTheme(
            colorScheme = scheme,
            typography = PicoTheme.typography.withNotoSans(),
            content = content,
        )
    }
}

fun Context.localized(language: Language): Context {
    val locale = when (language) {
        Language.SYSTEM -> return this
        Language.ZH -> Locale.SIMPLIFIED_CHINESE
        Language.EN -> Locale.ENGLISH
    }
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    // A wrapper, not the bare configuration context: the Spatial SDK walks the context chain to find
    // the Activity that owns a SpatialView, and a plain ContextImpl would not lead there.
    val localizedResources = createConfigurationContext(config).resources
    return object : ContextWrapper(this) {
        override fun getResources(): Resources = localizedResources
    }
}
