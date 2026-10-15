package dev.dhun.lyrics

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

internal actual fun readLyricsXml(input: String, sink: LyricsXmlSink) {
    val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
    // Token mode exposes DOCDECL before entity use; reject it even though DTD
    // processing is disabled. Android's pull parser never receives an entity
    // resolver, URL or file input. Unknown/custom entities are rejected below.
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
    parser.setInput(StringReader(input))
    while (true) {
        when (parser.nextToken()) {
            XmlPullParser.START_TAG -> {
                require(parser.attributeCount <= 32)
                sink.start(parser.namespace.orEmpty(), parser.name, (0 until parser.attributeCount).map {
                    LyricsXmlAttribute(parser.getAttributeNamespace(it).orEmpty(), parser.getAttributeName(it), parser.getAttributeValue(it))
                })
            }
            XmlPullParser.END_TAG -> sink.end()
            XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.IGNORABLE_WHITESPACE -> sink.text(parser.text.orEmpty())
            XmlPullParser.ENTITY_REF -> {
                // The tokenizer decodes predefined and numeric character references.
                // A custom unresolved entity must never be silently dropped.
                sink.text(requireNotNull(parser.text))
            }
            XmlPullParser.DOCDECL, XmlPullParser.PROCESSING_INSTRUCTION -> error("DTD/instructions forbidden")
            XmlPullParser.END_DOCUMENT -> return
        }
    }
}
