package com.routemate.app.room

import com.routemate.app.room.infrastructure.HttpRoomAccess
import com.routemate.app.trip.infrastructure.SessionStore
import com.routemate.app.vehicle.domain.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RoomInvitationHttpTest {
    @Test fun codesAndUuidSwitchesUseAuthenticatedAtomicJoinProtocol()=runTest {
        val server=MockWebServer();server.start()
        val id="d546dcdc-d6de-4416-a6f7-e5c9783a4001"
        val json="""{"id":"$id","name":"동행","isPublic":false,"capacity":4,"participants":2,"expiresAt":"later","inviteCode":"ABCDEFG2"}"""
        val identity=VehicleIdentity(Vin("KNA00000000000001"),VehicleSource.MOCK,null)
        val store=object:SessionStore{override fun load()="test-session";override fun save(token:String)=Unit}
        val access=HttpRoomAccess("http://127.0.0.1:${server.port}",{store},VehicleIdentityProvider{identity},allowLocalHttp=true)
        try{
            repeat(3){server.enqueue(MockResponse().setBody(json))}
            assertEquals("ABCDEFG2",access.join(" abcdef23 ").inviteCode)
            val join=server.takeRequest();assertEquals("/api/v1/rooms/join",join.path);assertEquals("Bearer test-session",join.getHeader("Authorization"))
            val body=JSONObject(join.body.readUtf8());assertEquals("ABCDEF23",body.getString("code"));assertFalse(body.getBoolean("replaceCurrent"))
            access.switchTo("ABCDEF23");assertTrue(JSONObject(server.takeRequest().body.readUtf8()).getBoolean("replaceCurrent"))
            access.switchTo(id);assertEquals("/api/v1/rooms/$id/switch",server.takeRequest().path)
        }finally{server.shutdown()}
    }
}
