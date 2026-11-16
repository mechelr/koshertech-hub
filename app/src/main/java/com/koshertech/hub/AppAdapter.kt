package com.koshertech.hub

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val all: List<AppItem>,
    private val onOpen: (AppItem) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var shown = all.toMutableList()

    fun filter(query: String) {
        val q = query.trim().lowercase()
        shown = if (q.isEmpty()) all.toMutableList()
        else all.filter {
            it.name.lowercase().contains(q) || it.category.lowercase().contains(q)
        }.toMutableList()
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: View = view.findViewById(R.id.icon)
        val iconLetter: TextView = view.findViewById(R.id.iconLetter)
        val name: TextView = view.findViewById(R.id.name)
        val category: TextView = view.findViewById(R.id.category)
        val open: TextView = view.findViewById(R.id.openButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = shown[position]
        holder.name.text = app.name
        holder.category.text = app.category
        holder.iconLetter.text = app.name.take(1)
        val d = holder.icon.background.mutate() as GradientDrawable
        d.setColor(app.color)

        // "Open" if the app is already on the phone, otherwise "Install".
        val pm = holder.itemView.context.packageManager
        val installed = pm.getLaunchIntentForPackage(app.packageName) != null
        holder.open.text = if (installed) "Open" else "Install"

        holder.open.setOnClickListener { onOpen(app) }
    }

    override fun getItemCount() = shown.size
}
