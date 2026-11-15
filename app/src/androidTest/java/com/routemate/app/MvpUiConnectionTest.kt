package com.routemate.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.routemate.app.room.infrastructure.HttpRoomAccess
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class MvpUiConnectionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun autoRegistersCreatesAndConnectsRoomPreservesCallOnRecreationAndEndsTrip() {
        assumeTrue(BuildConfig.BACKEND_URL.isNotBlank() && InstrumentationRegistry.getArguments().getString("mvpE2E") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.RECORD_AUDIO").close()
        val access = HttpRoomAccess(BuildConfig.BACKEND_URL, { vehicleStore(context, it) }, vehicleProvider(context), allowLocalHttp = true)
        try {
            compose.waitUntil(40_000) { compose.onAllNodesWithText("목적지 등록 완료").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("광주송정역").assertIsDisplayed()
            compose.clickReadyText("방 만들기")
            compose.confirmParkedIfNeeded()
            val name = "UI 동행 ${UUID.randomUUID().toString().take(6)}"
            compose.onNodeWithText("방 이름").performTextClearance()
            compose.onNodeWithText("방 이름").performTextInput(name)
            compose.onNodeWithText("방 이름").performImeAction()
            compose.onNodeWithText("만들고 참여").performClick()
            compose.waitUntil(35_000) { compose.onAllNodesWithText("음성 연결 완료").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("마이크 켜기").performScrollTo().performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("마이크 끄기").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("마이크 끄기").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("마이크 켜기").fetchSemanticsNodes().isNotEmpty() }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("음성 연결 완료").fetchSemanticsNodes().isNotEmpty() }
            compose.clickReadyDescription("통화방 퇴장")
            compose.waitUntil(15_000) { compose.onAllNodesWithText("방 만들기").fetchSemanticsNodes().isNotEmpty() }
            compose.endTripFromMenu()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("목적지를 선택하면 동행을 찾습니다.").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("3km 방 검색").performScrollTo().assertIsNotEnabled()
        } finally { runBlocking { runCatching { access.endTrip() } } }
    }
}
