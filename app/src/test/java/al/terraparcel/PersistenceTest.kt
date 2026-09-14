package al.terraparcel
import al.terraparcel.domain.*
import al.terraparcel.data.*
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class PersistenceTest {
    private lateinit var context:Context
    @Before fun setup(){context=ApplicationProvider.getApplicationContext();context.deleteDatabase("test.db")}
    private fun open()=Room.databaseBuilder(context,LandDatabase::class.java,"test.db").addMigrations(LandDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
    @Test fun survivesDatabaseRestart()=runBlocking {
        val d=Draft(name="Olive field",points=listOf(Vertex(40.0,19.0),Vertex(40.0,19.01),Vertex(40.01,19.01)))
        var db=open()
        ParcelRepository(db).save(d);db.close()
        db=open()
        val saved=db.dao().get(d.id)!!
        assertEquals(d,saved.draft())
        assertEquals(3,db.dao().pointsFor(d.id).size)
        assertTrue(saved.area>0)
        db.dao().trash(d.id,123L);assertEquals(123L,db.dao().get(d.id)!!.deletedAt)
        db.dao().restore(d.id);assertNull(db.dao().get(d.id)!!.deletedAt)
        db.close()
    }
    @Test fun exportImportRoundTrips() {
        val d=Draft(name="Field, \"North\"",notes="A & B",points=listOf(Vertex(40.0,19.0),Vertex(40.0,19.01),Vertex(40.01,19.01)))
        listOf("geojson","kml","kmz","gpx","csv").forEach { format->
            val result=Exchange.read(Exchange.export(listOf(d),format)).single()
            assertEquals(format,d.shape,result.shape)
            assertEquals(format,d.points.map{it.lat to it.lon},result.points.map{it.lat to it.lon})
            assertEquals(Geo.metrics(d.points,d.shape).first,Geo.metrics(result.points,result.shape).first,0.001)
        }
    }
    @Test fun transactionalBackupRestore()=runBlocking {
        val db=open();val repo=ParcelRepository(db)
        val d=Draft(name="Backup",shape=Shape.POINT,points=listOf(Vertex(41.0,20.0)))
        repo.save(d)
        val dir=java.io.File(context.cacheDir,"testphotos").apply{mkdirs()}
        val backup=Backups(repo,dir)
        val data=backup.create(Preferences(language="sq"))
        db.dao().delete(d.id)
        assertEquals("sq",backup.restore(data).language)
        assertEquals(d,db.dao().get(d.id)!!.draft())
        db.close()
    }
    @Test(expected=IllegalArgumentException::class) fun rejectHole(){
        Exchange.read("""{"type":"Polygon","coordinates":[[[19,40],[20,40],[20,41],[19,40]],[[19.1,40.1],[19.2,40.1],[19.2,40.2],[19.1,40.1]]]}""".toByteArray())
    }
    @Test fun csvMultilineName(){
        val d=Draft(name="Line\nTwo",shape=Shape.POINT,points=listOf(Vertex(40.0,19.0)))
        assertEquals(d.name,Exchange.read(Exchange.export(listOf(d),"csv")).single().name)
    }
}
