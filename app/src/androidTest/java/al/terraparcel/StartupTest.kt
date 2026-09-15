package al.terraparcel

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun awaitHome() {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithText("Your land, on your device").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Your land, on your device").assertIsDisplayed()
    }

    @Test fun coldLaunchAndRecreationReachNativeMap() {
        // This launches the real Activity and native MapLibre library, not a mocked JVM view.
        awaitHome()
        compose.onNodeWithText("Map").performClick()
        compose.onNodeWithText("Long-press to add. Tap a vertex, then drag to move.").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        awaitHome()
    }
}
