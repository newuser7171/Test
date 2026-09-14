package al.terraparcel.domain

import net.sf.geographiclib.Geodesic
import net.sf.geographiclib.PolygonArea
import org.locationtech.proj4j.*
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.operation.valid.IsValidOp
import kotlin.math.*

object Geo {
    fun distance(a: Vertex, b: Vertex): Double = Geodesic.WGS84.Inverse(a.lat,a.lon,b.lat,b.lon).s12
    fun bearing(a: Vertex, b: Vertex): Double = (Geodesic.WGS84.Inverse(a.lat,a.lon,b.lat,b.lon).azi1 + 360) % 360
    fun metrics(points: List<Vertex>, shape: Shape): Pair<Double,Double> {
        if (points.size < 2 || shape == Shape.POINT) return 0.0 to 0.0
        val p = PolygonArea(Geodesic.WGS84, shape != Shape.POLYGON || points.size < 3)
        points.forEach { p.AddPoint(it.lat,it.lon) }
        val r = p.Compute(false,true)
        return (if (shape == Shape.POLYGON && points.size >= 3) abs(r.area) else 0.0) to r.perimeter
    }
    fun validate(d: Draft) {
        val minimum = when(d.shape) { Shape.POINT -> 1; Shape.LINE -> 2; Shape.POLYGON -> 3 }
        require(d.points.size >= minimum) { "Needs at least $minimum points" }
        require(d.shape != Shape.POINT || d.points.size == 1) { "A point measurement contains one point" }
        require(d.points.size <= 50000) { "Too many points" }
        if(d.shape == Shape.POLYGON) {
            // Unwrap longitude before topology validation, including parcels crossing the antimeridian.
            var previous = d.points.first().lon
            val coords = d.points.map {
                var x=it.lon
                while(x-previous > 180) x-=360
                while(x-previous < -180) x+=360
                previous=x
                Coordinate(x,it.lat)
            }.toMutableList()
            coords.add(Coordinate(coords.first()))
            val poly = GeometryFactory().createPolygon(coords.toTypedArray())
            require(IsValidOp(poly).isValid && metrics(d.points,d.shape).first > 0.01) { "Polygon overlaps itself or has zero area" }
        }
    }
    fun area(value: Double, unit: String) = value / when(unit) { "ha" -> 10000.0; "ac" -> 4046.8564224; "km²" -> 1000000.0; else -> 1.0 }
    fun length(value: Double, unit: String) = value / when(unit) { "km" -> 1000.0; "ft" -> 0.3048; "mi" -> 1609.344; else -> 1.0 }
    data class Utm(val zone: Int, val hemisphere: String, val easting: Double, val northing: Double) {
        override fun toString() = java.lang.String.format(java.util.Locale.US,"%d%s %.2f E %.2f N",zone,hemisphere,easting,northing)
    }
    fun utm(p: Vertex): Utm {
        require(p.lat in -80.0..84.0) { "Outside UTM range" }
        var zone = (floor((p.lon+180)/6).toInt()+1).coerceIn(1,60)
        if(p.lat in 56.0..<64.0 && p.lon in 3.0..<12.0) zone=32
        if(p.lat in 72.0..84.0 && p.lon in 0.0..<42.0) zone = when { p.lon<9->31;p.lon<21->33;p.lon<33->35;else->37 }
        val factory=CRSFactory()
        val src=factory.createFromParameters("WGS84","+proj=longlat +datum=WGS84")
        val dst=factory.createFromParameters("UTM","+proj=utm +zone=$zone +datum=WGS84 +units=m " + if(p.lat<0) "+south" else "")
        val out=ProjCoordinate()
        CoordinateTransformFactory().createTransform(src,dst).transform(ProjCoordinate(p.lon,p.lat),out)
        return Utm(zone,if(p.lat<0) "S" else "N",out.x,out.y)
    }
    fun accuracyLabel(a: Float?) = when { a==null->"Unknown"; a<3->"Excellent"; a<=5->"Good"; a<=10->"Moderate"; else->"Poor" }
}
