package com.routemate.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class CarUxSmokeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun primaryTouchTargetsAndDayNightToggleRemainAccessible() {
        compose.onNodeWithText("목적지 등록").performScrollTo().assertWidthIsAtLeast(76.dp).assertHeightIsAtLeast(76.dp)
        compose.onNodeWithText("야간").assertWidthIsAtLeast(76.dp).assertHeightIsAtLeast(76.dp).performClick()
        compose.onNodeWithText("주간").assertIsDisplayed().performClick()
        compose.onNodeWithText("야간").assertIsDisplayed()
        compose.onNodeWithText("계정 관리").assertDoesNotExist()
        compose.openParkedSettings()
        compose.onNodeWithText("계정 관리").assertIsDisplayed()
    }

    @Test fun offlinePrototypeLabelsSimulationAndSupportsTheGroupFlow() {
        assumeTrue("Requires the explicitly isolated offline prototype build",BuildConfig.PROTOTYPE_DEMO)
        compose.onNodeWithText("프로토타입 체험 · 예시 차량·통화방이며 실제 음성은 연결되지 않습니다").assertIsDisplayed()
        compose.onNodeWithText("목적지 등록").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(10_000){compose.onAllNodesWithText("현재 운행 · 서울역 (샘플)").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("5km 방 검색").performScrollTo().performClick()
        compose.waitUntil(10_000){compose.onAllNodesWithText("서울역으로 가는 길 참여").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("서울역으로 가는 길 참여").performScrollTo()
        capture("dashboard-results.png")
        compose.onNodeWithText("서울역으로 가는 길 참여").performScrollTo().performClick()
        compose.waitUntil(10_000){compose.onAllNodesWithText("통화 화면 체험").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("통화 화면 체험").performScrollTo().performClick()
        compose.waitUntil(10_000){compose.onAllNodesWithText("통화 화면 체험 중").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("체험 모드에서는 실제 음성을 송수신하지 않습니다.").performScrollTo().assertIsDisplayed()
        capture("dashboard-call.png")
        if(InstrumentationRegistry.getArguments().getString("prototypeScreenshots")=="true"){
            compose.onNodeWithText("야간").performClick();compose.waitForIdle();capture("dashboard-night.png")
            compose.onNodeWithText("주간").performClick()
        }
        compose.onNodeWithText("음성 연결 완료").assertDoesNotExist()
        compose.onNodeWithText("통화방 퇴장").performScrollTo().performClick()
        compose.waitUntil(10_000){compose.onAllNodesWithText("새 방 만들기").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("운행 종료").performScrollTo().performClick()
        compose.onNodeWithText("운행 종료 확인").performClick()
        compose.waitUntil(10_000){compose.onAllNodesWithText("먼저 운행 목적지를 등록하세요.").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun capture(name:String){
        if(InstrumentationRegistry.getArguments().getString("prototypeScreenshots")!="true")return
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val bitmap=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try{File(instrumentation.targetContext.getExternalFilesDir(null),name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
        finally{bitmap.recycle()}
    }
}
