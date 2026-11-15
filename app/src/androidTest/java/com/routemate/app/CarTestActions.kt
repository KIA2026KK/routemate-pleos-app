package com.routemate.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.semantics.SemanticsProperties

internal fun ComposeContentTestRule.clickReadyText(text: String) {
    waitUntil(40_000){onAllNodesWithText(text).fetchSemanticsNodes().any{!it.config.contains(SemanticsProperties.Disabled)}}
    onNodeWithText(text).performScrollTo().performClick()
}
internal fun ComposeContentTestRule.clickReadyDescription(description: String) {
    waitUntil(40_000){onAllNodesWithContentDescription(description).fetchSemanticsNodes().any{!it.config.contains(SemanticsProperties.Disabled)}}
    onNodeWithContentDescription(description).performScrollTo().performClick()
}

internal fun ComposeContentTestRule.confirmParkedIfNeeded() {
    waitForIdle()
    if(onAllNodesWithText("정차했어요").fetchSemanticsNodes().isNotEmpty())onNodeWithText("정차했어요").performClick()
}
internal fun ComposeContentTestRule.openAccount() {
    onNodeWithContentDescription("더보기").performClick()
    onNodeWithText("계정 관리").performClick()
    confirmParkedIfNeeded()
}
internal fun ComposeContentTestRule.endTripFromMenu() {
    onNodeWithContentDescription("통화방 메뉴").performScrollTo().performClick()
    onNodeWithText("운행 종료").performClick()
    onNodeWithText("운행 종료 확인").performClick()
}
