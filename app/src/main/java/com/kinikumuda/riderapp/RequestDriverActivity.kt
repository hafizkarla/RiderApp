package com.kinikumuda.riderapp

import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.inflate
import android.view.animation.LinearInterpolator
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import com.google.maps.android.ui.IconGenerator
import com.kinikumuda.riderapp.Comon.Comon
import com.kinikumuda.riderapp.Model.DriverGeoModel
import com.kinikumuda.riderapp.Model.EventBus.DeclineRequestFromDriver
import com.kinikumuda.riderapp.Model.EventBus.SelectedPlaceEvent
import com.kinikumuda.riderapp.Remote.IGoogleAPI
import com.kinikumuda.riderapp.Remote.RetrofitClient
import com.kinikumuda.riderapp.Utils.UserUtils
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_request_driver.*
import kotlinx.android.synthetic.main.layout_confirm_pickup.*
import kotlinx.android.synthetic.main.layout_confirm_uber.*
import kotlinx.android.synthetic.main.layout_finding_your_driver.*
import kotlinx.android.synthetic.main.origin_info_windows.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import retrofit2.Retrofit
import java.lang.Exception
import java.lang.StringBuilder

class RequestDriverActivity : AppCompatActivity(), OnMapReadyCallback {

    //spinning animation
     var animator:ValueAnimator?=null
    private val DESIRED_NUM_OF_SPINS = 5
    private val DESIRED_SECONDS_PER_ONE_FULL_360_SPIN=40

    //effect
     var lastUserCircle:Circle?=null
    val duration =1000
     var lastPulseAnimator:ValueAnimator?=null

    private lateinit var mMap: GoogleMap
    private lateinit var txt_origin:TextView

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

    private var lastDriverCall: DriverGeoModel?=null




    override fun onStart() {
        super.onStart()
        if (!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this)

    }

