package com.s1607754.user.coinz

import android.arch.lifecycle.Lifecycle
import android.content.Context
import android.location.Location
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.android.core.location.LocationEngineListener
import com.mapbox.android.core.location.LocationEnginePriority
import com.mapbox.android.core.location.LocationEngineProvider
import android.widget.Toast
import android.widget.Toolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.geojson.FeatureCollection
import com.mapbox.mapboxsdk.annotations.Icon
import com.mapbox.mapboxsdk.annotations.IconFactory
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.CameraMode
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode
import kotlinx.android.synthetic.main.activity_classic_mode.*
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ClassicModeActivity : AppCompatActivity(),OnMapReadyCallback,LocationEngineListener,PermissionsListener {

    //map elements
    private var mapView: MapView?=null
    private var map: MapboxMap? = null
    private lateinit var originLocation: Location
    private var locationEngine : LocationEngine?=null
    private var locationLayerPlugin:LocationLayerPlugin?=null

    //permission manager to manage location permission requests
    private lateinit var permissionsManager: PermissionsManager

    //tag for current activity for logs
    private val tag="ClassicModeActivity"

    //elements needed to download map from inf.ed.ac.uk
    val arg_for_download = DownloadCompleteRunner
    private val link = DownloadFileTask(arg_for_download)
    private var db: FirebaseFirestore = FirebaseFirestore.getInstance()

    //elements needed for settings and preferences
    private var lastDownloadDate="" // Format: YYYY/MM/DD
    private lateinit var downloadDate:String
    private val preferencesFile = "MyPrefsFile" // for storing preferences

    //elements needed for markers
    private var markeropts : ArrayList<MarkerOptions>?=ArrayList()
    private lateinit var markers: ArrayList<Marker>
    private var user: FirebaseUser?=FirebaseAuth.getInstance().currentUser

    //collected coins
    private var allCoinz:HashMap<String,HashMap<String,Any>>?=HashMap()
    private var collectedCoinz:HashMap<String,HashMap<String,Any>>?=HashMap()
    private var todayRates:HashMap<String,Double>?=HashMap()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(applicationContext, getString(R.string.access_token) )
        setContentView(R.layout.activity_classic_mode)
        setSupportActionBar(my_toolbar)
        mapView = findViewById(R.id.mapboxMapView)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this) //asynchronous taSk of getMap callback
        downloadDate = getCurrentDateTime().toString("yyyy/MM/dd") //reformat current date to store last date for URL download
        link.execute("http://homepages.inf.ed.ac.uk/stg/coinz/$downloadDate/coinzmap.geojson")
    }

     override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.my_menu, menu)
        return true
    }
    private fun getCurrentDateTime(): Date {
        return Calendar.getInstance().time
    }

    //parse marker options from json file
    fun loadMarkers(){
        val json = File("/data/data/com.s1607754.user.coinz/files/coinzmap.geojson").readText(Charsets.UTF_8)
        //defining fc, f, g, p for the properties of each marker(feature) in the feature collection as explained in the slides
        val fc = FeatureCollection.fromJson(json).features()
        fc?.forEach {
            val g = it.geometry()!!.toJson()
            val p = Point.fromJson(g)
            val long = p.longitude()
            val lat = p.latitude()
            val x = LatLng(lat, long)
            val props = it.properties()!!
            val symbol = props.get("marker-symbol").asString
            val currency = props.get("currency").asString
            //var color = props.get("marker-color").asString   //Decided to not use color to color each marker icon accordingly, manually colored corresponding icons instead
            val id = props.get("id").asString
            val value = props.get("value").asString
            val marker = MarkerOptions().title("$symbol $currency").snippet(id).position(x).icon(matchIcon(currency))
            markeropts?.add(marker)
            val newCoin=HashMap<String,Any>()
            newCoin.put("id",id)
            newCoin.put("currency",currency)
            newCoin.put("value",value)
            allCoinz?.put(id,newCoin)
        }
        //parsing the rates of currencies for today and storing them locally
        val ratesJson = JSONObject(json).getJSONObject("rates")
        val shil = ratesJson.getString("SHIL").toDouble()
        val dolr = ratesJson.getString("DOLR").toDouble()
        val quid = ratesJson.getString("QUID").toDouble()
        val peny = ratesJson.getString("PENY").toDouble()
        todayRates?.put("SHIL",shil)
        todayRates?.put("DOLR",dolr)
        todayRates?.put("QUID",quid)
        todayRates?.put("PENY",peny)
    }


    fun matchIcon(currency:String):Icon{
        val id = when (currency) {
            //matching icons with colors specified in json file (inspected colors and recolored icons with the corresponding color codes for each currency)
            "DOLR" -> R.drawable.green_coin
            "SHIL" -> R.drawable.blue_coin
            "PENY" -> R.drawable.red_coin
            "QUID" -> R.drawable.yellow_coin
            //capture invalid case by using question mark icon
            else ->R.drawable.alien_coin
        }
        return IconFactory.getInstance(this).fromResource(id)
    }


    override fun onMapReady(mapboxMap: MapboxMap?) {

        if (mapboxMap == null) {
            Log.d(tag, "[onMapReady] mapboxMap is null")
        } else {

            map = mapboxMap
            map?.uiSettings?.isCompassEnabled=true
            enableLocation()
            loadMarkers()
            db.collection("users").document(user!!.uid).get().addOnSuccessListener {

                //updating FireStore with today's currency rates
                it.reference.update("Rates", todayRates)
                Log.d(tag, "[loadMarkers] Successfully updated Firestore with today's rates")

                //updating collected coins map from FireStore
                collectedCoinz = it.get("classicModeCollectedCoinz") as HashMap<String, HashMap<String, Any>>?
                Log.d(tag, "[loadMarkers] Successfully fetched from Firestore previously collected coins in this day's session")
                collectedCoinz?.forEach {
                    val id = it.key
                    val ids = markeropts?.map { marker -> marker.snippet } as ArrayList<String>
                    if (ids.contains(id)) {
                        markeropts?.removeAt(ids.indexOf(id))
                        Log.d(tag, "[onCreate] Removed marker with id: $id from Map")
                    }
                }
                mapView?.getMapAsync {_ ->
                    markers = map?.addMarkers(markeropts!!) as ArrayList
                }
            }
        }
    }



    @SuppressWarnings("MissingPermission")
    private fun initialiseLocationEngine() {
        locationEngine = LocationEngineProvider(this).obtainBestLocationEngineAvailable()
        locationEngine?.apply {
            interval = 5000 // preferably every 5 seconds
            fastestInterval = 1000 // at most every second
            priority = LocationEnginePriority.HIGH_ACCURACY
            activate()
        }
        val lastLocation = locationEngine?.lastLocation
        if (lastLocation != null) {
            originLocation = lastLocation
            setCameraPosition(lastLocation)
        } else { locationEngine?.addLocationEngineListener(this) }
    }

    private fun enableLocation() {
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            Log.d(tag, "Permissions are granted")
            initialiseLocationEngine()
            initialiseLocationLayer()
        } else {
            Log.d(tag, "Permissions are not granted")
            permissionsManager = PermissionsManager(this)
            permissionsManager.requestLocationPermissions(this)
        }
    }


    @SuppressWarnings("MissingPermission")
    private fun initialiseLocationLayer() {
        if (mapView == null) {Log.d(tag, "mapView is null") }
        else {
            if (map == null) {Log.d(tag,"map is null") }
            else {
                locationLayerPlugin = LocationLayerPlugin(mapView!!,map!!,locationEngine)
                locationLayerPlugin?.apply {
                    setLocationLayerEnabled(true)
                    cameraMode = CameraMode.TRACKING
                    renderMode = RenderMode.NORMAL
                    val lifecycle:Lifecycle =lifecycle
                    lifecycle.addObserver(this)
                }
            }
        }
    }

    override fun onLocationChanged(location: Location?) {
        if (location == null) {
            Log.d(tag, "[onLocationChanged] location is null")
        } else {
            originLocation = location
            setCameraPosition(originLocation)
            val latLng = LatLng(location.latitude, location.longitude)
            for (marker in markeropts!!) {
                val mPosition = marker.position
                if (latLng.distanceTo(mPosition) <= 25) {

                    val id=marker.snippet
                    val nowCollected=allCoinz?.get(id)

                    db.collection("users").document(user!!.uid).get().addOnSuccessListener {
                        collectedCoinz= it.get("classicModeCollectedCoinz") as HashMap<String, HashMap<String, Any>>?
                        val spares = it.get("SpareChange") as HashMap<String,HashMap<String,Any>>?
                        Toast.makeText(this, "You collected a coin worth ${marker.title}", Toast.LENGTH_LONG).show()
                        markeropts?.remove(marker)
                        collectedCoinz?.put(id,nowCollected!!)
                        if (collectedCoinz!!.size < 25) {
                            collectedCoinz?.put(id,nowCollected!!)
                            it.reference.update("classicModeCollectedCoinz", collectedCoinz).addOnSuccessListener {
                                Log.d(tag, "[onLocationChanged] Added Collected Coin with id: ${marker.snippet} to Firestore successfully")
                            }.addOnFailureListener{
                                    Log.d(tag, "[onLocationChanged] Adding Collected Coin to Firestore FAILED")
                                }
                            }
                         else {
                            collectedCoinz!!.remove(id)
                            spares?.put(id,nowCollected!!)
                            it.reference.update("SpareChange", spares).addOnSuccessListener {
                                Log.d(tag,"[onLocationChanged] Added Spare Coin to Firestore successfully")
                            }.addOnFailureListener{
                                Log.d(tag,"[onLocationChanged] Adding Spare Coin to Firestore FAILED")
                            }
                        }
                        mapView?.getMapAsync {_ ->
                            markers.forEach { m ->
                                if (m.snippet == marker.snippet) {
                                    map?.removeMarker(m)
                                }
                            }
                        }
                    }

                }
            }
        }
    }


    @SuppressWarnings("MissingPermission")
    override fun onConnected() {
        Log.d(tag, "[onConnected] requesting location updates")
        locationEngine?.requestLocationUpdates()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onExplanationNeeded(permissionsToExplain: List<String>) {
        Toast.makeText(this, R.string.user_location_permission_explanation, Toast.LENGTH_LONG).show()
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            enableLocation()
        } else {
            Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show()
            finish()
        }
    }
    private fun setCameraPosition(location: Location) {
        val latlng = LatLng(location.latitude, location.longitude)
        map?.animateCamera(CameraUpdateFactory.newLatLng(latlng))
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_bank -> {
            // User chose the "Settings" item, show the app settings UI...
            true
        }

        R.id.action_spare_change -> {
            // User chose the "Favorite" action, mark the current item
            // as a favorite...
            true
        }

        else -> {
            // If we got here, the user's action was not recognized.
            // Invoke the superclass to handle it.
            super.onOptionsItemSelected(item)
        }
    }

