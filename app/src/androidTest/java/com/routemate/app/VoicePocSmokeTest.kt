package com.routemate.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.routemate.app.voice.presentation.VoicePocActivity
import org.junit.Rule
import org.junit.Test

class VoicePocSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun opensVoicePocAndRequiresCredentialsBeforeJoin() {
        compose.openParkedSettings()
        compose.onNodeWithText("더보기").performClick()
        compose.onNodeWithText("음성 통화 테스트").performClick()
        // Activity transition can briefly leave the process without a Compose root on CI.
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("음성 통화 PoC")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithText("음성 통화 PoC").assertIsDisplayed()
        compose.onNodeWithText("연결 전").assertIsDisplayed()
        compose.onNodeWithText("통화 입장").assertIsNotEnabled()
        compose.onNodeWithText("참가자 토큰").performTextInput("temporary-test-token")
        compose.onNodeWithText("통화 입장").assertIsEnabled()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<VoicePocActivity>().forEach { it.finish() }
        }
    }
}
