package com.s1607754.user.coinz

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {
    //declare a firebase auth private object
    private var fAuth=FirebaseAuth.getInstance()
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
    }

}
