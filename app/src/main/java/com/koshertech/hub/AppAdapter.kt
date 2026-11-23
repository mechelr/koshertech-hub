package com.koshertech.hub

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    initial: List<AppItem>,
    private val onOpen: (AppItem) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var all = initial.toMutableList()
    private var shown = all.toMutableList()

    // Replace the whole app list (used when the backend pushes apps).
    fun setApps(newList: List<AppItem>) {
        all = newList.toMutableList()
        shown = all.toMutableList()
        notifyDataSetChanged()
    }

    fun allApps(): List<AppItem> = all

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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_grid, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = shown[position]
        holder.name.text = app.name
        holder.iconLetter.text = app.name.take(1)
        val d = holder.icon.background.mutate() as GradientDrawable
        d.setColor(0xFFF5F5F5.toInt())              // monochrome: white tile, black letter
        holder.iconLetter.setTextColor(0xFF0C0C0C.toInt())

        // Show "Install" under apps not yet on the phone; otherwise the category.
        val pm = holder.itemView.context.packageManager
        val installed = pm.getLaunchIntentForPackage(app.packageName) != null
        if (installed) {
            holder.category.text = app.category
            holder.category.setTextColor(0xFF7A7A7A.toInt())
        } else {
            holder.category.text = "Install"
            holder.category.setTextColor(0xFFFFFFFF.toInt())
        }

        // Parental Controls: locked apps turn gray and can't be opened.
        val ctx = holder.itemView.context
        val st = ParentalRules.state(ctx, app.packageName)
        if (!st.allowed) {
            holder.itemView.alpha = 0.45f
            d.setColor(0xFF2E2E2E.toInt()); holder.iconLetter.setTextColor(0xFF7A7A7A.toInt())
            holder.category.text = if (st.label == "Blocked") "LOCKED" else st.label.uppercase()
            holder.category.setTextColor(0xFF9A9A9A.toInt())
            holder.itemView.setOnClickListener {
                val extra = if (st.label.startsWith("Opens")) " · " + st.label.lowercase() else ""
                android.widget.Toast.makeText(ctx, app.name + " is locked by Parental Controls" + extra,
                    android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }
        holder.itemView.alpha = 1f

        // The whole tile opens (or installs) the app.
        holder.itemView.setOnClickListener { onOpen(app) }
    }

    override fun getItemCount() = shown.size
}
