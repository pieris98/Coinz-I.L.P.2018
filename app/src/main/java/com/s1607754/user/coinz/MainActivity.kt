package com.s1607754.user.coinz

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.s1607754.user.coinz.R.id.BankButton
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {
    //declare a firebase auth private object
    private var fAuth=FirebaseAuth.getInstance()

    //elements needed for settings and preferences
    private var lastDownloadDate = "" // Format: YYYY/MM/DD
    private lateinit var downloadDate: String
    private val preferencesFile = "MyPrefsFile" // for storing preferences

    //flag to see if the user has already played Coin Fever mode today (limit of 1 play per day for Coin Fever Mode, design decision)
    private var alreadyPlayed:Boolean=false
    //also need firestore to access that stored value from the database for this user
    private var db: FirebaseFirestore = FirebaseFirestore.getInstance()

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

        //check if the day has changed
        val settings = getSharedPreferences(preferencesFile, Context.MODE_PRIVATE)
        lastDownloadDate = settings.getString("lastDownloadDate", "")
        // Write a message to ”logcat” (for debugging purposes)
        Log.d("MainActivity", "[onCreate] Recalled lastDownloadDate is ’$lastDownloadDate’")
        downloadDate = getCurrentDateTime().toString("yyyy/MM/dd")
        if (!downloadDate.equals(lastDownloadDate)) {
            db.collection("users").document(fAuth.uid!!).get().addOnSuccessListener { snapshot ->
            snapshot.reference.update("alreadyPlayed",false)
            }
        }
        else {//if the day hasn't changed, we need to check if the user can play Coin Fever or not (already played the limit of 1 time/daily)
            //retrieve today's value from Firestore
            db.collection("users").document(fAuth.uid!!).get().addOnSuccessListener { snapshot ->
                alreadyPlayed = snapshot.getBoolean("alreadyPlayed")!!
            }
        }


        //when user clicks button to sign out
        signOutButton.setOnClickListener() {
            Log.d("MainActivity", "Try to sign user out")
            fAuth.signOut()
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
            finish()
        }


        //when user clicks button to play Classic Mode
        ClassicModeButton.setOnClickListener(){
            val intent=Intent(this, ClassicModeActivity::class.java)
            startActivity(intent)
        }

        //when user clicks button to play Coin Fever Mode
        FeverModeButton.setOnClickListener(){
            if(alreadyPlayed){
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Already played today!")
                builder.setMessage("You have already played Coin Fever Mode today. You are only allowed to play Coin Fever mode 1 time per day. Check back tomorrow!")
                builder.setNegativeButton("OK") { _: DialogInterface, _: Int -> }
                builder.show()
                return@setOnClickListener
            }
            else{
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
        BankButton.setOnClickListener(){
            val intent=Intent(this, Bank::class.java)
            startActivity(intent)
        }

    }

    //methods for date and formatting of date
    private fun getCurrentDateTime(): Date {
        return Calendar.getInstance().time
    }
    fun Date.toString(format: String, locale: Locale = Locale.getDefault()): String {
        val formatter = SimpleDateFormat(format, locale)
        return formatter.format(this)
    }

}
