package al.terraparcel.map

import android.annotation.SuppressLint
import android.graphics.PointF
import android.view.MotionEvent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import al.terraparcel.domain.*
import al.terraparcel.data.*
import al.terraparcel.location.Fix
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.*
import org.maplibre.android.geometry.*
import org.maplibre.android.camera.*
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.json.JSONObject
import org.json.JSONArray
import kotlin.math.*

interface MapProvider { fun style(p:Preferences):String }
class RasterMapProvider:MapProvider {
    override fun style(p:Preferences):String {
        val sources=JSONObject();val layers=JSONArray().put(JSONObject().put("id","background").put("type","background").put("paint",JSONObject().put("background-color","#e6eee8")))
        if(p.onlineMaps){
            val custom=p.layer!="OpenStreetMap" && p.customTiles.isNotBlank()
            val url=if(custom)p.customTiles else "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
            require(url.startsWith("https://")){"Map services must use HTTPS"}
            val attribution=if(custom)p.attribution else "© OpenStreetMap contributors"
            sources.put("basemap",JSONObject().put("type","raster").put("tiles",JSONArray().put(url)).put("tileSize",256).put("maxzoom",19).put("attribution",attribution))
            layers.put(JSONObject().put("id","basemap").put("type","raster").put("source","basemap"))
        }
        return JSONObject().put("version",8).put("sources",sources).put("layers",layers).toString()
    }
}
class MapHandle {
    private var pending:List<Vertex> = emptyList()
    var position:CameraPosition?=null
    var map:MapLibreMap?=null
        set(value){field=value;if(value!=null && pending.isNotEmpty())go(pending)}
    fun center():Vertex?=map?.cameraPosition?.target?.let { Vertex(it.latitude,it.longitude) }
    fun go(points:List<Vertex>){
        pending=points
        val m=map?:return
        if(points.isEmpty())return
        if(points.size==1)m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(points[0].lat,points[0].lon),18.0))
        else m.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points.map{LatLng(it.lat,it.lon)}).build(),80))
    }
}
private fun render(m:MapLibreMap,d:Draft,saved:List<Parcel>,fix:Fix?,selected:Int?) {
    val features=JSONArray()
    fun feature(g:JSONObject,color:String)=JSONObject().put("type","Feature").put("geometry",g).put("properties",JSONObject().put("color",color))
    saved.forEach { p->runCatching {features.put(feature(Exchange.geometry(p.draft()),p.color))} }
    if(d.points.isNotEmpty()){
        val shape=if(d.shape==Shape.POLYGON && d.points.size<3) if(d.points.size==1)Shape.POINT else Shape.LINE else d.shape
        features.put(feature(Exchange.geometry(d.copy(shape=shape)),d.color))
        d.points.forEachIndexed { i,p->features.put(feature(Exchange.geometry(Draft(shape=Shape.POINT,points=listOf(p))),if(i==selected)"#F09132" else "#173D36")) }
    }
    fix?.let {features.put(feature(Exchange.geometry(Draft(shape=Shape.POINT,points=listOf(it.point))),"#237BE3"))}
    m.style?.getSourceAs<GeoJsonSource>("measurements")?.setGeoJson(JSONObject().put("type","FeatureCollection").put("features",features).toString())
}
@SuppressLint("ClickableViewAccessibility")
@Composable
fun ParcelMap(modifier:Modifier,draft:Draft,parcels:List<Parcel>,prefs:Preferences,fix:Fix?,selected:Int?,handle:MapHandle,onAdd:(Vertex)->Unit,onSelect:(Int)->Unit,onMove:(Int,Vertex)->Unit,onCamera:(Double)->Unit) {
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current
    val view=remember { MapLibre.getInstance(context);MapView(context).apply {onCreate(null)} }
    val currentDraft by rememberUpdatedState(draft)
    val currentSaved by rememberUpdatedState(parcels)
    val currentFix by rememberUpdatedState(fix)
    val currentSelected by rememberUpdatedState(selected)
    val add by rememberUpdatedState(onAdd);val select by rememberUpdatedState(onSelect);val move by rememberUpdatedState(onMove)
    val camera by rememberUpdatedState(onCamera)
    var loaded by remember { mutableStateOf<MapLibreMap?>(null) }
    val styleJson=remember(prefs.onlineMaps,prefs.layer,prefs.customTiles,prefs.attribution){RasterMapProvider().style(prefs)}
    DisposableEffect(view,owner){
        val observer=LifecycleEventObserver { _,event->when(event){
            Lifecycle.Event.ON_START->view.onStart()
            Lifecycle.Event.ON_RESUME->view.onResume()
            Lifecycle.Event.ON_PAUSE->view.onPause()
            Lifecycle.Event.ON_STOP->view.onStop()
            else->Unit
        } }
        owner.lifecycle.addObserver(observer)
        if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))view.onStart()
        if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))view.onResume()
        onDispose { owner.lifecycle.removeObserver(observer);view.onPause();view.onStop();handle.position=handle.map?.cameraPosition;view.onDestroy();handle.map=null }
    }
    AndroidView(modifier=modifier,factory={
        view.getMapAsync { m->
            m.cameraPosition=handle.position?:CameraPosition.Builder().target(LatLng(41.3275,19.8187)).zoom(15.0).build()
            handle.map=m;loaded=m
            m.uiSettings.isCompassEnabled=true
            m.addOnMapLongClickListener { p->add(Vertex(p.latitude,p.longitude));true }
            m.addOnMapClickListener { p->
                val screen=m.projection.toScreenLocation(p)
                val nearest=currentDraft.points.indices.minByOrNull { i->val q=m.projection.toScreenLocation(LatLng(currentDraft.points[i].lat,currentDraft.points[i].lon));hypot((q.x-screen.x).toDouble(),(q.y-screen.y).toDouble()) }
                if(nearest!=null){
                    val q=m.projection.toScreenLocation(LatLng(currentDraft.points[nearest].lat,currentDraft.points[nearest].lon))
                    if(hypot((q.x-screen.x).toDouble(),(q.y-screen.y).toDouble())<32*context.resources.displayMetrics.density){select(nearest);true}else false
                }else false
            }
            m.addOnCameraIdleListener {
                val a=m.projection.fromScreenLocation(PointF(0f,view.height/2f))
                val b=m.projection.fromScreenLocation(PointF(100*context.resources.displayMetrics.density,view.height/2f))
                camera(Geo.distance(Vertex(a.latitude,a.longitude),Vertex(b.latitude,b.longitude)))
            }
            var drag:Int?=null
            view.setOnTouchListener { v,event->
                when(event.actionMasked){
                    MotionEvent.ACTION_DOWN->{
                        val i=currentSelected
                        if(i!=null && i in currentDraft.points.indices){
                            val p=currentDraft.points[i];val q=m.projection.toScreenLocation(LatLng(p.lat,p.lon))
                            if(hypot((q.x-event.x).toDouble(),(q.y-event.y).toDouble())<32*context.resources.displayMetrics.density){drag=i;v.parent?.requestDisallowInterceptTouchEvent(true)}
                        }
                        drag!=null
                    }
                    MotionEvent.ACTION_MOVE->{
                        drag?.let { i->
                            val p=m.projection.fromScreenLocation(PointF(event.x,event.y))
                            val ps=currentDraft.points.toMutableList()
                            if(i in ps.indices){ps[i]=Vertex(p.latitude,p.longitude);render(m,currentDraft.copy(points=ps),currentSaved,currentFix,i)}
                        };drag!=null
                    }
                    MotionEvent.ACTION_UP->{
                        val i=drag
                        if(i!=null){val p=m.projection.fromScreenLocation(PointF(event.x,event.y));move(i,Vertex(p.latitude,p.longitude));drag=null;v.parent?.requestDisallowInterceptTouchEvent(false);true}else false
                    }
                    MotionEvent.ACTION_CANCEL->{val active=drag!=null;drag=null;render(m,currentDraft,currentSaved,currentFix,currentSelected);active}
                    else->drag!=null
                }
            }
        };view
    })
    LaunchedEffect(loaded,styleJson){
        loaded?.setStyle(Style.Builder().fromJson(styleJson)){ style->
            style.addSource(GeoJsonSource("measurements"))
            style.addLayer(FillLayer("areas","measurements").withFilter(eq(geometryType(),literal("Polygon"))).withProperties(fillColor(get("color")),fillOpacity(0.22f)))
            style.addLayer(LineLayer("edges","measurements").withFilter(neq(geometryType(),literal("Point"))).withProperties(lineColor(get("color")),lineWidth(3f)))
            style.addLayer(CircleLayer("vertices","measurements").withFilter(eq(geometryType(),literal("Point"))).withProperties(circleColor(get("color")),circleRadius(7f),circleStrokeColor("#FFFFFF"),circleStrokeWidth(2f)))
            loaded?.let {render(it,currentDraft,currentSaved,currentFix,currentSelected)}
        }
    }
    LaunchedEffect(loaded,draft,parcels,fix,selected){loaded?.let {render(it,draft,parcels,fix,selected)}}
}
