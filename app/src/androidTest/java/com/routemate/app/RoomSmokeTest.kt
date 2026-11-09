package com.routemate.app
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue

class RoomSmokeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun roomActionsRequireAnActiveTrip(){
        assumeTrue(BuildConfig.BACKEND_URL.isBlank())
        compose.onNodeWithText("목적지 그룹 통화").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("5km 방 검색").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("새 방 만들기").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("운행 종료").performScrollTo().assertIsNotEnabled()
    }
}
