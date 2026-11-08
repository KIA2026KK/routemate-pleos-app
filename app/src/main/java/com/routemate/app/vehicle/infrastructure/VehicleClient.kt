package com.routemate.app.vehicle.infrastructure

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import com.routemate.app.vehicle.domain.VinReadAccess

internal interface VehicleClient {
    fun initialize()
    fun checkVinAccess(onComplete: (VinReadAccess?) -> Unit, onFailed: (Exception) -> Unit)
    fun getVin(onComplete: (String?) -> Unit, onFailed: (Exception) -> Unit)
    fun release()
}

internal class AndroidVehicleClient(context: Context) : VehicleClient {
    private val vehicle = Vehicle(context.applicationContext)
    override fun initialize() = vehicle.initialize()
    override fun checkVinAccess(onComplete: (VinReadAccess?) -> Unit, onFailed: (Exception) -> Unit) {
        vehicle.getCarInfo().checkVINInfoCapabilities({ config ->
            onComplete(when (config.accessType) {
                AccessType.READ -> VinReadAccess.READ
                AccessType.READ_WRITE -> VinReadAccess.READ_WRITE
                else -> null
            })
        }, onFailed)
    }
    override fun getVin(onComplete: (String?) -> Unit, onFailed: (Exception) -> Unit) =
        vehicle.getCarInfo().getVINInfo(onComplete, onFailed)
    override fun release() = vehicle.release()
}
