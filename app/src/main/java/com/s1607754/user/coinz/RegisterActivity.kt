package com.s1607754.user.coinz

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import kotlinx.android.synthetic.main.activity_register.*
import com.google.firebase.firestore.FirebaseFirestore

//defining a class constructor for the User collection that will be stored in Firebase Firestore
private class FireUser(var uid:String, var email:String, var classicModeCollectedCoinz:HashMap<String,HashMap<String,Any>>, var Rates:HashMap<String,Double>, var spareChange:HashMap<String,HashMap<String,Any>>,var spareChangeToSend:HashMap<String,HashMap<String,Any>>,var ReceivedSpares:HashMap<String,HashMap<String,Any>>, var alreadyPlayed:Boolean,var tossed:Boolean,var bank:Double)

class RegisterActivity : AppCompatActivity() {
    private var fAuth=FirebaseAuth.getInstance()
    private var db:FirebaseFirestore= FirebaseFirestore.getInstance()
    private val tag="RegisterActivity"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        RegisterButton.setOnClickListener {
            newRegister()
        }
        AlreadyAccountText.setOnClickListener {
            Log.d(tag, "Try to move back to login activity")
            finish()
        }
    }

    private fun newRegister() {
        val email = emailTextRegister.text.toString()
        val password = passwordRegister.text.toString()
        val passwordConfirm=passwordRegister2.text.toString()
        if (email.isEmpty() || password.isEmpty() || passwordConfirm.isEmpty()) {
            Toast.makeText(this, "Please enter text in Email and Password fields", Toast.LENGTH_SHORT).show()
            return
        }
        if (!password.equals(passwordConfirm)){
            Toast.makeText(this, "Passwords don't match!", Toast.LENGTH_SHORT).show()
            return
        }
        Log.d(tag, "Email is: $email")
        Log.d(tag, "Password: $password")

        // Firebase Authentication to create a user with email and password
        fAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener {
                    if (!it.isSuccessful) return@addOnCompleteListener

                    // else if successful
                    Log.d(tag, "Successfully created user with uid: ${it.result?.user?.uid}")
                    val user = FireUser(fAuth.uid ?: "", email, HashMap(), HashMap(),HashMap(), HashMap(),HashMap(),false,false,0.0)
                    val uid:String = user.uid
                    db.collection("users").document(uid).set(user).addOnSuccessListener {_->
                        Log.d(tag, "Successfully saved user with email ${user.email} collection to FireStore")
                    }
                    finish()
                }.addOnFailureListener{
                    Log.d(tag, "Failed to create user: ${it.message}")
                    Toast.makeText(this, "Failed to create user: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

