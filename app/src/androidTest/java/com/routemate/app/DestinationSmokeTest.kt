package com.routemate.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class DestinationSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun displaysMockDestinationAfterRefresh() {
        compose.onNodeWithText("샘플 목적지 가져오기").performClick()
        compose.onNodeWithText("서울역 (샘플)").assertIsDisplayed()
    }
}
