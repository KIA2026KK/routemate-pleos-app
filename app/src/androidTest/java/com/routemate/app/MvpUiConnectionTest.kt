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
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun registersDestinationCreatesRoomLeavesAndEndsTrip(){
        assumeTrue(BuildConfig.BACKEND_URL.isNotBlank()&&InstrumentationRegistry.getArguments().getString("mvpE2E")=="true")
        compose.waitUntil(15_000){compose.onAllNodesWithText("서울역 (샘플)").fetchSemanticsNodes().isNotEmpty()}
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.RECORD_AUDIO").close()
        val access=HttpRoomAccess(BuildConfig.BACKEND_URL,{vehicleStore(context,it)},vehicleProvider(context),allowLocalHttp=true)
        runBlocking{runCatching{access.endTrip()}}
        compose.onNodeWithText("통화방 상태 확인").performScrollTo().performClick()
        compose.waitUntil(15_000){compose.onAllNodesWithText("먼저 운행 목적지를 등록하세요.").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("목적지 등록").performScrollTo().performClick()
        compose.waitUntil(15_000){compose.onAllNodesWithText("현재 운행 · 서울역 (샘플)").fetchSemanticsNodes().isNotEmpty()}
        val name="UI 동행 ${UUID.randomUUID().toString().take(6)}"
        compose.openParkedSettings()
        compose.onNodeWithText("새 방 만들기").performScrollTo().performClick()
        compose.onNodeWithText("새 통화방 이름").performTextInput(name)
        compose.onNodeWithText("새 통화방 이름").performImeAction()
        compose.onNodeWithText("통화방 생성").performClick()
        compose.waitUntil(15_000){compose.onAllNodesWithText("참여 중 · $name · 1/4명").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("그룹 음성 연결").performScrollTo().performClick()
        compose.waitUntil(30_000){compose.onAllNodesWithText("음성 연결 완료").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("마이크 켜기").performScrollTo().performClick()
        compose.waitUntil(15_000){compose.onAllNodesWithText("마이크 끄기").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("마이크 끄기").performScrollTo().performClick()
        compose.waitUntil(15_000){compose.onAllNodesWithText("마이크 켜기").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("통화방 퇴장").performScrollTo().performClick()
        compose.waitUntil(15_000){compose.onAllNodesWithText("새 방 만들기").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("운행 종료").performScrollTo().performClick()
        compose.onNodeWithText("운행 종료 확인").performClick()
        compose.waitUntil(15_000){compose.onAllNodesWithText("먼저 운행 목적지를 등록하세요.").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("운행 종료").assertIsNotEnabled()
    }
}
