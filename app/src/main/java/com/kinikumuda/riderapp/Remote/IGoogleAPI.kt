package com.kinikumuda.riderapp.Remote

import io.reactivex.Observable
import retrofit2.http.GET
import retrofit2.http.Query

interface IGoogleAPI {
    //must enable billing
    @GET("maps/api/directions/json")
    fun getDirection(
        @Query("mode") mode:String?,
        @Query("transit_routing_preference") transit_routing:String?,
        @Query("origin") from:String?,
        @Query("destination") to:String?,
        @Query("key") key:String?
    ):Observable<String>?
}