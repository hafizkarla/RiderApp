package com.kinikumuda.riderapp

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.inflate
import android.view.animation.LinearInterpolator
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import android.content.Context

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.maps.android.ui.IconGenerator
import com.kinikumuda.riderapp.Comon.Comon
import com.kinikumuda.riderapp.Model.DriverGeoModel
import com.kinikumuda.riderapp.Model.EventBus.DeclineRequestFromDriver
import com.kinikumuda.riderapp.Model.EventBus.DriverAcceptTripEvent
import com.kinikumuda.riderapp.Model.EventBus.SelectedPlaceEvent
import com.kinikumuda.riderapp.Model.EventBus.TripFinished
import com.kinikumuda.riderapp.Model.TripPlanModel
import com.kinikumuda.riderapp.Remote.IGoogleAPI
import com.kinikumuda.riderapp.Remote.RetrofitClient
import com.kinikumuda.riderapp.Utils.UserUtils
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_request_driver.*
import kotlinx.android.synthetic.main.layout_confirm_pickup.*
import kotlinx.android.synthetic.main.layout_confirm_uber.*
import kotlinx.android.synthetic.main.layout_driver_info.*
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

    private var driverOldPosition: String = ""
    private var handler: Handler?=null
    private var v=0f
    private var lat=0.0
    private var lng=0.0
    private var index=0
    private var next=0
    private var start:LatLng?=null
    private var end:LatLng?=null


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

    private var phoneNumber:String?=""




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
        if (EventBus.getDefault().hasSubscriberForEvent(DriverAcceptTripEvent::class.java))
            EventBus.getDefault().removeStickyEvent(DriverAcceptTripEvent::class.java)
        if (EventBus.getDefault().hasSubscriberForEvent(TripFinished::class.java))
            EventBus.getDefault().removeStickyEvent(TripFinished::class.java)
        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    @Subscribe(sticky = true,threadMode = ThreadMode.MAIN)
    fun onDriverAcceptTripEvent(event:DriverAcceptTripEvent)
    {
        FirebaseDatabase.getInstance().getReference(Comon.TRIP)
            .child(event.tripId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(p0: DataSnapshot) {
                    if (p0.exists())

                    {
                        val tripPlanModel=p0.getValue(TripPlanModel::class.java)
                        mMap.clear()
                        fill_maps.visibility=View.GONE
                        if (animator != null) animator!!.end()
                        val cameraPos=CameraPosition.Builder().target(mMap.cameraPosition.target)
                            .tilt(0f).zoom(mMap.cameraPosition.zoom).build()
                        mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPos))

                        //get routes
                        val driverLocation=StringBuilder()
                            .append(tripPlanModel!!.currentLat)
                            .append(",")
                            .append(tripPlanModel!!.currentLng)
                            .toString()

                        compositeDisposable.add(
                            iGoogleAPI.getDirection("driving",
                            "less_driving",
                            tripPlanModel!!.destination,driverLocation,
                            getString(R.string.google_api_key))!!
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe{returnResult ->
                                    var blackPolylineOptions:PolylineOptions?=null
                                    var polylineList:List<LatLng?>?=null
                                    var blackPolyline:Polyline?=null
                                    try {

                                        val jsonObject = JSONObject(returnResult)
                                        val jsonArray = jsonObject.getJSONArray("routes");
                                        for (i in 0 until jsonArray.length()) {
                                            val route = jsonArray.getJSONObject(i)
                                            val poly = route.getJSONObject("overview_polyline")
                                            val polyline = poly.getString("points")
                                            polylineList = Comon.decodePoly(polyline)

                                        }


                                        blackPolylineOptions = PolylineOptions()
                                        blackPolylineOptions!!.color(Color.BLACK)
                                        blackPolylineOptions!!.width(5f)
                                        blackPolylineOptions!!.startCap(SquareCap())
                                        blackPolylineOptions!!.jointType(JointType.ROUND)
                                        blackPolylineOptions!!.addAll(polylineList)
                                        blackPolyline = mMap.addPolyline(blackPolylineOptions)



                                        //add car icon for origin
                                        val objects = jsonArray.getJSONObject(0)
                                        val legs = objects.getJSONArray("legs")
                                        val legsObject = legs.getJSONObject(0)

                                        val time = legsObject.getJSONObject("duration")
                                        val duration = time.getString("text")

                                       val origin=LatLng(tripPlanModel!!.origin!!.split(",").get(0).toDouble(),
                                           tripPlanModel!!.origin!!.split(",").get(1).toDouble())
                                        val destination=LatLng(tripPlanModel.currentLat,tripPlanModel.currentLng)

                                        btn_call_driver.setOnClickListener {
                                            checkPermission()
                                        }

                                        val latLngBound = LatLngBounds.Builder()
                                            .include(origin)
                                            .include(destination)
                                            .build()

                                        addPickupMarkerWithDuration(duration,origin)
                                        addDriverMarker(destination)




                                        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(latLngBound, 160))
                                        mMap.moveCamera(CameraUpdateFactory.zoomTo(mMap.cameraPosition!!.zoom - 1))

                                        initDriverForMoving(event.tripId,tripPlanModel)
                                        //load driver avatar
                                        Glide.with(this@RequestDriverActivity)
                                            .load(tripPlanModel.driverInfoModel!!.avatar!!)
                                            .into(img_driver)
                                        txt_driver_name.setText(tripPlanModel!!.driverInfoModel!!.firstName+" "+tripPlanModel!!.driverInfoModel!!.lastName)
                                        txt_car_type.setText(tripPlanModel!!.driverInfoModel!!.motorType)
                                        txt_phone_number.setText(tripPlanModel!!.driverInfoModel!!.phoneNumber)
                                        txt_vehicle_number.setText(tripPlanModel!!.driverInfoModel!!.vehicleLicenseNumber)
                                        phoneNumber=tripPlanModel.driverInfoModel!!.phoneNumber

                                        confirm_uber_layout.visibility=View.GONE
                                        confirm_pickup_layout.visibility=View.GONE
                                        driver_info_layout.visibility=View.VISIBLE



                                    } catch (e: java.lang.Exception) {
                                        Toast.makeText(this@RequestDriverActivity, e.message!!, Toast.LENGTH_SHORT).show()
                                    }
                                }
                        )





                    }
                    else
                        Snackbar.make(main_layout,getString(R.string.trip_not_found)+event.tripId,Snackbar.LENGTH_LONG).show()
                }

                override fun onCancelled(p0: DatabaseError) {
                    Snackbar.make(main_layout,p0.message,Snackbar.LENGTH_LONG).show()

                }

            })
    }

    private fun tripFinish() {

        val tripPlanModel = TripPlanModel()
        if (!tripPlanModel!!.isDone) {
            driver_info_layout.visibility = View.GONE
            mMap.clear()
            Toast.makeText(
                this@RequestDriverActivity,
                "Your package has been arived!",
                Toast.LENGTH_LONG
            ).show()
        }
        else
            Toast.makeText(
                this@RequestDriverActivity,
                "belum sampai",
                Toast.LENGTH_LONG
            ).show()
    }

    fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this@RequestDriverActivity,
                Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {

            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this@RequestDriverActivity,
                    Manifest.permission.CALL_PHONE)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(
                    this@RequestDriverActivity,
                    arrayOf(Manifest.permission.CALL_PHONE),
                    42)
            }
        } else {
            // Permission has already been granted
            callPhone()
        }
    }
    fun callPhone(){
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber"))
        startActivity(intent)
    }


    private fun initDriverForMoving(tripId: String, tripPlanModel: TripPlanModel) {
        driverOldPosition= StringBuilder().append(tripPlanModel.currentLat)
            .append(",").append(tripPlanModel.currentLng).toString()

        FirebaseDatabase.getInstance().getReference(Comon.TRIP)
            .child(tripId)
            .addValueEventListener(object :ValueEventListener{
                override fun onDataChange(p0: DataSnapshot) {
                    val newData = p0.getValue(TripPlanModel::class.java)
                    val driverNewPosition=StringBuilder().append(newData!!.currentLat)
                        .append(",").append(newData!!.currentLng).toString()
                    if (!driverOldPosition.equals(driverNewPosition)) //not equals
                        moveMarkerAnimation(destinationMarker!!,driverOldPosition,driverNewPosition)
                }

                override fun onCancelled(p0: DatabaseError) {
                    Snackbar.make(main_layout,p0.message,Snackbar.LENGTH_LONG).show()
                }

            })

    }

    private fun moveMarkerAnimation(marker: Marker, from: String, to: String) {

        compositeDisposable.add(
            iGoogleAPI.getDirection("driving",
                "less_driving",
                from,to,
                getString(R.string.google_api_key))!!
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe{returnResult ->
                    try {

                        val jsonObject = JSONObject(returnResult)
                        val jsonArray = jsonObject.getJSONArray("routes");
                        for (i in 0 until jsonArray.length()) {
                            val route = jsonArray.getJSONObject(i)
                            val poly = route.getJSONObject("overview_polyline")
                            val polyline = poly.getString("points")
                            polylineList = Comon.decodePoly(polyline)

                        }


                        blackPolylineOptions = PolylineOptions()
                        blackPolylineOptions!!.color(Color.BLACK)
                        blackPolylineOptions!!.width(5f)
                        blackPolylineOptions!!.startCap(SquareCap())
                        blackPolylineOptions!!.jointType(JointType.ROUND)
                        blackPolylineOptions!!.addAll(polylineList)
                        blackPolyLine = mMap.addPolyline(blackPolylineOptions)



                        //add car icon for origin
                        val objects = jsonArray.getJSONObject(0)
                        val legs = objects.getJSONArray("legs")
                        val legsObject = legs.getJSONObject(0)

                        val time = legsObject.getJSONObject("duration")
                        val duration = time.getString("text")

                        val bitmap=Comon.createIconWithDuration(this@RequestDriverActivity,duration)
                        originMarker!!.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap))

                        //moving
                        val runnable=object:Runnable{
                            override fun run() {
                                if (index<polylineList!!.size -2)
                                {
                                    index++
                                    next=index+1
                                    start=polylineList!![index]
                                    end=polylineList!![next]
                                }
                                val valueAnimator=ValueAnimator.ofInt(0,1)
                                valueAnimator.duration=1500
                                valueAnimator.interpolator=LinearInterpolator()
                                valueAnimator.addUpdateListener { valueAnimatorNew->
                                    v=valueAnimatorNew.animatedFraction
                                    lat=v*end!!.latitude+(1-v)*start!!.latitude
                                    lng=v*end!!.longitude+(1-v)*end!!.longitude
                                    val newPos=LatLng(lat,lng)
                                    marker.position=newPos
                                    marker.setAnchor(0.5f,0.5f)
                                    marker.rotation=Comon.getBearing(start!!,newPos)
                                    mMap.moveCamera(CameraUpdateFactory.newLatLng(newPos))

                                }
                                valueAnimator.start()
                                if (index<polylineList!!.size -2) handler!!.postDelayed(this,1500)
                            }

                        }
                        handler=Handler()
                        index=-1
                        next=1
                        handler!!.postDelayed(runnable,1500)
                        driverOldPosition=to //set new driver position


                    } catch (e: java.lang.Exception) {
                        Toast.makeText(this@RequestDriverActivity, e.message!!, Toast.LENGTH_SHORT).show()
                    }
                }
        )
    }

    private fun addDriverMarker(destination: LatLng) {
        destinationMarker=mMap.addMarker(MarkerOptions().position(destination).flat(true)
            .icon(BitmapDescriptorFactory.fromResource(R.drawable.car)))

    }

    private fun addPickupMarkerWithDuration(duration: String, origin: LatLng) {
        val icon=Comon.createIconWithDuration(this@RequestDriverActivity,duration)!!
        originMarker=mMap.addMarker(MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(icon)).position(origin))

    }

    @Subscribe(sticky = true,threadMode = ThreadMode.MAIN)
    fun onDoneReceived(event:TripFinished)
    {
        driver_info_layout.visibility=View.GONE
        lastDriverCall=null
        mMap.clear()
        finish()
            Comon.driversFound.get(lastDriverCall!!.key)!!.isDone=true
//            Toast.makeText(this,"Done",Toast.LENGTH_LONG).show()

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