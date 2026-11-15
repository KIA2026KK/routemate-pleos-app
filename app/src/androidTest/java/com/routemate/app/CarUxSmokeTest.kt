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
        compose.onNodeWithContentDescription("야간 모드").assertWidthIsAtLeast(76.dp).assertHeightIsAtLeast(76.dp).performClick()
        compose.onNodeWithContentDescription("주간 모드").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("야간 모드").assertIsDisplayed()
        compose.onNodeWithText("계정 관리").assertDoesNotExist()
        compose.onNodeWithContentDescription("더보기").performClick()
        compose.onNodeWithText("계정 관리").assertIsDisplayed()
    }

    @Test fun offlinePrototypeLabelsSimulationAndSupportsTheGroupFlow() {
        assumeTrue("Requires the explicitly isolated offline prototype build",BuildConfig.PROTOTYPE_DEMO)
        compose.onNodeWithText("체험 모드 · 실제 음성 없음").assertIsDisplayed()
        compose.waitUntil(10_000){compose.onAllNodesWithText("목적지 등록 완료").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("3km 방 검색").performScrollTo().assertHeightIsAtLeast(64.dp)
        compose.onNodeWithContentDescription("광주송정역 동행 참여").performScrollTo()
        capture("dashboard-results.png")
        compose.onNodeWithContentDescription("광주송정역 동행 참여").performClick()
        compose.waitUntil(10_000){compose.onAllNodesWithText("통화 체험 중").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("마이크 켜기").performScrollTo().assertHeightIsAtLeast(100.dp)
        capture("dashboard-call.png")
        if(InstrumentationRegistry.getArguments().getString("prototypeScreenshots")=="true"){
            compose.onNodeWithContentDescription("야간 모드").performClick();compose.waitForIdle();capture("dashboard-night.png")
            compose.onNodeWithContentDescription("주간 모드").performClick()
        }
        compose.onNodeWithText("음성 연결 완료").assertDoesNotExist()
        compose.onNodeWithContentDescription("통화방 퇴장").performScrollTo().performClick()
        compose.waitUntil(10_000){compose.onAllNodesWithText("방 만들기").fetchSemanticsNodes().isNotEmpty()}
        compose.clickReadyText("방 만들기");compose.confirmParkedIfNeeded()
        compose.onNodeWithText("방 이름").assertIsDisplayed()
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.waitUntil(10_000){compose.onAllNodesWithText("방 이름").fetchSemanticsNodes().isEmpty()}
        compose.endTripFromMenu()
        compose.waitUntil(10_000){compose.onAllNodesWithText("목적지를 선택하면 동행을 찾습니다.").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("3km 방 검색").performScrollTo().assertIsNotEnabled()
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
