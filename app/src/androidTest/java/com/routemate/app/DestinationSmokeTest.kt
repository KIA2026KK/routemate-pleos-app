package com.routemate.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeFalse

class DestinationSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun displaysMockDestinationAfterRefresh() {
        assumeFalse("This smoke test is for the explicit Mock build", BuildConfig.USE_PLEOS_DESTINATION)
        compose.onNodeWithText("개발용 샘플 목적지 · 실제 내비게이션과 연결되지 않았습니다.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("샘플 목적지 가져오기").performScrollTo().performClick()
        compose.onNodeWithText("서울역 (샘플)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("현재 위치 대신, 내비게이션의 최종 목적지로 연결합니다.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("위도 37.554700 · 경도 126.970600").assertDoesNotExist()
    }
}
