package com.kinikumuda.riderapp.Model

import com.firebase.geofire.GeoLocation

class DriverGeoModel {
    var key:String?=null
    var geoLocation: GeoLocation?=null
    var driverInfoModel:DriverInfoModel?=null
    var isDecline:Boolean=false
    var isDone:Boolean=false

    constructor(key:String?,geoLocation:GeoLocation?){
        this.key=key
        this.geoLocation=geoLocation!!

    }


}