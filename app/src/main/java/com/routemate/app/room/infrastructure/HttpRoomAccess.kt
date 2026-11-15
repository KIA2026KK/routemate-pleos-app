package com.routemate.app.room.infrastructure

import com.routemate.app.common.infrastructure.*
import com.routemate.app.destination.domain.Destination
import com.routemate.app.room.domain.*
import com.routemate.app.trip.infrastructure.SessionStore
import com.routemate.app.trip.domain.TripRegistrationException
import com.routemate.app.vehicle.domain.*
import com.routemate.app.voice.domain.VoiceConnection
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.json.*

class HttpRoomAccess(baseUrl: String,private val stores: (VehicleIdentity)->SessionStore,
    private val provider: VehicleIdentityProvider,client: OkHttpClient=BackendJsonClient.client(),allowLocalHttp: Boolean=false) : RoomAccess {
    private val http=BackendJsonClient(baseUrl,client,allowLocalHttp)
    private fun room(value: JSONObject)=RoomInfo(value.getString("id"),value.getString("name"),value.getBoolean("isPublic"),
        value.getInt("capacity"),value.getInt("participants"),value.getString("expiresAt"))
    private fun id(value: String)=try { UUID.fromString(value).toString() } catch (_: Exception){throw RoomAccessException(RoomError.INVALID_REQUEST)}
    override suspend fun snapshot()=access { token ->
        val trip=http.get("/api/v1/trips/me",token).takeIf { it.isNotBlank() && it!="null" }?.let(::JSONObject)
        val current=http.get("/api/v1/rooms/me",token).takeIf { it.isNotBlank() && it!="null" }?.let(::JSONObject)
        RoomSnapshot(trip?.getString("id"),trip?.getJSONObject("destination")?.let { Destination(it.getString("name"),it.getDouble("latitude"),it.getDouble("longitude")) },current?.let(::room))
    }
    override suspend fun nearby(radiusKm: Int)=access { token ->
        require(radiusKm in RoomPolicy.radii)
        val array=JSONArray(http.get("/api/v1/rooms/nearby",token,mapOf("radiusKm" to radiusKm.toString())))
        List(array.length()) { index -> val value=array.getJSONObject(index);NearbyRoom(room(value.getJSONObject("room")),value.getLong("distanceMeters")) }
    }
    override suspend fun create(name: String,isPublic: Boolean,capacity: Int)=access { token ->
        require(name.isNotBlank()&&name.length<=60&&capacity in 2..8)
        room(http.post("/api/v1/rooms",token,JSONObject().put("name",name.trim()).put("isPublic",isPublic).put("capacity",capacity)))
    }
    override suspend fun join(id: String)=access { room(http.post("/api/v1/rooms/${id(id)}/join",it)) }
    override suspend fun leave(id: String) { access { http.post("/api/v1/rooms/${id(id)}/leave",it) } }
    override suspend fun heartbeat(id: String)=access { room(http.post("/api/v1/rooms/${id(id)}/heartbeat",it)) }
    override suspend fun endTrip() { access { http.post("/api/v1/trips/me/end",it) } }
    override suspend fun voice(id: String)=access { token ->
        val value=http.post("/api/v1/voice/token",token,JSONObject().put("roomId",id(id)))
        VoiceConnection(value.getString("url"),value.getString("token"))
    }
    private suspend fun <T> access(block: suspend (String)->T): T=withContext(Dispatchers.IO) {
        try {
            http.endpoint("/")
            val token=stores(provider.currentVehicle()).load() ?: throw RoomAccessException(RoomError.UNAUTHORIZED)
            block(token)
        }catch(e: CancellationException){throw e}
        catch(e: RoomAccessException){throw e}
        catch(_: TripRegistrationException){throw RoomAccessException(RoomError.UNAUTHORIZED)}
        catch(_: VehicleIdentityException){throw RoomAccessException(RoomError.VEHICLE_UNAVAILABLE)}
        catch(_: BackendConfigurationException){throw RoomAccessException(RoomError.CONFIGURATION)}
        catch(e: BackendHttpException){throw RoomAccessException(RoomError.entries.find { it.name==e.code } ?: when(e.status){401->RoomError.UNAUTHORIZED;400->RoomError.INVALID_REQUEST;else->RoomError.SERVER})}
        catch(_: IOException){throw RoomAccessException(RoomError.NETWORK)}
        catch(_: IllegalArgumentException){throw RoomAccessException(RoomError.INVALID_REQUEST)}
        catch(_: Exception){throw RoomAccessException(RoomError.SERVER)}
    }
}
