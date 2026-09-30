package al.terraparcel
import al.terraparcel.domain.*
import org.junit.Assert.*
import org.junit.Test
class RecordingPolicyTest {
    @Test fun admitsBoundaryAccuracy(){assertNull(RecordingPolicy.rejection(Vertex(41.0,20.0,accuracy=5f),10L,20L,5f))}
    @Test fun rejectsPoorFix(){assertNotNull(RecordingPolicy.rejection(Vertex(41.0,20.0,accuracy=5.1f),10L,20L,5f))}
    @Test fun rejectsUnknownFix(){assertNotNull(RecordingPolicy.rejection(Vertex(41.0,20.0),10L,20L,5f))}
    @Test fun rejectsStaleFix(){assertNotNull(RecordingPolicy.rejection(Vertex(41.0,20.0,accuracy=1f),10L,16_000_000_010L,5f))}
    @Test fun rejectsFutureFix(){assertNotNull(RecordingPolicy.rejection(Vertex(41.0,20.0,accuracy=1f),21L,20L,5f))}
    @Test fun expiresPreviouslyAcceptedFix(){
        val point=Vertex(41.0,20.0,accuracy=1f)
        val captured=1_000_000_000L
        assertNull(RecordingPolicy.rejection(point,captured,captured+15_000_000_000L,5f))
        assertEquals("GPS fix is stale",RecordingPolicy.rejection(point,captured,captured+15_000_000_001L,5f))
    }
    @Test fun rejectsNonFiniteAccuracy(){assertNotNull(RecordingPolicy.rejection(Vertex(41.0,20.0,accuracy=Float.NaN),10L,20L,5f))}
}
