package com.local.webcaster.relay

import java.io.StringReader
import java.io.StringWriter
import java.net.URI
import javax.xml.XMLConstants
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
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(manifest)))
        require(document.documentElement?.localName == "MPD" || document.documentElement?.nodeName == "MPD") {
            "Invalid DASH manifest"
        }
        val protected = document.getElementsByTagNameNS("*", "ContentProtection").length > 0 ||
            document.getElementsByTagName("ContentProtection").length > 0
        if (protected) return DashRewriteResult(manifest, true)

        rewriteTree(document.documentElement, manifestUrl, hasRelayedBase = false, relayUrlFor)

        val transformer = TransformerFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        }.newTransformer().apply {
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

    private companion object {
        val URL_ATTRIBUTES = listOf("media", "initialization", "sourceURL", "index")
    }
}
