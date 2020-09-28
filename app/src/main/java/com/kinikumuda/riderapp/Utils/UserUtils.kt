package com.kinikumuda.riderapp.Utils

import android.content.Context
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast
import com.google.android.gms.maps.model.LatLng
import com.kinikumuda.riderapp.Comon.Comon
import com.kinikumuda.riderapp.Model.TokenModel
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.kinikumuda.riderapp.Model.DriverGeoModel
import com.kinikumuda.riderapp.Model.EventBus.SelectedPlaceEvent
import com.kinikumuda.riderapp.Model.FCMSendData
import com.kinikumuda.riderapp.R
import com.kinikumuda.riderapp.Remote.IFCMService
import com.kinikumuda.riderapp.Remote.RetrofitFCMClient
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.lang.StringBuilder

object UserUtils {
    fun updateUser(
        view: View?,
        updateData:Map<String,Any>
    ){
        FirebaseDatabase.getInstance()
            .getReference(Comon.RIDER_INFO_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .updateChildren(updateData)
            .addOnFailureListener{e->
                Snackbar.make(view!!,e.message!!, Snackbar.LENGTH_SHORT).show()
            }.addOnSuccessListener {
                Snackbar.make(view!!,"Update information success", Snackbar.LENGTH_SHORT).show()
            }
    }
    fun updateToken(context: Context, token: String) {
        val tokenModel= TokenModel()
        tokenModel.token=token;

        FirebaseDatabase.getInstance()
            .getReference(Comon.TOKEN_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .setValue(tokenModel)
            .addOnFailureListener{e-> Toast.makeText(context,e.message, Toast.LENGTH_SHORT).show()}
            .addOnSuccessListener {  }
    }

    fun sendRequestToDriver(context: Context, mainLayout: RelativeLayout?, foundDriver: DriverGeoModel?, selectedPlaceEvent: SelectedPlaceEvent) {
        val compositeDisposable=CompositeDisposable()
        val ifcmService=RetrofitFCMClient.instance!!.create(IFCMService::class.java)

        //get token
        FirebaseDatabase.getInstance()
            .getReference(Comon.TOKEN_REFERENCE)
            .child(foundDriver!!.key!!)
            .addListenerForSingleValueEvent(object :ValueEventListener{
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.exists())
                    {
                        val tokenModel=dataSnapshot.getValue(TokenModel::class.java)

                        val notificationData:MutableMap<String,String> = HashMap()
                        notificationData.put(Comon.NOTI_TITLE,Comon.REQUEST_DRIVER_TITLE)
                        notificationData.put(Comon.NOTI_BODY,"This message represent for request driver action")
                        notificationData.put(Comon.RIDER_KEY,FirebaseAuth.getInstance().currentUser!!.uid)

                        notificationData.put(Comon.PICKUP_LOCATION_STRING,selectedPlaceEvent.originString)
                        notificationData.put(Comon.PICKUP_LOCATION,StringBuilder()
                            .append(selectedPlaceEvent.origin.latitude)
                            .append(",")
                            .append(selectedPlaceEvent.origin.longitude)
                            .toString())

                        notificationData.put(Comon.DESTINATION_LOCATION_STRING,selectedPlaceEvent.destinationString)
                        notificationData.put(Comon.DESTINATION_LOCATION,StringBuilder()
                            .append(selectedPlaceEvent.destination.latitude)
                            .append(",")
                            .append(selectedPlaceEvent.destination.longitude)
                            .toString())

                        val fcmSendData=FCMSendData(tokenModel!!.token,notificationData)

                        compositeDisposable.add(ifcmService.sendNotification(fcmSendData)!!
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({fcmResponse->
                                if (fcmResponse!!.success == 0)
                                {
                                    compositeDisposable.clear()
                                    Snackbar.make(mainLayout!!,context.getString(R.string.send_request_driver_failed),Snackbar.LENGTH_LONG).show()
                                }
                            },{t: Throwable?->

                                compositeDisposable.clear()
                                Snackbar.make(mainLayout!!,t!!.message!!,Snackbar.LENGTH_LONG).show()

                            }))
                    }
                    else
                    {
                        Snackbar.make(mainLayout!!,context.getString(R.string.token_not_found),Snackbar.LENGTH_LONG).show()
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                Snackbar.make(mainLayout!!,databaseError.message,Snackbar.LENGTH_LONG).show()
                }

            })
    }
}