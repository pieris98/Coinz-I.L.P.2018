package com.s1607754.user.coinz

import android.arch.lifecycle.Lifecycle
import android.content.Context
import android.content.Intent
import android.location.Location
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
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

class ClassicModeActivity : AppCompatActivity(), OnMapReadyCallback, LocationEngineListener, PermissionsListener {

    //map elements
    private var mapView: MapView? = null
    private var map: MapboxMap? = null
    private lateinit var originLocation: Location
    private var locationEngine: LocationEngine? = null
    private var locationLayerPlugin: LocationLayerPlugin? = null

    //permission manager to manage location permission requests
    private lateinit var permissionsManager: PermissionsManager

    //tag for current activity for logs
    private val tag = "ClassicModeActivity"

    //elements needed to download map from inf.ed.ac.uk
    private val argfordownload = DownloadCompleteRunner
    private val link = DownloadFileTask(argfordownload)
    private var db: FirebaseFirestore = FirebaseFirestore.getInstance()

    //elements needed for settings and preferences
    private var lastDownloadDate = "" // Format: YYYY/MM/DD
    private lateinit var downloadDate: String
    private val preferencesFile = "MyPrefsFile" // for storing preferences

    //elements needed for markers
    private var markeropts: ArrayList<MarkerOptions>? = ArrayList()
    private lateinit var markers: ArrayList<Marker>
    private var markeroptsbonus: ArrayList<MarkerOptions>? = ArrayList()
    private var user: FirebaseUser? = FirebaseAuth.getInstance().currentUser

    //collected coins
    private var allCoinz: HashMap<String, HashMap<String, String>>? = HashMap()
    private var collectedCoinz: HashMap<String, HashMap<String, String>>? = HashMap()
    private var todayRates: HashMap<String, Double>? = HashMap()
    private var spares: HashMap<String, HashMap<String, String>>? = HashMap()
    private var sparesToSend: HashMap<String, HashMap<String, String>>? = HashMap()

    //flag to check if user tossed spare change today for 75% Fine to be applied to him on any received spare coins
    private var tossed=false

