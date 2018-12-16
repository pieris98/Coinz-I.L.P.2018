package com.s1607754.user.coinz

import android.annotation.SuppressLint
import android.arch.lifecycle.Lifecycle
import android.content.Context
import android.location.Location
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.support.v7.app.AlertDialog
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineListener
import com.mapbox.android.core.location.LocationEnginePriority
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.Icon
import com.mapbox.mapboxsdk.annotations.IconFactory
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.CameraMode
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode
import kotlinx.android.synthetic.main.activity_fever_mode.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

@SuppressLint("LogNotTimber")
class FeverModeActivity : AppCompatActivity(), OnMapReadyCallback, LocationEngineListener, PermissionsListener {
    //map elements
    private var mapView: MapView? = null
    private var map: MapboxMap? = null
    private lateinit var originLocation: Location
    private var locationEngine: LocationEngine? = null
    private var locationLayerPlugin: LocationLayerPlugin? = null
    //permission manager to manage location permission requests
    private lateinit var permissionsManager: PermissionsManager

    //elements needed for markers
    private var markeropts: ArrayList<MarkerOptions>? = ArrayList()
    private lateinit var markers: ArrayList<Marker>

    //hashmap of all and collected coins
    private var allCoinz: HashMap<String, HashMap<String, String>>? = HashMap()
    private var collectedCoinz: HashMap<String, HashMap<String, String>>? = HashMap()
    //count to count how many coins player collected
    private var countCollected:Int=0

    //elements needed for settings and preferences
    private var lastDownloadDate = "" // Format: YYYY/MM/DD
    private lateinit var downloadDate: String
    private val preferencesFile = "MyPrefsFile" // for storing preferences

    //elements for Firebase
    private var user: FirebaseUser? = FirebaseAuth.getInstance().currentUser
    private var db: FirebaseFirestore = FirebaseFirestore.getInstance()

    //private val for debugging with Activity's name
    private val tag = "FeverModeActivity"

    //timer elements
    private lateinit var timer: CountDownTimer
    private var timerLengthSeconds: Long = 0
    //length of timer in seconds
    private var secondsRemaining: Long = 180

    //flag so that the user can't collect coins after the timer finishes
    private var finished=false

    @SuppressLint("LogNotTimber")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fever_mode)
        Mapbox.getInstance(applicationContext, getString(R.string.access_token))
        mapView = findViewById(R.id.mapboxMapView)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this) //asynchronous task of getMap callback
        // Restore preferences
        val settings = getSharedPreferences(preferencesFile, Context.MODE_PRIVATE)
        // use ”” as the default value (this might be the first time the app is run)
        lastDownloadDate = settings.getString("lastDownloadDate", "")
        // Write a message to ”logcat” (for debugging purposes)
        Log.d(tag, "[onStart] Recalled lastDownloadDate is ’$lastDownloadDate’")
        downloadDate = getCurrentDateTime().toString("yyyy/MM/dd") //reformat current date to store last date for URL download
        //store nowest date back to preferences file (overwrite obsolete lastDownloadDate)
        val editor = settings.edit()
        editor.putString("lastDownloadDate", downloadDate)
        // Apply the edits!
        editor.apply()

        //build an alert dialog to start the countdown when user presses the button
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Coin Fever Mode!")
        builder.setMessage("In this mode, you have 3 minutes to collect 10 coins or more. If you succeed, the value of collected coins will be converted to GOLD and deposited in your bank account on midnight. If you don't make it to 10 coins, you win nothing. Are you ready?")
        builder.setPositiveButton("Let's Go!") { _, _ ->
            initTimer() //when user presses let's go, the timer is initiated
        }
        builder.show()
        }


    private fun initTimer(){
        startTimer()
        updateCountdownUI()
    }

    private fun startTimer(){

        timer = object : CountDownTimer(secondsRemaining * 1000, 1000) {
            override fun onFinish() = onTimerFinished()

            override fun onTick(millisUntilFinished: Long) {
                secondsRemaining = millisUntilFinished / 1000
                updateCountdownUI()
            }
        }.start()
    }

    @SuppressLint("SetTextI18n")
    private fun updateCountdownUI(){
        val minutesUntilFinished = secondsRemaining / 60
        val secondsInMinuteUntilFinished = secondsRemaining - minutesUntilFinished * 60
        val secondsStr = secondsInMinuteUntilFinished.toString()
        textView_countdown.text = "$minutesUntilFinished:${if (secondsStr.length == 2) secondsStr else "0$secondsStr"}"

    }

    private fun onTimerFinished() {
        finished=true
        secondsRemaining = timerLengthSeconds
        updateCountdownUI()
        if (countCollected >= 10) { //if the user collected the target 10 coins or more
            //add the collected coins to firebase so that tomorrow they will be converted to gold
            db.collection("users").document(user!!.uid).get().addOnSuccessListener { snapshot ->
                snapshot.reference.update("feverModeCollectedCoinz", collectedCoinz).addOnSuccessListener {
                    Log.d(tag, "[onTimerFinished] Added Collected Coins to Firestore successfully")
                }.addOnFailureListener { _ ->
                    Log.d(tag, "[onTimerFinished] Adding Collected Coins to Firestore FAILED")
                }
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Time's up!")
                builder.setMessage("Well done! You've collected $countCollected coins! The coins will be converted to gold and deposited in your bank automatically on midnight. Thank you for playing Coin Fever Mode! Come back tomorrow to play once more! \nBye Bye!")
                builder.setPositiveButton("Bye!") { _, _ ->
                    finish()
                }
                builder.show()
            }

        }
        else{ //if the user collected 9 or less coins, just output a dialog to let him know he did not gain anything
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Time's up!")
            builder.setMessage("Too bad! You only collected $countCollected coins so the coins will be tossed and you've gained nothing. Thank you for playing Coin Fever Mode! Come back tomorrow to try again!\nBye Bye!")
            builder.setPositiveButton("Bye!") { _, _ ->
                finish()
            }
            builder.show()
        }
    }




    //methods to get Current Date and format it into the desired format
    private fun getCurrentDateTime(): Date {
        return Calendar.getInstance().time
    }
    //specialised toString method for Date for formatting
    private fun Date.toString(format: String, locale: Locale = Locale.getDefault()): String {
        val formatter = SimpleDateFormat(format, locale)
        return formatter.format(this)
    }




    //method to parse marker options from json file
    @SuppressLint("SdCardPath")
    private fun loadMarkers() {
        val json = File("/data/data/com.s1607754.user.coinz/coinzmap.json").readText(Charsets.UTF_8)
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
            val id = props.get("id").asString
            val value = props.get("value").asString
            val marker = MarkerOptions().title("$symbol $currency").snippet(id).position(x).icon(matchIcon(currency))
            val newCoin = HashMap<String, String>() //create a newCoin map for the new parsed coin with its most important attributes
            newCoin["id"] = id
            newCoin["currency"] = currency
            newCoin["value"] = value
            allCoinz?.put(key = id, value = newCoin) //add the newCoin map to a map of all coins
            markeropts?.add(marker)//add the coin as a marker
        }
    }

    //method to match rate of coin with corresponding color icon for each coin
    private fun matchIcon(currency: String): Icon {
        val id = when (currency) {
        //matching icons with colors specified in json file (inspected colors and recolored icons with the corresponding color codes for each currency)
            "DOLR" -> R.drawable.green_coin
            "SHIL" -> R.drawable.blue_coin
            "PENY" -> R.drawable.red_coin
            "QUID" -> R.drawable.yellow_coin
        //capture invalid case by using question mark icon(will never be used)
            else -> R.drawable.alien_coin
        }
        return IconFactory.getInstance(this).fromResource(id)
    }


