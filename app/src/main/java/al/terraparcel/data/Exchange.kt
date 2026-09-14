package al.terraparcel.data

import al.terraparcel.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.zip.*
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import androidx.room.withTransaction

object Exchange {
    private fun xyz(p: Vertex)=JSONArray().put(p.lon).put(p.lat).also { a->p.altitude?.let { a.put(it) } }
    fun geometry(d: Draft): JSONObject {
        val coordinates=JSONArray()
        d.points.forEach { coordinates.put(xyz(it)) }
        return JSONObject().put("type",when(d.shape){Shape.POINT->"Point";Shape.LINE->"LineString";Shape.POLYGON->"Polygon"})
            .put("coordinates",when(d.shape){
                Shape.POINT->xyz(d.points.first())
                Shape.LINE->coordinates
                Shape.POLYGON->{ coordinates.put(xyz(d.points.first())); JSONArray().put(coordinates) }
            })
    }
    fun feature(d: Draft)=JSONObject().put("type","Feature").put("geometry",geometry(d))
        .put("properties",JSONObject().put("name",d.name).put("notes",d.notes).put("owner",d.owner).put("category",d.category).put("color",d.color))
    fun geojson(ds: List<Draft>)=JSONObject().put("type","FeatureCollection").put("features",JSONArray().also { a->ds.forEach { a.put(feature(it)) } }).toString(2)
    private fun esc(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
    fun kml(ds: List<Draft>): String = """<?xml version="1.0" encoding="UTF-8"?><kml xmlns="http://www.opengis.net/kml/2.2"><Document>""" +
        ds.joinToString("") { d->
            val points=if(d.shape==Shape.POLYGON) d.points+d.points.first() else d.points
            val coords=points.joinToString(" ") { "${it.lon},${it.lat}"+(it.altitude?.let { z->",$z" }?:"") }
            val geom=when(d.shape){Shape.POINT->"<Point><coordinates>$coords</coordinates></Point>";Shape.LINE->"<LineString><coordinates>$coords</coordinates></LineString>";Shape.POLYGON->"<Polygon><outerBoundaryIs><LinearRing><coordinates>$coords</coordinates></LinearRing></outerBoundaryIs></Polygon>"}
            "<Placemark><name>${esc(d.name)}</name><description>${esc(d.notes)}</description>$geom</Placemark>"
        }+"</Document></kml>"
    fun gpx(ds: List<Draft>): String = """<?xml version="1.0" encoding="UTF-8"?><gpx version="1.1" creator="TerraParcel" xmlns="http://www.topografix.com/GPX/1/1">""" +
        ds.joinToString("") { d->
            fun pt(p:Vertex,tag:String)="<$tag lat=\"${p.lat}\" lon=\"${p.lon}\">"+(p.altitude?.let { "<ele>$it</ele>" }?:"")+"</$tag>"
            if(d.shape==Shape.POINT) "<wpt lat=\"${d.points[0].lat}\" lon=\"${d.points[0].lon}\"><name>${esc(d.name)}</name></wpt>"
            else "<trk><name>${esc(d.name)}</name><type>${d.shape.name}</type><trkseg>"+(if(d.shape==Shape.POLYGON) d.points+d.points.first() else d.points).joinToString(""){pt(it,"trkpt")}+"</trkseg></trk>"
        }+"</gpx>"
    private fun quote(s:String)="\""+s.replace("\"","\"\"")+"\""
    fun csv(ds:List<Draft>):String="parcel,name,shape,order,latitude,longitude,elevation\n"+ds.flatMap { d->d.points.mapIndexed { i,p->listOf(d.id,d.name,d.shape.name,i.toString(),p.lat.toString(),p.lon.toString(),p.altitude?.toString()?:"").joinToString(","){quote(it)} } }.joinToString("\n")
    fun export(ds:List<Draft>,format:String):ByteArray {
        require(ds.isNotEmpty()) { "No measurements selected" }; ds.forEach(Geo::validate)
        if(format=="kmz") return ByteArrayOutputStream().also { out->ZipOutputStream(out).use { z->z.putNextEntry(ZipEntry("doc.kml"));z.write(kml(ds).toByteArray());z.closeEntry() } }.toByteArray()
        return when(format){"gpx"->gpx(ds);"kml"->kml(ds);"csv"->csv(ds);else->geojson(ds)}.toByteArray()
    }
    fun limited(input:InputStream,max:Int=20_000_000):ByteArray {
        val out=ByteArrayOutputStream();val buf=ByteArray(8192)
        while(true){val n=input.read(buf);if(n<0)break;require(out.size()+n<=max){"File exceeds size limit"};out.write(buf,0,n)}
        return out.toByteArray()
    }
    fun read(bytes:ByteArray):List<Draft> {
        require(bytes.size<=20_000_000){"Import exceeds 20 MB"}
        if(bytes.size>2 && bytes[0]==80.toByte() && bytes[1]==75.toByte()) {
            ZipInputStream(ByteArrayInputStream(bytes)).use { z->
                var entry=z.nextEntry;var count=0
                while(entry!=null){require(++count<1000){"Too many ZIP entries"};if(entry.name.endsWith(".kml",true))return read(limited(z));entry=z.nextEntry}
            };error("KMZ has no KML")
        }
        val s=bytes.toString(Charsets.UTF_8).trim().removePrefix("\uFEFF")
        val ds=when { s.startsWith("{")->readJson(JSONObject(s));s.startsWith("<")->readXml(bytes);else->readCsv(s) }
        require(ds.isNotEmpty()){"No supported geometries found"}
        require(ds.size<=5000){"Too many features"}
        ds.forEach(Geo::validate);return ds
    }
    private fun clean(ps:List<Vertex>):List<Vertex> = if(ps.size>1 && ps.first().lat==ps.last().lat && ps.first().lon==ps.last().lon) ps.dropLast(1) else ps
    private fun readJson(root:JSONObject):List<Draft> {
        if(root.optString("type")=="FeatureCollection") return (root.getJSONArray("features")).let { a->(0 until a.length()).flatMap { readJson(a.getJSONObject(it)) } }
        val prop=root.optJSONObject("properties")?:JSONObject()
        val g=if(root.optString("type")=="Feature")root.getJSONObject("geometry")else root
        require(!g.has("crs") && !root.has("crs")){"Only WGS84 GeoJSON is supported"}
        val a=g.getJSONArray("coordinates")
        fun p(x:JSONArray)=Vertex(x.getDouble(1),x.getDouble(0),if(x.length()>2)x.getDouble(2)else null)
        fun ps(x:JSONArray)=(0 until x.length()).map { p(x.getJSONArray(it)) }
        val shape:Shape;val points:List<Vertex>
        when(g.getString("type")){
            "Point"->{shape=Shape.POINT;points=listOf(p(a))}
            "LineString"->{shape=Shape.LINE;points=ps(a)}
            "Polygon"->{require(a.length()==1){"Polygon holes are not supported; import rejected to avoid changing area"};shape=Shape.POLYGON;points=clean(ps(a.getJSONArray(0)))}
            else->error("Unsupported GeoJSON geometry; use separate Point, LineString or Polygon features")
        }
        return listOf(Draft(name=prop.optString("name","Imported"),notes=prop.optString("notes"),owner=prop.optString("owner"),category=prop.optString("category","Property"),shape=shape,points=points))
    }
    private fun readXml(bytes:ByteArray):List<Draft> {
        val factory=DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware=true
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false)
        val doc=factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        fun nodes(e:Element,tag:String):List<Element> = e.getElementsByTagNameNS("*",tag).let { n->(0 until n.length).map { n.item(it) as Element } }
        fun text(e:Element,tag:String)=nodes(e,tag).firstOrNull()?.textContent.orEmpty()
        val root=doc.documentElement
        if(root.localName=="kml") return nodes(root,"Placemark").map { e->
            require(nodes(e,"innerBoundaryIs").isEmpty() && nodes(e,"MultiGeometry").isEmpty()){"Holes and MultiGeometry are not supported"}
            val shape=when {nodes(e,"Polygon").isNotEmpty()->Shape.POLYGON;nodes(e,"LineString").isNotEmpty()->Shape.LINE;else->Shape.POINT}
            val points=text(e,"coordinates").trim().split(Regex("\\s+")).filter { it.isNotBlank() }.map { t->val a=t.split(",");Vertex(a[1].toDouble(),a[0].toDouble(),a.getOrNull(2)?.toDoubleOrNull()) }
            Draft(name=text(e,"name"),notes=text(e,"description"),shape=shape,points=if(shape==Shape.POLYGON)clean(points)else points)
        }
        require(root.localName=="gpx"){"Only GPX and KML XML are supported"}
        fun point(e:Element)=Vertex(e.getAttribute("lat").toDouble(),e.getAttribute("lon").toDouble(),text(e,"ele").toDoubleOrNull())
        val result=mutableListOf<Draft>()
        nodes(root,"wpt").forEach { result+=Draft(name=text(it,"name"),shape=Shape.POINT,points=listOf(point(it))) }
        nodes(root,"trk").forEach { trk->nodes(trk,"trkseg").forEachIndexed { i,seg->
            val points=nodes(seg,"trkpt").map(::point)
            val polygon=text(trk,"type")=="POLYGON"
            result+=Draft(name=text(trk,"name")+" / ${i+1}",shape=if(polygon)Shape.POLYGON else Shape.LINE,points=if(polygon)clean(points)else points)
        } }
        nodes(root,"rte").forEach { result+=Draft(name=text(it,"name"),shape=Shape.LINE,points=nodes(it,"rtept").map(::point)) }
        return result
    }
    private fun readCsv(s:String):List<Draft> {
        val rows=mutableListOf<List<String>>();val row=mutableListOf<String>();val cell=StringBuilder();var quoted=false;var i=0
        while(i<s.length){val c=s[i];when {
            c=='"' && quoted && i+1<s.length && s[i+1]=='"'->{cell.append('"');i++}
            c=='"'->quoted=!quoted
            c==',' && !quoted->{row+=cell.toString();cell.clear()}
            c=='\n' && !quoted->{row+=cell.toString().trimEnd('\r');rows+=row.toList();row.clear();cell.clear()}
            else->cell.append(c)
        };i++}
        require(!quoted){"Unclosed CSV quote"}
        if(cell.isNotEmpty()||row.isNotEmpty()){row+=cell.toString().trimEnd('\r');rows+=row.toList()}
        require(rows.isNotEmpty()){"Empty CSV"}
        val h=rows.first().map { it.trim().lowercase() }
        require("latitude" in h && "longitude" in h){"CSV needs latitude and longitude columns"}
        fun value(r:List<String>,k:String)=r.getOrNull(h.indexOf(k)).orEmpty()
        return rows.drop(1).filter { it.any(String::isNotBlank) }.groupBy { value(it,"parcel").ifBlank{"import"} }.map { (_,rs)->
            val ordered=if("order" in h)rs.sortedBy { value(it,"order").toInt() }else rs
            Draft(name=value(rs.first(),"name").ifBlank{"Imported"},shape=if("shape" in h)Shape.valueOf(value(rs.first(),"shape").uppercase())else if(rs.size==1)Shape.POINT else Shape.POLYGON,
                points=ordered.map { Vertex(value(it,"latitude").toDouble(),value(it,"longitude").toDouble(),value(it,"elevation").toDoubleOrNull()) })
        }
    }
}
@Serializable data class BackupManifest(val version:Int=1,val parcels:List<Parcel>,val photos:List<PhotoAttachment>,val preferences:Preferences)
class Backups(private val repo:ParcelRepository,private val directory:File) {
    suspend fun create(p:Preferences):ByteArray {
        val manifest=repo.db.withTransaction { BackupManifest(parcels=repo.dao.all(),photos=repo.dao.photos(),preferences=p) }
        return ByteArrayOutputStream().also { out->ZipOutputStream(out).use { z->
            z.putNextEntry(ZipEntry("manifest.json"));z.write(codec.encodeToString(manifest).toByteArray());z.closeEntry()
            manifest.photos.forEach { photo->
                val file=File(directory,photo.filename)
                require(file.isFile){"Missing photo ${photo.id}; backup aborted"}
                z.putNextEntry(ZipEntry("photos/"+photo.filename));file.inputStream().use { it.copyTo(z) };z.closeEntry()
            }
        } }.toByteArray()
    }
    suspend fun restore(bytes:ByteArray):Preferences {
        val entries=mutableMapOf<String,ByteArray>();var size=0
        ZipInputStream(ByteArrayInputStream(bytes)).use { z->
            var e=z.nextEntry
            while(e!=null){
                require(entries.size<5000 && !e.name.contains("..") && !e.name.startsWith("/")){"Unsafe backup"}
                val data=Exchange.limited(z,100_000_000);size+=data.size;require(size<=200_000_000){"Backup exceeds 200 MB"}
                require(e.name !in entries){"Duplicate backup entry"};entries[e.name]=data;e=z.nextEntry
            }
        }
        val m=codec.decodeFromString<BackupManifest>(entries["manifest.json"]?.toString(Charsets.UTF_8)?:error("Invalid backup"))
        require(m.version==1){"Unsupported backup version"}
        m.parcels.forEach { Geo.validate(it.draft()) }
        require(m.parcels.map { it.id }.distinct().size==m.parcels.size){"Duplicate parcel IDs"}
        // New filenames prevent an interrupted restore from overwriting existing attachments.
        val replacements=m.photos.map { p->
            require(m.parcels.any { it.id==p.parcelId }){"Orphan photo"}
            require(p.filename.matches(Regex("[A-Za-z0-9._-]+"))){"Invalid photo filename"}
            val bytesForPhoto=entries["photos/"+p.filename]?:error("Missing backup photo")
            val name=java.util.UUID.randomUUID().toString()+".jpg"
            File(directory,name).writeBytes(bytesForPhoto)
            p.copy(filename=name)
        }
        repo.db.withTransaction {
            m.parcels.forEach { p->repo.save(p.draft());if(p.deletedAt!=null)repo.dao.trash(p.id,p.deletedAt) }
            replacements.forEach { repo.dao.photo(it) }
            repo.dao.setting(AppSettings("preferences",codec.encodeToString(m.preferences)))
        }
        return m.preferences
    }
}
