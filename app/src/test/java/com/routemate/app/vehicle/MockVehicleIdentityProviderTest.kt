package com.routemate.app.vehicle

import com.routemate.app.vehicle.domain.*
import com.routemate.app.vehicle.infrastructure.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class MockVehicleIdentityProviderTest {
    @Test fun simultaneousFirstReadsAcrossProvidersCreateOneInstallationVin()=runBlocking {
        val value=AtomicReference<String?>();val writes=AtomicInteger()
        val store=object:MockVinStore {
            override fun load():String? {
                val before=value.get()
                if(before==null)Thread.sleep(20)
                return before
            }
            override fun save(vin:String):Boolean {writes.incrementAndGet();value.set(vin);return true}
        }
        val start=CompletableDeferred<Unit>()
        val results=List(8){
            val provider=MockVehicleIdentityProvider(store)
            async(Dispatchers.IO){start.await();provider.currentVehicle()}
        }
        start.complete(Unit)
        val vehicles=results.awaitAll()
        assertEquals(1,writes.get())
        assertEquals(1,vehicles.map{it.vin.value}.distinct().size)
        assertEquals(value.get()?.uppercase(java.util.Locale.ROOT),vehicles.first().vin.value)
        assertTrue(vehicles.all{it.source==VehicleSource.MOCK})
    }

    @Test fun failedPersistenceAndCorruptVinNeverReturnAnUnstableIdentity()=runBlocking {
        val failed=object:MockVinStore {override fun load():String?=null;override fun save(vin:String)=false}
        try{MockVehicleIdentityProvider(failed).currentVehicle();fail("Expected persistence failure")}
        catch(e:VehicleIdentityException){assertEquals(VehicleIdentityFailure.CONNECTION,e.reason)}
        val corrupt=object:MockVinStore {override fun load()="invalid";override fun save(vin:String)=error("Must preserve stored data")}
        try{MockVehicleIdentityProvider(corrupt).currentVehicle();fail("Expected invalid identity")}
        catch(e:VehicleIdentityException){assertEquals(VehicleIdentityFailure.INVALID_DATA,e.reason)}
    }
}
