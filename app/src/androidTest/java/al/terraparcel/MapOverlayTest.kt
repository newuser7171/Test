package al.terraparcel

import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import al.terraparcel.domain.*
import al.terraparcel.ui.LandViewModel
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapOverlayTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun findMap(v: View): MapView? {
        if (v is MapView) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findMap(v.getChildAt(i))?.let { return it }
        return null
    }
    @Test fun draftVerticesAndLinesAreRenderedAfterEditsAndStyleReload() {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("Your land, on your device").fetchSemanticsNodes().isNotEmpty()
        }
        lateinit var vm: LandViewModel
        compose.runOnIdle {
            vm = ViewModelProvider(compose.activity)[LandViewModel::class.java]
            vm.settings(Preferences())
            vm.new(Shape.LINE)
        }
        compose.onNodeWithText("Map").performClick()
        var map: MapLibreMap? = null
        lateinit var view: MapView
        compose.runOnIdle {
            view = requireNotNull(findMap(compose.activity.window.decorView))
            view.getMapAsync { map = it }
        }
        compose.waitUntil(20_000) {
            var ready = false
            compose.runOnIdle { ready = map?.style?.getLayer("vertices") != null }
            ready
        }
        val points = listOf(Vertex(41.3273,19.8183),Vertex(41.3277,19.8183),
            Vertex(41.3277,19.8191),Vertex(41.3273,19.8191))
        fun visible(layer: String, minimum: Int) {
            try { compose.waitUntil(20_000) {
                var count = 0
                compose.runOnIdle {
                    count = map!!.queryRenderedFeatures(RectF(0f,0f,view.width.toFloat(),view.height.toFloat()),layer).size
                }
                count >= minimum
            } } catch (e: Exception) { throw AssertionError("Expected at least $minimum rendered features in $layer", e) }
        }
        // A line's very first vertex must be visible before a second point exists.
        compose.runOnIdle { vm.add(points[0]) }
        visible("vertices",1)
        compose.runOnIdle { points.drop(1).forEach(vm::add) }
        visible("vertices",4)
        visible("edges",1)
        compose.runOnIdle { vm.move(1,points[1].copy(lon=19.8185)) }
        visible("vertices",4)
        compose.runOnIdle { vm.edit(vm.draft.value.copy(shape=Shape.POLYGON)) }
        visible("areas",1)
        // Reload the style without network tiles; retained draft must reappear.
        compose.runOnIdle { vm.settings(vm.prefs.value.copy(attribution="Reload test")) }
        compose.runOnIdle { vm.settings(vm.prefs.value.copy(onlineMaps=true,layer="Custom",customTiles="https://127.0.0.1/{z}/{x}/{y}.png",attribution="Reload test")) }
        visible("vertices",4)
        visible("areas",1)
        compose.runOnIdle { assertTrue(vm.draft.value.points.size == 4) }
        // Move the measurement to Fier, well outside the initial Tirana viewport.
        compose.runOnIdle {
            vm.edit(vm.draft.value.copy(points=points.map { it.copy(lat=it.lat-0.6,lon=it.lon-0.27) }))
        }
        compose.onNodeWithText("Fit measurement").performClick()
        visible("vertices",4)
        visible("areas",1)
        compose.runOnIdle { vm.deletePoint(3) }
        visible("vertices",3)
        compose.runOnIdle { vm.undo() }
        visible("vertices",4)
    }
}