//when the map is ready
    override fun onMapReady(mapboxMap: MapboxMap?) {
        if (mapboxMap == null) {
            Log.d(tag, "[onMapReady] mapboxMap is null")
        } else {
            map = mapboxMap
            map?.uiSettings?.isCompassEnabled = true
            enableLocation() //enable location tracking
            loadMarkers()//call the method for loading markers locally, see above method

            mapView?.getMapAsync { _ ->
                markers = map?.addMarkers(markeropts!!) as ArrayList//add the loaded markers on the map
            }
        }
    }


    //////////////////////////////methods copied from the lecture slides
    private fun setCameraPosition(location: Location) {
        val latlng = LatLng(location.latitude, location.longitude)
        map?.animateCamera(CameraUpdateFactory.newLatLng(latlng))
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
        } else {
            locationEngine?.addLocationEngineListener(this)
        }
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
        if (mapView == null) {
            Log.d(tag, "mapView is null")
        } else {
            locationLayerPlugin = LocationLayerPlugin(mapView!!, map!!, locationEngine)
            locationLayerPlugin?.apply {
                isLocationLayerEnabled = true
                cameraMode = CameraMode.TRACKING
                renderMode = RenderMode.NORMAL
                val lifecycle: Lifecycle = lifecycle
                lifecycle.addObserver(this)
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
    //////////////////////////////end of methods from slides


    override fun onLocationChanged(location: Location?) {
        if (location == null) {
            Log.d(tag, "[onLocationChanged] location is null")
        } else {
            originLocation = location
            setCameraPosition(originLocation)
            val latLng = LatLng(location.latitude, location.longitude)
            if(finished){//if the timer is finished, user cannot collect coins (do nothing)
                Log.d(tag, "[onFinished] timer is finished, cannot collect coin")
            }
            else {//if the timer is not finished yet, the user can collect collect coins
                for (marker in markeropts!!) { //for each marker
                    val mPosition = marker.position
                    if (latLng.distanceTo(mPosition) <= 25) {//if user is within 25 metre radius from the marker
                        Toast.makeText(this, "You collected a coin worth ${marker.title}", Toast.LENGTH_LONG).show()
                        val id = marker.snippet
                        val nowCollected = allCoinz?.get(id)//create a local map of id, attributes of collected coin
                        collectedCoinz?.put(id, nowCollected!!)//add the collected coin to the map of collected coins
                        countCollected++ //increase the counter to output to the user the number of collected coins
                        collectedCountView.text = countCollected.toString()
                        mapView?.getMapAsync { _ ->
                            markers.forEach { m ->
                                if (m.snippet == id) {
                                    map?.removeMarker(m) //remove the collected coin marker from the map
                                }
                            }
                        }
                    }
                }
            }
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

    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
        timer.cancel()
        finish()
    }
    @SuppressLint("LogNotTimber")
    override fun onStop() {
        super.onStop()
        mapView?.onStop()
        timer.cancel()
        finish()
        if (locationEngine != null) {
            locationEngine?.removeLocationEngineListener(this)
            locationEngine?.removeLocationUpdates()
        }
        //code from lectures
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

}
