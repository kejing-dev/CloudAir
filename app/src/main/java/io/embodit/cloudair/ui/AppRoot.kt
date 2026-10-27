package io.embodit.cloudair.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.embodit.cloudair.platform.CloudAirGraph
import io.embodit.cloudair.ui.theme.CloudAirTheme

/** The theme of every window, following the in-app language setting. */
@Composable
fun AppRoot(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val graph = remember(context) { CloudAirGraph.from(context) }
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    graph.sounds.enabled = settings.sound
    CloudAirTheme(language = settings.language, content = content)
}
