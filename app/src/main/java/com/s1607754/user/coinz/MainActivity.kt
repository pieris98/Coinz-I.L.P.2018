package com.s1607754.user.coinz

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("LogNotTimber")
@Suppress("UNCHECKED_CAST")
class MainActivity : AppCompatActivity() {
    //declare a firebase auth private object
    private var fAuth=FirebaseAuth.getInstance()

    //elements needed for settings and preferences
    private var lastDownloadDate = "" // Format: YYYY/MM/DD
    private lateinit var downloadDate: String
    private val preferencesFile = "MyPrefsFile" // for storing preferences
    private val argfordownload = DownloadCompleteRunner//for downloading json file each new day
    private val link = DownloadFileTask(argfordownload)//for downloading json file each new day (link to execute with DownloadFileTask)

    //flag to see if the user has already played Coin Fever mode today (limit of 1 play per day for Coin Fever Mode, design decision)
    private var alreadyPlayed:Boolean=false
    //also need firestore to access that stored value from the database for this user
    private var db: FirebaseFirestore = FirebaseFirestore.getInstance()
    //flag to check if user tossed spare change today for 75% Fine to be applied to him on any received spare coins
    private var tossed=false
    //vars for collected coins and rates of previous day from FireStore
    private var collectedCoinz: HashMap<String, HashMap<String, String>>? = HashMap()
    private var feverCollectedCoinz: HashMap<String, HashMap<String, String>>? = HashMap()
    private var todayRates: HashMap<String, Double>? = HashMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //get the current user id(if there is one logged in)
        val uid=fAuth.uid
        //if there's no user logged in
        if (uid==null){
            Log.d("Login","User is not logged in, jumping to SignInActivity")
            //jump to SignInActivity to sign in
            val intent=Intent(this, SignInActivity::class.java)
            startActivity(intent)
            finish()
        }
        else {

            //check if the day has changed
            val settings = getSharedPreferences(preferencesFile, Context.MODE_PRIVATE)
            lastDownloadDate = settings.getString("lastDownloadDate", "")
            // Write a message to ”logcat” (for debugging purposes)
            Log.d("MainActivity", "[onCreate] Recalled lastDownloadDate is ’$lastDownloadDate’")
            downloadDate = getCurrentDateTime().toString("yyyy/MM/dd")
            //if it is a new date and we haven't already downloaded the json file
            if (downloadDate != lastDownloadDate) {
                //download the json file using DownloadFileTask
                link.execute("http://homepages.inf.ed.ac.uk/stg/coinz/$downloadDate/coinzmap.geojson")
                //re-enable the ability to play Fever Mode by resetting the alreadyPlayed flag entry in FireStore
                db.collection("users").document(fAuth.uid!!).get().addOnSuccessListener { snapshot ->
                    snapshot.reference.update("alreadyPlayed", false)
                }
                //we need to convert all the collected coins (classic mode & fever mode),received spare change(with fine or not) to GOLD,
                // put it in the bank and reset all fields for the new day
                db.collection("users").document(fAuth.uid!!).get().addOnSuccessListener { snapshot ->

                    //get the accumulated bank account of the user from the previous days (stored in FireStore)
                    var bank:Double= snapshot.get("bank") as Double
                    //retrieving last day's rates FireStore
                    todayRates = snapshot.get("rates") as HashMap<String, Double>?
                    //retrieving last day's collected coins from FireStore
                    collectedCoinz = snapshot.get("classicModeCollectedCoinz") as HashMap<String, HashMap<String, String>>?
                    //retrieve tossed spare coins flag from FireStore
                    tossed= snapshot.getBoolean("tossed")!!
                    //retrieving last day's received spare coins from FireStore
                    val receivedSpares = snapshot.get("receivedSpares") as HashMap<String, HashMap<String, String>>?
                    //retrieving last day's collected coins from FireStore
                    feverCollectedCoinz = snapshot.get("feverModeCollectedCoinz") as HashMap<String, HashMap<String, String>>?

                    //for each collected coin in classic mode from last day(if any), calculate its GOLD value and add it to the bank of the user
                    collectedCoinz?.forEach {
                        val value = it.value["value"]
                        val currency = it.value["currency"]
                        bank += (value?.toDouble()!!.times(todayRates?.get(currency)!!.toDouble()))
                    }
                    //for each collected coin in fever mode from last day(if any), calculate its GOLD value and add it to the bank of the user
                    feverCollectedCoinz?.forEach {
                        val value = it.value["value"]
                        val currency = it.value["currency"]
                        bank += (value?.toDouble()!!.times(todayRates?.get(currency)!!.toDouble()))
                    }
                    //for all received spare coins from last day(if any)
                    receivedSpares?.forEach {
                        val value = it.value["value"]
                        val currency = it.value["currency"]

                        //BONUS FEATURE: Apply fine of 75% on received spare change if the player tossed any spare change the previous day
                        bank += if(tossed){//if the user tossed spare coins on the last day, apply a 75% fine on the calculation of value to be added to bank
                            0.25*(value?.toDouble()!!.times(todayRates?.get(currency)!!.toDouble()))
                        } else{//else do not apply the 75% fine to the added GOLD value
                            (value?.toDouble()!!.times(todayRates?.get(currency)!!.toDouble()))
                        }
                    }
                    //clear the local variables
                    collectedCoinz?.clear()
                    todayRates?.clear()
                    @SuppressLint("SdCardPath")
                    val json = File("/data/data/com.s1607754.user.coinz/coinzmap.json").readText(Charsets.UTF_8)
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
                    //updating FireStore with today's currency rates
                    snapshot.reference.update("rates", todayRates)

                    //reset all the fields in FireStore for the new day
                    snapshot.reference.update("classicModeCollectedCoinz", collectedCoinz)
                    snapshot.reference.update("feverModeCollectedCoinz", collectedCoinz)
                    snapshot.reference.update("spareChange", collectedCoinz)
                    snapshot.reference.update("spareChangeToSend", collectedCoinz)
                    snapshot.reference.update("receivedSpares", collectedCoinz)
                    snapshot.reference.update("bank", bank)
                    snapshot.reference.update("tossed",false)
                }
            } else {//if the day hasn't changed, we need to check if the user can play Coin Fever or not (already played the limit of 1 time/daily)
                //retrieve today's value from Firestore
                db.collection("users").document(fAuth.uid!!).get().addOnSuccessListener { snapshot ->
                    alreadyPlayed = snapshot.getBoolean("alreadyPlayed")!!
                }
            }

        }
        //when user clicks button to sign out
        signOutButton.setOnClickListener {
            Log.d("MainActivity", "Try to sign user out")
            fAuth.signOut()//sign out the user from FireBase
            val intent = Intent(this, SignInActivity::class.java)//go to SignInActivity
            startActivity(intent)
            finish()
        }


