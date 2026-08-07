package com.local.webcaster.data

import android.content.Context
import androidx.core.content.edit

class PreferencesRepository(context: Context) {
    private val preferences = context.getSharedPreferences("local_web_caster", Context.MODE_PRIVATE)

    fun isAdBlockEnabled(host: String): Boolean = preferences.getBoolean("adblock_${normalize(host)}", true)
    fun setAdBlockEnabled(host: String, enabled: Boolean) {
        preferences.edit { putBoolean("adblock_${normalize(host)}", enabled) }
    }

    var blockPopups: Boolean
        get() = preferences.getBoolean("block_popups", true)
        set(value) { preferences.edit { putBoolean("block_popups", value) } }

    var searchEngineTemplate: String
        get() = preferences.getString("search_engine", "https://www.google.com/search?q=%s")!!
        set(value) { preferences.edit { putString("search_engine", value) } }

    private fun normalize(host: String) = host.lowercase().removePrefix("www.").replace(Regex("[^a-z0-9.-]"), "_")
}
