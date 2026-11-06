package com.routemate.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class VoicePocSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun opensVoicePocAndRequiresCredentialsBeforeJoin() {
        compose.onNodeWithText("음성 통화 테스트").performClick()
        compose.onNodeWithText("음성 통화 PoC").assertIsDisplayed()
        compose.onNodeWithText("연결 전").assertIsDisplayed()
        compose.onNodeWithText("통화 입장").assertIsNotEnabled()
        compose.onNodeWithText("참가자 토큰").performTextInput("temporary-test-token")
        compose.onNodeWithText("통화 입장").assertIsEnabled()
    }
}
