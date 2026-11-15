package com.routemate.app
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue

class RoomSmokeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun roomActionsRequireAnActiveTrip(){
        assumeTrue(BuildConfig.BACKEND_URL.isBlank()&&!BuildConfig.PROTOTYPE_DEMO)
        compose.onNodeWithText("가까운 동행").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("3km 방 검색").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("5km 방 검색").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("10km 방 검색").assertDoesNotExist()
        compose.onNodeWithText("방 만들기").performScrollTo().assertIsNotEnabled()
    }
}
