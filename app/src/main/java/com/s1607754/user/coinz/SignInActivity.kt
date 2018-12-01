package com.s1607754.user.coinz

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.s1607754.user.coinz.R.id.*
import kotlinx.android.synthetic.main.activity_signin.*

class SignInActivity : AppCompatActivity() {
 private var fAuth=FirebaseAuth.getInstance()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signin)

        signInbutton.setOnClickListener(){
            val email = emailText.text.toString()
            val password = passwordText.text.toString()
            Log.d("Login", "Attempt login with email/pw: $email/***")

            fAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener {
                        if (!it.isSuccessful) return@addOnCompleteListener
                        Log.d("Login", "Successfully logged in user with uid: ${it.result?.user?.uid}")
                        finish()
                    }
                    .addOnFailureListener{
                        Log.d("Login", "Failed to login user: ${it.message}")
                        Toast.makeText(this, "Failed to login user: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
        }


        DontHaveAccount.setOnClickListener {
            Log.d("SignInActivity", "Try to show register activity")
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

    }
}