//lifecycle methods below
    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    if (locationEngine != null) {

        try {
            locationEngine?.requestLocationUpdates()
        } catch (ignored: SecurityException) {
        }

        locationEngine?.addLocationEngineListener(this)
    }
        // Restore preferences
        val settings = getSharedPreferences(preferencesFile, Context.MODE_PRIVATE)
        // use ”” as the default value (this might be the first time the app is run)
        lastDownloadDate = settings.getString("lastDownloadDate","")
        // Write a message to ”logcat” (for debugging purposes)
        Log.d(tag, "[onStart] Recalled lastDownloadDate is ’$lastDownloadDate’")

    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
        if(locationEngine != null){
            locationEngine?.removeLocationEngineListener(this)
            locationEngine?.removeLocationUpdates()
        }
        Log.d(tag, "[onStop] Storing latest lastDownloadDate of $downloadDate")
        // All objects are from android.context.Context
        val settings = getSharedPreferences(preferencesFile, Context.MODE_PRIVATE)
        // We need an Editor object to make preference changes.
        val editor = settings.edit()
        editor.putString("lastDownloadDate", downloadDate)
        // Apply the edits!
        editor.apply()

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }
    fun Date.toString(format: String, locale: Locale = Locale.getDefault()): String {
        val formatter = SimpleDateFormat(format, locale)
        return formatter.format(this)
    }


}
