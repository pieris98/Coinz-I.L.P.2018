package com.s1607754.user.coinz

import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.s1607754.user.coinz.R
import kotlinx.android.synthetic.main.activity_register.*

class RegisterActivity : AppCompatActivity() {
    private var fAuth=FirebaseAuth.getInstance()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        RegisterButton.setOnClickListener {
            newRegister()
        }
        AlreadyAccountText.setOnClickListener {
            Log.d("RegisterActivity", "Try to move back to login activity")
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
        Log.d("RegisterActivity", "Email is: " + email)
        Log.d("RegisterActivity", "Password: $password")

        // Firebase Authentication to create a user with email and password
        fAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener {
                    if (!it.isSuccessful) return@addOnCompleteListener

                    // else if successful
                    Log.d("Register", "Successfully created user with uid: ${it.result?.user?.uid}")
                    finish()
                }
                .addOnFailureListener{
                    Log.d("Register", "Failed to create user: ${it.message}")
                    Toast.makeText(this, "Failed to create user: ${it.message}", Toast.LENGTH_SHORT).show()
                }
    }


}