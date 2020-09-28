package com.kinikumuda.riderapp.Remote

import com.kinikumuda.riderapp.Model.FCMResponse
import com.kinikumuda.riderapp.Model.FCMSendData
import io.reactivex.Observable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface IFCMService {
    @Headers(
        "Content-Type:application/json",
        "Authorization:key=AAAAcx2tNn4:APA91bFwwqGDpeEV8dc-pQ_zzuUNnnjfv_jhCEH_g7bfyM48PftQv5vgvcK81kILR4UCspQrLgcR4qm7EZta2RBM4vQs-QqgdFYsa91KQvABll_P78uC_3bmL687Bm2A_1zEbx2KihWN"
    )
    @POST("fcm/send")
    fun sendNotification(@Body body: FCMSendData?):Observable<FCMResponse?>?
}