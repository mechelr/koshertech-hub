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
        val appIcon: android.widget.ImageView = view.findViewById(R.id.appIcon)
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
        val ctx = holder.itemView.context
        holder.name.text = app.name
        val d = holder.icon.background.mutate() as GradientDrawable
        d.setColor(AppStyle.color(app))
        val ic = AppStyle.icon(app)
        if (ic != 0) {
            holder.appIcon.setImageResource(ic); holder.appIcon.visibility = View.VISIBLE
            holder.iconLetter.visibility = View.GONE
        } else {
            holder.appIcon.visibility = View.GONE
            holder.iconLetter.visibility = View.VISIBLE; holder.iconLetter.text = app.name.take(1)
        }

        // Small line under the name only when it matters: not installed yet, or locked.
        val installed = app.internal.isNotEmpty() || ctx.packageManager.getLaunchIntentForPackage(app.packageName) != null
        holder.category.visibility = if (installed) View.GONE else View.VISIBLE
        holder.category.text = "INSTALL"
        holder.category.setTextColor(0xFF7C3AED.toInt())

        // Parental Controls: locked apps turn gray and can't be opened.
        val st = ParentalRules.state(ctx, app.packageName)
        if (!st.allowed) {
            holder.itemView.alpha = 0.6f
            d.setColor(0xFFCBD5E1.toInt())
            holder.category.visibility = View.VISIBLE
            holder.category.text = "\uD83D\uDD12 " + (if (st.label == "Blocked") "LOCKED" else st.label.uppercase())
            holder.category.setTextColor(0xFF64748B.toInt())
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

// Colors and line icons for app tiles (also used by Parental Controls).
object AppStyle {
    private val icons = mapOf(
        "com.anthropic.claude" to R.drawable.ic_a_claude,
        "fm.jewishmusic.application" to R.drawable.ic_a_music,
        "com.jtech.zemer" to R.drawable.ic_a_pulse,
        "app.tfs.prod" to R.drawable.ic_a_play,
        "com.google.android.dialer" to R.drawable.ic_a_phone,
        "org.fossify.camera" to R.drawable.ic_a_camera,
        "org.fossify.gallery" to R.drawable.ic_a_gallery,
        "com.google.android.contacts" to R.drawable.ic_a_contacts,
        "com.koshertech.settings" to R.drawable.ic_a_settings,
        "com.koshertech.hub.calendar" to R.drawable.ic_a_calendar,
        "com.google.android.deskclock" to R.drawable.ic_a_clock, "com.android.deskclock" to R.drawable.ic_a_clock,
        "com.google.android.calculator" to R.drawable.ic_a_calc, "com.android.calculator2" to R.drawable.ic_a_calc
    )
    fun icon(app: AppItem): Int = icons[app.packageName] ?: 0
    fun icon(pkg: String): Int = icons[pkg] ?: 0
    fun color(app: AppItem): Int = if (app.color == 0xFFFFFFFF.toInt() || app.color == 0) 0xFF64748B.toInt() else app.color
}
