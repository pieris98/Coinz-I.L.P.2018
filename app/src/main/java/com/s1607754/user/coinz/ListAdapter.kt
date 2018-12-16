package com.s1607754.user.coinz

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class ListAdapter (var cont:Context, var resources:Int, var items:List<RankData>)
    :ArrayAdapter<RankData>(cont,resources,items){
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        //inflate layout
        val layoutInflater:LayoutInflater= LayoutInflater.from(cont)
        val view:View=layoutInflater.inflate(resources,null)

        val textView:TextView=view.findViewById(R.id.list_item)
        var mItems:RankData=items[position]
        textView.text=mItems.rankData
        return view
    }
}