        //when user clicks button to play Classic Mode
        ClassicModeButton.setOnClickListener{
            val intent=Intent(this, ClassicModeActivity::class.java)//go to ClassicMode Activity
            startActivity(intent)
        }

        //when user clicks button to play Coin Fever Mode
        FeverModeButton.setOnClickListener{
            if(alreadyPlayed){//if the user already played, restrict him from playing and
                // produce an alert dialog to notify him that he passed the daily limit 1
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Already played today!")
                builder.setMessage("You have already played Coin Fever Mode today. You are only allowed to play Coin Fever mode 1 time per day. Check back tomorrow!")
                builder.setNegativeButton("OK") { _: DialogInterface, _: Int -> }
                builder.show()
                return@setOnClickListener
            }
            else{ //if the user hasn't played yet for today, then he can play
                //entering Coin Fever Mode. Making sure that the user will not be able to play it again this particular day
                alreadyPlayed=true
                //updated the online Firestore entry for this user's today limit of Coin Fever Mode
                db.collection("users").document(fAuth.uid!!).get().addOnSuccessListener { snapshot ->
                    snapshot.reference.update("alreadyPlayed", alreadyPlayed)
                }
                //starting Coin Fever Mode
                val intent=Intent(this, FeverModeActivity::class.java)
                startActivity(intent)
            }
        }

        //when user clicks button to go to Bank
        BankButton.setOnClickListener{
            val intent=Intent(this, Bank::class.java)//go to Bank Activity
            startActivity(intent)
        }

        //when user clicks button to go to Leaderboard
        leaderboardButton.setOnClickListener{
            val intent=Intent(this, LeaderboardActivity::class.java)//go to Leaderboard Activity
            startActivity(intent)
        }

    }

    //methods for date and formatting of date
    private fun getCurrentDateTime(): Date {
        return Calendar.getInstance().time
    }
    private fun Date.toString(format: String, locale: Locale = Locale.getDefault()): String {
        val formatter = SimpleDateFormat(format, locale)
        return formatter.format(this)
    }

}
