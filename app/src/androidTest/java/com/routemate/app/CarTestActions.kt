package com.routemate.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/** The test confirms the same explicit parked-use notice shown to users. */
internal fun ComposeContentTestRule.openParkedSettings() {
    onNodeWithText("정차 중 설정").performClick()
    onNodeWithText("정차 후 설정 열기").performClick()
}
