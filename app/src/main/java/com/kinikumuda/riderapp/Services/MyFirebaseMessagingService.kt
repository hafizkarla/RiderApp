package com.kinikumuda.riderapp.Services

import com.kinikumuda.riderapp.Comon.Comon
import com.kinikumuda.riderapp.Utils.UserUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kinikumuda.riderapp.Model.EventBus.DeclineRequestFromDriver
import com.kinikumuda.riderapp.Model.EventBus.DriverAcceptTripEvent
import org.greenrobot.eventbus.EventBus
import kotlin.random.Random

class MyFirebaseMessagingService: FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (FirebaseAuth.getInstance().currentUser != null)
            UserUtils.updateToken(this, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        if (data != null) {
            if (data[Comon.NOTI_TITLE] != null) {
                if (data[Comon.NOTI_TITLE].equals(Comon.REQUEST_DRIVER_DECLINE)) {
                    EventBus.getDefault().postSticky(DeclineRequestFromDriver())

                } else if (data[Comon.NOTI_TITLE] != null) {
                    if (data[Comon.NOTI_TITLE].equals(Comon.REQUEST_DRIVER_ACCEPT)) {
                        EventBus.getDefault().postSticky(DriverAcceptTripEvent(data[Comon.TRIP_KEY]!!))

                    } else
                        Comon.showNotification(
                            this, Random.nextInt(),
                            data[Comon.NOTI_TITLE],
                            data[Comon.NOTI_BODY],
                            null
                        )
                }
            }
        }
    }
}