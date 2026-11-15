package com.routemate.app.room.presentation

import androidx.lifecycle.ViewModel
import com.routemate.app.destination.domain.Destination
import com.routemate.app.room.application.*
import com.routemate.app.room.domain.RoomPolicy
import com.routemate.app.trip.domain.TripRegistration
import com.routemate.app.voice.domain.VoiceCall

typealias RoomUiState = RoomSessionState

/** A screen observes the application session; clearing a screen never ends a production call. */
class RoomViewModel(val session: RoomSession, private val ownsSession: Boolean = false) : ViewModel() {
    constructor(rooms: ManageRooms, voice: VoiceCall, trips: TripRegistration, heartbeatMs: Long = RoomPolicy.heartbeatMs) :
        this(RoomSession(rooms,voice,trips,heartbeatMs=heartbeatMs),true)
    val state = session.state
    fun refresh() = session.refresh()
    fun search(radiusKm:Int) = session.search(radiusKm)
    fun create(name:String,isPublic:Boolean,capacity:Int) = session.create(name,isPublic,capacity)
    fun join(id:String) = session.join(id)
    fun switchTo(invitation:String) = session.switchTo(invitation)
    fun leave() = session.leave()
    fun endTrip() = session.endTrip()
    fun connect(permitted:Boolean) = session.connect(permitted)
    fun toggleMicrophone() = session.toggleMicrophone()
    fun destinationChanged(value:Destination?) = session.destinationChanged(value)
    fun startDestination(value:Destination) = session.startDestination(value)
    fun registerDestination(value:Destination) = session.registerDestination(value)
    fun syncDestination() = session.syncDestination()
    fun foreground() = session.foreground()
    fun background() = session.background()
    override fun onCleared(){if(ownsSession)session.close()}
}
