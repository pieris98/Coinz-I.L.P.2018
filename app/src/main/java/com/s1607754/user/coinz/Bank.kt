package com.s1607754.user.coinz

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

import kotlinx.android.synthetic.main.activity_bank.*

class Bank : AppCompatActivity() {
    private var db = FirebaseFirestore.getInstance()
    private var user = FirebaseAuth.getInstance().currentUser
    private var gold: Double? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bank)
        db.collection("users").document(user!!.uid).get().addOnSuccessListener { snapshot ->
            gold = snapshot.getDouble("bank")
            gold_textView.text="%.3f".format(gold)
        }

    }
}
