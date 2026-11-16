package com.koshertech.hub

// One app in your hub.
// To add an app later: copy a line in MainActivity's list and fill these in.
data class AppItem(
    val name: String,       // shown in bold, e.g. "Zemer"
    val category: String,   // small grey line, e.g. "Music · Jewish songs"
    val packageName: String,// the app's exact ID on the phone (we read this off the real phone)
    val color: Int          // the icon color, e.g. 0xFFE0A52B.toInt()
)
