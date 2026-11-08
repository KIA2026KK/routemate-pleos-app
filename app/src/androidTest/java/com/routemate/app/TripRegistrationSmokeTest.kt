package com.routemate.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class TripRegistrationSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun unconfiguredServerDoesNotAllowRegistration() {
        assumeTrue(BuildConfig.BACKEND_URL.isBlank())
        compose.onNodeWithText("서버 연결이 설정되지 않았습니다.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("목적지 등록").performScrollTo().assertIsNotEnabled()
    }

    @Test fun registersMockDestinationWithActualBackend() {
        assumeTrue(!BuildConfig.USE_PLEOS_DESTINATION && BuildConfig.BACKEND_URL.isNotBlank() &&
            InstrumentationRegistry.getArguments().getString("tripE2E") == "true")
        compose.onNodeWithText("목적지 등록").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText("등록 완료 · 서울역 (샘플)")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithText("등록 완료 · 서울역 (샘플)").performScrollTo().assertIsDisplayed()
    }
}
