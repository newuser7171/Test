package al.terraparcel.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import al.terraparcel.domain.Preferences
import al.terraparcel.map.MapLayers
import kotlin.math.roundToInt

/** Existing custom basemaps retain their URL, attribution and name. Overlays use separate fields. */
@Composable
fun MapLayerSelector(p: Preferences, change: (Preferences) -> Unit) {
    Text(tr("Basemap"))
    Strip {
        MapLayers.PRESETS.forEach { layer ->
            FilterChip(p.layer==layer,{change(MapLayers.selectBasemap(p,layer))},label={Text(tr(layer))})
        }
        if(p.customTiles.isNotBlank())FilterChip(p.layer !in MapLayers.PRESETS,
            {change(MapLayers.selectBasemap(p,"Custom"))},label={Text(p.customLayerName.ifBlank {if(p.layer !in MapLayers.PRESETS)p.layer else tr("Custom basemap")})})
    }
    Text(tr("Overlays"))
    Row(verticalAlignment=Alignment.CenterVertically) {
        Switch(p.cadastreEnabled,{change(p.copy(cadastreEnabled=it))})
        Text(tr("Albania Cadastre")+" (ASIG/ASHK)")
    }
    if(p.cadastreEnabled) {
        var opacity by remember(p.cadastreOpacity){mutableFloatStateOf(p.cadastreOpacity)}
        Text(tr("Cadastre opacity")+": "+(opacity*100).roundToInt()+"%")
        Slider(opacity,{opacity=it},onValueChangeFinished={change(p.copy(cadastreOpacity=opacity))})
        Text(tr("Cadastre is visible at parcel scales (zoom 13–21), within Albania. Missing service tiles leave the basemap visible."),style=MaterialTheme.typography.bodySmall)
    }
    if(p.customOverlayTiles.isNotBlank())Row(verticalAlignment=Alignment.CenterVertically) {
        Switch(p.customOverlayEnabled,{change(p.copy(customOverlayEnabled=it))})
        Text(p.customOverlayName)
    }
    Text(tr("Licensed custom raster / WMS / WMTS tiles"))
    var overlay by remember {mutableStateOf(false)}
    Strip {
        FilterChip(!overlay,{overlay=false},label={Text(tr("Custom basemap"))})
        FilterChip(overlay,{overlay=true},label={Text(tr("Custom overlay"))})
    }
    val savedName=if(overlay)p.customOverlayName else p.customLayerName.ifBlank {
        if(p.layer !in MapLayers.PRESETS)p.layer else "Custom"
    }
    var name by remember(overlay,savedName){mutableStateOf(savedName)}
    var url by remember(overlay,p.customTiles,p.customOverlayTiles){mutableStateOf(if(overlay)p.customOverlayTiles else p.customTiles)}
    var credit by remember(overlay,p.attribution,p.customOverlayAttribution){mutableStateOf(if(overlay)p.customOverlayAttribution else p.attribution)}
    OutlinedTextField(name,{name=it},label={Text(tr("Layer name"))},singleLine=true)
    OutlinedTextField(url,{url=it},label={Text("HTTPS tile URL")},supportingText={Text("{z}/{x}/{y} or {bbox-epsg-3857}")})
    OutlinedTextField(credit,{credit=it},label={Text(tr("Provider attribution"))})
    if(overlay)Text(tr("Use transparent PNG tiles for overlays."),style=MaterialTheme.typography.bodySmall)
    Action("Apply layer",MapLayers.validTemplate(url.trim()) && credit.isNotBlank()) {
        if(overlay)change(p.copy(customOverlayEnabled=true,customOverlayName=name.ifBlank{"Custom overlay"},
            customOverlayTiles=url.trim(),customOverlayAttribution=credit.trim()))
        else change(p.copy(layer="Custom",customLayerName=name.ifBlank{"Custom"},customTiles=url.trim(),attribution=credit.trim()))
    }
}
