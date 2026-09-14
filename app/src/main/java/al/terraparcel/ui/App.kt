package al.terraparcel.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.LocaleList
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import al.terraparcel.R
import al.terraparcel.domain.*
import al.terraparcel.data.*
import al.terraparcel.map.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.io.File
import kotlin.math.roundToInt

val LocalLanguage=staticCompositionLocalOf { "en" }
@Composable fun T(s:String):String {
    val context=LocalContext.current
    val language=LocalLanguage.current
    val resources=remember(context,language){
        context.createConfigurationContext(Configuration(context.resources.configuration).apply {setLocales(LocaleList(Locale.forLanguageTag(language)))}).resources
    }
    val id=localizedLabels[s.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"),"_").trim('_')]?:0
    return if(id!=0)resources.getString(id)else s
}
fun number(d:Double)=String.format(Locale.getDefault(),"%,.2f",d)
@Composable fun Action(text:String,enabled:Boolean=true,click:()->Unit) {OutlinedButton(onClick=click,enabled=enabled,contentPadding=PaddingValues(horizontal=14.dp,vertical=10.dp)){Text(T(text))}}
@Composable fun Strip(content:@Composable RowScope.()->Unit){Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically,content=content)}
@Composable fun TerraApp(vm:LandViewModel=viewModel()) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val dark=when(prefs.theme){"dark"->true;"light"->false;else->isSystemInDarkTheme()}
    val colors=if(dark)darkColorScheme(primary=Color(0xFF79D7B9),secondary=Color(0xFFDBC49C))else lightColorScheme(primary=Color(0xFF126B55),secondary=Color(0xFF866426),background=Color(0xFFF4F7F3))
    CompositionLocalProvider(LocalLanguage provides prefs.language){
        MaterialTheme(colorScheme=colors){Surface(Modifier.fillMaxSize()){AppContent(vm)}}
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AppContent(vm:LandViewModel) {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val draft by vm.draft.collectAsStateWithLifecycle()
    val parcels by vm.parcels.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val fix by vm.fix.collectAsStateWithLifecycle()
    val walking by vm.walking.collectAsStateWithLifecycle()
    val paused by vm.paused.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val undo by vm.undoAvailable.collectAsStateWithLifecycle()
    val redo by vm.redoAvailable.collectAsStateWithLifecycle()
    val target by vm.cameraTarget.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf("Home") }
    var showPoint by remember { mutableStateOf(false) }
    var showPhotos by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }
    var coordinateDialog by remember { mutableStateOf(false) }
    var exportDialog by remember { mutableStateOf(false) }
    var permission by remember { mutableStateOf<String?>(null) }
    var fullScreen by remember { mutableStateOf(false) }
    var follow by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(0.0) }
    var format by remember { mutableStateOf("geojson") }
    var exportAll by remember { mutableStateOf(false) }
    var photoPoint by remember { mutableStateOf<String?>(null) }
    var restoreWarning by remember { mutableStateOf(false) }
    var deleteForever by remember { mutableStateOf<Parcel?>(null) }
    val handle=remember { MapHandle() }
    val snackbar=remember { SnackbarHostState() }
    val importLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::importFile)}
    val exportLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){it?.let {u->vm.exportFile(u,format,exportAll)}}
    val backupLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){it?.let(vm::backupFile)}
    val restoreLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::restoreFile)}
    val photoLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let { u->vm.attach(u,photoPoint)}}
    var requestedAction by remember { mutableStateOf("location") }
    fun locationAction(action:String){
        vm.startLocation()
        if(action=="walk")vm.startWalk()else{follow=true;fix?.let {handle.go(listOf(it.point))}}
    }
    val permissionLauncher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){result->
        if(result.values.any {it})locationAction(requestedAction)else vm.message.value="Permission declined; use manual map measurement"
    }
    DisposableEffect(owner){
        val observer=LifecycleEventObserver { _,e->if(e==Lifecycle.Event.ON_STOP)vm.background() }
        owner.lifecycle.addObserver(observer)
        onDispose {owner.lifecycle.removeObserver(observer)}
    }
    DisposableEffect(walking,paused){
        val activity=context as? ComponentActivity
        if(walking && !paused)activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
    }
    LaunchedEffect(message){message?.let {snackbar.showSnackbar(it);vm.message.value=null}}
    LaunchedEffect(target,page){if(page=="Map" && target.isNotEmpty()){kotlinx.coroutines.delay(500);handle.go(target)}}
    LaunchedEffect(fix,follow){if(follow)fix?.let {handle.go(listOf(it.point))}}
    if(!ready){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()};return}
    val active=parcels.filter {it.deletedAt==null}
    val metrics=Geo.metrics(draft.points,draft.shape)
    Scaffold(
        snackbarHost={SnackbarHost(snackbar)},
        topBar={if(!fullScreen)TopAppBar(title={Column {Text("TerraParcel",fontWeight=FontWeight.Bold);Text(T("Land measurement"),style=MaterialTheme.typography.labelSmall)}},actions={TextButton(onClick={coordinateDialog=true}){Text(T("Coordinates"))}})},
        bottomBar={if(!fullScreen)NavigationBar {listOf("Home","Map","My Parcels","Settings").forEach { p->NavigationBarItem(selected=page==p,onClick={page=p},icon={Text(when(p){"Home"->"⌂";"Map"->"◇";"My Parcels"->"▤";else->"⚙"})},label={Text(T(p),maxLines=1)})}}}
    ){padding->
        Column(Modifier.padding(padding).fillMaxSize()){
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            when(page){
                "Home"->Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text(T("Your land, on your device"),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp)){
                        Text("${active.size} "+T("My Parcels"),style=MaterialTheme.typography.titleLarge)
                        Text(number(Geo.area(active.sumOf {it.area},prefs.areaUnit))+" "+prefs.areaUnit,style=MaterialTheme.typography.headlineLarge)
                        Text(T("Total Saved Area"))
                    }}
                    Box(Modifier.height(170.dp).fillMaxWidth()){
                        val previewHandle=remember {MapHandle()}
                        ParcelMap(Modifier.fillMaxSize(),Draft(),active,prefs,null,null,previewHandle,{},{},{_,_->},{})
                        LaunchedEffect(active){kotlinx.coroutines.delay(600);previewHandle.go(active.flatMap {it.draft().points})}
                        FilledTonalButton(onClick={page="Map"},modifier=Modifier.align(Alignment.TopEnd).padding(8.dp)){Text(T("Open map"))}
                    }
                    Strip {Action("Measure Area"){vm.new(Shape.POLYGON);page="Map"};Action("Import"){importLauncher.launch(arrayOf("*/*"))};Action("Export"){exportAll=true;exportDialog=true}}
                    Text(T("Recent Measurements"),style=MaterialTheme.typography.titleMedium)
                    active.take(5).forEach {p->ParcelCard(p,prefs,{vm.open(p);page="Map"},{vm.favorite(p)})}
                    Text(T("Phone measurements are estimates, not official cadastral or legal boundaries."),style=MaterialTheme.typography.bodySmall)
                }
                "My Parcels"->{
                    var query by remember {mutableStateOf("")}
                    var sort by remember {mutableStateOf("Recent")}
                    var trash by remember {mutableStateOf(false)}
                    OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=12.dp),label={Text(T("Search saved parcels"))},singleLine=true)
                    Strip {Action(if(trash)"Show active" else "Trash"){trash=!trash};Action(sort){sort=when(sort){"Recent"->"Name";"Name"->"Area";else->"Recent"}};Action("Export all"){exportAll=true;exportDialog=true}}
                    val filtered=parcels.filter {(it.deletedAt!=null)==trash && (it.name+" "+it.notes+" "+it.owner+" "+it.category).contains(query,true)}
                    val sorted=when(sort){"Name"->filtered.sortedBy{it.name.lowercase()};"Area"->filtered.sortedByDescending{it.area};else->filtered.sortedByDescending{it.modified}}
                    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                        if(sorted.isEmpty())item{Text(T("No saved measurements"))}
                        items(sorted,key={it.id}){p->
                            Column{
                                ParcelCard(p,prefs,{vm.open(p);page="Map"},{vm.favorite(p)})
                                Strip {
                                    if(trash){Action("Restore",!busy){vm.restore(p)};Action("Delete permanently",!busy){deleteForever=p}}
                                    else {Action("Edit"){vm.open(p);page="Map"};Action("Duplicate",!busy){vm.duplicate(p)};Action("Delete",!busy){vm.trash(p)};Action("Navigate"){navigate(context,p,vm)}}
                                }
                            }
                        }
                    }
                }
                "Settings"->SettingsPanel(prefs,vm::settings,{backupLauncher.launch("TerraParcel-backup.zip")},{restoreWarning=true},{exportAll=true;exportDialog=true},{page="GPS Tools"})
                "GPS Tools"->Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text(T("GPS Tools"),style=MaterialTheme.typography.headlineMedium)
                    Text("PHONE GPS")
                    Text(fix?.point?.let {coordinateText(it)}?:T("Waiting for GPS"))
                    Text(T("Speed")+": "+(fix?.speed?.let {number(it.toDouble())+" m/s"}?:"—"))
                    Text(T("Heading")+": "+(fix?.bearing?.let {number(it.toDouble())+"°"}?:"—"))
                    Text(T("Points")+": "+draft.points.size)
                    Text(T("Distance walked")+": "+number(Geo.metrics(draft.points,Shape.LINE).second)+" m")
                    Action("Current Location"){permission="location"}
                    Action("Map"){page="Map"}
                    Text(T("Accuracy labels are guidance only. Accuracy is reported by Android."))
                }
                "Map"->{
                    Strip {Action("Layers"){page="Settings"};Action("Search"){page="My Parcels"};Action(if(fullScreen)"Exit full screen" else "Full screen"){fullScreen=!fullScreen};Action("GPS Tools"){fullScreen=false;page="GPS Tools"}}
                    Box(Modifier.weight(1f).fillMaxWidth()){
                        ParcelMap(Modifier.fillMaxSize(),draft,if(prefs.showSaved)active.filter{it.id!=draft.id}else emptyList(),prefs,fix,selected,handle,{follow=false;vm.add(it)}, {vm.selected.value=it;showPoint=true},{i,p->follow=false;vm.move(i,p)},{scale=it})
                        Text("+",Modifier.align(Alignment.Center),color=Color(0xFF173D36),style=MaterialTheme.typography.headlineLarge)
                        Column(Modifier.align(Alignment.CenterEnd).padding(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                            FloatingActionButton(onClick={permission="location"}){Text("◎")}
                            FloatingActionButton(onClick={follow=false;handle.center()?.let(vm::add)}){Text("+")}
                        }
                        Surface(Modifier.align(Alignment.BottomStart).padding(8.dp),shape=RoundedCornerShape(8.dp),tonalElevation=4.dp){
                            Column(Modifier.padding(8.dp)){Box(Modifier.width(100.dp).height(2.dp).background(MaterialTheme.colorScheme.onSurface));Text(number(scale)+" m",style=MaterialTheme.typography.labelSmall)}
                        }
                        if(!prefs.onlineMaps)Surface(Modifier.align(Alignment.TopCenter).padding(8.dp),shape=RoundedCornerShape(8.dp)){Text(T("Offline canvas · enable maps in Layers"),Modifier.padding(8.dp))}
                    }
                    Surface(tonalElevation=3.dp){
                        Column(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                            Text(if(draft.shape==Shape.POLYGON)number(Geo.area(metrics.first,prefs.areaUnit))+" "+prefs.areaUnit else T("Distance")+": "+number(Geo.length(metrics.second,prefs.lengthUnit))+" "+prefs.lengthUnit,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                            Text(T("Perimeter")+": "+number(Geo.length(metrics.second,prefs.lengthUnit))+" "+prefs.lengthUnit+"  ·  "+draft.points.size+" "+T("Points"),style=MaterialTheme.typography.bodySmall)
                            val accuracy=fix?.point?.accuracy
                            Text("GPS: "+(accuracy?.let {"±${number(it.toDouble())} m · "+T(Geo.accuracyLabel(it))}?:T("Unknown")),color=if(accuracy!=null && accuracy<=prefs.maxAccuracy)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            Strip {
                                Action("Undo",undo){vm.undo()};Action("Redo",redo){vm.redo()}
                                Action("Save",!busy && draft.points.isNotEmpty()){vm.stopWalk();saveDialog=true}
                                if(!walking)Action("GPS Walk"){if(draft.shape!=Shape.POLYGON)vm.message.value="Select a polygon measurement first" else permission="walk"}
                                else {Action(if(paused)"Resume" else "Pause"){if(paused)vm.startLocation();vm.pauseWalk()};Action("Close Parcel",draft.points.size>=3){vm.stopWalk();saveDialog=true}}
                                Action("GPS point"){vm.useFix()}
                            }
                            Strip {
                                Action("New polygon"){vm.new(Shape.POLYGON)};Action("New line"){vm.new(Shape.LINE)};Action("New point"){vm.new(Shape.POINT)}
                                Action("Export"){exportAll=false;exportDialog=true}
                                Action("Photo"){photoPoint=null;photoLauncher.launch(arrayOf("image/*"))}
                                Action("Photos"){showPhotos=true}
                                Action("Elevation profile"){showProfile=true}
                                Action("Screenshot"){handle.map?.snapshot { bitmap->
                                    vm.task {
                                        val file=withContext(Dispatchers.IO){File(context.cacheDir,"exports").apply{mkdirs()}.let {dir->File(dir,"map.png").apply {outputStream().use {attributedSnapshot(bitmap,prefs).compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}}}
                                        shareFile(context,file)
                                    }
                                }}
                            }
                            Text(T("Long-press to add. Tap a vertex, then drag to move."),style=MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
    if(showPoint && selected!=null && selected!! in draft.points.indices && page=="Map") {
        val i=selected!!;val p=draft.points[i]
        ModalBottomSheet(onDismissRequest={showPoint=false}){
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(T("Coordinates")+" · ${i+1}",style=MaterialTheme.typography.titleLarge)
                Text(coordinateText(p))
                if(i>0)Text(T("Distance")+": "+number(Geo.distance(draft.points[i-1],p))+" m · "+T("Bearing")+": "+number(Geo.bearing(draft.points[i-1],p))+"°")
                Strip {
                    Action("Copy"){context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("WGS84","${p.lat}, ${p.lon}"))}
                    Action("Delete"){vm.deletePoint(i)}
                    Action("Insert after"){handle.center()?.let {vm.insert(i,it)};vm.selected.value=null}
                    Action("Photo"){photoPoint=p.id;photoLauncher.launch(arrayOf("image/*"))}
                }
                Action("Move on map"){showPoint=false;vm.message.value="Drag the selected orange point to move it"}
                CoordinateEdit(p){vm.move(i,it);vm.selected.value=null}
                Text(T("Close this sheet to continue drawing."),style=MaterialTheme.typography.bodySmall)
            }
        }
    }
    if(showPhotos)ModalBottomSheet(onDismissRequest={showPhotos=false}){PhotoGallery(vm,draft.id)}
    if(showProfile)ModalBottomSheet(onDismissRequest={showProfile=false}){ElevationProfile(draft)}
    if(saveDialog)SaveDialog(draft,{saveDialog=false}){vm.save(it){saveDialog=false}}
    if(coordinateDialog)CoordinateDialog({coordinateDialog=false}){p->follow=false;vm.cameraTarget.value=listOf(p);page="Map";coordinateDialog=false}
    if(permission!=null)AlertDialog(onDismissRequest={permission=null},title={Text(T("Location permission"))},text={Text(T("GPS is used to show your position and record boundary points. Manual measurement works without permission. Walking pauses when the app leaves the foreground."))},confirmButton={TextButton(onClick={requestedAction=permission!!;permission=null;permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}){Text(T("Continue"))}},dismissButton={TextButton(onClick={permission=null}){Text(T("Cancel"))}})
    if(exportDialog)AlertDialog(onDismissRequest={exportDialog=false},title={Text(T("Export"))},text={Column{
        Text(T(if(exportAll)"All active measurements" else "Current measurement"))
        listOf("geojson","kml","kmz","gpx","csv").forEach {v->Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected=format==v,onClick={format=v});Text(v.uppercase())}}
    }},confirmButton={TextButton(onClick={exportDialog=false;exportLauncher.launch("TerraParcel.$format")}){Text(T("Save file"))}},dismissButton={TextButton(onClick={exportDialog=false;vm.share(format,exportAll){shareFile(context,it)}}){Text(T("Share"))}})
    if(restoreWarning)AlertDialog(onDismissRequest={restoreWarning=false},title={Text(T("Restore backup"))},text={Text(T("Matching parcel IDs will be updated. Other measurements are kept. Export a backup first if needed."))},confirmButton={TextButton(onClick={restoreWarning=false;restoreLauncher.launch(arrayOf("application/zip","application/octet-stream"))}){Text(T("Continue"))}},dismissButton={TextButton(onClick={restoreWarning=false}){Text(T("Cancel"))}})
    deleteForever?.let {p->AlertDialog(onDismissRequest={deleteForever=null},title={Text(T("Delete permanently"))},text={Text(p.name)},confirmButton={TextButton(onClick={vm.permanentDelete(p);deleteForever=null}){Text(T("Delete"))}},dismissButton={TextButton(onClick={deleteForever=null}){Text(T("Cancel"))}})}
}
fun coordinateText(p:Vertex)= "WGS84: ${p.lat}, ${p.lon}\nUTM: "+runCatching{Geo.utm(p).toString()}.getOrDefault("Outside UTM range")+"\nElevation: "+(p.altitude?.let{number(it)+" m"}?:"—")+"\nGPS accuracy: "+(p.accuracy?.let{"±$it m"}?:"—")
private fun shareFile(c:Context,f:File){
    val uri=FileProvider.getUriForFile(c,c.packageName+".files",f)
    c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {type=if(f.extension=="png")"image/png" else "application/octet-stream";putExtra(Intent.EXTRA_STREAM,uri);clipData=ClipData.newRawUri("export",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Share"))
}
private fun navigate(c:Context,p:Parcel,vm:LandViewModel){
    val point=p.draft().points.first()
    try{c.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("geo:${point.lat},${point.lon}?q=${point.lat},${point.lon}("+Uri.encode(p.name)+")")))}
    catch(e:android.content.ActivityNotFoundException){vm.message.value="No navigation application installed"}
}
@Composable private fun ParcelCard(p:Parcel,prefs:Preferences,open:()->Unit,favorite:()->Unit){
    Card(onClick=open,modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){
            Text(p.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            Text(if(p.shape==Shape.POLYGON.name)number(Geo.area(p.area,prefs.areaUnit))+" "+prefs.areaUnit else number(Geo.length(p.perimeter,prefs.lengthUnit))+" "+prefs.lengthUnit)
            Text(T("Perimeter")+": "+number(Geo.length(p.perimeter,prefs.lengthUnit))+" "+prefs.lengthUnit+" · "+p.category,style=MaterialTheme.typography.bodySmall)
            Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(p.modified)),style=MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick=favorite){Text(if(p.favorite)"★" else "☆")}
    }}
}
@Composable private fun SaveDialog(d:Draft,dismiss:()->Unit,save:(Draft)->Unit){
    var name by remember {mutableStateOf(d.name)}
    var notes by remember {mutableStateOf(d.notes)}
    var owner by remember {mutableStateOf(d.owner)}
    var category by remember {mutableStateOf(d.category)}
    var color by remember {mutableStateOf(d.color)}
    AlertDialog(onDismissRequest=dismiss,title={Text(T("Save parcel"))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedTextField(name,{name=it},label={Text(T("Name"))},singleLine=true)
        OutlinedTextField(notes,{notes=it},label={Text(T("Notes"))})
        OutlinedTextField(owner,{owner=it},label={Text(T("Owner"))},singleLine=true)
        OutlinedTextField(category,{category=it},label={Text(T("Category"))},singleLine=true)
        Strip {listOf("Farm","Field","Property","House","Construction","Forest").forEach {c->Action(c){category=c}}}
        Strip {listOf("#167B62","#2F6FC0","#CB7331","#8C56A7","#D04F60").forEach {c->Box(Modifier.size(44.dp).background(Color(android.graphics.Color.parseColor(c)),RoundedCornerShape(12.dp)).clickable {color=c},contentAlignment=Alignment.Center){if(color==c)Text("✓",color=Color.White)}}}
        Text(T("Phone measurements are estimates, not official cadastral or legal boundaries."),style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={save(d.copy(name=name,notes=notes,owner=owner,category=category.ifBlank{"Property"},color=color))},enabled=name.isNotBlank()){Text(T("Save"))}},dismissButton={TextButton(onClick=dismiss){Text(T("Cancel"))}})
}
@Composable private fun CoordinateEdit(p:Vertex,done:(Vertex)->Unit){
    var lat by remember {mutableStateOf(p.lat.toString())};var lon by remember {mutableStateOf(p.lon.toString())}
    var error by remember {mutableStateOf<String?>(null)}
    OutlinedTextField(lat,{lat=it},label={Text("Latitude")},singleLine=true)
    OutlinedTextField(lon,{lon=it},label={Text("Longitude")},singleLine=true)
    error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    Action("Apply"){runCatching {Vertex(lat.trim().toDouble(),lon.trim().toDouble())}.onSuccess(done).onFailure {error=it.message}}
}
@Composable private fun CoordinateDialog(dismiss:()->Unit,go:(Vertex)->Unit){
    AlertDialog(onDismissRequest=dismiss,title={Text(T("Go to coordinates"))},text={Column{Text("WGS84 · decimal degrees");CoordinateEdit(Vertex(41.3275,19.8187),go)}},confirmButton={TextButton(onClick=dismiss){Text(T("Cancel"))}})
}
@Composable private fun SettingsPanel(p:Preferences,change:(Preferences)->Unit,backup:()->Unit,restore:()->Unit,export:()->Unit,gps:()->Unit){
    var tile by remember(p.customTiles){mutableStateOf(p.customTiles)}
    var attribution by remember(p.attribution){mutableStateOf(p.attribution)}
    var layer by remember(p.layer){mutableStateOf(if(p.layer=="OpenStreetMap")"Custom" else p.layer)}
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(T("Settings"),style=MaterialTheme.typography.headlineMedium)
        Strip {Action("English"){change(p.copy(language="en"))};Action("Shqip"){change(p.copy(language="sq"))}}
        Text(T("Theme"));Strip {listOf("auto","light","dark").forEach {s->Action(s){change(p.copy(theme=s))}}}
        Text(T("Area units"));Strip {listOf("m²","ha","ac","km²").forEach {u->FilterChip(p.areaUnit==u,{change(p.copy(areaUnit=u))},label={Text(u)})}}
        Text(T("Distance units"));Strip {listOf("m","km","ft","mi").forEach {u->FilterChip(p.lengthUnit==u,{change(p.copy(lengthUnit=u))},label={Text(u)})}}
        Text(T("Only record point when accuracy")+ " ≤ ${p.maxAccuracy.roundToInt()} m")
        Slider(p.maxAccuracy,{change(p.copy(maxAccuracy=it.roundToInt().toFloat()))},valueRange=1f..100f,steps=98)
        Text(T("Minimum point spacing")+": ${number(p.minSpacing.toDouble())} m")
        Slider(p.minSpacing,{change(p.copy(minSpacing=it))},valueRange=0.5f..20f)
        Row(verticalAlignment=Alignment.CenterVertically){Switch(p.showSaved,{change(p.copy(showSaved=it))});Text(T("Show saved parcels"))}
        HorizontalDivider()
        Text(T("Map layers"),style=MaterialTheme.typography.titleLarge)
        Text(T("Online maps send tile requests revealing the viewed area to the chosen provider. Parcel geometry and GPS history are not uploaded."))
        Row(verticalAlignment=Alignment.CenterVertically){Switch(p.onlineMaps,{change(p.copy(onlineMaps=it))});Text(T("Enable online maps"))}
        Action("OpenStreetMap"){change(p.copy(layer="OpenStreetMap"))}
        Text(T("Licensed custom raster / WMS / WMTS tiles"))
        OutlinedTextField(layer,{layer=it},label={Text(T("Layer name"))},singleLine=true)
        OutlinedTextField(tile,{tile=it},label={Text("HTTPS tile URL")},supportingText={Text("{z}/{x}/{y} or {bbox-epsg-3857}")})
        OutlinedTextField(attribution,{attribution=it},label={Text(T("Provider attribution"))})
        Action("Apply layer",tile.startsWith("https://") && attribution.isNotBlank() && (tile.contains("{z}") || tile.contains("{bbox-epsg-3857}"))){change(p.copy(layer=layer.ifBlank{"Custom"},customTiles=tile,attribution=attribution))}
        Text(T("Use only services whose terms permit use. Cadastral layers are reference data, separate from your measurements."),style=MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Action("GPS Tools",click=gps)
        Action("Export all",click=export)
        Action("Local backup",click=backup)
        Action("Restore backup",click=restore)
        Text(T("Automatic backups keep the last three days on this device. Export a backup to protect against uninstall or device loss."))
        Text(T("Future features"),style=MaterialTheme.typography.titleMedium)
        Text(T("Downloadable map regions, MGRS, external Bluetooth GNSS/RTK, Shapefile, GeoPackage and a verified ASIG catalogue are not included in this version."))
        Text("TerraParcel 0.1.0 · WGS84\nMapLibre Native · GeographicLib\n© OpenStreetMap contributors")
    }
}

@Composable private fun PhotoGallery(vm:LandViewModel,id:String) {
    val photos by remember(id){vm.repo.dao.photosFor(id)}.collectAsState(initial=emptyList())
    LazyColumn(Modifier.fillMaxWidth().heightIn(max=600.dp),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item {Text(T("Photos"),style=MaterialTheme.typography.titleLarge)}
        if(photos.isEmpty())item {Text(T("No photos attached"))}
        items(photos,key={it.id}){p->
            var bitmap by remember(p.filename){mutableStateOf<android.graphics.Bitmap?>(null)}
            LaunchedEffect(p.filename){
                bitmap=withContext(Dispatchers.IO){android.graphics.BitmapFactory.decodeFile(vm.photoFile(p).path,android.graphics.BitmapFactory.Options().apply{inSampleSize=4})}
            }
            bitmap?.let {Image(it.asImageBitmap(),T("Photo"),Modifier.fillMaxWidth().height(220.dp))}
            Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(p.timestamp))+(p.pointId?.let{" · "+T("Point photo")}?:""))
        }
    }
}
@Composable private fun ElevationProfile(d:Draft){
    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(T("Elevation profile"),style=MaterialTheme.typography.titleLarge)
        val points=d.points
        if(points.size<2 || points.any{it.altitude==null})Text(T("Elevation is unavailable for some points. No elevation data is invented."))
        else {
            val distances=mutableListOf(0.0)
            points.zipWithNext().forEach{(a,b)->distances+=distances.last()+Geo.distance(a,b)}
            val min=points.minOf{it.altitude!!};val max=points.maxOf{it.altitude!!}
            val color=MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxWidth().height(180.dp)){
                val path=androidx.compose.ui.graphics.Path()
                points.forEachIndexed{i,p->
                    val x=(distances[i]/distances.last().coerceAtLeast(0.01)*size.width).toFloat()
                    val y=(size.height-(p.altitude!!-min)/(max-min).coerceAtLeast(1.0)*size.height).toFloat()
                    if(i==0)path.moveTo(x,y)else path.lineTo(x,y)
                }
                drawPath(path,color,style=androidx.compose.ui.graphics.drawscope.Stroke(width=4f))
            }
            Text("${number(min)}–${number(max)} m · ${number(distances.last())} m")
            Text(T("Elevation is the recorded receiver altitude; no terrain service is queried."))
        }
    }
}
