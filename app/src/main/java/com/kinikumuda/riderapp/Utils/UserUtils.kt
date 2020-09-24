package com.kinikumuda.riderapp.Utils

import android.content.Context
import android.view.View
import android.widget.Toast
import com.kinikumuda.riderapp.Comon.Comon
import com.kinikumuda.riderapp.Model.TokenModel
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

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
}