package com.local.webcaster.relay

import android.content.Context
import android.net.ConnectivityManager
import android.util.Base64
import android.webkit.CookieManager
import com.local.webcaster.detection.MediaCandidate
import com.local.webcaster.detection.MediaType
import com.local.webcaster.detection.MediaUrlClassifier
import com.local.webcaster.security.UrlValidator
import com.local.webcaster.security.PublicNetworkDns
import com.local.webcaster.security.SafeLogger
import com.local.webcaster.security.BoundedBodyReader
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.newChunkedResponse
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import java.io.ByteArrayInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URI
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response as OkResponse

class LocalMediaRelay(context: Context) : MediaRelay {
    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .dns(PublicNetworkDns)
        // Redirects are handled explicitly so credentials can be stripped on origin changes and
        // relative manifest URLs can be resolved against the final response URL.
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val registered = ConcurrentHashMap<String, RelayTarget>()
    private val targetIds = ConcurrentHashMap<String, String>()
    private val rewriter = HlsRelayRewriter()
    private val dashRewriter = DashRelayRewriter()
    @Volatile private var server: RelayServer? = null
    @Volatile private var session: RelaySession? = null
    @Volatile override var activeCandidate: MediaCandidate? = null
        private set
    @Volatile override var lastStatusCode: Int? = null
        private set

    override val isRunning: Boolean get() = server?.isAlive == true

