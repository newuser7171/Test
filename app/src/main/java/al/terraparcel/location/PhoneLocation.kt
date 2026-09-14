package al.terraparcel.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import al.terraparcel.domain.Vertex

data class Fix(val point: Vertex,val speed: Float?,val bearing: Float?,val elapsedNanos: Long,val source: String="PHONE GPS")
interface LocationSource { fun start(onFix:(Fix)->Unit,onError:(String)->Unit); fun stop() }
class PhoneLocation(c: Context): LocationSource {
    private val manager=c.getSystemService(LocationManager::class.java)
    private var listener: LocationListener?=null
    @SuppressLint("MissingPermission")
    override fun start(onFix:(Fix)->Unit,onError:(String)->Unit) {
        stop()
        val l=object: LocationListener {
            override fun onLocationChanged(location: Location) {
                if(SystemClock.elapsedRealtimeNanos()-location.elapsedRealtimeNanos > 15_000_000_000L) return
                onFix(Fix(Vertex(location.latitude,location.longitude,
                    if(location.hasAltitude()) location.altitude else null,
                    if(location.hasAccuracy()) location.accuracy else null,location.time),
                    if(location.hasSpeed()) location.speed else null,
                    if(location.hasBearing()) location.bearing else null,location.elapsedRealtimeNanos))
            }
            override fun onProviderDisabled(provider: String) { onError("Location provider disabled") }
            override fun onProviderEnabled(provider: String) {}
            @Deprecated("Legacy Android callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        listener=l
        try {
            val providers=listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER).filter { manager.isProviderEnabled(it) }
            if(providers.isEmpty()) onError("Enable location in Android settings")
            providers.forEach { manager.requestLocationUpdates(it,1000L,0f,l,Looper.getMainLooper()) }
        } catch(e: SecurityException) { onError("Location permission is required for GPS; manual drawing remains available") }
    }
    override fun stop() { listener?.let { manager.removeUpdates(it) }; listener=null }
}
