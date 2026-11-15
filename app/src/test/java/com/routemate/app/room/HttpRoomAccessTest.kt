package com.routemate.app.room

import com.routemate.app.room.domain.*
import com.routemate.app.room.infrastructure.HttpRoomAccess
import com.routemate.app.trip.infrastructure.SessionStore
import com.routemate.app.trip.domain.*
import com.routemate.app.vehicle.domain.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class HttpRoomAccessTest {
    private val roomId="ec89f44d-f167-40d0-9bd4-8785fa82bdbc"
    private val room="""{"id":"$roomId","name":"동행","isPublic":true,"capacity":3,"participants":1,"expiresAt":"2026-12-01T00:00:00Z"}"""
    private val vehicle=VehicleIdentity(Vin("KNA00000000000001"),VehicleSource.MOCK,null)
    private class Store(var token: String?="a".repeat(43)):SessionStore {
        override fun load()=token
        override fun save(token: String){error("Room API must not replace credentials")}
    }
    private fun api(server: MockWebServer,store: SessionStore=Store())=HttpRoomAccess("http://127.0.0.1:${server.port}",{store},VehicleIdentityProvider{vehicle},allowLocalHttp=true)
    @Test fun loadsNullableTripAndRoomWithoutCreatingSession()=runBlocking {
        MockWebServer().use { server ->
            server.start();server.enqueue(MockResponse().setBody(""));server.enqueue(MockResponse().setBody("null"))
            val snapshot=api(server).snapshot();assertNull(snapshot.tripId);assertNull(snapshot.room)
            assertEquals("GET",server.takeRequest().method);assertEquals("/api/v1/rooms/me",server.takeRequest().path)
            assertEquals(2,server.requestCount)
        }
    }
    @Test fun searchesByRadiusAndCreatesJoinsAndLeavesUsingBearerOnly()=runBlocking {
        MockWebServer().use { server ->
            server.start();val access=api(server)
            server.enqueue(MockResponse().setBody("""[{"room":$room,"distanceMeters":1234}]"""))
            assertEquals(1234L,access.nearby(3).single().distanceMeters)
            assertEquals("/api/v1/rooms/nearby?radiusKm=3",server.takeRequest().path)
            repeat(3){server.enqueue(MockResponse().setBody(room))}
            assertEquals(roomId,access.create("동행",true,3).id)
            val create=server.takeRequest();assertEquals("Bearer "+"a".repeat(43),create.getHeader("Authorization"));assertFalse(create.body.readUtf8().contains("vin"))
            access.join(roomId);assertEquals("/api/v1/rooms/$roomId/join",server.takeRequest().path)
            access.heartbeat(roomId);assertEquals("/api/v1/rooms/$roomId/heartbeat",server.takeRequest().path)
            server.enqueue(MockResponse().setBody("{\"left\":true}"));access.leave(roomId)
            assertEquals("/api/v1/rooms/$roomId/leave",server.takeRequest().path)
        }
    }
    @Test fun mapsCapacityConflictAndVoiceOutageWithoutLeakingServerDetails()=runBlocking {
        MockWebServer().use { server ->
            server.start();val access=api(server)
            server.enqueue(MockResponse().setResponseCode(409).setBody("{\"code\":\"ROOM_FULL\"}"))
            try{access.join(roomId);fail()}catch(e:RoomAccessException){assertEquals(RoomError.ROOM_FULL,e.reason)}
            server.enqueue(MockResponse().setResponseCode(503).setBody("{\"code\":\"VOICE_UNAVAILABLE\"}"))
            try{access.voice(roomId);fail()}catch(e:RoomAccessException){assertEquals(RoomError.VOICE_UNAVAILABLE,e.reason)}
        }
    }
    @Test fun missingCredentialAndInvalidInvitationDoNotSendRequests()=runBlocking {
        MockWebServer().use { server ->
            server.start()
            try{api(server,Store(null)).snapshot();fail()}catch(e:RoomAccessException){assertEquals(RoomError.UNAUTHORIZED,e.reason)}
            try{api(server).join("../admin");fail()}catch(e:RoomAccessException){assertEquals(RoomError.INVALID_REQUEST,e.reason)}
            assertEquals(0,server.requestCount)
        }
    }
    @Test fun unreadableCredentialRequestsRecoveryInsteadOfReportingServerFailure()=runBlocking {
        MockWebServer().use { server ->
            server.start()
            val broken=object:SessionStore {
                override fun load():String?=throw TripRegistrationException(TripFailure.UNAUTHORIZED)
                override fun save(token:String)=error("Must not replace credentials")
            }
            try{api(server,broken).snapshot();fail("Expected authentication failure")}
            catch(e:RoomAccessException){assertEquals(RoomError.UNAUTHORIZED,e.reason)}
            assertEquals(0,server.requestCount)
        }
    }
}