    override fun onStop() {
        compositeDisposable.clear()
        if (EventBus.getDefault().hasSubscriberForEvent(SelectedPlaceEvent::class.java))
            EventBus.getDefault().removeStickyEvent(SelectedPlaceEvent::class.java)
        if (EventBus.getDefault().hasSubscriberForEvent(DeclineRequestFromDriver::class.java))
            EventBus.getDefault().removeStickyEvent(DeclineRequestFromDriver::class.java)
        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    @Subscribe(sticky = true,threadMode = ThreadMode.MAIN)
    fun onDeclineReceived(event:DeclineRequestFromDriver)
    {
        if (lastDriverCall != null)
        {
            Comon.driversFound.get(lastDriverCall!!.key)!!.isDecline=true
            findNearbyDriver(selectedPlaceEvent!!)
        }
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

        //event
        btn_confirm_uber.setOnClickListener {
            confirm_pickup_layout.visibility=View.VISIBLE
            confirm_uber_layout.visibility=View.GONE

            setDataPickup()
        }

        btn_confirm_pickup.setOnClickListener {
            if(mMap==null) return@setOnClickListener
            if (selectedPlaceEvent==null) return@setOnClickListener

            //clear map
            mMap.clear()
            //tilt
            val cameraPos=CameraPosition.Builder().target(selectedPlaceEvent!!.origin)
                .tilt(45f)
                .zoom(16f)
                .build()
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPos))

            //start animation
            addMarkerWithPulseAnimation()
        }

    }

    private fun addMarkerWithPulseAnimation() {
        confirm_pickup_layout.visibility=View.GONE
        fill_maps.visibility=View.VISIBLE
        finding_your_ride_layout.visibility=View.VISIBLE

        originMarker=mMap.addMarker(MarkerOptions().icon(BitmapDescriptorFactory.defaultMarker())
            .position(selectedPlaceEvent!!.origin))

        addPulsatingEffect(selectedPlaceEvent!!)
    }

    private fun addPulsatingEffect(selectedPlaceEvent: SelectedPlaceEvent) {
        if (lastPulseAnimator!=null) lastPulseAnimator!!.cancel()
        if (lastUserCircle!=null) lastUserCircle!!.center=selectedPlaceEvent.origin
        lastPulseAnimator=Comon.valueAnimate(duration,object :ValueAnimator.AnimatorUpdateListener{
            override fun onAnimationUpdate(p0: ValueAnimator?) {
                if (lastUserCircle!=null) lastUserCircle!!.radius=p0!!.animatedValue.toString().toDouble() else{
                    lastUserCircle=mMap.addCircle(CircleOptions()
                        .center(selectedPlaceEvent.origin)
                        .radius(p0!!.animatedValue.toString().toDouble())
                        .strokeColor(Color.WHITE)
                        .fillColor(ContextCompat.getColor(this@RequestDriverActivity,R.color.map_darker)))
                }
            }

        })

            //start rotating camera
        startMapCameraSpinningAnimation(selectedPlaceEvent)
    }

    private fun startMapCameraSpinningAnimation(selectedPlaceEvent: SelectedPlaceEvent?) {
        if (animator !=null) animator!!.cancel()
        animator=ValueAnimator.ofFloat(0f,(DESIRED_NUM_OF_SPINS*360).toFloat())
        animator!!.duration=  (DESIRED_NUM_OF_SPINS*DESIRED_SECONDS_PER_ONE_FULL_360_SPIN*1000).toLong()
        animator!!.interpolator=LinearInterpolator()
        animator!!.startDelay=(100)
        animator!!.addUpdateListener { valueAnimator->
            val newBearingValue = valueAnimator.animatedValue as Float
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder()
                .target(selectedPlaceEvent!!.origin)
                .zoom(16f)
                .tilt(45f)
                .bearing(newBearingValue)
                .build()
            ))
        }
        animator!!.start()

        findNearbyDriver(selectedPlaceEvent)
    }

    private fun findNearbyDriver(selectedPlaceEvent: SelectedPlaceEvent?) {
        if (Comon.driversFound.size>0)
        {
            var min=0f
            var foundDriver:DriverGeoModel?=null
            val currentRiderLocation= Location("")
            currentRiderLocation.latitude=selectedPlaceEvent!!.origin!!.latitude
            currentRiderLocation.longitude=selectedPlaceEvent!!.origin!!.longitude

            for (key in Comon.driversFound.keys)
            {
                val driverLocation= Location("")
                driverLocation.latitude=Comon.driversFound[key]!!.geoLocation!!.latitude
                driverLocation.longitude=Comon.driversFound[key]!!.geoLocation!!.longitude

                //first, init min value and found driver if first driver in list
                if (min==0f)
                {
                    min=driverLocation.distanceTo(currentRiderLocation)
                    if (!Comon.driversFound[key]!!.isDecline)
                    {
                        foundDriver=Comon.driversFound[key]
                        break; //exit loop,coz already found driver
                    }
                    else
                        continue; //if already decline before,just skip and continue

                }
                else if (driverLocation.distanceTo(currentRiderLocation) < min)
                {
                    min=driverLocation.distanceTo(currentRiderLocation)
                    if (!Comon.driversFound[key]!!.isDecline)
                    {
                        foundDriver=Comon.driversFound[key]
                        break; //exit loop,coz already found driver
                    }
                    else
                        continue; //if already decline before,just skip and continue

                }
            }

            if (foundDriver!=null)
            {
                UserUtils.sendRequestToDriver(this@RequestDriverActivity,
                    main_layout,
                    foundDriver,
                    selectedPlaceEvent!!)
                lastDriverCall=foundDriver;
            }
            else
            {
                Toast.makeText(this,getString(R.string.no_driver_accept),Toast.LENGTH_SHORT).show()
                lastDriverCall=null
                finish()
            }

        }
        else
        {
            Snackbar.make(main_layout,getString(R.string.drivers_not_found),Snackbar.LENGTH_LONG).show()
            lastDriverCall=null
            finish()
        }

    }

    override fun onDestroy() {
        if (animator!=null) animator!!.end()
        super.onDestroy()
    }

    private fun setDataPickup() {
        txt_address_pickup.text=if(txt_origin != null) txt_origin.text else "None"
        mMap.clear()
        addPickupMarker()
    }

    private fun addPickupMarker() {
        val view=layoutInflater.inflate(R.layout.pickup_info_windows,null)

        val generator=IconGenerator(this)
        generator.setContentView(view)
        generator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon =generator.makeIcon()

        originMarker=mMap.addMarker(MarkerOptions()
            .icon(BitmapDescriptorFactory.fromBitmap(icon))
            .position(selectedPlaceEvent!!.origin))
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

        drawPath(selectedPlaceEvent!!)

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

        val view=layoutInflater.inflate(R.layout.origin_info_windows,null)

        val txt_time =view.findViewById<View>(R.id.txt_time) as TextView
        txt_origin =view.findViewById<View>(R.id.txt_origin) as TextView

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