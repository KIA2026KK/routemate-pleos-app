package com.routemate.app

import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.routemate.app.auth.presentation.VehicleAccountActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VehicleAccountSmokeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun opensProtectedAccountScreenAndRequiresRecoveryKey() {
        compose.openParkedSettings()
        compose.onNodeWithText("계정 관리").performClick()
        compose.waitUntil(timeoutMillis=10_000){compose.onAllNodesWithText("차량 계정 관리").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()}
        compose.onNodeWithText("복구키로 계정 복구").performScrollTo().assertIsNotEnabled()
        InstrumentationRegistry.getInstrumentation().runOnMainSync{
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<VehicleAccountActivity>().forEach{
                assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0);it.finish()
            }
        }
    }
}
