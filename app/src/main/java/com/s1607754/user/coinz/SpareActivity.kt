package com.s1607754.user.coinz

import android.content.DialogInterface
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_spare.*

class SpareActivity : AppCompatActivity() {
    private var spares: HashMap<String, HashMap<String, String>>? = HashMap()
    private var addspares: HashMap<String, HashMap<String, String>>? = HashMap()
    private lateinit var foundid: String
    private var foundUser: Boolean = false
    private var db = FirebaseFirestore.getInstance()
    private var user = FirebaseAuth.getInstance().currentUser
    private var tossed: Boolean = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spare)
        db.collection("users").document(user!!.uid).get().addOnSuccessListener { snapshot ->
            @Suppress("UNCHECKED_CAST")
            spares = snapshot.get("spareChangeToSend") as HashMap<String, HashMap<String, String>>?
            tossed = snapshot.getBoolean("tossed")!!
            spareCount.text = spares?.size.toString()
        }
        tossSpares.setOnClickListener {
            if (spareCount.text.isEmpty() || spares?.size == 0) {
                Toast.makeText(this, "You do not have any spare coins to toss", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val builder = AlertDialog.Builder(this)
            builder.setTitle("WARNING!")
            builder.setMessage("If you choose to toss your spare change,then there will be a 75% FINE to the GOLD value any spare change you receive. Are you sure you want to toss your spare change?")
            builder.setNeutralButton("No, go back") { _: DialogInterface, _: Int -> }
            builder.setPositiveButton("Yes, toss it all!") { dialog, which ->
                spares?.clear()
                db.collection("users").document(user!!.uid).get().addOnSuccessListener { snapshot ->
                    snapshot.reference.update("spareChangeToSend", spares)
                    snapshot.reference.update("tossed", true)
                    Toast.makeText(this, "Tossed all spare change!", Toast.LENGTH_SHORT).show()
                    spareCount.text = spares?.size.toString()
                }


            }
            builder.show()

        }
        sendSpares.setOnClickListener {
            val email = spareEmail.text.toString()

            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter text in Email field", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (spareCount.text.isEmpty() || spares?.size == 0) {
                Toast.makeText(this, "You do not have any spare coins to send", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (email.equals(user!!.email)) {
                Toast.makeText(this, "You cannot send spare coins to yourself", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            db.collection("users").get().addOnSuccessListener { snapshot ->
                snapshot.forEach {
                    val uid = it["uid"] as String
                    val uemail = it.get("email") as String
                    if (uemail.equals(email)) {
                        foundUser = true
                        foundid = uid
                    }
                }
                if (!foundUser) {
                    Toast.makeText(this, "This user email does not exist, try again", Toast.LENGTH_SHORT).show()
                } else {

                    addspares?.putAll(spares!!)
                    db.collection("users").document(foundid).get().addOnSuccessListener { snapshot ->

                        //updating FireStore with today's currency rates
                        var onlinespares = snapshot.get("receivedSpares") as HashMap<String, HashMap<String, String>>?
                        onlinespares?.putAll(addspares!!)
                        snapshot.reference.update("receivedSpares", onlinespares)
                        Log.d("SpareActivity", "[sendSpares] Successfully sent spare change to $email's Firestore")
                        Toast.makeText(this, "Sent your spare change to $email", Toast.LENGTH_SHORT).show()
                    }
                    spares?.clear()
                    spareCount.text = spares?.size.toString()
                    db.collection("users").document(user!!.uid).get().addOnSuccessListener { snapshot ->
                        snapshot.reference.update("spareChangeToSend", spares)
                        Log.d("SpareActivity", "[sendSpares] Removed all sent spare change from current user's Firestore successfully")

                    }
                    if (tossed == true) {
                        val builder = AlertDialog.Builder(this)
                        builder.setTitle("Regarding your fine")
                        builder.setMessage("You will still get fined 75% on any received spare change for tossing some spare change earlier today. Any spare change you send to friends now will not undo the fine.")
                        builder.setNegativeButton("OK") { _: DialogInterface, _: Int -> }
                        builder.show()
                    }
                }
            }

        }
    }

}
