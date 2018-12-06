package com.s1607754.user.coinz

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_spare.*

class SpareActivity : AppCompatActivity() {
    private var spares: HashMap<String, HashMap<String, String>>? = HashMap()
    private var addspares:HashMap<String, HashMap<String, String>>? = HashMap()
    private lateinit var foundid:String
    private var foundUser:Boolean=false
    private var db=FirebaseFirestore.getInstance()
    private var user= FirebaseAuth.getInstance().currentUser
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spare)
        db.collection("users").document(user!!.uid).get().addOnSuccessListener { snapshot ->
            @Suppress("UNCHECKED_CAST")
            spares= snapshot.get("spareChangeToSend") as HashMap<String, HashMap<String, String>>?
            spareCount.text=spares?.size.toString()
        }

        spareCount.text=spares?.size.toString()
        sendSpares.setOnClickListener {
            val email = spareEmail.text.toString()

            if (email.isEmpty() ) {
                Toast.makeText(this, "Please enter text in Email field", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (spareCount.text.isEmpty()||spares?.size==0) {
                Toast.makeText(this, "You do not have any spare coins to send", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if(email.equals(user!!.email)){
                Toast.makeText(this, "You cannot send spare coins to yourself", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            db.collection("users").get().addOnSuccessListener { snapshot ->
                snapshot.forEach{
                    val uid=it["uid"] as String
                    val uemail= it.get("email") as String
                    if(uemail.equals(email)){
                        foundUser=true
                        foundid=uid
                    }
                }
                if(!foundUser){
                    Toast.makeText(this, "This user email does not exist, try again", Toast.LENGTH_SHORT).show()
                }
                else {
                    addspares?.putAll(spares!!)
                    db.collection("users").document(foundid).get().addOnSuccessListener { snapshot ->

                        //updating FireStore with today's currency rates
                        var onlinespares= snapshot.get("receivedSpares") as HashMap<String, HashMap<String, String>>?
                        onlinespares?.putAll(addspares!!)
                        snapshot.reference.update("receivedSpares", onlinespares)
                        Log.d("SpareActivity", "[sendSpares] Successfully sent spare change to $email's Firestore")
                        Toast.makeText(this, "Sent your spare change to $email", Toast.LENGTH_SHORT).show()
                        }
                    spares?.clear()
                    spareCount.text=spares?.size.toString()
                    db.collection("users").document(user!!.uid).get().addOnSuccessListener { snapshot ->
                        snapshot.reference.update("spareChangeToSend", spares)
                        Log.d("SpareActivity", "[sendSpares] Removed all sent spare change from current user's Firestore successfully")

                    }
                }
            }

        }
    }

}
