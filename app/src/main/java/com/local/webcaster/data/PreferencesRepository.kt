package com.local.webcaster.data

import android.content.Context
import androidx.core.content.edit
import com.local.webcaster.security.UrlValidator
import org.json.JSONArray
import org.json.JSONObject

data class SitePreferences(
    val ads: Boolean = true,
    val trackers: Boolean = true,
    val popups: Boolean = true,
    val quickCast: Boolean = true,
    val desktopMode: Boolean = false,
)

data class PersistedTab(
    val id: String,
    val url: String,
    val title: String,
    val lastAccessed: Long,
)

class PreferencesRepository(context: Context) {
    private val preferences = context.getSharedPreferences("local_web_caster", Context.MODE_PRIVATE)

    fun isAdBlockEnabled(host: String): Boolean = preferences.getBoolean("adblock_${normalize(host)}", true)
    fun setAdBlockEnabled(host: String, enabled: Boolean) {
        preferences.edit { putBoolean("adblock_${normalize(host)}", enabled) }
    }

    fun sitePreferences(host: String): SitePreferences {
        val key = normalize(host)
        val legacy = preferences.getBoolean("adblock_$key", true)
        return SitePreferences(
            ads = preferences.getBoolean("site_ads_$key", legacy),
            trackers = preferences.getBoolean("site_trackers_$key", legacy),
            popups = preferences.getBoolean("site_popups_$key", true),
            quickCast = preferences.getBoolean("site_quick_cast_$key", true),
            desktopMode = preferences.getBoolean("site_desktop_$key", false),
        )
    }

    fun setSitePreferences(host: String, value: SitePreferences) {
        val key = normalize(host)
        if (key.isBlank()) return
        preferences.edit {
            putBoolean("site_ads_$key", value.ads)
            putBoolean("site_trackers_$key", value.trackers)
            putBoolean("site_popups_$key", value.popups)
            putBoolean("site_quick_cast_$key", value.quickCast)
            putBoolean("site_desktop_$key", value.desktopMode)
            putBoolean("adblock_$key", value.ads || value.trackers)
        }
    }

    fun resetSitePreferences(host: String) {
        val key = normalize(host)
        preferences.edit {
            remove("site_ads_$key")
            remove("site_trackers_$key")
            remove("site_popups_$key")
            remove("site_quick_cast_$key")
            remove("site_desktop_$key")
            remove("adblock_$key")
        }
    }

    fun restoreTabs(): List<PersistedTab> = runCatching {
        val array = JSONArray(preferences.getString(TABS_KEY, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = item.optString("url")
                if (url != HOME_URL && !UrlValidator.isValidMediaUrl(url)) continue
                val id = item.optString("id").takeIf { it.matches(Regex("[a-zA-Z0-9_-]{1,64}")) } ?: continue
                add(
                    PersistedTab(
                        id = id,
                        url = url,
                        title = item.optString("title").take(500).ifBlank { "Nouvel onglet" },
                        lastAccessed = item.optLong("lastAccessed").coerceAtLeast(0),
                    )
                )
                if (size >= MAX_PERSISTED_TABS) break
            }
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    fun saveTabs(tabs: List<PersistedTab>, activeTabId: String) {
        val array = JSONArray()
        tabs.take(MAX_PERSISTED_TABS).forEach { tab ->
            array.put(JSONObject().apply {
                put("id", tab.id)
                put("url", tab.url)
                put("title", tab.title.take(500))
                put("lastAccessed", tab.lastAccessed)
            })
        }
        preferences.edit {
            putString(TABS_KEY, array.toString())
            putString(ACTIVE_TAB_KEY, activeTabId)
        }
    }

    fun restoredActiveTabId(): String? = preferences.getString(ACTIVE_TAB_KEY, null)

    var blockPopups: Boolean
        get() = preferences.getBoolean("block_popups", true)
        set(value) { preferences.edit { putBoolean("block_popups", value) } }

    var searchEngineTemplate: String
        get() = preferences.getString("search_engine", "https://www.google.com/search?q=%s")!!
        set(value) { preferences.edit { putString("search_engine", value) } }

    private fun normalize(host: String) = host.lowercase().removePrefix("www.").replace(Regex("[^a-z0-9.-]"), "_")

    private companion object {
        const val TABS_KEY = "browser_tabs_v1"
        const val ACTIVE_TAB_KEY = "browser_active_tab_v1"
        const val MAX_PERSISTED_TABS = 12
        const val HOME_URL = "https://appassets.androidplatform.net/assets/home.html"
    }
}
