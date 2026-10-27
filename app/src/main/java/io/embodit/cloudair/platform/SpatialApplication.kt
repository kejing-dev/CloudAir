package io.embodit.cloudair.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import io.embodit.cloudair.mainApp

class SpatialApplication : Application() {
    lateinit var graph: CloudAirGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = CloudAirGraph(this)
        launch(::mainApp)
    }
}
