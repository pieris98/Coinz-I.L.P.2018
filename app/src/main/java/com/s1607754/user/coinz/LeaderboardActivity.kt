package com.s1607754.user.coinz

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_leaderboard.*

class RankData(val rankData:String)
class LeaderboardActivity : AppCompatActivity() {
    private var db = FirebaseFirestore.getInstance()
    private var ranklist:ArrayList<Pair<String,Double>>? = null
    private var list = mutableListOf<RankData>()
    private lateinit var sortedranklist:List<Pair<String,Double>>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leaderboard)
        db.collection("users").get().addOnSuccessListener {
            it.forEach{
                val email=it["email"] as String
                val bank=it["bank"] as Double
                val rankPair=Pair(email,bank)
                ranklist?.add(rankPair)
            }
            sortedranklist=ranklist?.sortedWith(compareByDescending { it.second })!!
            sortedranklist.forEach{
                var data="${it.first} ${it.second}"
                list.add(RankData(data))
            }
            list_view.adapter=ListAdapter(this,R.layout.leaderboard_item,list)
        }


    }

}
