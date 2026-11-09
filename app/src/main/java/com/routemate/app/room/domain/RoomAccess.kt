package com.routemate.app.room.domain

import com.routemate.app.destination.domain.Destination
import com.routemate.app.voice.domain.VoiceConnection

data class RoomInfo(val id: String,val name: String,val isPublic: Boolean,val capacity: Int,val participants: Int,val expiresAt: String)
data class NearbyRoom(val room: RoomInfo,val distanceMeters: Long)
data class RoomSnapshot(val tripId: String?,val destination: Destination?,val room: RoomInfo?)
enum class RoomError { NETWORK, UNAUTHORIZED, TRIP_REQUIRED, ROOM_UNAVAILABLE, ROOM_FULL, ALREADY_IN_ROOM, NOT_IN_ROOM, VOICE_UNAVAILABLE, CONFIGURATION, VEHICLE_UNAVAILABLE, MOCK_VEHICLE_DISABLED, INVALID_REQUEST, TRIP_CHANGED, SERVER }
class RoomAccessException(val reason: RoomError) : Exception(reason.name)
interface RoomAccess {
    suspend fun snapshot(): RoomSnapshot
    suspend fun nearby(radiusKm: Int): List<NearbyRoom>
    suspend fun create(name: String,isPublic: Boolean,capacity: Int): RoomInfo
    suspend fun join(id: String): RoomInfo
    suspend fun leave(id: String)
    suspend fun heartbeat(id: String): RoomInfo
    suspend fun endTrip()
    suspend fun voice(id: String): VoiceConnection
}
