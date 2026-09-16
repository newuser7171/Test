package al.terraparcel

import android.graphics.Bitmap
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import al.terraparcel.map.attributedSnapshot
import al.terraparcel.domain.*
import al.terraparcel.location.Fix
import al.terraparcel.ui.LandViewModel
import org.maplibre.android.maps.*
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.layers.RasterLayer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient

@RunWith(AndroidJUnit4::class)
class LayerStackTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun findMap(v:View):MapView? {
        if(v is MapView)return v
        if(v is ViewGroup)for(i in 0 until v.childCount)findMap(v.getChildAt(i))?.let{return it}
        return null
    }
    @Test fun liveImageryCadastreAndMeasurementsSurviveLayerChanges() {
        compose.waitUntil(20_000){compose.onAllNodesWithText("Your land, on your device").fetchSemanticsNodes().isNotEmpty()}
        lateinit var vm:LandViewModel
        compose.runOnIdle {
            vm=ViewModelProvider(compose.activity)[LandViewModel::class.java]
            vm.settings(Preferences())
            vm.new(Shape.POLYGON)
        }
        compose.onNodeWithText("Map").performClick()
        lateinit var view:MapView
        var map:MapLibreMap?=null
        val full=AtomicBoolean(false)
        compose.runOnIdle {
            view=requireNotNull(findMap(compose.activity.window.decorView))
            view.addOnDidFinishRenderingMapListener { complete -> full.set(complete) }
            view.getMapAsync {map=it}
        }
        fun awaitStyle(cadastre:Boolean,online:Boolean=true) {
            compose.waitUntil(30_000) {
                var ready=false
                compose.runOnIdle {
                    val s=map?.style
                    ready=s?.getLayer("vertices")!=null && (s.getLayer("cadastre")!=null)==cadastre && (s.getSource("basemap")!=null)==online
                }
                ready
            }
        }
        awaitStyle(false,false)
        val satellite=AtomicInteger();val cadastral=AtomicInteger()
        // Observe genuine successful service responses; never substitute test images.
        val client=OkHttpClient.Builder().addNetworkInterceptor { chain ->
            val response=chain.proceed(chain.request())
            if(response.isSuccessful && response.header("Content-Type").orEmpty().startsWith("image/")) {
                if(chain.request().url.host=="services.arcgisonline.com")satellite.incrementAndGet()
                if(chain.request().url.host=="geoportal.asig.gov.al")cadastral.incrementAndGet()
            }
            response
        }.build()
        compose.runOnIdle {
            org.maplibre.android.module.http.HttpRequestImpl.setOkHttpClient(client)
            map!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(41.3275,19.8187),16.0))
            listOf(Vertex(41.3271,19.8181),Vertex(41.3278,19.8181),Vertex(41.3278,19.8192)).forEach(vm::add)
            vm.fix.value=Fix(Vertex(41.3275,19.8187,accuracy=4f),0f,0f,android.os.SystemClock.elapsedRealtimeNanos())
            full.set(false)
            vm.settings(Preferences(onlineMaps=true,layer="Satellite",cadastreEnabled=true))
        }
        awaitStyle(true)
        compose.waitUntil(90_000){satellite.get()>0 && cadastral.get()>0 && full.get()}
        fun assertStack() {
            compose.runOnIdle {
                val ids=map!!.style!!.layers.map{it.id}
                assertEquals(ids.size,ids.distinct().size)
                assertTrue(ids.indexOf("basemap")<ids.indexOf("cadastre"))
                for(id in listOf("areas","edge-halo","edges","vertices"))assertTrue(ids.indexOf("cadastre")<ids.indexOf(id))
            }
        }
        fun visible(layer:String,minimum:Int) {
            compose.waitUntil(20_000) {
                var count=0
                compose.runOnIdle { count=map!!.queryRenderedFeatures(RectF(0f,0f,view.width.toFloat(),view.height.toFloat()),layer).size }
                count>=minimum
            }
        }
        fun snapshot(name:String) {
            val done=AtomicBoolean(false)
            compose.runOnIdle {map!!.snapshot { bitmap ->
                val folder=File(compose.activity.getExternalFilesDir(null),"verification").apply{mkdirs()}
                File(folder,name+".png").outputStream().use { attributedSnapshot(bitmap,vm.prefs.value).compress(Bitmap.CompressFormat.PNG,100,it) }
                done.set(true)
            }}
            compose.waitUntil(20_000){done.get()}
            // UTP uninstalls the target after testing; copy evidence outside its data directory first.
            val command="mkdir -p /sdcard/Download/TerraParcel-verification && cp /sdcard/Android/data/al.terraparcel/files/verification/$name.png /sdcard/Download/TerraParcel-verification/$name.png"
            ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
        assertStack();visible("areas",1);visible("edges",1);visible("vertices",4);snapshot("satellite-cadastre-measurement")
        compose.runOnIdle { vm.settings(vm.prefs.value.copy(cadastreEnabled=false)) }
        awaitStyle(false)
        compose.runOnIdle {assertTrue(vm.prefs.value.layer=="Satellite");vm.settings(vm.prefs.value.copy(cadastreEnabled=true,layer="OpenStreetMap"))}
        awaitStyle(true);assertStack()
        compose.runOnIdle {vm.settings(vm.prefs.value.copy(layer="Satellite"))}
        awaitStyle(true);assertStack()
        // Check zoom/pan projection round-trip and request new WMS tiles for the new viewport.
        val previous=cadastral.get()
        compose.runOnIdle { full.set(false);map!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(41.3282,19.8193),17.0)) }
        compose.waitUntil(90_000){full.get() && cadastral.get()>previous}
        compose.runOnIdle {
            val coordinate=LatLng(41.3278,19.8192)
            val restored=map!!.projection.fromScreenLocation(map!!.projection.toScreenLocation(coordinate))
            assertEquals(coordinate.latitude,restored.latitude,0.000001)
            assertEquals(coordinate.longitude,restored.longitude,0.000001)
        }
        snapshot("satellite-cadastre-zoomed")
        compose.runOnIdle {vm.settings(vm.prefs.value.copy(cadastreOpacity=0.35f))}
        compose.waitUntil(20_000) {
            var opacity:Float?=null
            compose.runOnIdle {opacity=map!!.style?.getLayerAs<RasterLayer>("cadastre")?.rasterOpacity?.value}
            opacity==0.35f
        }
        assertStack()
        lateinit var stableStyle:Style
        compose.runOnIdle {stableStyle=map!!.style!!;vm.selected.value=1}
        compose.waitForIdle()
        compose.runOnIdle {assertSame(stableStyle,map!!.style);vm.settings(vm.prefs.value.copy(onlineMaps=false))}
        awaitStyle(false,false);visible("vertices",1)
        compose.runOnIdle {vm.settings(vm.prefs.value.copy(customOverlayEnabled=false));assertEquals(3,vm.draft.value.points.size)}
    }
}
