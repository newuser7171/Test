package al.terraparcel
import al.terraparcel.domain.*
import org.junit.Assert.*
import org.junit.Test

class GeoTest {
    private val square=listOf(Vertex(0.0,0.0),Vertex(0.0,1.0),Vertex(1.0,1.0),Vertex(1.0,0.0))
    @Test fun wgs84Square(){
        val (area,perimeter)=Geo.metrics(square,Shape.POLYGON)
        assertEquals(12308778361.469,area,1.0)
        assertEquals(443770.917,perimeter,0.01)
        assertEquals(area,Geo.metrics(square.reversed(),Shape.POLYGON).first,0.01)
    }
    @Test fun equatorialDegree(){assertEquals(111319.490793,Geo.distance(square[0],square[1]),0.001)}
    @Test fun lineDoesNotClose(){assertEquals(111319.490793,Geo.metrics(square.take(2),Shape.LINE).second,0.001)}
    @Test fun unitConversions(){
        assertEquals(1.0,Geo.area(10000.0,"ha"),1e-9)
        assertEquals(1.0,Geo.area(4046.8564224,"ac"),1e-9)
        assertEquals(1.0,Geo.area(1000000.0,"km²"),1e-9)
        assertEquals(1.0,Geo.length(0.3048,"ft"),1e-9)
        assertEquals(1.0,Geo.length(1609.344,"mi"),1e-9)
        assertEquals(1.0,Geo.length(1000.0,"km"),1e-9)
    }
    @Test fun utmCentralMeridian(){
        val u=Geo.utm(Vertex(0.0,21.0))
        assertEquals(34,u.zone);assertEquals("N",u.hemisphere)
        assertEquals(500000.0,u.easting,0.01);assertEquals(0.0,u.northing,0.01)
        assertEquals(34,Geo.utm(Vertex(41.3275,19.8187)).zone)
    }
    @Test fun datelineParcel(){
        val ps=listOf(Vertex(10.0,179.9),Vertex(10.0,-179.9),Vertex(10.1,-179.9),Vertex(10.1,179.9))
        Geo.validate(Draft(points=ps))
        assertTrue(Geo.metrics(ps,Shape.POLYGON).first in 200_000_000.0..300_000_000.0)
    }
    @Test(expected=IllegalArgumentException::class)fun rejectBowtie(){Geo.validate(Draft(points=listOf(square[0],square[2],square[1],square[3])))}
    @Test(expected=IllegalArgumentException::class)fun rejectInvalidLatitude(){Vertex(91.0,0.0)}
    @Test fun boundaries(){assertEquals("Excellent",Geo.accuracyLabel(2.9f));assertEquals("Good",Geo.accuracyLabel(5f));assertEquals("Moderate",Geo.accuracyLabel(10f));assertEquals("Poor",Geo.accuracyLabel(10.1f))}
}
