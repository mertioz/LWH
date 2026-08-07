package com.local.webcaster.security

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns

object PublicNetworkDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        Dns.SYSTEM.lookup(hostname)
            .filterNot(::isUnsafeAddress)
            .ifEmpty { throw UnknownHostException("Private upstream address refused") }

    fun isUnsafeAddress(address: InetAddress): Boolean =
        address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
}
