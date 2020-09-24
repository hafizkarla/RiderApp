package com.kinikumuda.riderapp

import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import com.google.maps.android.ui.IconGenerator
import com.kinikumuda.riderapp.Comon.Comon
import com.kinikumuda.riderapp.Model.EventBus.SelectedPlaceEvent
import com.kinikumuda.riderapp.Remote.IGoogleAPI
import com.kinikumuda.riderapp.Remote.RetrofitClient
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.origin_info_windows.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import retrofit2.Retrofit
import java.lang.Exception

class RequestDriverActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap

    private var selectedPlaceEvent:SelectedPlaceEvent?=null

    private lateinit var mapFragment:SupportMapFragment;


    //routes
    private val compositeDisposable=CompositeDisposable()
    private lateinit var iGoogleAPI: IGoogleAPI
    private var blackPolyLine: Polyline?=null
    private var greyPolyline:Polyline?=null
    private var polylineOptions:PolylineOptions?=null
    private var blackPolylineOptions:PolylineOptions?=null
    private var polylineList:ArrayList<LatLng?>?=null
    private var originMarker:Marker?=null
    private var destinationMarker:Marker?=null




    override fun onStart() {
        if (!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this)
        super.onStart()
    }

    override fun onStop() {
        compositeDisposable.clear()
        if (EventBus.getDefault().hasSubscriberForEvent(SelectedPlaceEvent::class.java))
            EventBus.getDefault().removeStickyEvent(SelectedPlaceEvent::class.java)
        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    @Subscribe(sticky = true,threadMode = ThreadMode.MAIN)
    fun onSelectPlaceEvent(event:SelectedPlaceEvent){
        selectedPlaceEvent=event
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_driver)

        init()

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun init() {
        iGoogleAPI=RetrofitClient.instance!!.create(IGoogleAPI::class.java)

    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            return
        }
        mMap.isMyLocationEnabled=true
        mMap.uiSettings.isMyLocationButtonEnabled=true
        mMap.setOnMyLocationClickListener {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(selectedPlaceEvent!!.origin,18f))
            true
        }

        drawPath(selectedPlaceEvent!!)
        //layout button
        val locationButton= (findViewById<View>("1".toInt())!!.parent!! as View)
            .findViewById<View>("2".toInt())
        val params=locationButton.layoutParams as RelativeLayout.LayoutParams
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP,0)
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM,RelativeLayout.TRUE)
        params.bottomMargin=250 //move to see zoom control

        mMap.uiSettings.isZoomControlsEnabled=true
        try {
            val success=googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this,
            R.raw.uber_maps_style))
            if (!success)
                Snackbar.make(mapFragment.requireView(),"Load map style failed",Snackbar.LENGTH_LONG).show()
        }catch (e:Exception)
        {
            Snackbar.make(mapFragment.requireView(),e.message!!,Snackbar.LENGTH_LONG).show()
        }

    }

    private fun drawPath(selectedPlaceEvent: SelectedPlaceEvent) {
        //request api
        compositeDisposable.add(iGoogleAPI.getDirection(
            "driving",
            "less_driving",
            selectedPlaceEvent.originString, selectedPlaceEvent.destinationString,
            getString(R.string.google_api_key)
        )
        !!.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { returnResult ->
                Log.d("API_RETURN", returnResult)
                try {

                    val jsonObject = JSONObject(returnResult)
                    val jsonArray = jsonObject.getJSONArray("routes");
                    for (i in 0 until jsonArray.length()) {
                        val route = jsonArray.getJSONObject(i)
                        val poly = route.getJSONObject("overview_polyline")
                        val polyline = poly.getString("points")
                        polylineList = Comon.decodePoly(polyline)

                    }
                    polylineOptions = PolylineOptions()
                    polylineOptions!!.color(Color.GRAY)
                    polylineOptions!!.width(12f)
                    polylineOptions!!.startCap(SquareCap())
                    polylineOptions!!.jointType(JointType.ROUND)
                    polylineOptions!!.addAll(polylineList)
                    greyPolyline = mMap.addPolyline(polylineOptions)

                    blackPolylineOptions = PolylineOptions()
                    blackPolylineOptions!!.color(Color.BLACK)
                    blackPolylineOptions!!.width(5f)
                    blackPolylineOptions!!.startCap(SquareCap())
                    blackPolylineOptions!!.jointType(JointType.ROUND)
                    blackPolylineOptions!!.addAll(polylineList)
                    blackPolyLine = mMap.addPolyline(blackPolylineOptions)

                    //animator
                    val valueAnimator = ValueAnimator.ofInt(0, 100)
                    valueAnimator.duration = 1100
                    valueAnimator.repeatCount = ValueAnimator.INFINITE
                    valueAnimator.interpolator = LinearInterpolator()
                    valueAnimator.addUpdateListener { value ->
                        val points = greyPolyline!!.points
                        val percentValue = value.animatedValue.toString().toInt()
                        val size = points.size
                        val newpoints = (size * (percentValue/100.0f)).toInt()
                        val p = points.subList(0, newpoints)
                        blackPolyLine!!.points=(p)

                    }

                    valueAnimator.start()

                    val latLngBound = LatLngBounds.Builder().include(selectedPlaceEvent.origin)
                        .include(selectedPlaceEvent.destination)
                        .build()

                    //add car icon for origin
                    val objects = jsonArray.getJSONObject(0)
                    val legs = objects.getJSONArray("legs")
                    val legsObject = legs.getJSONObject(0)

                    val time = legsObject.getJSONObject("duration")
                    val duration = time.getString("text")

                    val start_address = legsObject.getString("start_address")
                    val end_address = legsObject.getString("end_address")

                    addOriginMarker(duration, start_address)

                    addDestinationMarker(end_address)

                    mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(latLngBound, 160))
                    mMap.moveCamera(CameraUpdateFactory.zoomTo(mMap.cameraPosition!!.zoom - 1))


                } catch (e: java.lang.Exception) {
                    Toast.makeText(this, e.message!!, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun addDestinationMarker(endAddress: String) {
        val view=layoutInflater.inflate(R.layout.destination_info_window,null)

        val txt_destination=view.findViewById<View>(R.id.txt_destination) as TextView
        txt_destination.text=Comon.formatAddress(endAddress)

        val generator =IconGenerator(this)
        generator.setContentView(view)
        generator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon=generator.makeIcon()

        destinationMarker=mMap.addMarker(MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(icon))
            .position(selectedPlaceEvent!!.destination))
    }

    private fun addOriginMarker(duration: String, startAddress: String) {

        val view=layoutInflater .inflate(R.layout.origin_info_windows,null)

        val txt_time =view.findViewById<View>(R.id.txt_time) as TextView
        val txt_origin =view.findViewById<View>(R.id.txt_origin) as TextView

        txt_time.text=Comon.formatDuration(duration)
        txt_origin.text=Comon.formatAddress(startAddress)

        val generator =IconGenerator(this)
        generator.setContentView(view)
        generator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon=generator.makeIcon()

        originMarker=mMap.addMarker(MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(icon))
            .position(selectedPlaceEvent!!.origin))



    }
}