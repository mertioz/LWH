package com.local.webcaster.security

import okio.Buffer
import okio.BufferedSource

object BoundedBodyReader {
    /** Returns null when the body exceeds maxBytes; short bodies are read to EOF without error. */
    fun read(source: BufferedSource, maxBytes: Int): ByteArray? {
        require(maxBytes > 0)
        val output = Buffer()
        var remainingWithSentinel = maxBytes.toLong() + 1L
        while (remainingWithSentinel > 0) {
            val read = source.read(output, minOf(remainingWithSentinel, READ_CHUNK_BYTES))
            if (read == -1L) return output.readByteArray()
            remainingWithSentinel -= read
        }
        return null
    }

    private const val READ_CHUNK_BYTES = 8_192L
}
