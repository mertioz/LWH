package com.local.webcaster.relay

import java.io.StringReader
import java.io.StringWriter
import java.net.URI
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element
import org.xml.sax.InputSource

data class DashRewriteResult(val text: String, val isDrm: Boolean)

class DashRelayRewriter {
    fun rewrite(manifest: String, manifestUrl: String, relayUrlFor: (String) -> String): DashRewriteResult {
        require(!UNSAFE_XML.containsMatchIn(manifest)) { "DASH manifest declarations are not allowed" }
        val document = secureDocumentBuilder().parse(InputSource(StringReader(manifest)))
        require(document.documentElement?.localName == "MPD" || document.documentElement?.nodeName == "MPD") {
            "Invalid DASH manifest"
        }
        val protected = document.getElementsByTagNameNS("*", "ContentProtection").length > 0 ||
            document.getElementsByTagName("ContentProtection").length > 0
        if (protected) return DashRewriteResult(manifest, true)

        rewriteTree(document.documentElement, manifestUrl, hasRelayedBase = false, relayUrlFor)

        val transformerFactory = TransformerFactory.newInstance()
        runCatching { transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { transformerFactory.setAttribute(ACCESS_EXTERNAL_DTD, "") }
        runCatching { transformerFactory.setAttribute(ACCESS_EXTERNAL_STYLESHEET, "") }
        val transformer = transformerFactory.newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        }
        return DashRewriteResult(
            StringWriter().also { transformer.transform(DOMSource(document), StreamResult(it)) }.toString(),
            false,
        )
    }

    private fun rewriteTree(
        element: Element,
        inheritedBase: String,
        hasRelayedBase: Boolean,
        relayUrlFor: (String) -> String,
    ) {
        if (element.tag() == "Location") {
            val value = element.textContent?.trim().orEmpty()
            if (value.isNotEmpty()) element.textContent = relayUrlFor(resolve(inheritedBase, value))
            return
        }

        val children = (0 until element.childNodes.length)
            .mapNotNull { element.childNodes.item(it) as? Element }
        val baseElements = children.filter { it.tag() == "BaseURL" }
        val firstBase = baseElements.firstOrNull()?.textContent?.trim().orEmpty()
        val effectiveBase = if (firstBase.isNotEmpty()) resolve(inheritedBase, firstBase) else inheritedBase
        baseElements.forEach { baseElement ->
            val value = baseElement.textContent?.trim().orEmpty()
            if (value.isNotEmpty()) baseElement.textContent = relayUrlFor(resolve(inheritedBase, value))
        }
        val baseAvailable = hasRelayedBase || baseElements.isNotEmpty()

        URL_ATTRIBUTES.forEach { name ->
            val attribute = element.attributes?.getNamedItem(name) ?: return@forEach
            val value = attribute.nodeValue?.trim().orEmpty()
            if (value.isEmpty() || value.startsWith("data:", true)) return@forEach
            val absolute = value.startsWith("http://", true) || value.startsWith("https://", true)
            if (absolute || !baseAvailable) attribute.nodeValue = relayUrlFor(resolve(effectiveBase, value))
        }
        children.filterNot { it.tag() == "BaseURL" }
            .forEach { rewriteTree(it, effectiveBase, baseAvailable, relayUrlFor) }
    }

    private fun Element.tag(): String = localName ?: nodeName.substringAfter(':')

    private fun resolve(base: String, child: String): String = URI(base).resolve(child).toASCIIString()

    private fun secureDocumentBuilder(): DocumentBuilder {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { isXIncludeAware = false }
            runCatching { isExpandEntityReferences = false }
        }
        // Android XML providers differ in which hardening flags they implement. The lexical
        // declaration rejection above and the blocking entity resolver remain mandatory; these
        // provider flags add defense in depth without making valid MPDs unparseable on a device.
        listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
            "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
            XMLConstants.FEATURE_SECURE_PROCESSING to true,
        ).forEach { (name, value) -> runCatching { factory.setFeature(name, value) } }
        runCatching { factory.setAttribute(ACCESS_EXTERNAL_DTD, "") }
        runCatching { factory.setAttribute(ACCESS_EXTERNAL_SCHEMA, "") }
        return factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }
    }

    private companion object {
        val URL_ATTRIBUTES = listOf("media", "initialization", "sourceURL", "index")
        val UNSAFE_XML = Regex("<!\\s*(?:DOCTYPE|ENTITY)\\b", RegexOption.IGNORE_CASE)
        const val ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD"
        const val ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema"
        const val ACCESS_EXTERNAL_STYLESHEET = "http://javax.xml.XMLConstants/property/accessExternalStylesheet"
    }
}
