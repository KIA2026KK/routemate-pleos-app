package com.routemate.app.auth

import com.routemate.app.auth.domain.*
import com.routemate.app.auth.infrastructure.HttpVehicleAccountAccess
import com.routemate.app.trip.infrastructure.SessionStore
import com.routemate.app.vehicle.domain.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HttpVehicleAccountAccessTest {
    private val identity=VehicleIdentity(Vin("KNA00000000000001"),VehicleSource.PLEOS_SDK,VinReadAccess.READ)
    private val provider=VehicleIdentityProvider {identity}
    private val lease=""""vehicleId":"vehicle-a","maskedVin":"*************0001","expiresAt":"2027-01-01T00:00:00Z""""
    private class Store(var token:String?=null):SessionStore {
        override fun load()=token
        override fun save(token:String){this.token=token}
    }
    private fun server()=MockWebServer().apply{start(java.net.InetAddress.getByName("127.0.0.1"),0)}
    private fun MockWebServer.base()=url("/").newBuilder().host("127.0.0.1").build().toString()
    @Test fun renewalAndKeyIssuanceUseExistingCredentialWithoutCreatingUsers()=runBlocking {
        server().use{server->
            val store=Store("a".repeat(43));val access=HttpVehicleAccountAccess(server.base(),{store},provider,allowLocalHttp=true)
            server.enqueue(MockResponse().setBody("{$lease}"))
            server.enqueue(MockResponse().setResponseCode(201).setBody("{$lease,\"recoveryKey\":\"${"k".repeat(43)}\"}"))
            assertEquals("vehicle-a",access.renew().vehicleId)
            val key=access.createRecoveryKey();assertFalse(key.toString().contains("k".repeat(43)))
            assertEquals("/api/v1/auth/device-sessions/renew",server.takeRequest().path)
            val request=server.takeRequest();assertEquals("/api/v1/auth/recovery-keys",request.path)
            assertEquals("Bearer ${store.token}",request.getHeader("Authorization"))
            assertEquals(2,server.requestCount)
        }
    }
    @Test fun restoredCredentialIsStoredForCurrentVinOnly()=runBlocking {
        server().use{server->
            val store=Store();var scope:VehicleIdentity?=null
            val access=HttpVehicleAccountAccess(server.base(),{scope=it;store},provider,allowLocalHttp=true)
            server.enqueue(MockResponse().setBody("{$lease,\"accessToken\":\"${"n".repeat(43)}\"}"))
            access.recover("k".repeat(43))
            val request=server.takeRequest();assertNull(request.getHeader("Authorization"))
            assertEquals(identity.vin.value,JSONObject(request.body.readUtf8()).getString("vin"))
            assertEquals(identity,scope);assertEquals("n".repeat(43),store.token)
        }
    }
    @Test fun rejectedRecoveryNeverReplacesExistingCredential()=runBlocking {
        server().use{server->
            val store=Store("a".repeat(43));server.enqueue(MockResponse().setResponseCode(401))
            val access=HttpVehicleAccountAccess(server.base(),{store},provider,allowLocalHttp=true)
            try{access.recover("k".repeat(43));fail("Expected rejection")}
            catch(e:AccountAccessException){assertEquals(AccountFailure.RECOVERY_REJECTED,e.reason)}
            assertEquals("a".repeat(43),store.token)
        }
    }
    @Test fun missingCredentialAndMissingVinDoNotCreateAnonymousFallback()=runBlocking {
        server().use{server->
            val access=HttpVehicleAccountAccess(server.base(),{Store()},provider,allowLocalHttp=true)
            try{access.createRecoveryKey();fail("Expected missing session")}
            catch(e:AccountAccessException){assertEquals(AccountFailure.UNAUTHORIZED,e.reason)}
            val denied=VehicleIdentityProvider{throw VehicleIdentityException(VehicleIdentityFailure.PERMISSION)}
            try{HttpVehicleAccountAccess(server.base(),{Store()},denied,allowLocalHttp=true).recover("k".repeat(43));fail("Expected missing VIN")}
            catch(e:AccountAccessException){assertEquals(AccountFailure.VEHICLE_UNAVAILABLE,e.reason)}
            assertEquals(0,server.requestCount)
        }
    }
}
