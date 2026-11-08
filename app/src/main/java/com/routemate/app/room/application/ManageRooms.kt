package com.routemate.app.room.application
import com.routemate.app.room.domain.RoomAccess
/** Validates user choices before invoking remote transport. */
class ManageRooms(private val access: RoomAccess) : RoomAccess by access {
    override suspend fun nearby(radiusKm: Int): List<com.routemate.app.room.domain.NearbyRoom> {
        require(radiusKm==5||radiusKm==10)
        return access.nearby(radiusKm)
    }
    override suspend fun create(name: String,isPublic: Boolean,capacity: Int): com.routemate.app.room.domain.RoomInfo {
        require(name.isNotBlank()&&name.length<=60&&capacity in 2..8)
        return access.create(name.trim(),isPublic,capacity)
    }
}
