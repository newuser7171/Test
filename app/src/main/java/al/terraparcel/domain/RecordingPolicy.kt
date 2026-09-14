package al.terraparcel.domain

/** Pure admission policy shared by automatic walking and the manual GPS-point button. */
object RecordingPolicy {
    fun rejection(point:Vertex,fixElapsedNanos:Long,nowElapsedNanos:Long,maxAccuracy:Float):String? {
        val age=nowElapsedNanos-fixElapsedNanos
        if(age<0L || age>15_000_000_000L) return "GPS fix is stale"
        val accuracy=point.accuracy
        if(accuracy==null || !accuracy.isFinite() || accuracy<0f) return "GPS accuracy is unknown; point rejected"
        if(accuracy>maxAccuracy) return "GPS accuracy ±$accuracy m exceeds $maxAccuracy m; point rejected"
        return null
    }
}
