package com.routemate.app

import android.app.ActivityManager
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.routemate.app.destination.domain.Destination
import com.routemate.app.room.infrastructure.HttpRoomAccess
import com.routemate.app.trip.infrastructure.*
import com.routemate.app.vehicle.domain.*
import com.routemate.app.voice.domain.*
import com.routemate.app.voice.infrastructure.LiveKitVoiceCall
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in cloud test: real SDK tracks, not proof of two physical listeners hearing speech. */
class SustainedCallConnectionTest {
    @get:Rule(order=0) val sessionFixture=ResetRoomSessionRule()
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()
    private class Store:SessionStore {
        var token:String?=null
        override fun load()=token
        override fun save(token:String){this.token=token}
    }
    @Test fun threeSessionsKeepAudioTracksFor120SecondsWithActivityStopped()=runBlocking {
        assumeTrue(BuildConfig.BACKEND_URL.isNotBlank() && InstrumentationRegistry.getArguments().getString("sustainedE2E")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val session=(context.applicationContext as RouteMateApplication).graph.session
        grantVoicePermissions()
        val stores=List(2){Store()}
        val identities=List(2){VehicleIdentity(Vin("KNA"+UUID.randomUUID().toString().replace("-","").take(14)),VehicleSource.MOCK,null)}
        val providers=identities.map{identity->VehicleIdentityProvider{identity}}
        val trips=List(2){index->HttpTripRegistration(BuildConfig.BACKEND_URL,{stores[index]},providers[index])}
        val rooms=List(2){index->HttpRoomAccess(BuildConfig.BACKEND_URL,{stores[index]},providers[index])}
        val calls=List(2){LiveKitVoiceCall(context)}
        val states=List(2){MutableStateFlow(VoiceState())}
        val jobs=mutableListOf<Job>()
        var heartbeat:Job?=null
        try {
            withTimeout(260_000){
                compose.waitUntil(40_000){compose.onAllNodesWithText("목적지 등록 완료").fetchSemanticsNodes().isNotEmpty()}
                compose.clickReadyText("방 만들기");compose.confirmParkedIfNeeded()
                compose.onNodeWithText("방 이름").performTextClearance()
                compose.onNodeWithText("방 이름").performTextInput("지속 통화 "+UUID.randomUUID().toString().take(6))
                compose.onNodeWithText("방 이름").performImeAction()
                compose.onNodeWithText("만들고 참여").performClick()
                compose.waitUntil(40_000){compose.onAllNodesWithText("음성 연결 완료").fetchSemanticsNodes().isNotEmpty()}
                compose.clickReadyDescription("마이크 켜기")
                compose.waitUntil(15_000){compose.onAllNodesWithContentDescription("마이크 끄기").fetchSemanticsNodes().isNotEmpty()}
                val room=checkNotNull(session.state.value.snapshot.room)
                val destination=checkNotNull(session.state.value.snapshot.destination)
                trips.forEach{it.register(Destination(destination.name,destination.latitude,destination.longitude))}
                rooms.forEach{it.join(room.inviteCode)}
                heartbeat=launch{while(isActive){delay(20_000);rooms.forEach{it.heartbeat(room.id)}}}
                calls.forEachIndexed{index,call->val connection=rooms[index].voice(room.id);jobs+=launch{call.join(connection).collect{states[index].value=it}}}
                withTimeout(40_000){states.forEach{it.first{value->value.status==VoiceStatus.CONNECTED&&value.participants.size==3}}}
                calls.forEach{it.setMicrophoneEnabled(true)}
                withTimeout(40_000){states.forEach{it.first{value->value.microphoneEnabled&&value.subscribedAudioTracks==2}};session.state.first{it.voice.subscribedAudioTracks==2}}
                android.util.Log.i("RouteMateE2E","stage=sustained-tracks-ready")
                compose.onNodeWithContentDescription("홈으로").performClick()
                compose.onNodeWithContentDescription("현재 통화방 열기").assertIsDisplayed().performClick()
                compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
                android.util.Log.i("RouteMateE2E","stage=sustained-background-start")
                repeat(12){
                    delay(10_000)
                    assertTrue("Production call lost during background interval $it",session.state.value.callActive)
                    assertEquals(VoiceStatus.CONNECTED,session.state.value.voice.status)
                    assertEquals(2,session.state.value.voice.subscribedAudioTracks)
                    assertTrue(session.state.value.voice.microphoneEnabled)
                    states.forEach{state->assertEquals(VoiceStatus.CONNECTED,state.value.status);assertEquals(2,state.value.subscribedAudioTracks)}
                    @Suppress("DEPRECATION") val foreground=context.getSystemService(ActivityManager::class.java).getRunningServices(30)
                    assertTrue("Call foreground service missing",foreground.any{it.service.className.endsWith("VoiceCallService")&&it.foreground})
                }
                compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
                android.util.Log.i("RouteMateE2E","stage=sustained-background-120s-passed")
                compose.clickReadyDescription("통화방 퇴장")
                compose.waitUntil(20_000){session.state.value.snapshot.room==null&&!session.state.value.callActive}
                compose.endTripFromMenu()
                compose.waitUntil(20_000){session.state.value.snapshot.tripId==null}
                // Complete child collectors before leaving withTimeout's structured scope.
                // Waiting until the outer finally would keep this scope alive until its timeout.
                heartbeat?.cancelAndJoin();heartbeat=null
                jobs.forEach{it.cancel()};jobs.joinAll()
                android.util.Log.i("RouteMateE2E","stage=sustained-complete")
            }
        } finally {
            heartbeat?.cancelAndJoin();jobs.forEach{it.cancel()};jobs.joinAll()
            withContext(NonCancellable){rooms.forEach{runCatching{it.endTrip()}}}
            runCatching{compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)}
        }
    }
}
