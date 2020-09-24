package com.kinikumuda.riderapp.Callback

import com.kinikumuda.riderapp.Model.DriverGeoModel

interface FirebaseDriverInfoListener {
    fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?)
}