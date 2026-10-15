package dev.dhun.lyrics

import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler

internal actual fun readLyricsXml(input: String, sink: LyricsXmlSink) {
    val factory = SAXParserFactory.newInstance().apply {
        isNamespaceAware = true
        isXIncludeAware = false
        // Mandatory features: unsupported configuration rejects the input rather
        // than silently weakening XML security on an alternative JVM provider.
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    }
    val reader = factory.newSAXParser().xmlReader
    reader.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "")
    reader.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
    val handler = object : DefaultHandler() {
        override fun resolveEntity(publicId: String?, systemId: String?): InputSource = throw SAXException("External entities forbidden")
        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            require(attributes.length <= 32)
            sink.start(uri, localName, (0 until attributes.length).map {
                LyricsXmlAttribute(attributes.getURI(it), attributes.getLocalName(it), attributes.getValue(it))
            })
        }
        override fun characters(ch: CharArray, start: Int, length: Int) = sink.text(String(ch, start, length))
        override fun endElement(uri: String, localName: String, qName: String) = sink.end()
        override fun error(e: SAXParseException): Unit = throw e
        override fun fatalError(e: SAXParseException): Unit = throw e
        override fun processingInstruction(target: String, data: String): Unit = throw SAXException("Processing instructions unsupported")
    }
    reader.contentHandler = handler
    reader.errorHandler = handler
    reader.entityResolver = handler
    reader.parse(InputSource(StringReader(input)))
}
