package al.terraparcel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import al.terraparcel.domain.Vertex
import al.terraparcel.location.Fix
import al.terraparcel.location.LocationSource
import al.terraparcel.ui.LandViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class LocationLifecycleTest {
    private class Source:LocationSource {
        var starts=0
        var stops=0
        lateinit var deliver:(Fix)->Unit
        override fun start(onFix:(Fix)->Unit,onError:(String)->Unit){starts++;deliver=onFix}
        override fun stop(){stops++}
    }
    private val store=ViewModelStore()
    private val source=Source()
    private lateinit var vm:LandViewModel
    @Before fun setup(){
        val app=ApplicationProvider.getApplicationContext<Application>()
        app.deleteDatabase("terraparcel.db")
        vm=LandViewModel(app,source)
        store.put("land",vm)
    }
    @After fun cleanup(){store.clear()}
    @Test fun foregroundReturnDoesNotStartUnrequestedLocation(){
        vm.background();vm.resumeLocation()
        assertEquals(0,source.starts)
    }
    @Test fun resumesPositionUpdatesAfterForegroundReturn(){
        vm.startLocation();vm.background();vm.resumeLocation()
        assertEquals(2,source.starts)
        assertEquals(1,source.stops)
        assertFalse(vm.walking.value)
    }
    @Test fun returningKeepsWalkPausedAndDoesNotAppendPoints(){
        vm.startWalk();vm.background();vm.resumeLocation()
        assertTrue(vm.walking.value)
        assertTrue(vm.paused.value)
        val fix=Fix(Vertex(41.0,20.0,accuracy=1f),null,null,SystemClock.elapsedRealtimeNanos())
        source.deliver(fix)
        assertEquals(fix,vm.fix.value)
        assertTrue(vm.draft.value.points.isEmpty())
        vm.pauseWalk()
        source.deliver(fix)
        assertEquals(1,vm.draft.value.points.size)
    }
}
