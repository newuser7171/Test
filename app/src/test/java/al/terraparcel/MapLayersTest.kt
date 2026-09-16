package al.terraparcel

import al.terraparcel.domain.*
import al.terraparcel.map.*
import kotlinx.serialization.encodeToString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class MapLayersTest {
    private fun ids(p:Preferences):List<String> {
        val layers=JSONObject(RasterMapProvider().style(p)).getJSONArray("layers")
        return (0 until layers.length()).map {layers.getJSONObject(it).getString("id")}
    }
    @Test fun independentBasemapsAndOverlay() {
        for(base in listOf("Satellite","OpenStreetMap")) {
            val p=Preferences(onlineMaps=true,layer=base,cadastreEnabled=true)
            assertEquals(listOf("background","basemap","cadastre"),ids(p))
            assertEquals(listOf("background","basemap"),ids(p.copy(cadastreEnabled=false)))
            assertEquals(base,p.copy(cadastreEnabled=false).layer)
            assertTrue(p.copy(layer="Topographic").cadastreEnabled)
        }
    }
    @Test fun wmsContractBoundsScaleAndAlpha() {
        val p=Preferences(onlineMaps=true,layer="Satellite",cadastreEnabled=true,cadastreOpacity=0.4f)
        val json=JSONObject(RasterMapProvider().style(p));val source=json.getJSONObject("sources").getJSONObject("cadastre")
        val url=source.getJSONArray("tiles").getString(0)
        listOf("SRS=EPSG:900913","BBOX={bbox-epsg-3857}","TRANSPARENT=TRUE","VERSION=1.1.1","STYLES=","LAYERS=p_kadas_albscad_072026").forEach { assertTrue(url.contains(it)) }
        assertEquals(256,source.getInt("tileSize"));assertEquals(14,source.getInt("minzoom"));assertEquals(21,source.getInt("maxzoom"))
        assertEquals(4,source.getJSONArray("bounds").length())
        assertEquals(0.4,json.getJSONArray("layers").getJSONObject(2).getJSONObject("paint").getDouble("raster-opacity"),0.0001)
        assertEquals(MapLayers.SATELLITE,json.getJSONObject("sources").getJSONObject("basemap").getJSONArray("tiles").getString(0))
    }
    @Test fun legacySettingsAndNewSettingsRoundTrip() {
        val old="""{"layer":"My WMS","customTiles":"https://example.org/wms?bbox={bbox-epsg-3857}","attribution":"Original provider","onlineMaps":true}"""
        val p=codec.decodeFromString<Preferences>(old)
        assertFalse(p.cadastreEnabled);assertFalse(p.customOverlayEnabled)
        assertEquals(p.customTiles,rasterStack(p).single().url)
        val edited=p.copy(cadastreEnabled=true,cadastreOpacity=0.6f,customOverlayEnabled=true,customOverlayTiles="https://example.org/{z}/{x}/{y}.png",customOverlayAttribution="Custom credit")
        assertEquals(edited,codec.decodeFromString<Preferences>(codec.encodeToString(edited)))
        assertEquals(listOf("background","basemap","cadastre","custom-overlay"),ids(edited))
        assertEquals(p.customTiles,edited.copy(layer="Satellite").customTiles)
    }
    @Test fun offlineAndUnconfiguredCustomLayersAreSafe() {
        assertEquals(listOf("background"),ids(Preferences(cadastreEnabled=true,customOverlayEnabled=true)))
        assertEquals(listOf("background"),ids(Preferences(onlineMaps=true,layer="Unconfigured",customOverlayEnabled=true)))
    }
    @Test fun creditsAndUnrelatedSettingsDoNotChangeStyle() {
        val p=Preferences(onlineMaps=true,layer="Satellite",cadastreEnabled=true)
        assertTrue(mapAttribution(p).contains(MapLayers.ESRI_CREDIT));assertTrue(mapAttribution(p).contains("© ASIG / ASHK"))
        assertFalse(mapAttribution(p.copy(cadastreEnabled=false)).contains("ASHK"))
        assertEquals(RasterMapProvider().style(p),RasterMapProvider().style(p.copy(language="sq",theme="dark",maxAccuracy=10f)))
        assertEquals(RasterMapProvider().style(p),RasterMapProvider().style(p))
    }
}
