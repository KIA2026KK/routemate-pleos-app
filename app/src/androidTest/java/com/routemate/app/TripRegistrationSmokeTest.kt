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
        assumeTrue(BuildConfig.BACKEND_URL.isBlank()&&!BuildConfig.PROTOTYPE_DEMO)
        compose.onNodeWithText("서버 연결이 설정되지 않았습니다.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("방 만들기").performScrollTo().assertIsNotEnabled()
    }

    @Test fun registersMockDestinationWithActualBackend() {
        assumeTrue(!BuildConfig.USE_PLEOS_DESTINATION && BuildConfig.BACKEND_URL.isNotBlank() &&
            InstrumentationRegistry.getArguments().getString("tripE2E") == "true")
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText("목적지 등록 완료")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithText("목적지 등록 완료").performScrollTo().assertIsDisplayed()
    }

    @Test fun absentPleosVehicleServiceStopsRegistrationWithoutFallback() {
        assumeTrue(BuildConfig.USE_PLEOS_VEHICLE && !BuildConfig.USE_PLEOS_DESTINATION && BuildConfig.BACKEND_URL.isNotBlank() &&
            InstrumentationRegistry.getArguments().getString("vehicleServiceUnavailable") == "true")
        val error = "차량 VIN을 확인할 수 없습니다."
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText(error).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithText(error).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("목적지 등록 완료").assertDoesNotExist()
    }
}
