package al.terraparcel
import al.terraparcel.domain.*
import org.junit.Assert.*
import org.junit.Test
class RecordingPolicyTest {
    @Test fun admitsBoundaryAccuracy(){assertNull(RecordingPolicy.rejection(Vertex(41.0,20.0,accuracy=5f),10L,20L,5f))}
    @Test fun rejectsPoorFix(){assertNotNull(RecordingPolicy.rejection(Vertex(41.0,20.0,accuracy=5.1f),10L,20L,5f))}
    @Test fun rejectsUnknownFix(){assertNotNull(RecordingPolicy.rejection(Vertex(41.0,20.0),10L,20L,5f))}
    @Test fun rejectsStaleFix(){assertNotNull(RecordingPolicy.rejection(Vertex(41.0,20.0,accuracy=1f),10L,16_000_000_010L,5f))}
    @Test fun rejectsNonFiniteAccuracy(){assertNotNull(RecordingPolicy.rejection(Vertex(41.0,20.0,accuracy=Float.NaN),10L,20L,5f))}
}
