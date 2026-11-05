package com.routemate.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeFalse

class DestinationSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun displaysMockDestinationAfterRefresh() {
        assumeFalse("This smoke test is for the explicit Mock build", BuildConfig.USE_PLEOS_DESTINATION)
        compose.onNodeWithText("개발용 샘플 목적지 · 실제 내비게이션과 연결되지 않았습니다.").assertIsDisplayed()
        compose.onNodeWithText("샘플 목적지 가져오기").performClick()
        compose.onNodeWithText("서울역 (샘플)").assertIsDisplayed()
        compose.onNodeWithText("위도 37.554700 · 경도 126.970600").assertIsDisplayed()
    }
}
