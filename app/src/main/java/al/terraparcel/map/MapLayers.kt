package al.terraparcel.map

import al.terraparcel.domain.Preferences
import org.json.JSONArray
import org.json.JSONObject

object MapLayers {
    const val SATELLITE = "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
    const val ESRI_METADATA = "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer?f=pjson"
    // Service copyrightText verified 2026-09-16; refreshed from service metadata when online.
    const val ESRI_CREDIT = "Source: Esri, Vantor, Earthstar Geographics, and the GIS User Community"
    const val CADASTRE_CREDIT = "© ASIG / ASHK"
    const val CADASTRE = "https://geoportal.asig.gov.al/service/zrpp/wms?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&LAYERS=p_kadas_albscad_072026&STYLES=&SRS=EPSG:900913&BBOX={bbox-epsg-3857}&WIDTH=256&HEIGHT=256&FORMAT=image/png&TRANSPARENT=TRUE"
    val PRESETS = listOf("OpenStreetMap", "Satellite", "Topographic")
    fun selectBasemap(p: Preferences, layer: String): Preferences = p.copy(layer=layer,
        customLayerName=p.customLayerName.ifBlank { if(p.layer !in PRESETS)p.layer else "" })
    fun validTemplate(url: String) = url.startsWith("https://") &&
        (url.contains("{bbox-epsg-3857}") || listOf("{z}", "{x}", "{y}").all(url::contains))
}

data class RasterSpec(val id: String, val url: String, val attribution: String,
    val minZoom: Int = 0, val maxZoom: Int = 19, val opacity: Float = 1f,
    val bounds: List<Double>? = null)

fun rasterStack(p: Preferences, esriCredit: String = MapLayers.ESRI_CREDIT): List<RasterSpec> {
    if (!p.onlineMaps) return emptyList()
    val base = when (p.layer) {
        "Satellite" -> RasterSpec("basemap", MapLayers.SATELLITE, esriCredit, maxZoom = 23)
        "Topographic" -> RasterSpec("basemap", "https://tile.opentopomap.org/{z}/{x}/{y}.png", "© OpenStreetMap contributors · SRTM · OpenTopoMap (CC-BY-SA)", maxZoom = 17)
        "OpenStreetMap" -> RasterSpec("basemap", "https://tile.openstreetmap.org/{z}/{x}/{y}.png", "© OpenStreetMap contributors")
        else -> RasterSpec("basemap", p.customTiles, p.attribution)
    }
    return buildList {
        if (MapLayers.validTemplate(base.url)) add(base)
        if (p.cadastreEnabled) add(RasterSpec("cadastre", MapLayers.CADASTRE, MapLayers.CADASTRE_CREDIT,
            // WMS advertises scale denominators 200–50001. 256px z14–21 is within that range.
            // MapLibre camera zoom uses 512px tiles: camera 13–21 covers this raster range.
            minZoom = 14, maxZoom = 21, opacity = p.cadastreOpacity,
            bounds = listOf(19.238927169468482,39.64031238851075,21.090454133262412,42.661314178919916)))
        if (p.customOverlayEnabled && MapLayers.validTemplate(p.customOverlayTiles))
            add(RasterSpec("custom-overlay", p.customOverlayTiles, p.customOverlayAttribution))
    }
}

interface MapProvider { fun style(p: Preferences): String }
class RasterMapProvider(private val esriCredit: String = MapLayers.ESRI_CREDIT): MapProvider {
    override fun style(p: Preferences): String {
        val sources = JSONObject()
        val layers = JSONArray().put(JSONObject().put("id","background").put("type","background")
            .put("paint",JSONObject().put("background-color","#e6eee8")))
        rasterStack(p, esriCredit).forEach { spec ->
            val source = JSONObject().put("type","raster").put("tiles",JSONArray().put(spec.url))
                .put("tileSize",256).put("minzoom",spec.minZoom).put("maxzoom",spec.maxZoom)
                .put("attribution",spec.attribution)
            spec.bounds?.let { source.put("bounds",JSONArray(it)) }
            sources.put(spec.id,source)
            val layer = JSONObject().put("id",spec.id).put("type","raster").put("source",spec.id)
                .put("paint",JSONObject().put("raster-opacity",spec.opacity.toDouble()).put("raster-fade-duration",0))
            if (spec.id == "cadastre") layer.put("minzoom",13).put("maxzoom",21)
            layers.put(layer)
        }
        // Measurement layers are appended by ParcelMap after this style finishes loading.
        return JSONObject().put("version",8).put("sources",sources).put("layers",layers).toString()
    }
}

fun mapAttribution(p: Preferences, esriCredit: String = MapLayers.ESRI_CREDIT): String =
    if (!p.onlineMaps) "TerraParcel · WGS84" else rasterStack(p,esriCredit).map { it.attribution }
        .filter { it.isNotBlank() }.distinct().joinToString("\n")
