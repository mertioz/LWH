package com.local.webcaster.adblock

import android.content.Context
import com.local.webcaster.security.PublicNetworkDns
import com.local.webcaster.security.SafeLogger
import com.local.webcaster.security.BoundedBodyReader
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Keeps a bounded, cached snapshot of maintained EasyList network rules. The parser deliberately
 * accepts only domain rules whose WebView semantics can be reproduced safely; cosmetic and
 * context-sensitive rules are ignored rather than over-applied to media traffic.
 */
class FilterListUpdater(
    context: Context,
    private val scope: CoroutineScope,
    private val parser: FilterRuleParser,
) {
    private val cacheDirectory = File(context.filesDir, "filter_lists")
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .dns(PublicNetworkDns)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun start(fallbackRules: FilterRules, onRules: (FilterRules) -> Unit) {
        scope.launch(Dispatchers.IO) {
            cacheDirectory.mkdirs()
            publishCached(fallbackRules, onRules)
            val changed = SOURCES.map(::refreshIfNeeded).any { it }
            if (changed) publishCached(fallbackRules, onRules)
        }
    }

    private fun publishCached(fallbackRules: FilterRules, onRules: (FilterRules) -> Unit) {
        val cached = SOURCES.mapNotNull { source ->
            source.file().takeIf(File::isFile)?.let { file ->
                runCatching { parser.parse(file.readText()) }
                    .onFailure { SafeLogger.warn("ADBLOCK_FILTERS cache_read=${it.javaClass.simpleName}") }
                    .getOrNull()
            }
        }.fold(fallbackRules, FilterRules::plus)
        onRules(cached)
        SafeLogger.debug("ADBLOCK_FILTERS active_domains=${cached.domainCount}")
    }

    private fun refreshIfNeeded(source: Source): Boolean {
        val destination = source.file()
        if (destination.isFile && System.currentTimeMillis() - destination.lastModified() < REFRESH_INTERVAL_MS) {
            return false
        }
        return runCatching {
            val request = Request.Builder().url(source.url)
                .header("User-Agent", "LocalWebCaster/1.0 filter updater")
                .build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                val body = response.body ?: error("Empty filter response")
                val bytes = BoundedBodyReader.read(body.source(), MAX_LIST_BYTES)
                    ?: error("Filter list too large")
                val text = bytes.toString(Charsets.UTF_8)
                check(text.startsWith("[Adblock")) { "Unexpected filter format" }
                val temporary = File(cacheDirectory, "${source.name}.tmp")
                temporary.writeText(text)
                check(temporary.renameTo(destination) || run {
                    destination.delete()
                    temporary.renameTo(destination)
                }) { "Could not replace filter cache" }
            }
            SafeLogger.debug("ADBLOCK_FILTERS refreshed=${source.name}")
            true
        }.onFailure {
            SafeLogger.warn("ADBLOCK_FILTERS refresh=${source.name} error=${it.javaClass.simpleName}")
        }.getOrDefault(false)
    }

    private fun Source.file() = File(cacheDirectory, "$name.txt")

    private data class Source(val name: String, val url: String)

    private companion object {
        const val MAX_LIST_BYTES = 8 * 1_024 * 1_024
        const val REFRESH_INTERVAL_MS = 4 * 24 * 60 * 60 * 1_000L
        val SOURCES = listOf(
            Source("easylist", "https://easylist.to/easylist/easylist.txt"),
            Source("easyprivacy", "https://easylist.to/easylist/easyprivacy.txt"),
        )
    }
}
