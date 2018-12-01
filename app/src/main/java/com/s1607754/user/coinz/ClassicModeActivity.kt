package com.s1607754.user.coinz

import android.content.Context
import android.location.Location
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import android.widget.Toast
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.geojson.FeatureCollection
import com.mapbox.mapboxsdk.annotations.Icon
import com.mapbox.mapboxsdk.annotations.IconFactory
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.maps.MapView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ClassicModeActivity : AppCompatActivity(),OnMapReadyCallback,PermissionsListener {
    //map elements
    var mapView: MapView?=null
    private var map: MapboxMap? = null
    private lateinit var originLocation: Location
    //permission manager to manage location permission requests
    private lateinit var permissionsManager: PermissionsManager
    //tag for current activity for logs
    private val tag="ClassicModeActivity"

    //elements needed to download map from inf.ed.ac.uk
    val arg_for_download = DownloadCompleteRunner
    val link = DownloadFileTask(arg_for_download)
    var db: FirebaseFirestore = FirebaseFirestore.getInstance()

    //elements needed for settings and preferences
    private var downloadDate = "" // Format: YYYY/MM/DD
    private val preferencesFile = "MyPrefsFile" // for storing preferences

    //elements needed for markers
    private lateinit var markeropts : ArrayList<MarkerOptions>
    private lateinit var markers: ArrayList<Marker>
    private lateinit var user: FirebaseUser
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(applicationContext, getString(R.string.access_token) )
        setContentView(R.layout.activity_classic_mode)
        mapView = findViewById(R.id.mapboxMapView)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this) //asynchronous tack of getMap callback
        val todate = getCurrentDateTime() //getting current today's date and time
        downloadDate = todate.toString("yyyy/MM/dd") //reformat today's date for URL download
        link.execute("http://homepages.inf.ed.ac.uk/stg/coinz/$downloadDate/coinzmap.geojson")

    }

    fun getCurrentDateTime(): Date {
        return Calendar.getInstance().time
    }

    //parse marker options from json file
    fun loadMarkers():ArrayList<MarkerOptions> {
        val markopts = ArrayList<MarkerOptions>()
        var json = File("/data/data/com.s1607754.user.coinz/files/coinzmap.geojson").readText(Charsets.UTF_8)
        //defining fc, f, g, p for the properties of each marker(feature) in the feature collection as explained in the slides
        var fc = FeatureCollection.fromJson(json).features()
        fc?.forEach {
            var g = it.geometry()!!.toJson()
            var p = Point.fromJson(g)
            var long = p.longitude()
            var lat = p.latitude()
            var x = LatLng(lat, long)
            var props = it.properties()!!
            var symbol = props.get("marker-symbol").asString
            var currency = props.get("currency").asString
            var color = props.get("marker-color").asString
            var id = props.get("id").asString
            var value = props.get("value").asString
            var mark = MarkerOptions().title(id).snippet(currency + ": $value").position(x).icon(matchIcon(currency))
            markopts.add(mark)
        }
        return markopts
    }
    fun matchIcon(currency:String):Icon{
        var id = when (currency) {
            //matching icons with colors specified in json file (inspected colors online)
            "DOLR" -> R.drawable.green_coin
            "SHIL" -> R.drawable.purple_coin
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
            enableLocationComponent()
            map?.uiSettings?.isCompassEnabled=true
            markeropts=loadMarkers()
            mapView?.getMapAsync {_ ->
                markers = map?.addMarkers(markeropts) as ArrayList
            }
        }
    }


    @SuppressWarnings( "MissingPermission")
    private fun enableLocationComponent() {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            // Activate the MapboxMap LocationComponent to show user location
            // Adding in LocationComponentOptions is also an optional parameter
            val locationComponent = map?.locationComponent
            locationComponent?.activateLocationComponent(this)
            locationComponent?.setLocationComponentEnabled(true)
            // Set the component's camera mode
            locationComponent?.setCameraMode(CameraMode.TRACKING)

        } else {
            permissionsManager = PermissionsManager(this)
            permissionsManager.requestLocationPermissions(this)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onExplanationNeeded(permissionsToExplain: List<String>) {
        Toast.makeText(this, R.string.user_location_permission_explanation, Toast.LENGTH_LONG).show()
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            enableLocationComponent()
        } else {
            Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show()
            finish()
        }
    }
    private fun setCameraPosition(location: Location) {
        val latlng = LatLng(location.latitude, location.longitude)
        map?.animateCamera(CameraUpdateFactory.newLatLng(latlng))
    }

//lifecycle methods below
    override fun onStart() {
        super.onStart()
        mapView?.onStart()
        // Restore preferences
        val settings = getSharedPreferences(preferencesFile, Context.MODE_PRIVATE)
        // use ”” as the default value (this might be the first time the app is run)
        downloadDate = settings.getString("lastDownloadDate", "")
        // Write a message to ”logcat” (for debugging purposes)
        Log.d(tag, "[onStart] Recalled lastDownloadDate is ’$downloadDate’")

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
        Log.d(tag, "[onStop] Storing lastDownloadDate of $downloadDate")
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
