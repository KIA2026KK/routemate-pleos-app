package com.routemate.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeFalse

class DestinationSmokeTest {
    @get:Rule(order=0) val sessionFixture=ResetRoomSessionRule()
    @get:Rule(order=1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun defaultsToSongjeongAndSelectsRoadAddressWithoutSampleReloadButton() {
        assumeFalse("This smoke test is for the explicit Mock build", BuildConfig.USE_PLEOS_DESTINATION)
        compose.waitUntil(10_000){compose.onAllNodesWithText("광주송정역").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("샘플 목적지 가져오기").assertDoesNotExist()
        compose.onNodeWithText("광주송정역").assertIsDisplayed()
        compose.clickReadyDescription("목적지 변경")
        compose.confirmParkedIfNeeded()
        compose.onNodeWithText("장소명 또는 도로명").performTextInput("상무대로 201")
        compose.onNodeWithText("장소명 또는 도로명").performImeAction()
        compose.waitUntil(10_000){compose.onAllNodesWithText("광주송정역 · 예시").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("광주광역시 광산구 상무대로 201").assertIsDisplayed()
        compose.onNodeWithContentDescription("광주송정역 선택").performClick()
        compose.onNodeWithText("광주송정역").assertIsDisplayed()
        compose.clickReadyDescription("목적지 변경")
        compose.onNodeWithText("장소명 또는 도로명").performTextInput("서울역")
        compose.onNodeWithText("장소명 또는 도로명").performImeAction()
        compose.waitUntil(10_000){compose.onAllNodesWithText("서울역 · 예시").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("서울역 선택").performClick()
        compose.waitUntil(10_000){compose.onAllNodesWithText("서울역").fetchSemanticsNodes().isNotEmpty()}
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("서울역").assertIsDisplayed()
        compose.clickReadyDescription("목적지 변경")
        compose.confirmParkedIfNeeded()
        compose.waitUntil(10_000){compose.onAllNodesWithText("광주송정역 · 예시").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("광주송정역 선택").performClick()
    }
}
