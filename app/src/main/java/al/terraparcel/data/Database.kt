package al.terraparcel.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import al.terraparcel.domain.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Entity @Serializable
data class Parcel(@PrimaryKey val id: String, val name: String, val geometry: String,
    val area: Double, val perimeter: Double, val created: Long, val modified: Long,
    val notes: String, val category: String, val owner: String, val color: String,
    val shape: String, @ColumnInfo(defaultValue="0") val favorite: Boolean = false, val deletedAt: Long? = null)
@Entity(primaryKeys=["parcelId","ordinal"], foreignKeys=[ForeignKey(entity=Parcel::class,parentColumns=["id"],childColumns=["parcelId"],onDelete=ForeignKey.CASCADE)])
data class ParcelPoint(val parcelId: String, val ordinal: Int, val pointId: String, val latitude: Double, val longitude: Double, val elevation: Double?, val accuracy: Float?, val timestamp: Long)
@Entity(foreignKeys=[ForeignKey(entity=Parcel::class,parentColumns=["id"],childColumns=["parcelId"],onDelete=ForeignKey.CASCADE)],indices=[Index("parcelId")])
data class Measurement(@PrimaryKey val id: String, val parcelId: String, val area: Double, val length: Double, val calculatedAt: Long, val method: String = "WGS84 geodesic")
@Entity(foreignKeys=[ForeignKey(entity=Parcel::class,parentColumns=["id"],childColumns=["parcelId"],onDelete=ForeignKey.CASCADE)],indices=[Index("parcelId")])
@Serializable data class PhotoAttachment(@PrimaryKey val id: String, val parcelId: String, val pointId: String?, val filename: String, val timestamp: Long)
@Entity data class Category(@PrimaryKey val name: String, val color: String)
@Entity data class AppSettings(@PrimaryKey val key: String, val value: String)

@Dao
interface ParcelDao {
    @Query("SELECT * FROM Parcel ORDER BY modified DESC") fun observe(): Flow<List<Parcel>>
    @Query("SELECT * FROM Parcel ORDER BY modified DESC") suspend fun all(): List<Parcel>
    @Query("SELECT * FROM Parcel WHERE id=:id") suspend fun get(id: String): Parcel?
    @Upsert suspend fun put(p: Parcel)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun points(p: List<ParcelPoint>)
    @Query("DELETE FROM ParcelPoint WHERE parcelId=:id") suspend fun clearPoints(id: String)
    @Query("SELECT * FROM ParcelPoint WHERE parcelId=:id ORDER BY ordinal") suspend fun pointsFor(id: String): List<ParcelPoint>
    @Upsert suspend fun measurement(m: Measurement)
    @Upsert suspend fun setting(s: AppSettings)
    @Query("SELECT value FROM AppSettings WHERE key=:key") suspend fun setting(key: String): String?
    @Query("UPDATE Parcel SET deletedAt=:time, modified=:time WHERE id=:id") suspend fun trash(id: String,time: Long)
    @Query("UPDATE Parcel SET deletedAt=NULL WHERE id=:id") suspend fun restore(id: String)
    @Query("DELETE FROM Parcel WHERE id=:id") suspend fun delete(id: String)
    @Upsert suspend fun photo(p: PhotoAttachment)
    @Query("SELECT * FROM PhotoAttachment") suspend fun photos(): List<PhotoAttachment>
    @Query("SELECT * FROM PhotoAttachment WHERE parcelId=:id") fun photosFor(id: String): Flow<List<PhotoAttachment>>
    @Upsert suspend fun category(c: Category)
    @Query("SELECT * FROM Category ORDER BY name") fun categories(): Flow<List<Category>>
}
@Database(entities=[Parcel::class,ParcelPoint::class,Measurement::class,PhotoAttachment::class,Category::class,AppSettings::class],version=2,exportSchema=true)
abstract class LandDatabase: RoomDatabase() {
    abstract fun dao(): ParcelDao
    companion object {
        // Version 1 parcels had no favorite flag. Never fall back to destructive migration.
        val MIGRATION_1_2 = object: Migration(1,2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Parcel ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            }
        }
        fun open(c: Context)=Room.databaseBuilder(c.applicationContext,LandDatabase::class.java,"terraparcel.db")
            .addMigrations(MIGRATION_1_2).build()
    }
}
fun Parcel.draft(): Draft = Draft(id,name,notes,owner,category,color,Shape.valueOf(shape),codec.decodeFromString(geometry),created,favorite)
class ParcelRepository(val db: LandDatabase) {
    val dao=db.dao()
    suspend fun save(d: Draft): Parcel {
        Geo.validate(d)
        val metrics=Geo.metrics(d.points,d.shape)
        val p=Parcel(d.id,d.name.trim().ifBlank{"Parcel"},codec.encodeToString(d.points),metrics.first,metrics.second,d.created,System.currentTimeMillis(),d.notes,d.category,d.owner,d.color,d.shape.name,d.favorite)
        db.withTransaction {
            dao.put(p)
            dao.clearPoints(d.id)
            dao.points(d.points.mapIndexed { i,v->ParcelPoint(d.id,i,v.id,v.lat,v.lon,v.altitude,v.accuracy,v.time) })
            dao.measurement(Measurement(d.id,d.id,p.area,p.perimeter,p.modified))
            dao.category(Category(d.category,d.color))
        }
        return p
    }
}
