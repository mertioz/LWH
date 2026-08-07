package com.local.webcaster.hls

import java.net.URI

class HlsManifestParser {
    fun parse(text: String, manifestUrl: String): HlsManifest {
        val safeText = text.removePrefix("\uFEFF")
        val lines = safeText.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        if (lines.firstOrNull() != "#EXTM3U") return HlsManifest(false, false, false, false, emptyList())

        val variants = mutableListOf<HlsVariant>()
        var pendingAttributes: Map<String, String>? = null
        var drm = false
        var hasMediaSegments = false
        for (line in lines.drop(1)) {
            when {
                line.startsWith("#EXT-X-STREAM-INF:") -> pendingAttributes = parseAttributes(line.substringAfter(':'))
                line.startsWith("#EXT-X-KEY:") || line.startsWith("#EXT-X-SESSION-KEY:") -> {
                    val attrs = parseAttributes(line.substringAfter(':'))
                    val method = attrs["METHOD"].orEmpty()
                    val keyFormat = attrs["KEYFORMAT"].orEmpty()
                    if ((method.isNotBlank() && !method.equals("NONE", true)) ||
                        keyFormat.contains("widevine", true) || keyFormat.contains("fairplay", true) ||
                        keyFormat.contains("edef8ba9", true)
                    ) drm = true
                }
                line.startsWith("#EXTINF:") -> hasMediaSegments = true
                !line.startsWith("#") && pendingAttributes != null -> {
                    val attrs = pendingAttributes
                    val resolution = attrs["RESOLUTION"]?.split('x', 'X')
                    variants += HlsVariant(
                        url = resolve(manifestUrl, line),
                        bandwidth = attrs["BANDWIDTH"]?.toLongOrNull(),
                        averageBandwidth = attrs["AVERAGE-BANDWIDTH"]?.toLongOrNull(),
                        width = resolution?.getOrNull(0)?.toIntOrNull(),
                        height = resolution?.getOrNull(1)?.toIntOrNull(),
                        codecs = attrs["CODECS"],
                    )
                    pendingAttributes = null
                }
            }
        }
        val master = variants.isNotEmpty()
        val live = !master && hasMediaSegments && lines.none { it == "#EXT-X-ENDLIST" }
        return HlsManifest(true, master, live, drm, variants.distinctBy { it.url })
    }

    internal fun parseAttributes(value: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val token = StringBuilder()
        var quoted = false
        val parts = mutableListOf<String>()
        value.forEach { char ->
            when {
                char == '"' -> { quoted = !quoted; token.append(char) }
                char == ',' && !quoted -> { parts += token.toString(); token.clear() }
                else -> token.append(char)
            }
        }
        if (token.isNotEmpty()) parts += token.toString()
        for (part in parts) {
            val key = part.substringBefore('=', "").trim().uppercase()
            if (key.isNotEmpty()) result[key] = part.substringAfter('=', "").trim().removeSurrounding("\"")
        }
        return result
    }

    private fun resolve(base: String, child: String): String = runCatching {
        URI(base).resolve(child).toASCIIString()
    }.getOrDefault(child)
}