    override fun onCreate(savedInstanceState: Bundle?) {
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
        if (!downloadDate.equals(lastDownloadDate)) {
            link.execute("http://homepages.inf.ed.ac.uk/stg/coinz/$downloadDate/coinzmap.geojson")
            db.collection("users").document(user!!.uid).get().addOnSuccessListener { snapshot ->
                var bank:Double= snapshot.get("bank") as Double
                //retrieving last day's rates FireStore
                todayRates = snapshot.get("rates") as HashMap<String, Double>?
                //retrieving last day's collected coins from FireStore
                collectedCoinz = snapshot.get("classicModeCollectedCoinz") as HashMap<String, HashMap<String, String>>?
                //retrieve tossed spare coins flag from FireStore
                tossed= snapshot.getBoolean("tossed")!!
                //retrieving last day's received spare coins from FireStore
                var receivedSpares = snapshot.get("receivedSpares") as HashMap<String, HashMap<String, String>>?
                //for each collected coin from last day(if any), calculate its GOLD value and add it to the bank of the user
                collectedCoinz?.forEach {
                    val value = it.value["value"]
                    val currency = it.value["currency"]
                    bank += (value?.toDouble()!!.times(todayRates?.get(currency)!!.toDouble()))
                }
                //for all received spare coins from last day(if any)
                receivedSpares?.forEach {
                    val value = it.value["value"]
                    val currency = it.value["currency"]

                    //BONUS FEATURE
                    if(tossed){//if the user tossed spare coins on the last day, apply a 75% fine on the calculation of
                        bank += 0.25*(value?.toDouble()!!.times(todayRates?.get(currency)!!.toDouble()))
                    }
                    else{//else do not apply the 75% fine to the added GOLD value
                    bank += (value?.toDouble()!!.times(todayRates?.get(currency)!!.toDouble()))
                    }
                }
                collectedCoinz?.clear()
                todayRates?.clear()
                snapshot.reference.update("classicModeCollectedCoinz", collectedCoinz)
                snapshot.reference.update("rates", todayRates)
                snapshot.reference.update("spareChange", collectedCoinz)
                snapshot.reference.update("spareChangeToSend", collectedCoinz)
                snapshot.reference.update("receivedSpares", collectedCoinz)
                snapshot.reference.update("bank", bank)
                snapshot.reference.update("tossed",false)
            }
        }
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(applicationContext, getString(R.string.access_token))
        setContentView(R.layout.activity_classic_mode)
        my_toolbar.title = ""
        setSupportActionBar(my_toolbar)
        mapView = findViewById(R.id.mapboxMapView)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this) //asynchronous task of getMap callback
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
    private fun loadMarkers() {
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
            val id = props.get("id").asString
            val value = props.get("value").asString
            val marker = MarkerOptions().title("$symbol $currency").snippet(id).position(x).icon(matchIcon(currency))
            markeropts?.add(marker)
            val newCoin = HashMap<String, String>()
            newCoin["id"] = id
            newCoin["currency"] = currency
            newCoin["value"] = value
            allCoinz?.put(key = id, value = newCoin)
        }
        //parsing the rates of currencies for today and storing them locally
        val ratesJson = JSONObject(json).getJSONObject("rates")
        val shil = ratesJson.getString("SHIL").toDouble()
        val dolr = ratesJson.getString("DOLR").toDouble()
        val quid = ratesJson.getString("QUID").toDouble()
        val peny = ratesJson.getString("PENY").toDouble()
        todayRates?.put("SHIL", shil)
        todayRates?.put("DOLR", dolr)
        todayRates?.put("QUID", quid)
        todayRates?.put("PENY", peny)
        shil_rate.text = shil.toString()
        dolr_rate.text = dolr.toString()
        quid_rate.text = quid.toString()
        peny_rate.text = peny.toString()
    }


    private fun matchIcon(currency: String): Icon {
        val id = when (currency) {
            //matching icons with colors specified in json file (inspected colors and recolored icons with the corresponding color codes for each currency)
            "DOLR" -> R.drawable.green_coin
            "SHIL" -> R.drawable.blue_coin
            "PENY" -> R.drawable.red_coin
            "QUID" -> R.drawable.yellow_coin
            //capture invalid case by using question mark icon
            else -> R.drawable.alien_coin
        }
        return IconFactory.getInstance(this).fromResource(id)
    }


    override fun onMapReady(mapboxMap: MapboxMap?) {

        if (mapboxMap == null) {
            Log.d(tag, "[onMapReady] mapboxMap is null")
        } else {

            map = mapboxMap
            map?.uiSettings?.isCompassEnabled = true
            enableLocation()
            loadMarkers()
            @Suppress("UNCHECKED_CAST")
            db.collection("users").document(user!!.uid).get().addOnSuccessListener { snapshot ->

                //updating FireStore with today's currency rates
                snapshot.reference.update("rates", todayRates)
                Log.d(tag, "[loadMarkers] Successfully updated Firestore with today's rates")

                //updating collected coins map from FireStore
                collectedCoinz = snapshot.get("classicModeCollectedCoinz") as HashMap<String, HashMap<String, String>>?
                spares = snapshot.get("spareChange") as HashMap<String, HashMap<String, String>>?
                Log.d(tag, "[loadMarkers] Successfully fetched from Firestore previously collected coins in this day's session")
                collectedCoinz?.forEach {
                    val id = it.key
                    val ids = markeropts?.map { marker -> marker.snippet } as ArrayList<String>
                    if (ids.contains(id)) {
                        markeropts?.removeAt(ids.indexOf(id))
                        Log.d(tag, "[onCreate] Removed marker with id: $id from Map")
                    }
                }
                spares?.forEach {
                    val id = it.key
                    val ids = markeropts?.map { marker -> marker.snippet } as ArrayList<String>
                    if (ids.contains(id)) {
                        markeropts?.removeAt(ids.indexOf(id))
                        Log.d(tag, "[onCreate] Removed marker with id: $id from Map")
                    }
                }
                markeroptsbonus?.addAll(markeropts!!)
                mapView?.getMapAsync { _ ->
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
                setLocationLayerEnabled(true)
                cameraMode = CameraMode.TRACKING
                renderMode = RenderMode.NORMAL
                val lifecycle: Lifecycle = lifecycle
                lifecycle.addObserver(this)
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
            for (marker in markeroptsbonus!!) {
                val mPosition = marker.position
                if (latLng.distanceTo(mPosition) <= 25) {

                    val id = marker.snippet
                    val nowCollected = allCoinz?.get(id)
                    @Suppress("UNCHECKED_CAST")
                    db.collection("users").document(user!!.uid).get().addOnSuccessListener { snapshot ->
                        collectedCoinz = snapshot.get("classicModeCollectedCoinz") as HashMap<String, HashMap<String, String>>?
                        spares = snapshot.get("spareChange") as HashMap<String, HashMap<String, String>>?
                        sparesToSend = snapshot.get("spareChangeToSend") as HashMap<String, HashMap<String, String>>?
                        Toast.makeText(this, "You collected a coin worth ${marker.title}", Toast.LENGTH_LONG).show()
                        markeropts?.remove(marker)
                        markeroptsbonus?.remove(marker)
                        collectedCoinz?.put(id, nowCollected!!)
                        if (collectedCoinz!!.size <= 25) {
                            collectedCoinz?.put(id, nowCollected!!)
                            snapshot.reference.update("classicModeCollectedCoinz", collectedCoinz).addOnSuccessListener {
                                Log.d(tag, "[onLocationChanged] Added Collected Coin with id: ${marker.snippet} to Firestore successfully")
                            }.addOnFailureListener { _ ->
                                Log.d(tag, "[onLocationChanged] Adding Collected Coin to Firestore FAILED")
                            }
                        } else {
                            collectedCoinz!!.remove(id)
                            spares?.put(id, nowCollected!!)
                            sparesToSend?.put(id, nowCollected!!)
                            snapshot.reference.update("spareChange", spares).addOnSuccessListener {
                                Log.d(tag, "[onLocationChanged] Added Spare Coin to Firestore successfully")
                            }.addOnFailureListener {
                                Log.d(tag, "[onLocationChanged] Adding Spare Coin to Firestore FAILED")
                            }
                            snapshot.reference.update("spareChangeToSend", sparesToSend).addOnSuccessListener {
                                Log.d(tag, "[onLocationChanged] Added Sendable Spare Coin to Firestore successfully")
                            }.addOnFailureListener {
                                Log.d(tag, "[onLocationChanged] Adding Sendable Spare Coin to Firestore FAILED")
                            }
                        }
                        mapView?.getMapAsync { _ ->
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
            val intent = Intent(this, Bank::class.java)
            startActivity(intent)
            true
        }

        R.id.action_spare_change -> {
            val intent = Intent(this, SpareActivity::class.java)
            startActivity(intent)
            true
        }
        R.id.view_hide -> {
            val menuView = findViewById<View>(R.id.view_hide)
            showPopup(menuView)
            true
        }
        else -> {
            // If we got here, the user's action was not recognized.
            // Invoke the superclass to handle it.
            super.onOptionsItemSelected(item)
        }
    }

    private fun showPopup(v: View) {
        val popupMenu = PopupMenu(this, v)
        popupMenu.menuInflater.inflate(R.menu.popup_menu, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener {
            if ((it.itemId) == (R.id.view_all_coins)) {
                markeroptsbonus?.clear()
                markeroptsbonus?.addAll(markeropts!!)
                mapView?.getMapAsync { _ ->
                    map?.clear()
                    map?.addMarkers(markeroptsbonus!!) as ArrayList
                }
            }
            if ((it.itemId) == (R.id.view_dolr)) {
                markeroptsbonus?.clear()
                markeropts?.forEach {
                    if (it.title.substringAfter(" ").equals("DOLR")) {
                        markeroptsbonus?.add(it)
                    }
                }
                mapView?.getMapAsync { _ ->
                    map?.clear()
                    map?.addMarkers(markeroptsbonus!!) as ArrayList
                }
            }
            if ((it.itemId) == (R.id.view_shil)) {
                markeroptsbonus?.clear()
                markeropts?.forEach {
                    if (it.title.substringAfter(" ").equals("SHIL")) {
                        markeroptsbonus?.add(it)
                    }
                }
                mapView?.getMapAsync { _ ->
                    map?.clear()
                    map?.addMarkers(markeroptsbonus!!) as ArrayList
                }
            }
            if ((it.itemId) == (R.id.view_quid)) {
                markeroptsbonus?.clear()
                markeropts?.forEach {
                    if (it.title.substringAfter(" ").equals("QUID")) {
                        markeroptsbonus?.add(it)
                    }
                }
                mapView?.getMapAsync { _ ->
                    map?.clear()
                    map?.addMarkers(markeroptsbonus!!) as ArrayList
                }
            }
            if ((it.itemId) == (R.id.view_peny)) {
                markeroptsbonus?.clear()
                markeropts?.forEach {
                    if (it.title.substringAfter(" ").equals("PENY")) {
                        markeroptsbonus?.add(it)
                    }
                }
                mapView?.getMapAsync { _ ->
                    map?.clear()
                    map?.addMarkers(markeroptsbonus!!) as ArrayList
                }
            }

            true
        }
        popupMenu.show()
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
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
        if (locationEngine != null) {
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
