package com.nexusmount.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nexusmount.app.R

class SimpleAdapter(
    private var items: List<Pair<String, String>>,
    private val onClick: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<SimpleAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(android.R.id.text1)
        val subtitle: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        v.setBackgroundColor(0xFF171F33.toInt())
        v.setPadding(32, 36, 32, 36)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (t, s) = items[position]
        holder.title.text = t
        holder.title.setTextColor(0xFFDAE2FD.toInt())
        holder.subtitle.text = s
        holder.subtitle.setTextColor(0xFFC2C6D6.toInt())
        holder.itemView.setOnClickListener { onClick?.invoke(position) }
    }

    override fun getItemCount() = items.size

    fun submit(newItems: List<Pair<String, String>>) {
        items = newItems
        notifyDataSetChanged()
    }
}