    @Synchronized
    override fun start(candidate: MediaCandidate): Result<String> = runCatching {
        stop()
        lastStatusCode = null
        val host = findLanAddress() ?: throw IllegalStateException(
            "Le telephone et l'appareil Cast doivent etre sur le meme reseau Wi-Fi pour utiliser le relay."
        )
        val tokenBytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val token = Base64.encodeToString(tokenBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val relayServer = RelayServer(token)
        relayServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        server = relayServer
        session = RelaySession(token, relayServer.listeningPort, host)
        activeCandidate = candidate
        SafeLogger.debug("MEDIA_RELAY start type=${candidate.mediaType} url=${SafeLogger.redactedUrl(candidate.resolvedUrl)}")
        register(candidate.resolvedUrl, candidate.mediaType, HeaderContext.from(candidate))
    }.onFailure { stop() }

    override fun createRelayUrl(candidate: MediaCandidate): Result<String> = runCatching {
        check(isRunning) { "Le relay local n'est pas demarre." }
        register(candidate.resolvedUrl, candidate.mediaType, HeaderContext.from(candidate))
    }

    @Synchronized
    override fun stop() {
        server?.stop()
        server = null
        session = null
        activeCandidate = null
        lastStatusCode = null
        registered.clear()
        targetIds.clear()
    }

    override fun hasNetworkChanged(): Boolean {
        val active = session ?: return false
        return findLanAddress() != active.hostAddress
    }

    private fun register(url: String, type: MediaType, headers: HeaderContext): String {
        require(UrlValidator.isValidMediaUrl(url)) { "Media URL refused by relay" }
        return relayUrl(registerTarget(url, type, headers))
    }

    private fun registerChild(url: String, parent: RelayTarget, isPlaylist: Boolean): String {
        require(UrlValidator.isValidMediaUrl(url)) { "Child URL refused by relay" }
        val classified = MediaUrlClassifier.classify(url)
        val type = if (isPlaylist || classified == MediaType.HLS) MediaType.HLS else classified
        val headers = parent.headers.forUrl(parent.url, url, ::cookieForUrl)
        return relayUrl(registerTarget(url, type, headers))
    }

    private fun registerTarget(url: String, type: MediaType, headers: HeaderContext): String {
        val key = "${type.name}:$url"
        targetIds[key]?.let { return it }
        check(registered.size < MAX_REGISTERED_TARGETS) { "Manifest too large for local relay" }
        val newId = UUID.randomUUID().toString().replace("-", "")
        val id = targetIds.putIfAbsent(key, newId) ?: newId
        registered.putIfAbsent(id, RelayTarget(url, type, headers, allowRelative = false))
        return id
    }

    private fun registerDashReference(url: String, parent: RelayTarget): String {
        require(UrlValidator.isValidMediaUrl(url)) { "DASH child URL refused by relay" }
        val uri = URI(url)
        val rawPath = uri.rawPath.orEmpty()
        val slash = rawPath.lastIndexOf('/')
        val basePath = if (slash >= 0) rawPath.substring(0, slash + 1) else "/"
        val tail = if (slash >= 0) rawPath.substring(slash + 1) else rawPath
        val base = URI(uri.scheme, uri.rawAuthority, basePath, null, null).toASCIIString()
        val key = "DASH_BASE:$base"
        check(registered.size < MAX_REGISTERED_TARGETS) { "Manifest too large for local relay" }
        val newId = UUID.randomUUID().toString().replace("-", "")
        val id = targetIds.putIfAbsent(key, newId) ?: newId
        val headers = parent.headers.forUrl(parent.url, url, ::cookieForUrl)
        registered.putIfAbsent(id, RelayTarget(base, MediaType.UNKNOWN, headers, allowRelative = true))
        val suffix = buildString {
            append(tail)
            uri.rawQuery?.let { append('?').append(it) }
        }
        return "${relayUrl(id)}/$suffix"
    }

    private fun relayUrl(id: String): String {
        val active = session ?: error("Relay inactive")
        return "http://${active.hostAddress}:${active.port}/${active.token}/media/$id"
    }

    private inner class RelayServer(private val token: String) : NanoHTTPD(0) {
        override fun serve(request: IHTTPSession): Response {
            val prefix = "/$token/media/"
            if (!request.uri.startsWith(prefix)) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "")
            }
            val id = request.uri.removePrefix(prefix).substringBefore('/')
            val target = registered[id]
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "")
            if (request.method == Method.OPTIONS) {
                return corsResponse(Response.Status.NO_CONTENT, "text/plain", "")
            }
            if (request.method !in setOf(Method.GET, Method.HEAD)) {
                return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "text/plain", "")
            }
            return try {
                proxy(request, target.forRequest(request, prefix, id))
            } catch (error: Exception) {
                SafeLogger.warn(
                    "CAST_ERROR relay=${error.javaClass.simpleName} url=${SafeLogger.redactedUrl(target.url)}",
                    error,
                )
                corsResponse(Response.Status.SERVICE_UNAVAILABLE, "text/plain; charset=utf-8", "Upstream unavailable")
            }
        }
    }

    private fun proxy(incoming: IHTTPSession, target: RelayTarget): Response {
        SafeLogger.debug(
            "RELAY_REQUEST method=${incoming.method} type=${target.type} " +
                "range=${incoming.headers["range"] != null} url=${SafeLogger.redactedUrl(target.url)}"
        )
        val opened = openUpstream(incoming, target)
        val upstream = opened.response
        lastStatusCode = upstream.code
        val finalTarget = opened.target
        SafeLogger.debug(
            "RELAY_RESPONSE HTTP_STATUS=${upstream.code} type=${finalTarget.type} " +
                "mime=${upstream.header("Content-Type").orEmpty().substringBefore(';').take(80)} " +
                "url=${SafeLogger.redactedUrl(finalTarget.url)}"
        )
        if (finalTarget.type == MediaType.HLS && incoming.method == Method.GET && upstream.isSuccessful) {
            return hlsResponse(upstream, finalTarget)
        }
        if (finalTarget.type == MediaType.DASH && incoming.method == Method.GET && upstream.isSuccessful) {
            return dashResponse(upstream, finalTarget)
        }

        val status = Response.Status.lookup(upstream.code) ?: Response.Status.SERVICE_UNAVAILABLE
        val mime = upstream.header("Content-Type") ?: "application/octet-stream"
        if (incoming.method == Method.HEAD) {
            val response = newFixedLengthResponse(status, mime, "")
            copyHeaders(upstream, response, includeLength = true)
            upstream.close()
            return response
        }

        val body = upstream.body ?: run {
            upstream.close()
            return corsResponse(Response.Status.SERVICE_UNAVAILABLE, "text/plain", "")
        }
        val length = body.contentLength()
        val response = if (length >= 0) {
            newFixedLengthResponse(status, mime, body.byteStream(), length)
        } else {
            newChunkedResponse(status, mime, body.byteStream())
        }
        copyHeaders(upstream, response)
        return response
    }

    private fun hlsResponse(upstream: OkResponse, target: RelayTarget): Response {
        upstream.use { response ->
            val body = response.body
                ?: return corsResponse(Response.Status.SERVICE_UNAVAILABLE, "text/plain", "")
            val bytes = BoundedBodyReader.read(body.source(), MAX_MANIFEST_BYTES)
            if (bytes == null) {
                return corsResponse(Response.Status.PAYLOAD_TOO_LARGE, "text/plain", "")
            }
            val result = rewriter.rewriteTyped(bytes.toString(Charsets.UTF_8), target.url) { url, isPlaylist ->
                registerChild(url, target, isPlaylist)
            }
            if (result.isDrm) {
                return corsResponse(Response.Status.FORBIDDEN, "text/plain; charset=utf-8", "Encrypted HLS is not relayed")
            }
            val output = result.text.toByteArray()
            SafeLogger.debug(
                "${if (result.isMaster) "HLS_MASTER" else "HLS_VARIANT"} live=${result.isLive} refs=${result.referenceCount} " +
                    "url=${SafeLogger.redactedUrl(target.url)}"
            )
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/x-mpegURL",
                ByteArrayInputStream(output),
                output.size.toLong(),
            ).also {
                it.addHeader("Cache-Control", "no-store")
                addCorsHeaders(it)
            }
        }
    }

    private fun dashResponse(upstream: OkResponse, target: RelayTarget): Response {
        upstream.use { response ->
            val body = response.body
                ?: return corsResponse(Response.Status.SERVICE_UNAVAILABLE, "text/plain", "")
            val bytes = BoundedBodyReader.read(body.source(), MAX_MANIFEST_BYTES)
            if (bytes == null) {
                return corsResponse(Response.Status.PAYLOAD_TOO_LARGE, "text/plain", "")
            }
            val result = dashRewriter.rewrite(bytes.toString(Charsets.UTF_8), target.url) { url ->
                registerDashReference(url, target)
            }
            if (result.isDrm) {
                return corsResponse(Response.Status.FORBIDDEN, "text/plain; charset=utf-8", "DRM DASH is not relayed")
            }
            val output = result.text.toByteArray()
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/dash+xml",
                ByteArrayInputStream(output),
                output.size.toLong(),
            ).also {
                it.addHeader("Cache-Control", "no-store")
                addCorsHeaders(it)
            }
        }
    }

    private fun copyHeaders(from: OkResponse, to: Response, includeLength: Boolean = false) {
        val names = mutableListOf("Content-Range", "Accept-Ranges", "ETag", "Last-Modified")
        if (includeLength) names += "Content-Length"
        names.forEach { name -> from.header(name)?.let { to.addHeader(name, it) } }
        addCorsHeaders(to)
    }

    private fun openUpstream(incoming: IHTTPSession, initial: RelayTarget): OpenedUpstream {
        var target = initial
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val builder = Request.Builder().url(target.url)
            target.headers.asMap().forEach(builder::header)
            // A Range on a playlist can produce a syntactically valid but truncated manifest.
            // Manifests are always fetched whole; Range remains end-to-end for media resources.
            if (RelayRequestPolicy.shouldForwardRange(target.type)) {
                incoming.headers["range"]?.let { builder.header("Range", it) }
                incoming.headers["if-range"]?.let { builder.header("If-Range", it) }
            }
            if (incoming.method == Method.HEAD) builder.head()
            val response = client.newCall(builder.build()).execute()
            if (!response.isRedirect) return OpenedUpstream(response, target)

            val location = response.header("Location")
                ?: return OpenedUpstream(response, target)
            if (redirectCount >= MAX_REDIRECTS) {
                response.close()
                error("Too many upstream redirects")
            }
            val redirectedUrl = runCatching { URI(target.url).resolve(location).toASCIIString() }
                .getOrElse {
                    response.close()
                    throw IllegalArgumentException("Invalid upstream redirect")
                }
            if (!UrlValidator.isValidMediaUrl(redirectedUrl)) {
                response.close()
                throw IllegalArgumentException("Unsafe upstream redirect")
            }
            SafeLogger.debug(
                "HTTP_STATUS redirect=${response.code} from=${SafeLogger.redactedUrl(target.url)} " +
                    "to=${SafeLogger.redactedUrl(redirectedUrl)}"
            )
            response.close()
            target = target.copy(
                url = redirectedUrl,
                headers = target.headers.forUrl(target.url, redirectedUrl, ::cookieForUrl),
            )
        }
        error("Unreachable redirect state")
    }

    private fun cookieForUrl(url: String): String? = runCatching {
        CookieManager.getInstance().getCookie(url)
    }.getOrNull()?.takeIf(String::isNotBlank)

    private fun corsResponse(status: Response.Status, mime: String, body: String): Response =
        newFixedLengthResponse(status, mime, body).also(::addCorsHeaders)

    private fun addCorsHeaders(response: Response) {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Range, If-Range, Content-Type, Origin")
        response.addHeader("Access-Control-Expose-Headers", "Accept-Ranges, Content-Length, Content-Range, Content-Type")
    }

    private fun findLanAddress(): String? {
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
        val activeAddress = connectivity.activeNetwork
            ?.let(connectivity::getLinkProperties)
            ?.linkAddresses
            ?.asSequence()
            ?.map { it.address }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull(::isUsableLanAddress)
            ?.hostAddress
        if (activeAddress != null) return activeAddress

        return NetworkInterface.getNetworkInterfaces()?.toList()
            ?.asSequence()
            ?.filter { it.isUp && !it.isLoopback }
            ?.flatMap { it.inetAddresses.toList().asSequence() }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull(::isUsableLanAddress)
            ?.hostAddress
    }

    private fun isUsableLanAddress(address: Inet4Address): Boolean =
        !address.isLoopbackAddress && !address.isLinkLocalAddress && !address.isAnyLocalAddress &&
            !address.isMulticastAddress && address.isSiteLocalAddress

    private data class RelayTarget(
        val url: String,
        val type: MediaType,
        val headers: HeaderContext,
        val allowRelative: Boolean,
    ) {
        fun forRequest(request: IHTTPSession, prefix: String, id: String): RelayTarget {
            if (!allowRelative) return this
            val relativePath = request.uri.removePrefix(prefix).removePrefix(id).removePrefix("/")
            val relative = buildString {
                append(relativePath)
                request.queryParameterString?.takeIf(String::isNotBlank)?.let { append('?').append(it) }
            }
            val base = URI(url)
            val resolved = base.resolve(relative)
            require(resolved.scheme.equals(base.scheme, true) && resolved.host.equals(base.host, true) &&
                effectivePort(resolved) == effectivePort(base)
            ) { "Cross-origin DASH relay path refused" }
            require(resolved.rawPath.orEmpty().startsWith(base.rawPath.orEmpty())) {
                "DASH relay path escaped its registered directory"
            }
            require(UrlValidator.isValidMediaUrl(resolved.toASCIIString())) { "Invalid DASH relay path" }
            return copy(url = resolved.toASCIIString(), allowRelative = false)
        }

        private fun effectivePort(uri: URI): Int = when {
            uri.port >= 0 -> uri.port
            uri.scheme.equals("https", true) -> 443
            else -> 80
        }
    }

    private data class OpenedUpstream(val response: OkResponse, val target: RelayTarget)

    private companion object {
        const val MAX_MANIFEST_BYTES = 1_048_576
        const val MAX_REGISTERED_TARGETS = 10_000
        const val MAX_REDIRECTS = 8
    }
}
