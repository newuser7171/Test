package al.terraparcel.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.room.withTransaction
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import al.terraparcel.domain.*
import al.terraparcel.data.*
import al.terraparcel.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.UUID

class LandViewModel(app:Application):AndroidViewModel(app) {
    val repo=ParcelRepository(LandDatabase.open(app))
    val parcels=repo.dao.observe().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val categories=repo.dao.categories().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val draft=MutableStateFlow(Draft())
    val prefs=MutableStateFlow(Preferences())
    val fix=MutableStateFlow<Fix?>(null)
    val walking=MutableStateFlow(false)
    val paused=MutableStateFlow(false)
    val ready=MutableStateFlow(false)
    val busy=MutableStateFlow(false)
    val message=MutableStateFlow<String?>(null)
    val selected=MutableStateFlow<Int?>(null)
    val undoAvailable=MutableStateFlow(false)
    val redoAvailable=MutableStateFlow(false)
    val cameraTarget=MutableStateFlow<List<Vertex>>(emptyList())
    private val undo=ArrayDeque<Draft>()
    private val redo=ArrayDeque<Draft>()
    private val location=PhoneLocation(app)
    private val photos=File(app.filesDir,"photos").apply { mkdirs() }
    private val backup=Backups(repo,photos)
    private var persistJob:Job?=null
    private var lastFixNanos=0L
    init {
        viewModelScope.launch {
            try {
                repo.dao.setting("preferences")?.let { prefs.value=codec.decodeFromString(it) }
                repo.dao.setting("draft")?.let { draft.value=codec.decodeFromString(it) }
                listOf("Farm","Field","Property","House","Construction","Forest").forEach { repo.dao.category(Category(it,"#167B62")) }
            } catch(e:Exception){message.value="Could not load settings: ${e.message}"}
            ready.value=true
        }
    }
    private fun history() {undoAvailable.value=undo.isNotEmpty();redoAvailable.value=redo.isNotEmpty()}
    fun edit(d:Draft) {
        undo.addLast(draft.value);if(undo.size>100)undo.removeFirst();redo.clear()
        draft.value=d;history();persist()
    }
    private fun persist() {
        persistJob?.cancel()
        val d=draft.value
        persistJob=viewModelScope.launch { delay(300);repo.dao.setting(AppSettings("draft",codec.encodeToString(d))) }
    }
    fun undo() {if(undo.isNotEmpty()){redo.addLast(draft.value);draft.value=undo.removeLast();selected.value=null;history();persist()}}
    fun redo() {if(redo.isNotEmpty()){undo.addLast(draft.value);draft.value=redo.removeLast();selected.value=null;history();persist()}}
    fun add(p:Vertex) {edit(draft.value.copy(points=if(draft.value.shape==Shape.POINT)listOf(p)else draft.value.points+p))}
    fun move(index:Int,p:Vertex) {
        val list=draft.value.points.toMutableList()
        if(index !in list.indices)return
        list[index]=p.copy(id=list[index].id,altitude=null,accuracy=null);edit(draft.value.copy(points=list))
    }
    fun deletePoint(index:Int) {edit(draft.value.copy(points=draft.value.points.filterIndexed { i,_->i!=index }));selected.value=null}
    fun insert(index:Int,p:Vertex) {
        val list=draft.value.points.toMutableList();list.add((index+1).coerceIn(0,list.size),p)
        edit(draft.value.copy(points=list))
    }
    fun new(shape:Shape) {
        stopWalk()
        edit(Draft(shape=shape));selected.value=null
    }
    fun open(p:Parcel) {stopWalk();edit(p.draft());selected.value=null;cameraTarget.value=draft.value.points}
    fun settings(p:Preferences) {
        require(p.maxAccuracy in 1f..100f && p.minSpacing in 0.5f..100f)
        prefs.value=p
        viewModelScope.launch { repo.dao.setting(AppSettings("preferences",codec.encodeToString(p))) }
    }
    fun startLocation() {
        location.start({ f->
            fix.value=f
            if(walking.value && !paused.value) {
                val rejection=RecordingPolicy.rejection(f.point,f.elapsedNanos,SystemClock.elapsedRealtimeNanos(),prefs.value.maxAccuracy)
                if(rejection!=null) message.value=rejection
                else if(f.elapsedNanos>lastFixNanos && (draft.value.points.lastOrNull()?.let { Geo.distance(it,f.point)>=prefs.value.minSpacing }!=false)) {
                    lastFixNanos=f.elapsedNanos;add(f.point)
                }
            }
        },{message.value=it})
    }
    fun startWalk() {walking.value=true;paused.value=false;startLocation()}
    fun pauseWalk(){paused.value=!paused.value}
    fun stopWalk(){walking.value=false;paused.value=false}
    fun background() { if(walking.value){paused.value=true;message.value="Walking paused while app is in background"};location.stop() }
    fun resumeLocation() { /* Restarted after explicit location action; no background location permission. */ }
    fun useFix() {
        val f=fix.value?:return run { message.value="Waiting for GPS" }
        val rejection=RecordingPolicy.rejection(f.point,f.elapsedNanos,SystemClock.elapsedRealtimeNanos(),prefs.value.maxAccuracy)
        if(rejection!=null){message.value=rejection;return}
        add(f.point)
    }
    fun save(d:Draft,onSaved:()->Unit={})=task {
        repo.save(d);draft.value=d;persist()
        message.value="Saved on this device";onSaved()
        automaticBackup()
    }
    fun duplicate(p:Parcel)=task {
        repo.save(p.draft().copy(id=UUID.randomUUID().toString(),name=p.name+" (copy)",created=System.currentTimeMillis(),points=p.draft().points.map { it.copy(id=UUID.randomUUID().toString()) }))
        automaticBackup()
    }
    fun trash(p:Parcel)=task {repo.dao.trash(p.id,System.currentTimeMillis());automaticBackup()}
    fun restore(p:Parcel)=task {repo.dao.restore(p.id);automaticBackup()}
    fun permanentDelete(p:Parcel)=task {
        val attachments=repo.dao.photos().filter { it.parcelId==p.id }
        repo.dao.delete(p.id);attachments.forEach { File(photos,it.filename).delete() };automaticBackup()
    }
    fun favorite(p:Parcel)=task { repo.save(p.draft().copy(favorite=!p.favorite));automaticBackup() }
    fun importFile(uri:Uri)=task {
        val data=withContext(Dispatchers.IO){getApplication<Application>().contentResolver.openInputStream(uri)!!.use { Exchange.limited(it) }}
        val ds=withContext(Dispatchers.Default){Exchange.read(data)}
        repo.db.withTransaction { ds.forEach { repo.save(it) } }
        message.value="Imported ${ds.size} measurements";automaticBackup()
    }
    fun exportFile(uri:Uri,format:String,all:Boolean)=task {
        val ds=if(all)repo.dao.all().filter { it.deletedAt==null }.map { it.draft() }else listOf(draft.value)
        val bytes=withContext(Dispatchers.Default){Exchange.export(ds,format)}
        withContext(Dispatchers.IO){getApplication<Application>().contentResolver.openOutputStream(uri,"wt")!!.use { it.write(bytes) }}
        message.value="Exported"
    }
    fun share(format:String,all:Boolean,onFile:(File)->Unit)=task {
        val ds=if(all)repo.dao.all().filter { it.deletedAt==null }.map { it.draft() }else listOf(draft.value)
        val file=withContext(Dispatchers.IO){
            val dir=File(getApplication<Application>().cacheDir,"exports").apply { mkdirs() }
            File(dir,"TerraParcel.$format").apply {writeBytes(Exchange.export(ds,format))}
        };onFile(file)
    }
    fun backupFile(uri:Uri)=task {
        val bytes=withContext(Dispatchers.IO){backup.create(prefs.value)}
        withContext(Dispatchers.IO){getApplication<Application>().contentResolver.openOutputStream(uri,"wt")!!.use { it.write(bytes) }}
        message.value="Backup exported"
    }
    fun restoreFile(uri:Uri)=task {
        withContext(Dispatchers.IO) {
            val bytes=getApplication<Application>().contentResolver.openInputStream(uri)!!.use { Exchange.limited(it,200_000_000) }
            prefs.value=backup.restore(bytes)
        }
        message.value="Backup restored; matching parcel IDs updated"
    }
    fun attach(uri:Uri,pointId:String?)=task {
        require(repo.dao.get(draft.value.id)!=null){"Save this measurement before attaching photos"}
        val name=UUID.randomUUID().toString()+".jpg"
        withContext(Dispatchers.IO) {
            val data=getApplication<Application>().contentResolver.openInputStream(uri)!!.use { Exchange.limited(it,15_000_000) }
            val options=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
            android.graphics.BitmapFactory.decodeByteArray(data,0,data.size,options)
            require(options.outWidth>0 && options.outHeight>0){"Not a supported photo"}
            File(photos,name).writeBytes(data)
        }
        repo.dao.photo(PhotoAttachment(UUID.randomUUID().toString(),draft.value.id,pointId,name,System.currentTimeMillis()))
        automaticBackup()
    }
    fun photoFile(p:PhotoAttachment)=File(photos,p.filename)
    private suspend fun automaticBackup() {
        withContext(Dispatchers.IO){
            try {
                val dir=File(getApplication<Application>().filesDir,"backups").apply {mkdirs()}
                val name="backup-"+java.time.LocalDate.now()+".zip"
                val file=File(dir,name);val temp=File(dir,"pending.tmp")
                temp.writeBytes(backup.create(prefs.value))
                check(temp.renameTo(file)){"Backup rename failed"}
                dir.listFiles()?.filter {it.extension=="zip"}?.sortedByDescending {it.name}?.drop(3)?.forEach {it.delete()}
            } catch(e:Exception){message.value="Measurement saved, but automatic backup failed: ${e.message}"}
        }
    }
    fun task(block:suspend ()->Unit) {
        if(busy.value)return
        viewModelScope.launch {
            busy.value=true
            try{block()}catch(e:CancellationException){throw e}catch(e:Exception){message.value=e.message?:"Operation failed"}
            finally{busy.value=false}
        }
    }
    override fun onCleared(){location.stop();repo.db.close();super.onCleared()}
}